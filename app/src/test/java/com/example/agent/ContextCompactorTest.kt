package com.example.agent

import com.example.agent.core.agent.ContextCompactor
import com.example.agent.domain.model.AgentMessage
import com.example.agent.domain.model.MessageRole
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Input tokens were 97% of a real bill (1.4M in, 12.9K out), almost all of it screen
 * listings that had already been sent. These tests pin the rules that stop that.
 */
class ContextCompactorTest {

    private fun system() = AgentMessage(MessageRole.System, "You are Spidey.")
    private fun task(screen: String = "SCREEN_0") =
        AgentMessage(MessageRole.User, "Task: do it\n\nCurrent screen:\n$screen")

    private fun call(i: Int) =
        AgentMessage(MessageRole.Assistant, "", toolCallId = "call_$i", name = "click")

    private fun observation(i: Int) =
        AgentMessage(MessageRole.Tool, "Observation:\nSCREEN_$i", toolCallId = "call_$i", name = "click")

    private fun conversation(observations: Int): List<AgentMessage> {
        val out = mutableListOf(system(), task())
        for (i in 1..observations) {
            out += call(i)
            out += observation(i)
        }
        return out
    }

    @Test
    fun `only the most recent observations are kept in full`() {
        val compacted = ContextCompactor.compact(conversation(6))

        val full = compacted.filter {
            it.role == MessageRole.Tool && it.content.contains("SCREEN_")
        }
        assertThat(full).hasSize(ContextCompactor.OBSERVATIONS_KEPT_IN_FULL)
        assertThat(full.map { it.content }).containsExactly("Observation:\nSCREEN_5", "Observation:\nSCREEN_6")
    }

    @Test
    fun `older observations become a marker rather than being deleted`() {
        val compacted = ContextCompactor.compact(conversation(6))

        // Keeping the turn in place preserves the tool-call pairing every provider needs.
        val collapsed = compacted.filter { it.content == ContextCompactor.COLLAPSED_OBSERVATION }
        assertThat(collapsed).isNotEmpty()
        assertThat(collapsed.all { it.role == MessageRole.Tool }).isTrue()
    }

    @Test
    fun `the opening screen is dropped once newer screens exist`() {
        val compacted = ContextCompactor.compact(conversation(4))

        val taskMessage = compacted[1]
        assertThat(taskMessage.content).contains("Task: do it")
        assertThat(taskMessage.content).doesNotContain("SCREEN_0")
        assertThat(taskMessage.content).contains(ContextCompactor.COLLAPSED_SCREEN)
    }

    @Test
    fun `a short conversation is left alone`() {
        val original = conversation(1)
        val compacted = ContextCompactor.compact(original)

        assertThat(compacted).isEqualTo(original)
    }

    @Test
    fun `the system prompt and the task are never dropped`() {
        val compacted = ContextCompactor.compact(conversation(30))

        assertThat(compacted.first().content).isEqualTo("You are Spidey.")
        assertThat(compacted[1].content).contains("Task: do it")
    }

    @Test
    fun `the message count is bounded`() {
        val compacted = ContextCompactor.compact(conversation(40))

        assertThat(compacted.size).isAtMost(ContextCompactor.MAX_MESSAGES)
    }

    @Test
    fun `compacting twice changes nothing more`() {
        val once = ContextCompactor.compact(conversation(10))
        val twice = ContextCompactor.compact(once)

        assertThat(twice).isEqualTo(once)
    }

    @Test
    fun `tool call and result stay paired`() {
        val compacted = ContextCompactor.compact(conversation(8))

        val calls = compacted.filter { it.role == MessageRole.Assistant && it.toolCallId != null }
        val results = compacted.filter { it.role == MessageRole.Tool }
        assertThat(results.map { it.toolCallId }).containsExactlyElementsIn(calls.map { it.toolCallId })
    }

    @Test
    fun `a cut that lands inside a tool pair drops the orphan, it does not keep it`() {
        // An odd-length conversation puts the trim boundary on a tool result rather than on
        // the assistant turn that owns it. That used to produce an orphaned result, which
        // providers reject outright - the request fails, rather than merely losing context.
        val messages = conversation(7) + AgentMessage(MessageRole.User, "keep going")
        assertThat(messages.size).isEqualTo(ContextCompactor.MAX_MESSAGES + 1)

        val compacted = ContextCompactor.compact(messages)

        val callIds = compacted
            .filter { it.role == MessageRole.Assistant && it.toolCallId != null }
            .mapNotNull { it.toolCallId }
            .toSet()
        val orphaned = compacted
            .filter { it.role == MessageRole.Tool }
            .filterNot { it.toolCallId in callIds }
        assertThat(orphaned).isEmpty()

        // Not vacuous: the tail really does carry pairs, so an orphan had somewhere to hide.
        assertThat(callIds).isNotEmpty()
        assertThat(callIds).contains("call_7")
    }

    @Test
    fun `a batch keeps every result paired with its single call`() {
        // Batching means one assistant turn owns several results, so the boundary is far more
        // likely to fall inside a group.
        val batch = AgentMessage(MessageRole.Assistant, "", toolCallId = "call_1", name = "open_app")
        val results = (1..3).map {
            AgentMessage(MessageRole.Tool, "Observation:\nSCREEN_$it", toolCallId = "call_1", name = "open_app")
        }
        // Filler BEFORE the batch, so the batch itself survives the trim - otherwise the test
        // proves nothing, because there would be no results left to orphan.
        val filler = (10..30).map { AgentMessage(MessageRole.User, "filler $it") }

        val compacted = ContextCompactor.compact(listOf(system(), task()) + filler + listOf(batch) + results)

        val kept = compacted.filter { it.toolCallId == "call_1" }
        assertThat(kept.map { it.role }).contains(MessageRole.Assistant)
        assertThat(kept.count { it.role == MessageRole.Tool }).isEqualTo(results.size)

        val callIds = compacted
            .filter { it.role == MessageRole.Assistant && it.toolCallId != null }
            .mapNotNull { it.toolCallId }
            .toSet()
        val orphaned = compacted.filter { it.role == MessageRole.Tool }
            .filterNot { it.toolCallId in callIds }
        assertThat(orphaned).isEmpty()
    }

    @Test
    fun `dropping orphans never overruns the bound`() {
        for (observations in 6..40) {
            val compacted = ContextCompactor.compact(conversation(observations))
            assertThat(compacted.size).isAtMost(ContextCompactor.MAX_MESSAGES)
        }
    }

    @Test
    fun `an unchanged-screen note does not push a real listing out`() {
        val unchanged = AgentMessage(
            MessageRole.Tool,
            "The screen is unchanged, so the listing above is still current.",
            toolCallId = "call_9",
            name = "click",
        )
        val messages = conversation(2) + call(9) + unchanged

        val compacted = ContextCompactor.compact(messages)

        assertThat(compacted.map { it.content }).contains("Observation:\nSCREEN_1")
        assertThat(compacted.map { it.content }).contains("Observation:\nSCREEN_2")
    }
}
