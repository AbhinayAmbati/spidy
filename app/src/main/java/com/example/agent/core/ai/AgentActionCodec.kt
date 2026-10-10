package com.example.agent.core.ai

import com.example.agent.domain.model.AgentAction
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** What decoding one tool call produced. */
sealed interface DecodedCall {
    /** The call read cleanly. */
    data class Ok(val action: AgentAction) : DecodedCall

    /**
     * The model called a tool, but the arguments could not be read even after coercion.
     *
     * This is deliberately *not* an exception. A malformed call used to throw out of parsing
     * and kill the whole task — on the very first step, before the phone had done anything —
     * with "The model returned something I couldn't understand". But a model that fumbles one
     * call is usually fine on the next one, so the right answer is to show it what was wrong
     * and let it try again.
     */
    data class Malformed(val toolName: String?, val detail: String) : DecodedCall
}

/**
 * Turns a provider's tool call into an [AgentAction].
 *
 * Two separate problems are handled here.
 *
 * **The name is separate from the arguments.** The tool schemas declare only the tool's
 * parameters — `open_app` takes `{ "packageName": … }` — while [AgentAction] requires an
 * `action` field. The tool's *name* is the action, and it lives in the function call's
 * `name`, never inside the arguments object. Decoding the arguments alone therefore failed
 * with "Field 'action' is required" on every single step. The name has to be merged in first.
 *
 * **Models do not always send the shape the schema asked for.** A call like
 * `{"target": "Search"}` — a bare string where an object belongs — is extremely common, and
 * kotlinx.serialization rejects it outright. So the arguments are coerced into shape before
 * decoding: the intent is obvious to a human, and it should be obvious to us.
 */
internal object AgentActionCodec {

    private const val ACTION_KEY = "action"

    private val STRING_FIELDS = setOf("action", "text", "packageName", "url", "direction", "reason")
    private val TARGET_STRING_FIELDS =
        setOf("text", "contentDescription", "viewId", "key", "className")

    /** @return the decoded action, or null when there is nothing that looks like a call. */
    fun decode(toolName: String?, parameters: JsonObject?): DecodedCall? {
        val name = toolName?.takeIf { it.isNotBlank() }
        val rawArgs = parameters ?: JsonObject(emptyMap())

        val resolvedName = name
            ?: (rawArgs[ACTION_KEY] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
            ?: return null

        // The function name wins over anything the arguments claim, because the name is what
        // the provider actually routed to us.
        val merged = JsonObject(normalise(rawArgs) + (ACTION_KEY to JsonPrimitive(resolvedName)))

        return runCatching {
            DecodedCall.Ok(AgentJson.decodeFromJsonElement(AgentAction.serializer(), merged))
        }.getOrElse { error ->
            DecodedCall.Malformed(
                toolName = resolvedName,
                detail = "The model called \"$resolvedName\" with arguments I couldn't read: " +
                    "${rawArgs.toString().take(200)} (${error.message ?: "unexpected shape"})",
            )
        }
    }

    /**
     * Convenience for OpenAI-style responses, where arguments arrive as a JSON *string*.
     * A few gateways send them as an object instead, so both are accepted.
     */
    fun decodeJson(toolName: String?, parametersJson: String?): DecodedCall? {
        val name = toolName?.takeIf { it.isNotBlank() }
        if (name == null && parametersJson.isNullOrBlank()) return null
        val parsed = runCatching { AgentJson.parseToJsonElement(parametersJson ?: "{}") }.getOrNull()
        return decode(name, parsed as? JsonObject)
    }

    // ------------------------------------------------------------------ coercion

    /**
     * Rewrites the argument object into the shape [AgentAction] declares.
     *
     * Only the differences that actually occur are handled: a string where an object belongs,
     * numbers where strings belong, strings where numbers belong, and a nested `target` that
     * should have been flattened.
     */
    internal fun normalise(args: JsonObject): JsonObject {
        val out = LinkedHashMap<String, JsonElement>()
        for ((key, value) in args) {
            when (key) {
                "target" -> normaliseTarget(value)?.let { out[key] = it }
                in STRING_FIELDS -> asString(value)?.let { out[key] = it }
                "amount" -> asInt(value)?.let { out[key] = it }
                "durationMs" -> asLong(value)?.let { out[key] = it }
                else -> out[key] = value // ignored by the decoder, kept for the error message
            }
        }
        return JsonObject(out)
    }

    private fun normaliseTarget(value: JsonElement?): JsonObject? = when (value) {
        null, is JsonNull -> null
        // `"target": "Search"` — the single most common malformed call there is.
        is JsonPrimitive -> JsonObject(mapOf("text" to JsonPrimitive(value.content)))
        is JsonObject -> {
            val out = LinkedHashMap<String, JsonElement>()
            for ((key, v) in value) {
                when (key) {
                    in TARGET_STRING_FIELDS -> asString(v)?.let { out[key] = it }
                    "ref" -> asInt(v)?.let { out[key] = it }
                    // `{"target":{"target":{"text":"Search"}}}` — a needless extra level.
                    "target" -> normaliseTarget(v)?.forEach { (k, inner) ->
                        if (k !in out) out[k] = inner
                    }
                    else -> Unit
                }
            }
            JsonObject(out)
        }
        else -> null
    }

    private fun asString(value: JsonElement?): JsonPrimitive? = when (value) {
        null, is JsonNull -> null
        is JsonPrimitive -> JsonPrimitive(value.content)
        else -> null
    }

    private fun asInt(value: JsonElement?): JsonPrimitive? {
        val text = asString(value)?.content ?: return null
        return text.toIntOrNull()?.let { JsonPrimitive(it) }
            ?: text.toDoubleOrNull()?.let { JsonPrimitive(it.toInt()) }
    }

    private fun asLong(value: JsonElement?): JsonPrimitive? {
        val text = asString(value)?.content ?: return null
        return text.toLongOrNull()?.let { JsonPrimitive(it) }
            ?: text.toDoubleOrNull()?.let { JsonPrimitive(it.toLong()) }
    }
}
