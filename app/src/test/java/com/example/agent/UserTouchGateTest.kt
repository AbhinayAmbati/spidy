package com.example.agent

import com.example.agent.automation.accessibility.UserTouchGate
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Touch-to-stop only works if a real finger can be told from the agent's own gesture —
 * `dispatchGesture` raises exactly the same platform events. A false stop is much worse
 * than a missed one, so the window is deliberately generous.
 */
class UserTouchGateTest {

    private val touch = UserTouchGate.TYPE_TOUCH_INTERACTION_START
    private val windowChange = 0x00001000 // TYPE_WINDOW_CONTENT_CHANGED

    @Test
    fun `the mirrored touch constant matches the platform value`() {
        // This was 0x00000020, which is TYPE_WINDOW_STATE_CHANGED. Touch-to-stop therefore
        // fired on window changes rather than touches, and opening an app - a window change -
        // stopped the task. Pinned so the mirror cannot drift again.
        assertThat(UserTouchGate.TYPE_TOUCH_INTERACTION_START).isEqualTo(0x00100000)
    }

    @Test
    fun `window changes are never mistaken for a touch`() {
        // The exact bug, as a regression test: these are the events that fire when an app is
        // opened or its content changes.
        val windowStateChanged = 0x00000020
        val windowContentChanged = 0x00000800
        val windowsChanged = 0x00400000

        listOf(windowStateChanged, windowContentChanged, windowsChanged).forEach { type ->
            assertThat(UserTouchGate.isUserTouch(type, eventTimeMillis = 10_000L, suppressUntilMillis = 0L))
                .isFalse()
        }
    }

    @Test
    fun `a touch with no recent agent action is the user`() {
        assertThat(UserTouchGate.isUserTouch(touch, eventTimeMillis = 10_000L, suppressUntilMillis = 0L))
            .isTrue()
    }

    @Test
    fun `a touch during the agent's own action is not the user`() {
        val start = 10_000L
        val until = UserTouchGate.suppressUntil(start, durationMs = 300L)

        // The event the agent's own gesture raises, a moment later.
        assertThat(UserTouchGate.isUserTouch(touch, eventTimeMillis = start + 250L, suppressUntilMillis = until))
            .isFalse()
    }

    @Test
    fun `a touch after the window has closed is the user again`() {
        val start = 10_000L
        val until = UserTouchGate.suppressUntil(start, durationMs = 300L)

        assertThat(UserTouchGate.isUserTouch(touch, eventTimeMillis = until, suppressUntilMillis = until))
            .isTrue()
    }

    @Test
    fun `the window covers the gesture and then some grace`() {
        val until = UserTouchGate.suppressUntil(nowMillis = 1_000L, durationMs = 300L)

        assertThat(until).isEqualTo(1_000L + 300L + UserTouchGate.GESTURE_GRACE_MS)
        // Long literals throughout: Truth's LongSubject overloads isGreaterThan/isAtLeast for
        // both Long and Int, so an Int literal is ambiguous.
        assertThat(until).isGreaterThan(1_300L) // never shorter than the gesture itself
    }

    @Test
    fun `a long swipe keeps a long window`() {
        val until = UserTouchGate.suppressUntil(nowMillis = 0L, durationMs = 3_000L)

        assertThat(until).isAtLeast(3_000L)
    }

    @Test
    fun `other event types are never mistaken for a touch`() {
        listOf(windowChange, 0, 1, 0x00000001, 0x00000010).forEach { type ->
            assertThat(UserTouchGate.isUserTouch(type, eventTimeMillis = 10_000L, suppressUntilMillis = 0L))
                .isFalse()
        }
    }

    // ---------------------------------------------------------------- the full decision

    private val touchType = UserTouchGate.TYPE_TOUCH_INTERACTION_START
    private val longAgo = 0L
    private val wellAfterArming = UserTouchGate.ARM_DELAY_MS + 60_000L

    private fun decide(
        eventType: Int = touchType,
        eventTime: Long = wellAfterArming,
        suppressUntil: Long = 0L,
        taskStartedAt: Long = 0L,
        enabled: Boolean = true,
        awaitingUser: Boolean = false,
    ) = UserTouchGate.shouldStopForUserTouch(
        eventType = eventType,
        eventTimeMillis = eventTime,
        suppressUntilMillis = suppressUntil,
        taskStartedAtMillis = taskStartedAt,
        enabled = enabled,
        awaitingUser = awaitingUser,
    )

    @Test
    fun `a real touch on a running task stops it`() {
        assertThat(decide()).isTrue()
    }

    @Test
    fun `turning the setting off stops it stopping`() {
        assertThat(decide(enabled = false)).isFalse()
    }

    @Test
    fun `the opening seconds of a task are not armed`() {
        // The tap that started the task arrives late, batched by the platform. Without this
        // window, starting a task would immediately stop it.
        assertThat(decide(eventTime = 0L, taskStartedAt = 0L)).isFalse()
        assertThat(decide(eventTime = UserTouchGate.ARM_DELAY_MS - 1, taskStartedAt = 0L)).isFalse()
    }

    @Test
    fun `arming happens exactly at the delay`() {
        assertThat(decide(eventTime = UserTouchGate.ARM_DELAY_MS, taskStartedAt = 0L)).isTrue()
    }

    @Test
    fun `a touch while the agent is waiting on the user is the answer, not a stop`() {
        assertThat(decide(awaitingUser = true)).isFalse()
    }

    @Test
    fun `the agent's own gesture does not stop it, once armed`() {
        assertThat(decide(eventTime = 10_000L, suppressUntil = 11_000L)).isFalse()
    }

    @Test
    fun `a non-touch event never stops it`() {
        assertThat(decide(eventType = 0x00001000)).isFalse()
    }

    @Test
    fun `a late-delivered event from the agent's own gesture is still suppressed`() {
        // The bug the user hit. The agent taps at t=10_000 and the gesture lasts 300ms, so
        // the window closes at 11_000. The platform then delivers the event late - the run
        // was slow - at t=14_000. Judged by delivery time the window looks long expired and
        // the agent's own movement reads as the user. Judged by when it happened, it does not.
        val until = UserTouchGate.suppressUntil(nowMillis = 10_000L, durationMs = 300L)

        assertThat(UserTouchGate.isUserTouch(touch, eventTimeMillis = 10_100L, suppressUntilMillis = until))
            .isFalse()
        // Same event time, but the agent had already finished by then, so it IS the user.
        assertThat(UserTouchGate.isUserTouch(touch, eventTimeMillis = until, suppressUntilMillis = until))
            .isTrue()
    }

    @Test
    fun `an event with no usable timestamp never stops the task`() {
        // getEventTime() should always be set; if it is not, do not stop on a guess.
        assertThat(decide(eventTime = 0L, taskStartedAt = 0L)).isFalse()
        assertThat(decide(eventTime = -1L)).isFalse()
    }

    @Test
    fun `every guard is required`() {
        // Any single guard being off must be enough to prevent a stop.
        assertThat(decide(enabled = false)).isFalse()
        assertThat(decide(awaitingUser = true)).isFalse()
        assertThat(decide(eventTime = 0L, taskStartedAt = 0L)).isFalse()
        assertThat(decide(eventType = 0)).isFalse()
        assertThat(decide(suppressUntil = wellAfterArming + 1)).isFalse()
        assertThat(decide()).isTrue() // ...and with all of them satisfied, it stops
    }

    @Test
    fun `a stale window from a previous action does not suppress a later touch`() {
        val until = UserTouchGate.suppressUntil(nowMillis = 1_000L, durationMs = 300L)

        // Well after the action finished, a finger is a finger.
        assertThat(UserTouchGate.isUserTouch(touch, eventTimeMillis = 60_000L, suppressUntilMillis = until))
            .isTrue()
    }
}
