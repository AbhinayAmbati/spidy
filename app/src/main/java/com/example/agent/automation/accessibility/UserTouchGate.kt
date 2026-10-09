package com.example.agent.automation.accessibility

/**
 * Decides whether an accessibility event means "the user touched the screen".
 *
 * Touch-to-stop only works if a real finger can be told apart from the agent's own gesture:
 * `dispatchGesture` produces exactly the same platform events as a finger does. So every
 * action the agent performs opens a short suppression window, and anything outside it is
 * the user.
 *
 * The platform's event-type constant is mirrored rather than referenced, so this stays a
 * pure decision that can be unit-tested off-device.
 */
object UserTouchGate {

    /**
     * Mirrors android.view.accessibility.AccessibilityEvent.TYPE_TOUCH_INTERACTION_START.
     *
     * This was `0x00000020`, which is TYPE_WINDOW_STATE_CHANGED - so touch-to-stop fired on
     * window changes rather than touches, and opening an app stopped the task. The platform
     * value is 0x00100000. Production code now passes the platform's own constant in
     * (see [isUserTouch]'s `touchType`), so this mirror is only the default for tests; a test
     * pins the exact value so the two cannot silently diverge again.
     */
    const val TYPE_TOUCH_INTERACTION_START = 0x00100000

    /**
     * Extra time after a gesture during which the platform may still be delivering events
     * caused by it. Generous on purpose: a false stop is far worse than a missed one.
     */
    const val GESTURE_GRACE_MS = 700L

    /** Window for actions that do not inject a timed gesture (a semantic click, say). */
    const val ACTION_WINDOW_MS = 800L

    /**
     * How long after a task starts before touch-to-stop arms.
     *
     * The tap that *starts* a task is itself a touch, and accessibility events are batched
     * (the service asks for a 300 ms notification timeout), so it can be delivered after the
     * task is already running. Without this window, starting a task would immediately stop
     * it.
     */
    const val ARM_DELAY_MS = 2_500L

    /**
     * @param eventTimeMillis when the touch *happened*, from `AccessibilityEvent.getEventTime()`.
     *   It must not be the moment we heard about it: events are delivered late under load, and
     *   on a slow run a late-delivered event from the agent's own gesture was being mistaken
     *   for the user taking over. `getEventTime()` uses the `SystemClock.uptimeMillis()` base,
     *   so every clock in this decision must be too.
     * @return true when this event is a real user touch rather than one of ours.
     */
    fun isUserTouch(
        eventType: Int,
        eventTimeMillis: Long,
        suppressUntilMillis: Long,
        touchType: Int = TYPE_TOUCH_INTERACTION_START,
    ): Boolean =
        eventType == touchType &&
            eventTimeMillis > 0 &&
            eventTimeMillis >= suppressUntilMillis

    /** @return true once a task has been running long enough for touch-to-stop to be armed. */
    fun isArmed(nowMillis: Long, taskStartedAtMillis: Long): Boolean =
        nowMillis - taskStartedAtMillis >= ARM_DELAY_MS

    /**
     * The whole decision in one place: should this event stop the running task?
     *
     * Every reason not to stop is here rather than scattered through the runtime, because
     * a false stop is far more damaging than a missed one — it breaks the app for the user.
     */
    fun shouldStopForUserTouch(
        eventType: Int,
        eventTimeMillis: Long,
        suppressUntilMillis: Long,
        taskStartedAtMillis: Long,
        enabled: Boolean,
        awaitingUser: Boolean,
        touchType: Int = TYPE_TOUCH_INTERACTION_START,
    ): Boolean {
        if (!enabled) return false
        // While the agent is asking something, a touch is the answer, not an interruption.
        if (awaitingUser) return false
        // The tap that started the task, arriving late.
        if (!isArmed(eventTimeMillis, taskStartedAtMillis)) return false
        return isUserTouch(eventType, eventTimeMillis, suppressUntilMillis, touchType)
    }

    /** The instant a suppression window for an action of [durationMs] should end. */
    fun suppressUntil(nowMillis: Long, durationMs: Long): Long =
        nowMillis + durationMs + GESTURE_GRACE_MS
}
