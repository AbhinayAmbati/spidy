package com.example.agent.core.permissions

/**
 * Accessibility has two distinct states and they are not the same thing:
 * the service can be switched on in system settings yet still not be bound.
 * Treating them as one is what makes the app claim it is working when it is not.
 */
data class PermissionStatus(
    /** Switched on in system settings. */
    val accessibilityEnabled: Boolean,
    /** Actually bound to the system, i.e. running. */
    val accessibilityConnected: Boolean,
    val notificationListenerEnabled: Boolean,
    val notificationsGranted: Boolean,
) {
    // There is deliberately no `accessibilityStuck` here any more. "Enabled but not
    // connected" is the NORMAL state for the first seconds of every launch, because the
    // service dies with our process while the system setting survives. Whether it is a fault
    // depends on how long it lasts, which is not something a snapshot can answer - see
    // AccessibilityConnection.

    /** True only when the agent can really see the screen. */
    val accessibilityReady: Boolean get() = accessibilityEnabled && accessibilityConnected
}
