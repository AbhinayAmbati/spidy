package com.example.agent.automation.accessibility

/**
 * Decides when the screen has stopped changing.
 *
 * The wait used to poll the view tree — two traversals of up to 80 nodes every 120 ms for as
 * long as four seconds, on every step. It now watches the platform's window-change events, so
 * the decision is arithmetic on two timestamps rather than anything that touches the tree.
 *
 * Pure, so the timing rules are unit-testable.
 */
object SettleGate {

    /**
     * How long the screen must be quiet before we read it.
     *
     * Was 500 ms. Now that settling is driven by the platform's own change events rather than
     * by polling the tree, a shorter window is enough, and it is paid on every step.
     */
    const val DEFAULT_QUIET_MS = 300L

    /** Upper bound on the wait, in case the screen never settles. */
    const val DEFAULT_TIMEOUT_MS = 4_000L

    /** How often to re-check. Reads a volatile, so it is cheap enough to be responsive. */
    const val TICK_MS = 50L

    /**
     * @param lastChangeMillis when the screen last changed, on the uptime clock.
     * @param startedAtMillis when this wait began. A change from before the wait does not
     *   count, so the quiet window always starts from the action we are waiting on.
     */
    fun isSettled(
        nowMillis: Long,
        lastChangeMillis: Long,
        startedAtMillis: Long,
        quietMs: Long = DEFAULT_QUIET_MS,
    ): Boolean = nowMillis - maxOf(lastChangeMillis, startedAtMillis) >= quietMs

    fun isTimedOut(nowMillis: Long, startedAtMillis: Long, timeoutMs: Long = DEFAULT_TIMEOUT_MS): Boolean =
        nowMillis - startedAtMillis >= timeoutMs

    /**
     * Whether the screen changed at all since we started waiting.
     *
     * When it did not, the caller skips reading the screen: it is byte-for-byte what the model
     * already has, so both the traversal and the listing would be wasted.
     */
    fun changedDuring(lastChangeMillis: Long, startedAtMillis: Long): Boolean =
        lastChangeMillis >= startedAtMillis
}
