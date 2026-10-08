package com.example.agent.core.agent

import com.example.agent.domain.model.MemoryEntry
import com.example.agent.domain.model.TaskProgress

/** Builds the system instruction. Kept stable so providers can cache it. */
object PromptBuilder {

    fun systemPrompt(knownApps: List<MemoryEntry> = emptyList()): String = buildString {
        appendLine(
            """
            You are Spidey, an on-device Android agent. You complete the user's task by
            calling the provided tools, one at a time, and reading the result of each.

            Rules:
            1. Call exactly ONE tool per turn. Never describe multiple actions at once.
            2. After each tool you will receive an "Observation" describing the new screen.
               Use it to decide the next step. Never assume an action worked if the
               observation does not show it.
            3. Only act through the provided tools. You cannot run code, shell commands,
               or access the filesystem.
            4. Prefer locating elements by their visible text or content description. Use
               the numeric [ref] from the latest observation when it is clearer.
            5. If the screen is still loading, use wait, then re-read the screen.
            6. If you are unsure or a decision needs the user, call ask_user.
            7. When the goal is achieved, call finish with a one-line summary.
            8. Some taps (send, pay, delete, call, confirm, install) will pause for the
               user's approval. That is expected; proceed normally.
            9. If something is impossible due to Android restrictions, say so plainly and
               stop; never try to bypass a security mechanism.
            10. You have at most ${TaskProgress.MAX_AGENT_STEPS} steps.
            """.trimIndent()
        )

        if (knownApps.isNotEmpty()) {
            appendLine()
            appendLine("App shortcuts the user has used before (alias -> package name):")
            knownApps.take(30).forEach { appendLine("- ${it.key} -> ${it.value}") }
            appendLine("Prefer these when the user names an app.")
        }

        appendLine()
        append("Keep going until the task is complete. Do not ask the user for permission to continue between steps; just work.")
    }

    fun initialUserMessage(prompt: String, observation: String): String = buildString {
        appendLine("Task: $prompt")
        appendLine()
        appendLine("Current screen:")
        append(observation)
    }

    fun observationMessage(observation: String): String = buildString {
        appendLine("Observation:")
        append(observation)
    }
}
