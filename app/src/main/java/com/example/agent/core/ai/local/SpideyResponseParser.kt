package com.example.agent.core.ai.local

import com.example.agent.core.ai.AIProviderException
import com.example.agent.core.ai.AgentActionCodec
import com.example.agent.core.ai.AgentJson
import com.example.agent.core.ai.DecodedCall
import com.example.agent.domain.model.AgentActionCall
import com.example.agent.domain.model.AgentResponse
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/**
 * Reads the JSON object the Spidey engine returns for one turn:
 *
 *   {"function_calls":[{"name":"click","arguments":{...}}],"reasoning":"...","confidence":0.94}
 *
 * Pure and free of Android types, so the behaviour is unit-testable.
 */
object SpideyResponseParser {

    /**
     * @param minConfidence calls the model is less sure of than this are dropped. The engine
     * already withholds calls under its own floor, so 0 trusts it; raise this to be stricter.
     */
    fun parse(raw: String, minConfidence: Double = 0.0): AgentResponse {
        val root = runCatching { AgentJson.parseToJsonElement(raw) }.getOrNull() as? JsonObject
            ?: throw AIProviderException(
                AIProviderException.Kind.Parse,
                "The on-device model returned something that is not JSON: ${raw.take(200)}",
            )

        val confidence = (root["confidence"] as? JsonPrimitive)?.doubleOrNull
        val reasoning = (root["reasoning"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }

        if (confidence != null && confidence < minConfidence) {
            return AgentResponse(
                message = "The on-device model was not confident enough to act " +
                    "(${"%.2f".format(confidence)}).",
                raw = raw,
            )
        }

        val calls = mutableListOf<AgentActionCall>()
        val malformed = mutableListOf<String>()
        for (entry in root["function_calls"] as? JsonArray ?: JsonArray(emptyList())) {
            val call = entry as? JsonObject ?: continue
            val name = (call["name"] as? JsonPrimitive)?.content
            when (val decoded = AgentActionCodec.decode(name, argumentsOf(call["arguments"]))) {
                null -> Unit
                is DecodedCall.Ok -> calls += AgentActionCall(null, decoded.action)
                is DecodedCall.Malformed -> malformed += decoded.detail
            }
        }

        return AgentResponse(
            calls = calls,
            malformed = malformed,
            message = if (calls.isEmpty() && malformed.isEmpty()) reasoning else null,
            raw = raw,
        )
    }

    // Arguments arrive as an object, but some engines send them as a JSON string.
    private fun argumentsOf(element: JsonElement?): JsonObject? = when (element) {
        is JsonObject -> element
        is JsonPrimitive -> runCatching { AgentJson.parseToJsonElement(element.content) }.getOrNull() as? JsonObject
        else -> null
    }
}
