package com.example.agent.core.ai.provider

import com.example.agent.core.ai.AIProvider
import com.example.agent.core.ai.AgentActionCodec
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

/** Google Gemini (generativelanguage) adapter. */
class GeminiProvider(
    private val baseUrl: String,
    private val model: String,
    private val apiKey: String?,
    private val client: OkHttpClient = OpenAICompatibleProvider.defaultClient(),
) : AIProvider {

    override val id: String = "gemini"

    override suspend fun generateResponse(
        messages: List<AgentMessage>,
        tools: List<AgentToolSpec>,
    ): AgentResponse {
        val key = apiKey?.takeIf { it.isNotBlank() }
            ?: throw AIProviderException(AIProviderException.Kind.NoKey, "No API key configured for Gemini")

        val system = messages.filter { it.role == MessageRole.System }.joinToString("\n\n") { it.content }

        val body = buildJsonObject {
            if (system.isNotBlank()) {
                put("systemInstruction", buildJsonObject {
                    put("parts", JsonArray(listOf(buildJsonObject { put("text", system) })))
                })
            }
            put("contents", JsonArray(
                messages.filter { it.role != MessageRole.System }.toGeminiContents()
            ))
            if (tools.isNotEmpty()) {
                put("tools", JsonArray(listOf(buildJsonObject {
                    put("functionDeclarations", JsonArray(tools.map { it.toGeminiFunction() }))
                })))
                // Force a function call: the agent must act, never chat.
                put("toolConfig", buildJsonObject {
                    put("functionCallingConfig", buildJsonObject { put("mode", "ANY") })
                })
            }
            put("generationConfig", buildJsonObject { put("temperature", 0) })
        }

        val url = "${baseUrl.trimEnd('/')}/models/$model:generateContent?key=$key"
        val request = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()

        val raw = try {
            execute(request)
        } catch (io: IOException) {
            throw AIProviderException(AIProviderException.Kind.Network, "Could not reach Gemini", io)
        }

        return parse(raw)
    }

    private fun parse(raw: String): AgentResponse {
        val root = runCatching { AgentJson.parseToJsonElement(raw) as? JsonObject }.getOrNull()
            ?: return AgentResponse(message = null, raw = raw)

        val usage = root["usageMetadata"] as? JsonObject
        val inputTokens = (usage?.get("promptTokenCount") as? JsonPrimitive)?.content?.toIntOrNull()
        val outputTokens = (usage?.get("candidatesTokenCount") as? JsonPrimitive)?.content?.toIntOrNull()

        val candidates = root["candidates"] as? JsonArray
            ?: return AgentResponse(message = null, raw = raw, inputTokens = inputTokens, outputTokens = outputTokens)
        val contentObj = (candidates.firstOrNull() as? JsonObject)?.get("content") as? JsonObject
        val parts = contentObj?.get("parts") as? JsonArray
            ?: return AgentResponse(message = null, raw = raw)

        var text: String? = null
        val calls = mutableListOf<AgentActionCall>()
        for (part in parts) {
            val obj = part as? JsonObject ?: continue
            // Gemini can return several functionCall parts in one turn.
            (obj["functionCall"] as? JsonObject)?.let { call ->
                val name = (call["name"] as? JsonPrimitive)?.content
                val args = call["args"] as? JsonObject
                AgentActionCodec.fromToolCall(name, args)?.let { calls += AgentActionCall(null, it) }
            }
            (obj["text"] as? JsonPrimitive)?.content?.let { text = it }
        }

        if (calls.isNotEmpty()) {
            return AgentResponse(
                calls = calls,
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
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw when (response.code) {
                        400, 401, 403 -> AIProviderException(AIProviderException.Kind.Unauthorized, "The API key was rejected")
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

internal fun AgentMessage.toGeminiContent(): JsonObject = buildJsonObject {
    when {
        role == MessageRole.Assistant -> {
            put("role", "model")
            val parts = buildList {
                if (content.isNotBlank()) add(buildJsonObject { put("text", content) })
                toolCalls.forEach { call ->
                    add(buildJsonObject {
                        put("functionCall", buildJsonObject {
                            put("name", call.name)
                            put("args", parseObjectOrEmpty(call.arguments))
                        })
                    })
                }
            }
            put("parts", JsonArray(
                if (parts.isEmpty()) listOf(buildJsonObject { put("text", "") }) else parts,
            ))
        }

        // Gemini pairs a result with its call by function *name*, inside a user turn.
        role == MessageRole.Tool -> {
            put("role", "user")
            put("parts", JsonArray(listOf(buildJsonObject {
                put("functionResponse", buildJsonObject {
                    put("name", name ?: "tool")
                    put("response", buildJsonObject { put("result", content) })
                })
            })))
        }

        else -> {
            put("role", "user")
            put("parts", JsonArray(listOf(buildJsonObject { put("text", content) })))
        }
    }
}

/**
 * Gemini pairs results with calls by function name inside a *user* turn. Because the loop can
 * batch several actions per turn, consecutive Tool messages are merged into one turn with
 * several functionResponse parts.
 */
internal fun List<AgentMessage>.toGeminiContents(): List<JsonObject> {
    val out = mutableListOf<JsonObject>()
    var i = 0
    while (i < size) {
        if (this[i].role == MessageRole.Tool) {
            val parts = mutableListOf<JsonElement>()
            while (i < size && this[i].role == MessageRole.Tool) {
                val result = this[i]
                parts += buildJsonObject {
                    put("functionResponse", buildJsonObject {
                        put("name", result.name ?: "tool")
                        put("response", buildJsonObject { put("result", result.content) })
                    })
                }
                i++
            }
            out += buildJsonObject {
                put("role", "user")
                put("parts", JsonArray(parts))
            }
        } else {
            out += this[i].toGeminiContent()
            i++
        }
    }
    return out
}

private fun AgentToolSpec.toGeminiFunction(): JsonObject = buildJsonObject {
    put("name", name)
    put("description", description)
    put("parameters", AgentJson.parseToJsonElement(parametersJson))
}
