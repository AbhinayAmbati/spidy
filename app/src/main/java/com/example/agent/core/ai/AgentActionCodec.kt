package com.example.agent.core.ai

import com.example.agent.domain.model.AgentAction
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Turns a provider's tool call into an [AgentAction].
 *
 * This exists because of a subtle mismatch that made *every* tool call fail:
 * the tool schemas we send declare only the tool's parameters — `open_app` takes
 * `{ "packageName": … }` and nothing else — while [AgentAction] requires an `action`
 * field. The tool's **name** is the action, and it lives in the function call's
 * `name`, never inside the arguments object.
 *
 * Decoding the arguments on their own therefore failed with "Field 'action' is
 * required" on every single step. The name has to be merged in first.
 */
internal object AgentActionCodec {

    private const val ACTION_KEY = "action"

    /**
     * @param toolName  the function name the model called — authoritative.
     * @param parameters the arguments object, which never repeats the action.
     * @return the decoded action, or null when there is nothing that looks like a call.
     */
    fun fromToolCall(toolName: String?, parameters: JsonObject?): AgentAction? {
        val name = toolName?.takeIf { it.isNotBlank() }
        val args = parameters ?: JsonObject(emptyMap())

        val resolvedName = name
            ?: (args[ACTION_KEY] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
            ?: return null

        // The function name wins over anything the arguments claim, because the name
        // is what the provider actually routed to us.
        val merged = JsonObject(args + (ACTION_KEY to JsonPrimitive(resolvedName)))

        return runCatching { AgentJson.decodeFromJsonElement(AgentAction.serializer(), merged) }
            .getOrElse { error ->
                throw AIProviderException(
                    AIProviderException.Kind.Parse,
                    "The model called \"$resolvedName\" with arguments I couldn't read: " +
                        "${args.toString().take(200)} (${error.message ?: "unexpected shape"})",
                )
            }
    }

    /**
     * Convenience for OpenAI-style responses, where arguments arrive as a JSON
     * *string*. A few gateways send them as an object instead, so both are accepted.
     */
    fun fromToolCallJson(toolName: String?, parametersJson: String?): AgentAction? {
        val name = toolName?.takeIf { it.isNotBlank() }
        if (name == null && parametersJson.isNullOrBlank()) return null
        val parsed = runCatching { AgentJson.parseToJsonElement(parametersJson ?: "{}") }.getOrNull()
        return fromToolCall(name, parsed as? JsonObject)
    }
}
