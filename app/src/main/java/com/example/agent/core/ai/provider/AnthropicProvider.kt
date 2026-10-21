package com.example.agent.core.ai.provider

import com.example.agent.core.ai.AIProvider
import com.example.agent.core.ai.AgentActionCodec
import com.example.agent.core.ai.DecodedCall
import com.example.agent.core.ai.AIProviderException
import com.example.agent.core.ai.AgentJson
import com.example.agent.core.ai.AgentToolSpec
import com.example.agent.core.ai.parseObjectOrEmpty
import com.example.agent.domain.model.AgentActionCall
import com.example.agent.domain.model.AgentMessage
import com.example.agent.domain.model.AgentResponse
import com.example.agent.domain.model.MessageRole
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
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
                messages.filter { it.role != MessageRole.System }.toAnthropicMessages()
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

        val usage = root["usage"] as? JsonObject
        val inputTokens = (usage?.get("input_tokens") as? JsonPrimitive)?.content?.toIntOrNull()
        val outputTokens = (usage?.get("output_tokens") as? JsonPrimitive)?.content?.toIntOrNull()

        val blocks = root["content"] as? JsonArray
            ?: return AgentResponse(message = null, raw = raw, inputTokens = inputTokens, outputTokens = outputTokens)

        var text: String? = null
        val calls = mutableListOf<AgentActionCall>()
        val malformed = mutableListOf<String>()
        for (block in blocks) {
            val obj = block as? JsonObject ?: continue
            when ((obj["type"] as? JsonPrimitive)?.content) {
                "text" -> text = (obj["text"] as? JsonPrimitive)?.content
                // Anthropic can return several tool_use blocks in one turn.
                "tool_use" -> {
                    val id = (obj["id"] as? JsonPrimitive)?.content
                    val name = (obj["name"] as? JsonPrimitive)?.content
                    when (val decoded = AgentActionCodec.decode(name, obj["input"] as? JsonObject)) {
                        null -> Unit
                        is DecodedCall.Ok -> calls += AgentActionCall(id, decoded.action)
                        is DecodedCall.Malformed -> malformed += decoded.detail
                    }
                }
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
        return AgentResponse(message = text, raw = raw, inputTokens = inputTokens, outputTokens = outputTokens)
    }


    /**
     * OkHttp's execute() blocks. Run it on Dispatchers.IO so a caller on the main
     * dispatcher (viewModelScope) cannot trigger NetworkOnMainThreadException.
     */
    private suspend fun execute(request: Request): String = withContext(Dispatchers.IO) {
        client.newCall(request).awaitResponse().use { response ->
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
                toolCalls.forEach { call ->
                    add(buildJsonObject {
                        put("type", "tool_use")
                        put("id", call.id)
                        put("name", call.name)
                        put("input", parseObjectOrEmpty(call.arguments))
                    })
                }
            }
            put("content", JsonArray(
                if (blocks.isEmpty()) listOf(buildJsonObject { put("type", "text"); put("text", "") })
                else blocks,
            ))
        }

        // Anthropic delivers tool results inside a *user* turn, not a "tool" role. Consecutive
        // results are merged into one such turn by toAnthropicMessages().
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

/**
 * Anthropic expects every tool result for one assistant turn inside a SINGLE user message.
 * Because the loop can batch several actions per turn, consecutive Tool messages are merged
 * into one user turn carrying several tool_result blocks. Sending them as separate user turns
 * risks a same-role sequence Anthropic rejects.
 */
internal fun List<AgentMessage>.toAnthropicMessages(): List<JsonObject> {
    val out = mutableListOf<JsonObject>()
    var i = 0
    while (i < size) {
        if (this[i].role == MessageRole.Tool) {
            val blocks = mutableListOf<JsonElement>()
            while (i < size && this[i].role == MessageRole.Tool) {
                val result = this[i]
                blocks += buildJsonObject {
                    put("type", "tool_result")
                    put("tool_use_id", result.toolCallId.orEmpty())
                    put("content", result.content)
                }
                i++
            }
            out += buildJsonObject {
                put("role", "user")
                put("content", JsonArray(blocks))
            }
        } else {
            out += this[i].toAnthropicMessage()
            i++
        }
    }
    return out
}

private fun AgentToolSpec.toAnthropicTool(): JsonObject = buildJsonObject {
    put("name", name)
    put("description", description)
    put("input_schema", AgentJson.parseToJsonElement(parametersJson))
}
