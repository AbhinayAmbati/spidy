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
                messages.filter { it.role != MessageRole.System }.map { it.toGeminiContent() }
            ))
            if (tools.isNotEmpty()) {
                put("tools", JsonArray(listOf(buildJsonObject {
                    put("functionDeclarations", JsonArray(tools.map { it.toGeminiFunction() }))
                })))
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
        val root = runCatching { AgentJson.parseToJsonElement(raw).jsonObject }
            .getOrElse { throw AIProviderException(AIProviderException.Kind.Parse, "Malformed provider response") }

        val parts = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("content")?.jsonObject?.get("parts")?.jsonArray
            ?: return AgentResponse(message = null, raw = raw)

        var text: String? = null
        for (part in parts) {
            val obj = part.jsonObject
            obj["functionCall"]?.jsonObject?.let { call ->
                val args = call["args"]?.jsonObject
                if (args != null) {
                    val action = runCatching {
                        AgentJson.decodeFromJsonElement(AgentAction.serializer(), args)
                    }.getOrElse {
                        throw AIProviderException(
                            AIProviderException.Kind.Parse,
                            "Model returned an action that did not match the schema",
                        )
                    }
                    return AgentResponse(action = action, raw = raw)
                }
            }
            obj["text"]?.jsonPrimitive?.content?.let { text = it }
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

private fun AgentMessage.toGeminiContent(): JsonObject = buildJsonObject {
    put("role", if (role == MessageRole.Assistant) "model" else "user")
    put("parts", JsonArray(listOf(buildJsonObject { put("text", content) })))
}

private fun AgentToolSpec.toGeminiFunction(): JsonObject = buildJsonObject {
    put("name", name)
    put("description", description)
    put("parameters", AgentJson.parseToJsonElement(parametersJson))
}
