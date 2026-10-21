package com.example.agent.core.agent

import android.view.accessibility.AccessibilityEvent
import com.example.agent.automation.accessibility.AccessibilityBridge
import com.example.agent.automation.accessibility.UserTouchGate
import com.example.agent.data.preferences.SettingsRepository
import com.example.agent.domain.model.AwaitingKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Touch-to-stop. If the user puts a finger on the screen while the agent is working, they
 * have taken over, so the task stops.
 *
 * Every reason *not* to stop lives in [UserTouchGate]: disabled by the user, the agent
 * waiting on an answer, and the opening seconds of a task during which the tap that started
 * it is still being delivered.
 */
class TouchToStopWatcher(private val settings: SettingsRepository) {

    @Volatile
    private var enabled: Boolean = true

    fun watch(runtime: AgentRuntime, scope: CoroutineScope) {
        // Kept hot so the touch handler never has to hit DataStore on a touch.
        scope.launch {
            settings.touchToStop.collect { enabled = it }
        }

        scope.launch {
            AccessibilityBridge.userTouches.collect { eventTimeMillis ->
                if (!runtime.isRunning) return@collect
                val stop = UserTouchGate.shouldStopForUserTouch(
                    // The platform's own constant, not a mirrored number.
                    eventType = AccessibilityEvent.TYPE_TOUCH_INTERACTION_START,
                    // When the touch happened, not when the event reached us.
                    eventTimeMillis = eventTimeMillis,
                    suppressUntilMillis = AccessibilityBridge.suppressUntilMillis(),
                    taskStartedAtMillis = runtime.startedAtMillis,
                    enabled = enabled,
                    awaitingUser = runtime.progress.value.awaitingKind != AwaitingKind.None,
                )
                if (stop) runtime.stop(STOPPED_BY_TOUCH)
            }
        }
    }

    private companion object {
        /** Shown when touch-to-stop fires. */
        const val STOPPED_BY_TOUCH = "Stopped: you touched the screen."
    }
}
