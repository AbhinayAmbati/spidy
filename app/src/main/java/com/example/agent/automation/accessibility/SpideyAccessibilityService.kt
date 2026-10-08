package com.example.agent.automation.accessibility

import android.accessibilityservice.AccessibilityService
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
            AccessibilityBridge.notifyEvent(
                packageName = event?.packageName?.toString(),
                eventType = event?.eventType ?: 0,
            )
        }
    }

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

    data class UiEvent(val packageName: String?, val eventType: Int, val atMillis: Long)

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
