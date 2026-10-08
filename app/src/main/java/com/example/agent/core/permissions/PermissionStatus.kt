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
    /** Enabled in settings but not bound — usually needs a toggle off and on again. */
    val accessibilityStuck: Boolean get() = accessibilityEnabled && !accessibilityConnected

    /** True only when the agent can really see the screen. */
    val accessibilityReady: Boolean get() = accessibilityEnabled && accessibilityConnected
}
