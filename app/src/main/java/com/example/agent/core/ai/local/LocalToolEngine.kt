package com.example.agent.core.ai.local

/** A failure inside the on-device engine, with the engine's own explanation. */
class LocalEngineException(message: String) : Exception(message)

/**
 * What [SpideyProvider] needs from an on-device tool-calling model, and nothing else, so the
 * provider can be tested without the native library.
 */
interface LocalToolEngine {

    /**
     * Prepares the model with a system prompt and the tools it may call. Expensive, so callers
     * start it once and again only when the tools change.
     */
    fun start(systemPrompt: String, toolsJson: String)

    /** Forgets the conversation, keeping the model and the tools. */
    fun reset()

    /** @return the model's answer as the engine's JSON object. */
    fun complete(input: String, maxNewTokens: Int): String
}
