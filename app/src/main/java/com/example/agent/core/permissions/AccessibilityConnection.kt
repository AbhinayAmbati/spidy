package com.example.agent.core.permissions

/**
 * Decides whether accessibility is genuinely *stuck*, as opposed to merely not connected *yet*.
 *
 * The two look identical from a single reading, and telling them apart is the whole problem.
 *
 * An accessibility service runs **inside our own process**. Closing the app kills the process,
 * and the service dies with it - while `ENABLED_ACCESSIBILITY_SERVICES`, the system setting
 * that records the user's choice, survives untouched. So the instant a fresh process starts
 * it sees exactly this:
 *
 *     accessibilityEnabled   = true    (a system setting, still set)
 *     accessibilityConnected = false   (our in-process handle is new and empty)
 *
 * Android re-binds enabled services shortly after the process comes back. That makes
 * "enabled but not connected" the *normal* state for the first few seconds of every launch -
 * and treating it as a fault is what made the app demand a toggle off and on every single time
 * it was reopened.
 *
 * So the state is only a fault if it persists. [isStuck] is deliberately a pure function of
 * elapsed time, so that behaviour can be tested without a device.
 */
object AccessibilityConnection {

    /**
     * How long a freshly started process waits for the service to re-attach before calling it
     * stuck. Generous on purpose: the cost of waiting is a few seconds of an absent warning,
     * and the cost of not waiting is telling the user to fix something that is not broken.
     */
    const val GRACE_MS = 6_000L

    /** How often to look again while waiting. */
    const val POLL_MS = 400L

    /**
     * @param disconnectedSinceMillis when enabled-but-not-connected was first noticed, or
     *   null when that is not the current state.
     * @return true only once it has stayed that way past [GRACE_MS].
     */
    fun isStuck(disconnectedSinceMillis: Long?, nowMillis: Long): Boolean =
        disconnectedSinceMillis != null && nowMillis - disconnectedSinceMillis >= GRACE_MS

    /** Whether a status is the transient kind that is worth waiting on at all. */
    fun isWaiting(status: PermissionStatus): Boolean =
        status.accessibilityEnabled && !status.accessibilityConnected
}
