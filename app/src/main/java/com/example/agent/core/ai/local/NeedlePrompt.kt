package com.example.agent.core.ai.local

import com.example.agent.core.ai.AgentJson
import com.example.agent.core.ai.AgentToolSpec
import com.example.agent.domain.model.AgentMessage
import com.example.agent.domain.model.MessageRole
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Turns the agent's conversation into what Needle takes: a short system prompt, a tool list,
 * and one self-contained input per turn.
 *
 * Needle is a tool-calling model with a small context, not a chat model, and the tool schemas
 * share that context. So the input is the task, the last few results and a capped listing of
 * the current screen - never the whole history. Pure, so it is unit-testable.
 */
object NeedlePrompt {

    const val MAX_HISTORY = 5
    const val MAX_SCREEN_LINES = 40

    private const val TASK_PREFIX = "Task: "
    private const val SCREEN_MARKER = "Current screen:"
    private const val OBSERVATION_MARKER = "Observation:"

    val SYSTEM_PROMPT = "You control an Android phone by calling one tool per step. " +
        "Pick the tool that moves the task forward, and fill every argument from the task " +
        "and the screen. Call finish when the task is done."

    /** The tools in the shape the engine reads: a JSON array of name, description, parameters. */
    fun toolsJson(tools: List<AgentToolSpec>): String =
        JsonArray(
            tools.map { tool ->
                JsonObject(
                    mapOf(
                        "name" to JsonPrimitive(tool.name),
                        "description" to JsonPrimitive(tool.description),
                        "parameters" to (
                            runCatching { AgentJson.parseToJsonElement(tool.parametersJson) }.getOrNull()
                                ?: JsonObject(emptyMap())
                            ),
                    ),
                )
            },
        ).toString()

    fun input(messages: List<AgentMessage>): String = buildString {
        appendLine(TASK_PREFIX + taskOf(messages))

        val done = history(messages)
        if (done.isNotEmpty()) {
            appendLine("Done so far:")
            done.forEach { appendLine("- $it") }
        }

        appendLine("Screen:")
        append(screenOf(messages))
    }

    private fun taskOf(messages: List<AgentMessage>): String {
        val first = messages.firstOrNull { it.role == MessageRole.User }?.content ?: return ""
        return first.removePrefix(TASK_PREFIX).substringBefore("\n\n$SCREEN_MARKER").trim()
    }

    // "tap Search -> ok": each call paired with the first line of what came back.
    private fun history(messages: List<AgentMessage>): List<String> {
        val results = messages
            .filter { it.role == MessageRole.Tool && it.toolCallId != null }
            .associate { it.toolCallId to it.content.lineSequence().firstOrNull { line -> line.isNotBlank() }.orEmpty() }

        return messages
            .flatMap { it.toolCalls }
            .map { call -> "${call.name} ${call.arguments} -> ${results[call.id].orEmpty().take(80)}".trim() }
            .takeLast(MAX_HISTORY)
    }

    private fun screenOf(messages: List<AgentMessage>): String {
        val latest = messages.lastOrNull { it.content.contains(OBSERVATION_MARKER) && it.role != MessageRole.System }
        val listing = when {
            latest != null -> latest.content.substringAfter(OBSERVATION_MARKER)
            else -> messages.firstOrNull { it.role == MessageRole.User }
                ?.content?.substringAfter(SCREEN_MARKER, "").orEmpty()
        }
        return listing.trim().lineSequence().take(MAX_SCREEN_LINES).joinToString("\n")
    }
}
