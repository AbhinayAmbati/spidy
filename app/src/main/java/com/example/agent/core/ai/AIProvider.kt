package com.example.agent.core.ai

import com.example.agent.domain.model.AgentMessage
import com.example.agent.domain.model.AgentResponse

/**
 * The single seam between the agent runtime and any model backend. Providers are
 * resolved at runtime from settings, so the runtime never depends on a concrete
 * vendor.
 */
interface AIProvider {
    val id: String

    /**
     * @param messages the running conversation (system prompt first).
     * @param tools the tools currently offered to the model.
     * @return the model's next move, or a plain message when it is done.
     * @throws AIProviderException on transport/auth/rate-limit errors.
     */
    suspend fun generateResponse(
        messages: List<AgentMessage>,
        tools: List<AgentToolSpec>,
    ): AgentResponse
}

/** Lightweight, provider-agnostic description of a tool sent to the model. */
data class AgentToolSpec(
    val name: String,
    val description: String,
    val parametersJson: String,
)

/** Typed failure so the runtime can present friendly, specific error states. */
class AIProviderException(
    val kind: Kind,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    enum class Kind { NoKey, Unauthorized, RateLimited, Network, Server, Parse, Unknown }
}
