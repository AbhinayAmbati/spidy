package com.example.agent.domain.model

enum class MessageRole { System, User, Assistant, Tool }

/**
 * A single turn in the conversation sent to the AI provider. Provider adapters
 * translate this neutral shape into whatever wire format they need.
 */
data class AgentMessage(
    val role: MessageRole,
    val content: String,
    val toolCallId: String? = null,
    val name: String? = null,
)

/** What a provider hands back: either a tool call, a plain reply, or both. */
data class AgentResponse(
    val action: AgentAction? = null,
    val message: String? = null,
    val raw: String? = null,
) {
    val isFinal: Boolean get() = action == null
}
