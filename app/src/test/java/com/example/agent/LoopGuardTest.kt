package com.example.agent

import com.example.agent.core.agent.ActionSignature
import com.example.agent.core.agent.LoopGuard
import com.example.agent.domain.model.ActionTarget
import com.example.agent.domain.model.AgentAction
import com.example.agent.domain.model.ToolNames
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LoopGuardTest {

    private fun click(label: String) =
        AgentAction(action = ToolNames.CLICK, target = ActionTarget(text = label))

    private fun check(guard: LoopGuard, action: AgentAction) =
        guard.checkCall(action, ActionSignature.of(action))

    // ---- identical repeats -----------------------------------------------------------

    @Test
    fun `the same tap is allowed a few times and then refused`() {
        val guard = LoopGuard()
        val action = click("Next")

        repeat(LoopGuard.MAX_IDENTICAL_RUNS_GENERIC) {
            assertThat(check(guard, action)).isEqualTo(LoopGuard.CallCheck.Allowed)
        }

        val refused = check(guard, action)
        assertThat(refused).isInstanceOf(LoopGuard.CallCheck.Refused::class.java)
        assertThat((refused as LoopGuard.CallCheck.Refused).toModel).contains("already")
    }

    @Test
    fun `a tool that should never repeat is refused sooner`() {
        val guard = LoopGuard()
        val action = AgentAction(action = ToolNames.OPEN_APP, packageName = "com.example.app")

        repeat(LoopGuard.MAX_IDENTICAL_RUNS) {
            assertThat(check(guard, action)).isEqualTo(LoopGuard.CallCheck.Allowed)
        }
        assertThat(check(guard, action)).isInstanceOf(LoopGuard.CallCheck.Refused::class.java)
    }

    @Test
    fun `a repeatable tool is never refused for repeating`() {
        val guard = LoopGuard()
        val action = AgentAction(action = ToolNames.PRESS_BACK)

        repeat(10) {
            assertThat(guard.checkCall(action, ActionSignature.of(action)))
                .isEqualTo(LoopGuard.CallCheck.Allowed)
        }
    }

    @Test
    fun `different arguments are counted separately`() {
        val guard = LoopGuard()

        repeat(LoopGuard.MAX_IDENTICAL_RUNS_GENERIC) { i ->
            assertThat(check(guard, click("Item $i"))).isEqualTo(LoopGuard.CallCheck.Allowed)
        }
    }

    // ---- repeat with no change -------------------------------------------------------

    @Test
    fun `repeating an action that left the screen unchanged is refused, then given up on`() {
        val guard = LoopGuard()
        val action = click("Play")
        val signature = ActionSignature.of(action)

        assertThat(guard.checkCall(action, signature)).isEqualTo(LoopGuard.CallCheck.Allowed)
        guard.noteObservation("screen A", signature)
        guard.noteObservation("screen A", null)

        val verdicts = (1..LoopGuard.MAX_REPEAT_REFUSALS).map { guard.checkCall(action, signature) }

        assertThat(verdicts.first()).isInstanceOf(LoopGuard.CallCheck.Refused::class.java)
        assertThat(verdicts.last()).isInstanceOf(LoopGuard.CallCheck.GiveUp::class.java)
    }

    @Test
    fun `an action is allowed again once the screen has changed`() {
        val guard = LoopGuard()
        val action = click("Play")
        val signature = ActionSignature.of(action)

        guard.checkCall(action, signature)
        guard.noteObservation("screen A", signature)
        guard.noteObservation("screen B", null)

        assertThat(guard.checkCall(action, signature)).isEqualTo(LoopGuard.CallCheck.Allowed)
    }

    // ---- stalls ----------------------------------------------------------------------

    @Test
    fun `a stalled screen is redirected twice and then the task is given up`() {
        val guard = LoopGuard()

        repeat(LoopGuard.MAX_STALL_RECOVERIES) {
            repeat(LoopGuard.MAX_STALLED_STEPS) { guard.noteScreenUnchanged() }
            assertThat(guard.checkBeforeStep()).isEqualTo(LoopGuard.StepCheck.Redirect)
        }

        repeat(LoopGuard.MAX_STALLED_STEPS) { guard.noteScreenUnchanged() }
        assertThat(guard.checkBeforeStep()).isInstanceOf(LoopGuard.StepCheck.GiveUp::class.java)
    }

    @Test
    fun `a redirect resets the stall count`() {
        val guard = LoopGuard()
        repeat(LoopGuard.MAX_STALLED_STEPS) { guard.noteScreenUnchanged() }
        guard.checkBeforeStep()

        assertThat(guard.checkBeforeStep()).isEqualTo(LoopGuard.StepCheck.Proceed)
    }

    @Test
    fun `an identical rendering counts as unchanged and a different one resets the count`() {
        val guard = LoopGuard()

        assertThat(guard.noteObservation("A", null)).isFalse()
        assertThat(guard.noteObservation("A", null)).isTrue()
        assertThat(guard.noteObservation("B", null)).isFalse()
        assertThat(guard.lastObservation).isEqualTo("B")
    }

    // ---- one tool, over and over -----------------------------------------------------

    @Test
    fun `hammering one tool ends the task`() {
        val guard = LoopGuard()

        repeat(LoopGuard.MAX_SAME_TOOL_STREAK) { guard.trackTool(ToolNames.CLICK) }

        val verdict = guard.checkBeforeStep()
        assertThat(verdict).isInstanceOf(LoopGuard.StepCheck.GiveUp::class.java)
        assertThat((verdict as LoopGuard.StepCheck.GiveUp).summary).contains(ToolNames.CLICK)
    }

    @Test
    fun `switching tool or using a repeatable tool breaks the streak`() {
        val guard = LoopGuard()

        repeat(LoopGuard.MAX_SAME_TOOL_STREAK - 1) { guard.trackTool(ToolNames.CLICK) }
        guard.trackTool(ToolNames.SCROLL)
        repeat(LoopGuard.MAX_SAME_TOOL_STREAK - 1) { guard.trackTool(ToolNames.CLICK) }

        assertThat(guard.checkBeforeStep()).isEqualTo(LoopGuard.StepCheck.Proceed)
    }
}
