package com.example.agent.core.ai.local

/**
 * What the Spidey model is given for one request.
 *
 * The model was trained with no system prompt, no screen and no history: the five tool schemas
 * and the user's sentence, nothing else. Anything added here moves the model off the inputs it
 * was trained on and costs accuracy, so this stays deliberately tiny.
 *
 * @param toolsJson the five-tool schema, the contents of `assets/spidey_tools.json`. Passed in
 * so unit tests need no Android assets.
 */
class SpideyPrompt(val toolsJson: String) {

    /** The engine input: the user's sentence, trimmed, and nothing else. */
    fun input(sentence: String): String = sentence.trim()

    companion object {
        /** The model has no system prompt; the engine is started with this empty one. */
        const val SYSTEM_PROMPT = ""
    }
}
