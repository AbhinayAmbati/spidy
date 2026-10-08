package com.example.agent.core.ai.provider

import com.example.agent.core.ai.AIProvider
import com.example.agent.core.ai.AIProviderException
import com.example.agent.core.ai.AgentJson
import com.example.agent.core.ai.AgentToolSpec
import com.example.agent.domain.model.AgentAction
import com.example.agent.domain.model.AgentMessage
import com.example.agent.domain.model.AgentResponse
import com.example.agent.domain.model.MessageRole
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Talks to any OpenAI-compatible /chat/completions endpoint: OpenAI, OpenRouter,
 * Sarvam AI, and user-supplied custom gateways all use this same shape.
 *
 * [apiKeyHeader]/[apiKeyPrefix] exist because "OpenAI-compatible" covers the request
 * and response *shape*, not the auth convention. Sarvam AI, for instance, prefers
 * `api-subscription-key: <key>` over `Authorization: Bearer <key>`.
 */
class OpenAICompatibleProvider(
    override val id: String,
    private val baseUrl: String,
    private val model: String,
    private val apiKey: String?,
    private val apiKeyHeader: String = "Authorization",
    private val apiKeyPrefix: String = "Bearer ",
    private val client: OkHttpClient = defaultClient(),
) : AIProvider {

    override suspend fun generateResponse(
        messages: List<AgentMessage>,
        tools: List<AgentToolSpec>,
    ): AgentResponse {
        val key = apiKey?.takeIf { it.isNotBlank() }
            ?: throw AIProviderException(AIProviderException.Kind.NoKey, "No API key configured for $id")

        val body = buildJsonObject {
            put("model", model)
            put("temperature", 0)
            put("messages", JsonArray(messages.map { it.toOpenAiMessage() }))
            if (tools.isNotEmpty()) {
                put("tools", JsonArray(tools.map { it.toOpenAiTool() }))
                put("tool_choice", "auto")
            }
        }

        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/chat/completions")
            .addHeader(apiKeyHeader, "$apiKeyPrefix$key")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()

        val text = execute(request)
        return parse(text)
    }

    private fun parse(raw: String): AgentResponse {
        val root = runCatching { AgentJson.parseToJsonElement(raw).jsonObject }
            .getOrElse { throw AIProviderException(AIProviderException.Kind.Parse, "Malformed provider response") }

        val message = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonObject
            ?: throw AIProviderException(AIProviderException.Kind.Parse, "Provider returned no choices")

        val toolCall = message["tool_calls"]?.jsonArray?.firstOrNull()?.jsonObject
        val arguments = toolCall?.get("function")?.jsonObject?.get("arguments")?.jsonPrimitive?.content

        if (!arguments.isNullOrBlank()) {
            val action = runCatching { AgentJson.decodeFromString(AgentAction.serializer(), arguments) }
                .getOrElse {
                    throw AIProviderException(
                        AIProviderException.Kind.Parse,
                        "Model returned an action that did not match the schema",
                    )
                }
            return AgentResponse(action = action, raw = raw)
        }

        val content = message["content"]?.let { el ->
            runCatching { el.jsonPrimitive.content }.getOrNull()
        }
        return AgentResponse(message = content, raw = raw)
    }

    /**
     * OkHttp's execute() blocks. It must never run on the caller's dispatcher —
     * a ViewModel calling this from viewModelScope (Dispatchers.Main) would throw
     * NetworkOnMainThreadException.
     */
    private suspend fun execute(request: Request): String = withContext(Dispatchers.IO) {
        try {
            client.newCall(request).execute().use { response ->
                val bodyText = response.body?.string().orEmpty()
                if (response.isSuccessful) return@withContext bodyText

                // Providers often explain precisely what is wrong (an unknown model id,
                // a deprecated model, a missing scope). Surface that instead of a
                // generic status code whenever it is available.
                val detail = providerMessage(bodyText)

                throw when (response.code) {
                    // Sarvam returns 403 for both an invalid key and a forbidden
                    // request, so the body is the only way to tell them apart.
                    401, 403 -> AIProviderException(
                        AIProviderException.Kind.Unauthorized,
                        detail ?: "The API key was rejected",
                    )
                    429 -> AIProviderException(AIProviderException.Kind.RateLimited, "Rate limit reached")
                    in 500..599 -> AIProviderException(
                        AIProviderException.Kind.Server,
                        detail ?: "Provider error (${response.code})",
                    )
                    else -> AIProviderException(
                        AIProviderException.Kind.Unknown,
                        detail ?: "Request failed (${response.code})",
                    )
                }
            }
        } catch (io: IOException) {
            throw AIProviderException(AIProviderException.Kind.Network, "Could not reach the provider", io)
        }
    }

    /** Pulls `error.message` out of an OpenAI-style error body, if present. */
    private fun providerMessage(body: String): String? = runCatching {
        AgentJson.parseToJsonElement(body).jsonObject["error"]?.jsonObject
            ?.get("message")?.jsonPrimitive?.content
    }.getOrNull()?.takeIf { it.isNotBlank() }

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}

internal fun AgentMessage.toOpenAiMessage(): JsonObject = buildJsonObject {
    put("role", when (role) {
        MessageRole.System -> "system"
        MessageRole.User, MessageRole.Tool -> "user"
        MessageRole.Assistant -> "assistant"
    })
    put("content", content)
}

internal fun AgentToolSpec.toOpenAiTool(): JsonObject = buildJsonObject {
    put("type", "function")
    put("function", buildJsonObject {
        put("name", name)
        put("description", description)
        put("parameters", AgentJson.parseToJsonElement(parametersJson))
    })
}
