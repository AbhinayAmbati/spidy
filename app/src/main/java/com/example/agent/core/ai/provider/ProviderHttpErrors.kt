package com.example.agent.core.ai.provider

import com.example.agent.core.ai.AIProviderException
import com.example.agent.core.ai.AgentJson
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Turns a failed HTTP response into the typed error the runtime shows the user.
 *
 * Anthropic and Gemini both explain a failure in `error.message`. Dropping it left the user
 * with "Provider error (400)", and Gemini's 400 was reported as a rejected key even when the
 * request itself was malformed.
 */
internal object ProviderHttpErrors {

    fun exceptionFor(code: Int, body: String): AIProviderException {
        val detail = providerMessage(body)
        return when (code) {
            401, 403 -> AIProviderException(
                AIProviderException.Kind.Unauthorized,
                detail ?: "The API key was rejected",
            )
            429 -> AIProviderException(AIProviderException.Kind.RateLimited, detail ?: "Rate limit reached")
            in 500..599 -> AIProviderException(
                AIProviderException.Kind.Server,
                detail ?: "Provider error ($code)",
            )
            else -> AIProviderException(
                AIProviderException.Kind.Unknown,
                detail ?: "Request failed ($code)",
            )
        }
    }

    private fun providerMessage(body: String): String? = runCatching {
        AgentJson.parseToJsonElement(body).jsonObject["error"]?.jsonObject
            ?.get("message")?.jsonPrimitive?.content
    }.getOrNull()?.takeIf { it.isNotBlank() }
}
