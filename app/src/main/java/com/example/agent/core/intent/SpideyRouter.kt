package com.example.agent.core.intent

import com.example.agent.core.ai.AIProviderException
import com.example.agent.core.ai.local.LocalEngineException
import com.example.agent.core.ai.local.LocalToolEngine
import com.example.agent.core.ai.local.SpideyPrompt
import com.example.agent.core.ai.local.SpideyResponseParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * [IntentRouter] over the on-device engine.
 *
 * The engine allows one call at a time, so calls queue on [gate]. Every call starts from a
 * reset engine and passes the sentence and nothing else. A call in flight cannot be
 * interrupted. Calls that were malformed are dropped, so the result reads as "unsupported".
 *
 * An engine failure surfaces as an [AIProviderException] of kind Unknown.
 */
class SpideyRouter(
    private val engine: LocalToolEngine,
    prompt: SpideyPrompt,
    private val maxNewTokens: Int = DEFAULT_MAX_NEW_TOKENS,
) : IntentRouter {

    private val gate = Mutex()
    private var prompt: SpideyPrompt = prompt
    private var startedWith: String? = null

    /** Changes the tool set; the engine restarts on the next [route] only if it really differs. */
    suspend fun useTools(next: SpideyPrompt) = gate.withLock { prompt = next }

    override suspend fun route(sentence: String): RoutedRequest {
        if (sentence.isBlank()) return RoutedRequest(emptyList(), null, "")
        return gate.withLock {
            withContext(Dispatchers.Default) {
                try {
                    if (prompt.toolsJson != startedWith) {
                        engine.start(SpideyPrompt.SYSTEM_PROMPT, prompt.toolsJson)
                        startedWith = prompt.toolsJson
                    }
                    engine.reset()
                    val raw = engine.complete(prompt.input(sentence), maxNewTokens)
                    val parsed = SpideyResponseParser.parse(raw)
                    RoutedRequest(parsed.intents, parsed.confidence, raw)
                } catch (failure: LocalEngineException) {
                    // Forget the start so the next call re-initialises a possibly broken engine.
                    startedWith = null
                    throw AIProviderException(
                        AIProviderException.Kind.Unknown,
                        failure.message ?: "On-device model failed",
                        failure,
                    )
                }
            }
        }
    }

    companion object {
        /** A tool call is short. */
        const val DEFAULT_MAX_NEW_TOKENS = 192
    }
}
