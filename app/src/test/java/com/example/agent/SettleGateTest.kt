package com.example.agent

import com.example.agent.automation.accessibility.SettleGate
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The settle wait used to poll the view tree: two traversals of up to 80 nodes every 120 ms
 * for as long as four seconds, on every step. It now watches window-change events, so these
 * are the timing rules that replace that.
 */
class SettleGateTest {

    private val quiet = SettleGate.DEFAULT_QUIET_MS

    @Test
    fun `a screen that never changed settles after the quiet window`() {
        // lastChange = 0 means we have seen no change event at all.
        assertThat(SettleGate.isSettled(nowMillis = 1_000L, lastChangeMillis = 0L, startedAtMillis = 500L))
            .isTrue()
    }

    @Test
    fun `a change before the wait began does not count`() {
        // The wait starts at 1_000; a change at 900 is from before the action we are waiting
        // on, so the quiet window must start from 1_000, not 900.
        assertThat(SettleGate.isSettled(nowMillis = 1_200L, lastChangeMillis = 900L, startedAtMillis = 1_000L))
            .isFalse()
        assertThat(SettleGate.isSettled(nowMillis = 1_000L + quiet, lastChangeMillis = 900L, startedAtMillis = 1_000L))
            .isTrue()
    }

    @Test
    fun `each new change restarts the quiet window`() {
        val started = 1_000L
        // Changed at 1_400, so we are not settled at 1_700 even though the wait began at 1_000.
        assertThat(SettleGate.isSettled(nowMillis = 1_700L, lastChangeMillis = 1_400L, startedAtMillis = started))
            .isFalse()
        assertThat(SettleGate.isSettled(nowMillis = 1_900L, lastChangeMillis = 1_400L, startedAtMillis = started))
            .isTrue()
    }

    @Test
    fun `the wait is bounded`() {
        // One tick short of the limit is not yet a timeout.
        assertThat(SettleGate.isTimedOut(nowMillis = SettleGate.DEFAULT_TIMEOUT_MS - 1, startedAtMillis = 0L))
            .isFalse()
        assertThat(SettleGate.isTimedOut(nowMillis = SettleGate.DEFAULT_TIMEOUT_MS, startedAtMillis = 0L))
            .isTrue()
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
            assertThat(SettleGate.isSettled(now, lastChange, started)).isFalse()
        }
        assertThat(now).isAtLeast(SettleGate.DEFAULT_TIMEOUT_MS)
        // The old implementation did two tree walks per tick. This does none.
        assertThat(ticks).isAtMost((SettleGate.DEFAULT_TIMEOUT_MS / SettleGate.TICK_MS).toInt() + 1)
    }
}
