package com.example.agent.domain.model

enum class MessageRole { System, User, Assistant, Tool }

/**
 * A single turn in the conversation sent to the AI provider. Provider adapters
 * translate this neutral shape into whatever wire format they need.
 */
data class AgentMessage(
    val role: MessageRole,
    val content: String,
    /** Set on an assistant tool-call turn and on the matching tool-result turn. */
    val toolCallId: String? = null,
    /** Tool name for an assistant tool-call turn, or the tool that produced a result. */
    val name: String? = null,
    /** Raw JSON arguments the model produced, replayed back to it as its own call. */
    val toolArguments: String? = null,
)

/** What a provider hands back: either a tool call, a plain reply, or both. */
data class AgentResponse(
    val action: AgentAction? = null,
    val message: String? = null,
    val raw: String? = null,
) {
    val isFinal: Boolean get() = action == null
}
