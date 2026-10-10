package com.example.agent.core.ai.provider

import com.example.agent.core.ai.AIProvider
import com.example.agent.core.ai.AgentActionCodec
import com.example.agent.core.ai.DecodedCall
import com.example.agent.core.ai.AIProviderException
import com.example.agent.core.ai.AgentJson
import com.example.agent.core.ai.AgentToolSpec
import com.example.agent.domain.model.AgentActionCall
import com.example.agent.domain.model.AgentMessage
import com.example.agent.domain.model.AgentResponse
import com.example.agent.domain.model.MessageRole
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
                // The agent must always act, never chat. Forcing a tool call is what stops
                // the model answering with prose and stalling the loop mid-task.
                put("tool_choice", "required")
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
        // Safe casts throughout. The throwing accessors (jsonObject/jsonArray/jsonPrimitive)
        // blow up on any unexpected type — including a JSON null — and that exception used
        // to escape as "element class ... is not a JsonArray" on an otherwise fine response.
        val root = runCatching { AgentJson.parseToJsonElement(raw) as? JsonObject }.getOrNull()
            ?: throw AIProviderException(
                AIProviderException.Kind.Parse,
                "The provider did not return a JSON object: ${raw.take(200)}",
            )

        // Usage is optional; some OpenAI-compatible gateways omit it.
        val usage = root["usage"] as? JsonObject
        val inputTokens = (usage?.get("prompt_tokens") as? JsonPrimitive)?.content?.toIntOrNull()
        val outputTokens = (usage?.get("completion_tokens") as? JsonPrimitive)?.content?.toIntOrNull()

        val choices = root["choices"] as? JsonArray
            ?: throw AIProviderException(
                AIProviderException.Kind.Parse,
                "The provider's response had no choices array: ${raw.take(200)}",
            )

        val message = (choices.firstOrNull() as? JsonObject)?.get("message") as? JsonObject
            ?: throw AIProviderException(
                AIProviderException.Kind.Parse,
                "The provider's response had no message: ${raw.take(200)}",
            )

        // Every call, not just the first: the loop batches, so one turn can carry several.
        val calls = mutableListOf<AgentActionCall>()
        val malformed = mutableListOf<String>()
        for (element in (message["tool_calls"] as? JsonArray).orEmpty()) {
            val call = element as? JsonObject ?: continue
            val function = call["function"] as? JsonObject
            val toolName = (function?.get("name") as? JsonPrimitive)?.content
            val arguments = when (val el = function?.get("arguments")) {
                is JsonPrimitive -> el.content
                is JsonObject -> el.toString() // a few gateways send an object, not a string
                else -> null
            }
            when (val decoded = AgentActionCodec.decodeJson(toolName, arguments)) {
                null -> Unit
                is DecodedCall.Ok ->
                    calls += AgentActionCall((call["id"] as? JsonPrimitive)?.content, decoded.action)
                is DecodedCall.Malformed -> malformed += decoded.detail
            }
        }

        if (calls.isNotEmpty() || malformed.isNotEmpty()) {
            return AgentResponse(
                calls = calls,
                malformed = malformed,
                raw = raw,
                inputTokens = inputTokens,
                outputTokens = outputTokens,
            )
        }

        val content = (message["content"] as? JsonPrimitive)?.content
        return AgentResponse(
            message = content,
            raw = raw,
            inputTokens = inputTokens,
            outputTokens = outputTokens,
        )
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
        MessageRole.User -> "user"
        MessageRole.Tool -> "tool"
        MessageRole.Assistant -> "assistant"
    })

    when {
        // A tool result must name the call it answers, or the model cannot pair the two.
        role == MessageRole.Tool -> {
            put("tool_call_id", toolCallId.orEmpty())
            put("content", content)
        }

        // Replay the model's own calls verbatim so its history reads as real tool use.
        // A turn may carry several: the loop batches steps.
        role == MessageRole.Assistant && toolCalls.isNotEmpty() -> {
            put("content", if (content.isBlank()) JsonNull else JsonPrimitive(content))
            put("tool_calls", JsonArray(toolCalls.map { call ->
                buildJsonObject {
                    put("id", call.id)
                    put("type", "function")
                    put("function", buildJsonObject {
                        put("name", call.name)
                        put("arguments", call.arguments)
                    })
                }
            }))
        }

        else -> put("content", content)
    }
}

internal fun AgentToolSpec.toOpenAiTool(): JsonObject = buildJsonObject {
    put("type", "function")
    put("function", buildJsonObject {
        put("name", name)
        put("description", description)
        put("parameters", AgentJson.parseToJsonElement(parametersJson))
    })
}
