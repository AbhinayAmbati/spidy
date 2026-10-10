package com.example.agent.core.permissions

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Watches accessibility and answers the only question the UI actually has: is this a state the
 * user needs to fix, or one that is about to fix itself?
 *
 * It keeps re-reading while the service has not attached, so the moment Android re-binds it
 * the warning disappears on its own - the user never has to visit system settings for a
 * transient. Only a state that survives [AccessibilityConnection.GRACE_MS] is reported as
 * stuck.
 *
 * Takes a [CoroutineScope] rather than owning one, so a ViewModel can pass `viewModelScope`
 * and have the watching stop with the screen.
 */
class AccessibilityWatch(
    context: Context,
    private val scope: CoroutineScope,
) {

    private val appContext = context.applicationContext

    private val _status = MutableStateFlow(PermissionUtils.status(appContext))
    val status: StateFlow<PermissionStatus> = _status.asStateFlow()

    private val _stuck = MutableStateFlow(false)
    val stuck: StateFlow<Boolean> = _stuck.asStateFlow()

    private var job: Job? = null

    /** Re-read now, and keep re-reading for a short while if it has not attached yet. */
    fun refresh() {
        job?.cancel()
        val current = PermissionUtils.status(appContext)
        _status.value = current

        if (!AccessibilityConnection.isWaiting(current)) {
            _stuck.value = false
            return
        }

        val startedAt = System.currentTimeMillis()
        job = scope.launch {
            while (isActive) {
                delay(AccessibilityConnection.POLL_MS)
                val now = PermissionUtils.status(appContext)
                _status.value = now

                if (!AccessibilityConnection.isWaiting(now)) {
                    // Re-bound, or the user switched it off. Either way, not stuck.
                    _stuck.value = false
                    return@launch
                }
                if (AccessibilityConnection.isStuck(startedAt, System.currentTimeMillis())) {
                    _stuck.value = true
                    return@launch
                }
            }
        }
    }
}
