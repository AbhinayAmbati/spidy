package com.example.agent.core.agent

import com.example.agent.domain.model.AgentMessage
import com.example.agent.domain.model.MessageRole

/**
 * Keeps the conversation small enough to be affordable.
 *
 * A screen listing is roughly 2,500 tokens. The loop used to keep up to 22 messages, most
 * of them full listings, so every request re-sent tens of thousands of tokens — and the
 * model only ever needs the screen it is looking at *now*. On a real bill that showed up as
 * 1.4M input tokens against 12.9K output: 97% of the cost was input we had already sent.
 *
 * Two rules do almost all the work:
 *  - only the most recent observations are kept in full; older ones become a marker;
 *  - the screen captured at the very start is dropped once anything newer exists.
 *
 * Pure and free of Android types, so the behaviour is unit-testable.
 */
object ContextCompactor {

    /** Upper bound on messages carried into a request. */
    const val MAX_MESSAGES = 16

    /** How many recent screen listings survive in full. */
    const val OBSERVATIONS_KEPT_IN_FULL = 2

    const val OBSERVATION_MARKER = "Observation:"
    const val SCREEN_MARKER = "Current screen:"

    const val COLLAPSED_OBSERVATION = "[earlier screen listing omitted]"
    const val COLLAPSED_SCREEN = "[omitted - see the latest observation]"

    fun compact(messages: List<AgentMessage>): List<AgentMessage> {
        val bounded = bound(messages)
        val collapsed = collapse(bounded)
        return collapsed
    }

    /** Trims the middle, keeping the system prompt, the task, and the recent exchanges. */
    private fun bound(messages: List<AgentMessage>): List<AgentMessage> {
        if (messages.size <= MAX_MESSAGES) return messages
        val head = messages.take(2)
        val tail = messages.takeLast(MAX_MESSAGES - 2)
        return head + tail
    }

    private fun collapse(messages: List<AgentMessage>): List<AgentMessage> {
        val observationIndexes = messages.indices.filter { i ->
            messages[i].role == MessageRole.Tool &&
                messages[i].content.contains(OBSERVATION_MARKER)
        }

        // The opening screen is stale the moment the agent does anything at all.
        val staleInitialScreen = observationIndexes.size >= OBSERVATIONS_KEPT_IN_FULL + 1
        val keep = observationIndexes.takeLast(OBSERVATIONS_KEPT_IN_FULL).toSet()

        return messages.mapIndexed { i, message ->
            when {
                i == 1 && staleInitialScreen && message.content.contains(SCREEN_MARKER) ->
                    message.copy(
                        content = message.content.substringBefore(SCREEN_MARKER) + SCREEN_MARKER +
                            " " + COLLAPSED_SCREEN,
                    )

                i in observationIndexes && i !in keep &&
                    message.content != COLLAPSED_OBSERVATION ->
                    message.copy(content = COLLAPSED_OBSERVATION)

                else -> message
            }
        }
    }
}
