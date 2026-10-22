package com.example.agent.core.ai.local

import com.example.agent.core.intent.SpideyIntent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/**
 * What one engine answer contained.
 *
 * @property intents the calls that were well formed, in the order the model made them.
 * @property malformed one line for every call that was dropped (unknown tool, missing argument)
 * or for an answer that was not JSON at all. Never thrown: the caller decides what it means.
 * @property confidence the engine's score, or null when it gave none.
 * @property reasoning the engine's one-line explanation, when present.
 */
data class ParsedSpidey(
    val intents: List<SpideyIntent>,
    val malformed: List<String>,
    val confidence: Double?,
    val reasoning: String?,
)

/**
 * Reads the JSON object the Spidey engine returns:
 *
 *   {"function_calls":[{"name":"set_timer","arguments":{"minutes":10}}],"reasoning":"...","confidence":0.9}
 *
 * Pure and free of Android types, so it is unit-testable. It never throws.
 */
object SpideyResponseParser {

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(raw: String): ParsedSpidey {
        val root = runCatching { json.parseToJsonElement(raw) }.getOrNull() as? JsonObject
            ?: return ParsedSpidey(
                intents = emptyList(),
                malformed = listOf("not a JSON object: ${raw.take(120)}"),
                confidence = null,
                reasoning = null,
            )

        val confidence = (root["confidence"] as? JsonPrimitive)?.doubleOrNull
        val reasoning = (root["reasoning"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }

        val intents = mutableListOf<SpideyIntent>()
        val malformed = mutableListOf<String>()
        for (entry in root["function_calls"] as? JsonArray ?: JsonArray(emptyList())) {
            val call = entry as? JsonObject
            if (call == null) {
                malformed += "function call is not an object"
                continue
            }
            val name = (call["name"] as? JsonPrimitive)?.content
            val decoded = decode(name, argumentsOf(call["arguments"]))
            val intent = decoded.intent
            if (intent != null) intents += intent else malformed += decoded.problem
        }
        return ParsedSpidey(intents, malformed, confidence, reasoning)
    }

    private class Decoded(val intent: SpideyIntent?, val problem: String = "")

    private fun decode(name: String?, args: JsonObject?): Decoded {
        if (name == null) return Decoded(null, "function call has no name")
        if (args == null) return Decoded(null, "$name: arguments are missing or not an object")
        return when (name) {
            "open_app" -> text(args, "app")?.let { Decoded(SpideyIntent.OpenApp(it)) } ?: missing(name, "app")
            "send_message" -> {
                val contact = text(args, "contact")
                val body = text(args, "text")
                when {
                    contact == null -> missing(name, "contact")
                    body == null -> missing(name, "text")
                    else -> Decoded(SpideyIntent.SendMessage(contact, body))
                }
            }
            "call_contact" -> text(args, "contact")?.let { Decoded(SpideyIntent.CallContact(it)) }
                ?: missing(name, "contact")
            "set_alarm" -> text(args, "time")?.let { Decoded(SpideyIntent.SetAlarm(it)) } ?: missing(name, "time")
            "set_timer" -> minutes(args["minutes"])?.let { Decoded(SpideyIntent.SetTimer(it)) }
                ?: missing(name, "minutes")
            else -> Decoded(null, "unknown tool: $name")
        }
    }

    private fun missing(tool: String, arg: String) = Decoded(null, "$tool: missing or invalid '$arg'")

    // A required string must be present and non-blank.
    private fun text(args: JsonObject, key: String): String? =
        (args[key] as? JsonPrimitive)?.content?.trim()?.takeIf { it.isNotEmpty() }

    // Minutes may arrive as 10, 10.0, "10" or "10.0"; a fraction such as 10.5 is not a whole timer.
    private fun minutes(element: JsonElement?): Int? {
        val value = (element as? JsonPrimitive)?.content?.trim()?.toDoubleOrNull() ?: return null
        if (value.isNaN() || value.isInfinite() || value != Math.floor(value)) return null
        if (value < Int.MIN_VALUE || value > Int.MAX_VALUE) return null
        return value.toInt()
    }

    // Arguments arrive as an object, but some engines send them as a JSON string.
    private fun argumentsOf(element: JsonElement?): JsonObject? = when (element) {
        is JsonObject -> element
        is JsonPrimitive -> runCatching { json.parseToJsonElement(element.content) }.getOrNull() as? JsonObject
        else -> null
    }
}
