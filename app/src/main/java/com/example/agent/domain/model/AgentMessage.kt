package com.example.agent.domain.model

enum class MessageRole { System, User, Assistant, Tool }

/**
 * One tool call the model made, kept verbatim so its history replays as real tool use.
 *
 * A single assistant turn may carry several of these: the loop supports batching, so one
 * model call can drive more than one action.
 */
data class ToolCall(
    val id: String,
    val name: String,
    val arguments: String,
)

/**
 * A single turn in the conversation sent to the AI provider. Provider adapters
 * translate this neutral shape into whatever wire format they need.
 */
data class AgentMessage(
    val role: MessageRole,
    val content: String,
    /** Set on a tool-result turn: the call this result answers. */
    val toolCallId: String? = null,
    /** Tool name on a tool-result turn. */
    val name: String? = null,
    /** Set on an assistant turn that called tools — one entry per call. */
    val toolCalls: List<ToolCall> = emptyList(),
)

/** An action the model proposed, with the call id needed to answer it. */
data class AgentActionCall(
    val id: String?,
    val action: AgentAction,
)

/** What a provider hands back: the tool calls it made, a plain reply, or both. */
data class AgentResponse(
    /**
     * Every tool call this turn. Usually one. More than one when the model chose to batch
     * steps it is confident about, which is what keeps a multi-action task to a single
     * round trip.
     */
    val calls: List<AgentActionCall> = emptyList(),
    /**
     * Calls the model made that could not be read, with what was wrong. Kept separate from
     * [calls] so a fumbled call can be shown back to the model instead of ending the task.
     */
    val malformed: List<String> = emptyList(),
    val message: String? = null,
    val raw: String? = null,
    /** Reported by the provider when it includes usage; null when it does not. */
    val inputTokens: Int? = null,
    val outputTokens: Int? = null,
) {
    /** The first proposed action, for callers that only handle one. */
    val action: AgentAction? get() = calls.firstOrNull()?.action
    val isFinal: Boolean get() = calls.isEmpty() && malformed.isEmpty()
}
