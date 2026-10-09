package com.example.agent

import com.example.agent.automation.accessibility.SettleGate
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The settle wait used to poll the view tree: two traversals of up to 80 nodes every 120 ms
 * for as long as four seconds, on every step. It now watches window-change events, so these
 * are the timing rules that replace it.
 *
 * Every assertion is expressed in terms of the constants rather than literal milliseconds —
 * an earlier version hardcoded 500 ms and broke the moment the quiet window changed.
 */
class SettleGateTest {

    private val quiet = SettleGate.DEFAULT_QUIET_MS
    private val timeout = SettleGate.DEFAULT_TIMEOUT_MS

    private fun settled(now: Long, lastChange: Long, started: Long) =
        SettleGate.isSettled(now, lastChange, started)

    @Test
    fun `a screen that never changed settles after the quiet window`() {
        // lastChange = 0 means no change event has ever arrived.
        assertThat(settled(now = 1_000L + quiet, lastChange = 0L, started = 1_000L)).isTrue()
    }

    @Test
    fun `a change before the wait began does not count`() {
        // The wait starts at 1_000. A change at 900 belongs to whatever happened before the
        // action we are waiting on, so the quiet window must start from 1_000.
        val started = 1_000L
        assertThat(settled(now = started + quiet - 1, lastChange = 900L, started = started)).isFalse()
        assertThat(settled(now = started + quiet, lastChange = 900L, started = started)).isTrue()
    }

    @Test
    fun `each new change restarts the quiet window`() {
        val started = 1_000L
        val changedAt = 1_400L

        assertThat(settled(now = changedAt + quiet - 1, lastChange = changedAt, started = started)).isFalse()
        assertThat(settled(now = changedAt + quiet, lastChange = changedAt, started = started)).isTrue()
    }

    @Test
    fun `a change at or after the wait started counts`() {
        assertThat(SettleGate.changedDuring(lastChangeMillis = 1_500L, startedAtMillis = 1_000L)).isTrue()
        assertThat(SettleGate.changedDuring(lastChangeMillis = 1_000L, startedAtMillis = 1_000L)).isTrue()
    }

    @Test
    fun `a change from before the wait does not count`() {
        // Otherwise an action that did nothing would look like it changed the screen, and we
        // would spend a traversal and a listing re-reading an identical screen.
        assertThat(SettleGate.changedDuring(lastChangeMillis = 999L, startedAtMillis = 1_000L)).isFalse()
        assertThat(SettleGate.changedDuring(lastChangeMillis = 0L, startedAtMillis = 1_000L)).isFalse()
    }

    @Test
    fun `the wait is bounded`() {
        assertThat(SettleGate.isTimedOut(nowMillis = timeout - 1, startedAtMillis = 0L)).isFalse()
        assertThat(SettleGate.isTimedOut(nowMillis = timeout, startedAtMillis = 0L)).isTrue()
    }

    @Test
    fun `a screen that never stops changing still times out`() {
        val started = 0L
        var now = started
        var lastChange = started
        var ticks = 0
        while (!SettleGate.isTimedOut(now, started)) {
            now += SettleGate.TICK_MS
            lastChange = now // something is always changing
            ticks++
            assertThat(settled(now, lastChange, started)).isFalse()
        }
        assertThat(now).isAtLeast(timeout)
        // The old implementation did two tree walks per tick. This does none.
        assertThat(ticks).isAtMost((timeout / SettleGate.TICK_MS).toInt() + 1)
    }
}
