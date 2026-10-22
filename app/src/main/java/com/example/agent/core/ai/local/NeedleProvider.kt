package com.example.agent.core.ai.local

import com.example.agent.core.ai.AIProvider
import com.example.agent.core.ai.AIProviderException
import com.example.agent.core.ai.AgentToolSpec
import com.example.agent.domain.model.AgentMessage
import com.example.agent.domain.model.AgentResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * [AIProvider] that runs the model on the device, with no network and no API key.
 *
 * Each turn is independent: the engine is reset and given the task, the last few results and
 * the current screen. The engine allows one call at a time, so calls queue on [gate]. A call
 * in flight cannot be interrupted, so Stop takes effect when it returns.
 */
class NeedleProvider(
    private val engine: LocalToolEngine,
    private val minConfidence: Double = 0.0,
    private val maxNewTokens: Int = DEFAULT_MAX_NEW_TOKENS,
) : AIProvider {

    override val id: String = "needle"

    private val gate = Mutex()
    private var startedWith: String? = null

    override suspend fun generateResponse(
        messages: List<AgentMessage>,
        tools: List<AgentToolSpec>,
    ): AgentResponse = gate.withLock {
        withContext(Dispatchers.Default) {
            try {
                val toolsJson = NeedlePrompt.toolsJson(NeedlePrompt.toolsFor(messages, tools))
                if (toolsJson != startedWith) {
                    engine.start(NeedlePrompt.SYSTEM_PROMPT, toolsJson)
                    startedWith = toolsJson
                }
                engine.reset()
                NeedleResponseParser.parse(engine.complete(NeedlePrompt.input(messages), maxNewTokens), minConfidence)
            } catch (failure: LocalEngineException) {
                throw AIProviderException(AIProviderException.Kind.Unknown, failure.message ?: "On-device model failed", failure)
            }
        }
    }

    companion object {
        /** A tool call is short; a larger cap only takes context away from the screen. */
        const val DEFAULT_MAX_NEW_TOKENS = 192
    }
}
