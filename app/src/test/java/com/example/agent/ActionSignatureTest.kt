package com.example.agent

import com.example.agent.core.agent.ActionSignature
import com.example.agent.domain.model.ActionTarget
import com.example.agent.domain.model.AgentAction
import com.example.agent.domain.model.ToolNames
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Loop detection keys off these signatures. The agent repeatedly re-opened Chrome and
 * re-typed the same search terms, so "the same action" has to be recognised reliably —
 * while genuinely repeatable tools must never be treated as a loop.
 */
class ActionSignatureTest {

    @Test
    fun `identical clicks on the same element share a signature`() {
        val a = AgentAction(ToolNames.CLICK, target = ActionTarget(text = "New tab"))
        val b = AgentAction(ToolNames.CLICK, target = ActionTarget(text = "New tab"))

        assertThat(ActionSignature.of(a)).isEqualTo(ActionSignature.of(b))
    }

    @Test
    fun `clicks on different elements differ`() {
        val a = AgentAction(ToolNames.CLICK, target = ActionTarget(text = "New tab"))
        val b = AgentAction(ToolNames.CLICK, target = ActionTarget(text = "Search"))

        assertThat(ActionSignature.of(a)).isNotEqualTo(ActionSignature.of(b))
    }

    @Test
    fun `reopening the same app is the same action`() {
        val a = AgentAction(ToolNames.OPEN_APP, packageName = "com.android.chrome")
        val b = AgentAction(ToolNames.OPEN_APP, packageName = "com.android.chrome")

        assertThat(ActionSignature.of(a)).isEqualTo(ActionSignature.of(b))
    }

    @Test
    fun `typing the same text twice is a repeat, different text is not`() {
        val first = AgentAction(ToolNames.TYPE_TEXT, text = "Kubernetes")
        val again = AgentAction(ToolNames.TYPE_TEXT, text = "Kubernetes")
        val other = AgentAction(ToolNames.TYPE_TEXT, text = "Kubernetes tutorials")

        assertThat(ActionSignature.of(first)).isEqualTo(ActionSignature.of(again))
        assertThat(ActionSignature.of(first)).isNotEqualTo(ActionSignature.of(other))
    }

    @Test
    fun `a target identified only by ref still produces a signature`() {
        val a = AgentAction(ToolNames.CLICK, target = ActionTarget(ref = 12))
        val b = AgentAction(ToolNames.CLICK, target = ActionTarget(ref = 12))
        val c = AgentAction(ToolNames.CLICK, target = ActionTarget(ref = 13))

        assertThat(ActionSignature.of(a)).isEqualTo(ActionSignature.of(b))
        assertThat(ActionSignature.of(a)).isNotEqualTo(ActionSignature.of(c))
    }

    @Test
    fun `repeatable tools are never treated as a loop`() {
        val repeatable = listOf(
            AgentAction(ToolNames.SCROLL, direction = "forward"),
            AgentAction(ToolNames.SWIPE, direction = "up"),
            AgentAction(ToolNames.WAIT, durationMs = 1000),
            AgentAction(ToolNames.READ_SCREEN),
            AgentAction(ToolNames.PRESS_BACK),
            AgentAction(ToolNames.PRESS_HOME),
            AgentAction(ToolNames.GET_NOTIFICATIONS),
        )

        repeatable.forEach { assertThat(ActionSignature.of(it)).isNull() }
    }

    @Test
    fun `scrolling twice in a row is allowed`() {
        // The common case: paging down a list. This must not be blocked as a repeat.
        assertThat(ActionSignature.of(AgentAction(ToolNames.SCROLL, direction = "forward"))).isNull()
        assertThat(ActionSignature.of(AgentAction(ToolNames.SCROLL, direction = "forward"))).isNull()
    }
}
