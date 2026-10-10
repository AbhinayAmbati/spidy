package com.example.agent

import com.example.agent.core.agent.ActionSignature
import com.example.agent.domain.model.ActionTarget
import com.example.agent.domain.model.AgentAction
import com.example.agent.domain.model.ToolNames
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Loop detection.
 *
 * Two different rules live here, and they catch different failures. The signature is the key
 * both use; [ActionSignature.NEVER_REPEATED_TOOLS] is the set of tools that must never run
 * twice with identical arguments in one task, because the screenshots that prompted this show
 * "Opening com.android.chrome" five times and "Typing Kubernetes" six.
 */
class ActionSignatureTest {

    @Test
    fun `the same action and arguments give the same key`() {
        val a = AgentAction(action = ToolNames.CLICK, target = ActionTarget(text = "Next"))
        val b = AgentAction(action = ToolNames.CLICK, target = ActionTarget(text = "Next"))

        assertThat(ActionSignature.of(a)).isEqualTo(ActionSignature.of(b))
    }

    @Test
    fun `a different target gives a different key`() {
        val next = AgentAction(action = ToolNames.CLICK, target = ActionTarget(text = "Next"))
        val back = AgentAction(action = ToolNames.CLICK, target = ActionTarget(text = "Back"))

        assertThat(ActionSignature.of(next)).isNotEqualTo(ActionSignature.of(back))
    }

    @Test
    fun `a different action gives a different key`() {
        val click = AgentAction(action = ToolNames.CLICK, target = ActionTarget(text = "Go"))
        val long = AgentAction(action = ToolNames.LONG_CLICK, target = ActionTarget(text = "Go"))

        assertThat(ActionSignature.of(click)).isNotEqualTo(ActionSignature.of(long))
    }

    @Test
    fun `legitimately repeatable tools have no key at all`() {
        // These are expected to be called over and over in the same form on purpose.
        val repeatable = listOf(
            AgentAction(action = ToolNames.SCROLL, direction = "down"),
            AgentAction(action = ToolNames.SWIPE, direction = "up"),
            AgentAction(action = ToolNames.WAIT, durationMs = 500),
            AgentAction(action = ToolNames.READ_SCREEN),
            AgentAction(action = ToolNames.PRESS_BACK),
            AgentAction(action = ToolNames.PRESS_HOME),
            AgentAction(action = ToolNames.GET_NOTIFICATIONS),
        )

        for (action in repeatable) {
            assertThat(ActionSignature.of(action)).isNull()
        }
    }

    @Test
    fun `the never-repeated set covers the tools that caused the reported loops`() {
        assertThat(ActionSignature.NEVER_REPEATED_TOOLS).contains(ToolNames.OPEN_APP)
        assertThat(ActionSignature.NEVER_REPEATED_TOOLS).contains(ToolNames.TYPE_TEXT)
    }

    @Test
    fun `clicking is NOT never-repeated, because paging is legitimate`() {
        // Tapping the same "Next" button five times through a paginated flow is a real task,
        // and refusing it would break one to fix another.
        assertThat(ActionSignature.NEVER_REPEATED_TOOLS).doesNotContain(ToolNames.CLICK)
        assertThat(ActionSignature.NEVER_REPEATED_TOOLS).doesNotContain(ToolNames.LONG_CLICK)
    }

    @Test
    fun `the never-repeated set and the repeatable set do not overlap`() {
        val overlap = ActionSignature.NEVER_REPEATED_TOOLS intersect ActionSignature.REPEATABLE_TOOLS

        assertThat(overlap).isEmpty()
    }

    // ------------------------------------------------------------------ per-tool caps

    @Test
    fun `the search tools carry a per-tool cap, because a reworded query is a new signature`() {
        // "nallaru po", then "Nallaru Po song" - different signatures, so the identical-action
        // caps miss entirely. This is the rule that catches it.
        assertThat(ActionSignature.TOOL_CAPS).containsKey(ToolNames.LAUNCH_URL)
        assertThat(ActionSignature.TOOL_CAPS).containsKey(ToolNames.TYPE_TEXT)
    }

    @Test
    fun `a per-tool cap never contradicts the repeatable set`() {
        val contradiction = ActionSignature.TOOL_CAPS.keys intersect ActionSignature.REPEATABLE_TOOLS

        assertThat(contradiction).isEmpty()
    }

    @Test
    fun `tapping has no per-tool cap, only the identical-action one`() {
        // Clicking through a long list is bounded by the identical-action cap instead; a
        // per-tool cap here would refuse a legitimate fifth tap.
        assertThat(ActionSignature.TOOL_CAPS).doesNotContainKey(ToolNames.CLICK)
    }

    @Test
    fun `every per-tool cap is at least two, so a legitimate repeat still works`() {
        for ((tool, cap) in ActionSignature.TOOL_CAPS) {
            assertThat(cap).isAtLeast(2)
        }
    }
}
