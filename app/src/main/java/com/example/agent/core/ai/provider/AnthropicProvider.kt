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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/** Anthropic Messages API adapter. */
class AnthropicProvider(
    private val baseUrl: String,
    private val model: String,
    private val apiKey: String?,
    private val client: OkHttpClient = OpenAICompatibleProvider.defaultClient(),
) : AIProvider {

    override val id: String = "anthropic"

    override suspend fun generateResponse(
        messages: List<AgentMessage>,
        tools: List<AgentToolSpec>,
    ): AgentResponse {
        val key = apiKey?.takeIf { it.isNotBlank() }
            ?: throw AIProviderException(AIProviderException.Kind.NoKey, "No API key configured for Anthropic")

        val system = messages.filter { it.role == MessageRole.System }
            .joinToString("\n\n") { it.content }

        val body = buildJsonObject {
            put("model", model)
            put("max_tokens", 1024)
            put("temperature", 0)
            if (system.isNotBlank()) put("system", system)
            put("messages", JsonArray(
                messages.filter { it.role != MessageRole.System }
                    .map { it.toAnthropicMessage() }
            ))
            if (tools.isNotEmpty()) {
                put("tools", JsonArray(tools.map { it.toAnthropicTool() }))
            }
        }

        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/messages")
            .addHeader("x-api-key", key)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()

        val raw = try {
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw when (response.code) {
                        401, 403 -> AIProviderException(AIProviderException.Kind.Unauthorized, "The API key was rejected")
                        429 -> AIProviderException(AIProviderException.Kind.RateLimited, "Rate limit reached")
                        in 500..599 -> AIProviderException(AIProviderException.Kind.Server, "Provider error (${response.code})")
                        else -> AIProviderException(AIProviderException.Kind.Unknown, "Request failed (${response.code})")
                    }
                }
                text
            }
        } catch (io: IOException) {
            throw AIProviderException(AIProviderException.Kind.Network, "Could not reach Anthropic", io)
        }

        return parse(raw)
    }

    private fun parse(raw: String): AgentResponse {
        val root = runCatching { AgentJson.parseToJsonElement(raw).jsonObject }
            .getOrElse { throw AIProviderException(AIProviderException.Kind.Parse, "Malformed provider response") }

        val blocks = root["content"]?.jsonArray ?: return AgentResponse(message = null, raw = raw)
        var text: String? = null
        for (block in blocks) {
            val obj = block.jsonObject
            when (obj["type"]?.jsonPrimitive?.content) {
                "text" -> text = obj["text"]?.jsonPrimitive?.content
                "tool_use" -> {
                    val input = obj["input"]?.jsonObject
                    if (input != null) {
                        val action = runCatching {
                            AgentJson.decodeFromJsonElement(AgentAction.serializer(), input)
                        }.getOrElse {
                            throw AIProviderException(
                                AIProviderException.Kind.Parse,
                                "Model returned an action that did not match the schema",
                            )
                        }
                        return AgentResponse(action = action, raw = raw)
                    }
                }
            }
        }
        return AgentResponse(message = text, raw = raw)
    }

    private companion object {
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}

private fun AgentMessage.toAnthropicMessage(): JsonObject = buildJsonObject {
    put("role", if (role == MessageRole.Assistant) "assistant" else "user")
    put("content", content)
}

private fun AgentToolSpec.toAnthropicTool(): JsonObject = buildJsonObject {
    put("name", name)
    put("description", description)
    put("input_schema", AgentJson.parseToJsonElement(parametersJson))
}
