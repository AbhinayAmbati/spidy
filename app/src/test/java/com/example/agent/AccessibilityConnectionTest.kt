package com.example.agent

import com.example.agent.core.permissions.AccessibilityConnection
import com.example.agent.core.permissions.PermissionStatus
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Telling "not connected yet" apart from "not connected, and it needs you".
 *
 * The service runs inside our process, so closing the app kills it while the system setting
 * that says it is enabled survives. Every launch therefore begins enabled-but-not-connected,
 * and Android re-binds it a moment later. Reading that instant as a fault is what made the app
 * ask the user to toggle accessibility off and on every single time it was reopened.
 */
class AccessibilityConnectionTest {

    private fun status(enabled: Boolean, connected: Boolean) =
        PermissionStatus(
            accessibilityEnabled = enabled,
            accessibilityConnected = connected,
            notificationListenerEnabled = false,
            notificationsGranted = true,
        )

    @Test
    fun `a fresh process is not immediately stuck`() {
        // The exact state a launch begins in.
        assertThat(AccessibilityConnection.isStuck(0L, 0L)).isFalse()
    }

    @Test
    fun `it is not stuck one millisecond before the grace period ends`() {
        assertThat(AccessibilityConnection.isStuck(0L, AccessibilityConnection.GRACE_MS - 1)).isFalse()
    }

    @Test
    fun `it is stuck once the grace period has passed`() {
        assertThat(AccessibilityConnection.isStuck(0L, AccessibilityConnection.GRACE_MS)).isTrue()
    }

    @Test
    fun `it stays stuck long after`() {
        assertThat(AccessibilityConnection.isStuck(0L, 600_000L)).isTrue()
    }

    @Test
    fun `never having noticed a disconnection is never stuck`() {
        assertThat(AccessibilityConnection.isStuck(null, 600_000L)).isFalse()
    }

    @Test
    fun `enabled but not connected is the state worth waiting on`() {
        assertThat(AccessibilityConnection.isWaiting(status(enabled = true, connected = false))).isTrue()
    }

    @Test
    fun `connected is not worth waiting on`() {
        assertThat(AccessibilityConnection.isWaiting(status(enabled = true, connected = true))).isFalse()
    }

    @Test
    fun `switched off entirely is not worth waiting on`() {
        // There is nothing to re-bind: the user turned it off, so the card should say so now
        // rather than sit for six seconds pretending to wait.
        assertThat(AccessibilityConnection.isWaiting(status(enabled = false, connected = false))).isFalse()
    }

    @Test
    fun `the poll interval is shorter than the grace period, so it actually re-checks`() {
        assertThat(AccessibilityConnection.POLL_MS).isLessThan(AccessibilityConnection.GRACE_MS)
    }
}
