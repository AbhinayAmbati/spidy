package com.example.agent.core.ai.local

import com.example.agent.core.ai.AIProvider
import com.example.agent.core.ai.AgentToolSpec
import com.example.agent.domain.model.AgentMessage
import com.example.agent.domain.model.AgentResponse

/**
 * Legacy adapter, kept only so OnDeviceModelManager and the old agent loop still compile.
 *
 * Spidey no longer drives the accessibility agent loop: it routes one sentence to one intent
 * through [com.example.agent.core.intent.SpideyRouter]. This provider never touches the engine
 * and answers every turn with a plain message.
 *
 * TODO(integrator): delete this class together with the AIProvider wiring in
 * `core/agent/AgentRuntime.kt` and `OnDeviceModelManager.provider()`.
 */
@Suppress("UNUSED_PARAMETER")
class SpideyProvider(
    engine: LocalToolEngine,
    minConfidence: Double = 0.0,
    maxNewTokens: Int = DEFAULT_MAX_NEW_TOKENS,
) : AIProvider {

    override val id: String = "spidey"

    override val isDeterministic: Boolean = true

    override suspend fun generateResponse(
        messages: List<AgentMessage>,
        tools: List<AgentToolSpec>,
    ): AgentResponse = AgentResponse(
        message = "Spidey now answers one request at a time through the intent router.",
        raw = "",
    )

    companion object {
        const val DEFAULT_MAX_NEW_TOKENS = 192
    }
}
