package com.example.agent

import com.example.agent.core.agent.SkippedCalls
import com.example.agent.domain.model.AgentMessage
import com.example.agent.domain.model.MessageRole
import com.example.agent.domain.model.ToolCall
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * OpenAI and Anthropic reject a request whose assistant turn lists a tool call that has no
 * result. A batch stopped halfway used to leave exactly that.
 */
class SkippedCallsTest {

    private fun call(id: String) = ToolCall(id, "click", "{}")

    private fun result(id: String) =
        AgentMessage(MessageRole.Tool, "done", toolCallId = id, name = "click")

    @Test
    fun `every call after the one that stopped the batch gets a not-run result`() {
        val calls = listOf(call("a"), call("b"), call("c"))
        val messages = listOf(
            AgentMessage(MessageRole.Assistant, "", toolCalls = calls),
            result("a"),
        )

        val skipped = SkippedCalls.resultsFor(messages, calls)

        assertThat(skipped.map { it.toolCallId }).containsExactly("b", "c").inOrder()
        assertThat(skipped.all { it.role == MessageRole.Tool }).isTrue()
        assertThat(skipped.all { it.content == SkippedCalls.NOT_RUN_RESULT }).isTrue()
    }

    @Test
    fun `a fully answered batch gets nothing added`() {
        val calls = listOf(call("a"), call("b"))
        val messages = listOf(result("a"), result("b"))

        assertThat(SkippedCalls.resultsFor(messages, calls)).isEmpty()
    }
}
