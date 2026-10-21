package com.example.agent.core.agent

import com.example.agent.domain.model.AgentMessage
import com.example.agent.domain.model.MessageRole
import com.example.agent.domain.model.ToolCall

/**
 * Answers the tool calls a batch never reached.
 *
 * The loop stops a batch at the first refused, rejected, declined or failed call, because the
 * rest was planned against a screen that no longer holds. But the assistant turn already
 * lists every call, and OpenAI and Anthropic reject a request whose tool call has no result.
 * So each call left unanswered gets an explicit "not run" result.
 *
 * Pure and free of Android types, so the behaviour is unit-testable.
 */
object SkippedCalls {

    const val NOT_RUN_RESULT =
        "Not run: an earlier call in the same batch stopped it. Plan again from the screen as it is now."

    fun resultsFor(messages: List<AgentMessage>, calls: List<ToolCall>): List<AgentMessage> {
        val answered = messages
            .filter { it.role == MessageRole.Tool }
            .mapNotNull { it.toolCallId }
            .toSet()

        return calls
            .filter { it.id !in answered }
            .map { call ->
                AgentMessage(
                    role = MessageRole.Tool,
                    content = NOT_RUN_RESULT,
                    toolCallId = call.id,
                    name = call.name,
                )
            }
    }
}
