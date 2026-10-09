package com.example.agent.automation.accessibility

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * The single AccessibilityService in the app.
 *
 * It does nothing clever on its own — it exposes a live connection to
 * [AccessibilityController] via [AccessibilityBridge] and records a coarse
 * "something changed" signal.
 *
 * Everything here is defensive on purpose. Android treats an accessibility service that
 * throws as malfunctioning and disables it, so a bug in our code must never be allowed
 * to escape a callback.
 */
class SpideyAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        AccessibilityBridge.attach(this)
        super.onServiceConnected()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // This fires very often. Nothing consumes the signal today (the observer polls
        // instead), so it only records the last event — deliberately cheap.
        runCatching {
            val type = event?.eventType ?: 0

            // Platform constants, never a mirrored number: getting one of these wrong once
            // made touch-to-stop fire on window changes.
            if (isScreenChange(type)) AccessibilityBridge.noteScreenChanged(event?.eventTime ?: 0L)

            // Checked before anything else: a real touch means the user has taken over.
            // eventTime, not "now" - see UserTouchGate.isUserTouch.
            AccessibilityBridge.onTouchEvent(
                eventType = type,
                touchType = AccessibilityEvent.TYPE_TOUCH_INTERACTION_START,
                eventTimeMillis = event?.eventTime ?: 0L,
            )
            AccessibilityBridge.notifyEvent(
                packageName = event?.packageName?.toString(),
                eventType = type,
            )
        }
    }

    /** Window-level changes: what "the screen has settled" is judged against. */
    private fun isScreenChange(type: Int): Boolean =
        type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            type == AccessibilityEvent.TYPE_WINDOWS_CHANGED

    override fun onInterrupt() {
        // Nothing to interrupt: the agent loop drives actions explicitly.
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        AccessibilityBridge.detach(this)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        AccessibilityBridge.detach(this)
        super.onDestroy()
    }
}

/**
 * Process-wide handle to the running accessibility service. Everything that wants
 * to touch the UI goes through here, so there is exactly one place that holds the
 * service reference.
 */
object AccessibilityBridge {

    @Volatile
    private var service: SpideyAccessibilityService? = null

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    /**
     * Emitted when the user touches the screen while the agent is working, carrying *when*
     * the touch happened (`AccessibilityEvent.getEventTime()`, on the uptime clock).
     */
    private val _userTouches = MutableSharedFlow<Long>(extraBufferCapacity = 4)
    val userTouches: SharedFlow<Long> = _userTouches.asSharedFlow()

    data class UiEvent(val packageName: String?, val eventType: Int, val atMillis: Long)

    @Volatile
    private var suppressTouchesUntilMillis: Long = 0L

    /**
     * Opens a suppression window for an action the agent is about to perform, so the touch
     * events it generates are not mistaken for the user taking over.
     */
    fun suppressTouchesFor(durationMs: Long) {
        val until = UserTouchGate.suppressUntil(SystemClock.uptimeMillis(), durationMs)
        // Keep the longest window: a gesture nested inside a click must not shorten it.
        if (until > suppressTouchesUntilMillis) suppressTouchesUntilMillis = until
    }

    fun suppressUntilMillis(): Long = suppressTouchesUntilMillis

    /**
     * @param eventTimeMillis `AccessibilityEvent.getEventTime()` - when the touch happened,
     *   not when we were told about it.
     * @return true if this was a real user touch, which has now been signalled.
     */
    fun onTouchEvent(eventType: Int, touchType: Int, eventTimeMillis: Long): Boolean {
        if (!UserTouchGate.isUserTouch(eventType, eventTimeMillis, suppressTouchesUntilMillis, touchType)) {
            return false
        }
        _userTouches.tryEmit(eventTimeMillis)
        return true
    }

    // ---------------------------------------------------------------- settling

    /**
     * When the screen last changed, from the platform's own window-change events.
     *
     * This is what replaced polling the view tree to decide the screen had settled. The
     * old poll walked up to 80 nodes every 120 ms for as long as 4 seconds, on every step.
     */
    @Volatile
    private var lastScreenChangeAtMillis: Long = 0L

    fun noteScreenChanged(eventTimeMillis: Long) {
        val at = if (eventTimeMillis > 0) eventTimeMillis else SystemClock.uptimeMillis()
        if (at > lastScreenChangeAtMillis) lastScreenChangeAtMillis = at
    }

    fun lastScreenChangeMillis(): Long = lastScreenChangeAtMillis

    fun attach(service: SpideyAccessibilityService) {
        this.service = service
    }

    fun detach(service: SpideyAccessibilityService) {
        if (this.service === service) this.service = null
    }

    fun notifyEvent(packageName: String?, eventType: Int) {
        _events.tryEmit(UiEvent(packageName, eventType, System.currentTimeMillis()))
    }

    fun serviceOrNull(): SpideyAccessibilityService? = service

    fun isConnected(): Boolean = service != null
}
