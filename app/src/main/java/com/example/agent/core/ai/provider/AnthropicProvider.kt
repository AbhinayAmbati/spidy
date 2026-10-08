package com.example.agent.core.ai.provider

import com.example.agent.core.ai.AIProvider
import com.example.agent.core.ai.AgentActionCodec
import com.example.agent.core.ai.AIProviderException
import com.example.agent.core.ai.AgentJson
import com.example.agent.core.ai.AgentToolSpec
import com.example.agent.core.ai.parseObjectOrEmpty
import com.example.agent.domain.model.AgentMessage
import com.example.agent.domain.model.AgentResponse
import com.example.agent.domain.model.MessageRole
import kotlinx.serialization.json.JsonArray
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
                // Force a tool call: the agent must act, never chat.
                put("tool_choice", buildJsonObject { put("type", "any") })
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
            execute(request)
        } catch (io: IOException) {
            throw AIProviderException(AIProviderException.Kind.Network, "Could not reach Anthropic", io)
        }

        return parse(raw)
    }

    private fun parse(raw: String): AgentResponse {
        val root = runCatching { AgentJson.parseToJsonElement(raw) as? JsonObject }.getOrNull()
            ?: return AgentResponse(message = null, raw = raw)

        val blocks = root["content"] as? JsonArray ?: return AgentResponse(message = null, raw = raw)
        var text: String? = null
        for (block in blocks) {
            val obj = block as? JsonObject ?: continue
            when ((obj["type"] as? JsonPrimitive)?.content) {
                "text" -> text = (obj["text"] as? JsonPrimitive)?.content
                "tool_use" -> {
                    val name = (obj["name"] as? JsonPrimitive)?.content
                    val input = obj["input"] as? JsonObject
                    AgentActionCodec.fromToolCall(name, input)?.let {
                        return AgentResponse(action = it, raw = raw)
                    }
                }
            }
        }
        return AgentResponse(message = text, raw = raw)
    }


    /**
     * OkHttp's execute() blocks. Run it on Dispatchers.IO so a caller on the main
     * dispatcher (viewModelScope) cannot trigger NetworkOnMainThreadException.
     */
    private suspend fun execute(request: Request): String = withContext(Dispatchers.IO) {
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
    }

    private companion object {
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}

internal fun AgentMessage.toAnthropicMessage(): JsonObject = buildJsonObject {
    when {
        role == MessageRole.Assistant -> {
            put("role", "assistant")
            val blocks = buildList {
                if (content.isNotBlank()) {
                    add(buildJsonObject { put("type", "text"); put("text", content) })
                }
                if (name != null && toolCallId != null) {
                    add(buildJsonObject {
                        put("type", "tool_use")
                        put("id", toolCallId)
                        put("name", name)
                        put("input", parseObjectOrEmpty(toolArguments))
                    })
                }
            }
            put("content", JsonArray(
                if (blocks.isEmpty()) listOf(buildJsonObject { put("type", "text"); put("text", "") })
                else blocks,
            ))
        }

        // Anthropic delivers tool results inside a *user* turn, not a "tool" role.
        role == MessageRole.Tool -> {
            put("role", "user")
            put("content", JsonArray(listOf(buildJsonObject {
                put("type", "tool_result")
                put("tool_use_id", toolCallId.orEmpty())
                put("content", content)
            })))
        }

        else -> {
            put("role", "user")
            put("content", content)
        }
    }
}

private fun AgentToolSpec.toAnthropicTool(): JsonObject = buildJsonObject {
    put("name", name)
    put("description", description)
    put("input_schema", AgentJson.parseToJsonElement(parametersJson))
}
