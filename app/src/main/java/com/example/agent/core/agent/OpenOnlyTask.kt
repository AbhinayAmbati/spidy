package com.example.agent.core.agent

import com.example.agent.domain.model.StepStatus
import com.example.agent.domain.model.TaskStep

/**
 * Recognises a task that is finished the moment the app opens.
 *
 * "Open YouTube" is done once open_app has succeeded, but Needle never calls `finish` after it:
 * on the next turn it returns no calls (only a suppressed click on the "Opening YouTube" status
 * line), so a task that worked was reported as "The model never chose an action". Pure, so it
 * is unit-testable.
 */
object OpenOnlyTask {

    private val OPEN_INTENT = Regex("""^\s*(please\s+)?(open|launch|start|go to)\b""", RegexOption.IGNORE_CASE)
    private val OPENING_TOOLS = setOf("open_app", "launch_url")

    /** True when the prompt only asks to open something and exactly that has succeeded. */
    fun isSatisfied(prompt: String, toolsUsed: Set<String>, steps: List<TaskStep>): Boolean =
        OPEN_INTENT.containsMatchIn(prompt) &&
            toolsUsed.isNotEmpty() && toolsUsed.all { it in OPENING_TOOLS } &&
            steps.isNotEmpty() && steps.all { it.status == StepStatus.Done }

    /** What to tell the user: the last step's label, e.g. "Opening YouTube". */
    fun summary(steps: List<TaskStep>): String = steps.lastOrNull()?.label ?: "Done"
}
