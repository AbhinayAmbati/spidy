package com.example.agent.core.agent

import com.example.agent.domain.model.AwaitingKind
import com.example.agent.domain.model.StepStatus
import com.example.agent.domain.model.TaskProgress
import com.example.agent.domain.model.TaskState

/**
 * The one line of status the floating overlay shows.
 *
 * Deliberately a pure function of [TaskProgress], so it is unit-testable and so the overlay
 * can never drift out of step with what the task screen reports - both read the same state.
 *
 * This is the *observable* status only. It never carries the model's reasoning: the user sees
 * what the agent is doing, never a chain of thought.
 */
object AgentStatusText {

    /** How long a finished state stays on screen before the overlay is taken down. */
    const val LINGER_MILLIS = 2_500L

    /** @return the line to display, or null when the overlay should not be shown at all. */
    fun line(progress: TaskProgress): String? {
        val body = when (progress.state) {
            TaskState.IDLE -> return null
            TaskState.PLANNING -> "Thinking\u2026"
            TaskState.EXECUTING -> progress.steps.lastOrNull { it.status == StepStatus.Running }?.label
                ?: progress.steps.lastOrNull()?.label
                ?: "Working\u2026"
            TaskState.OBSERVING -> "Reading the screen"
            TaskState.WAITING_FOR_CONFIRMATION -> when (progress.awaitingKind) {
                AwaitingKind.Question -> "Waiting for your answer"
                else -> "Waiting for you to confirm"
            }
            TaskState.PAUSED -> "Paused"
            TaskState.COMPLETED -> "Done"
            TaskState.FAILED -> "Failed"
            TaskState.CANCELLED -> "Stopped"
        }

        // The step counter is only meaningful while there is still something to count.
        val counter = when {
            progress.state.isWorking || progress.state == TaskState.PAUSED ->
                "  \u00b7  ${progress.currentStep}/${progress.totalSteps}"
            else -> ""
        }

        return body + counter
    }

    /**
     * Whether a finished task should be shown at all.
     *
     * A task that completed while the user was in another app used to just make the overlay
     * vanish, which tells them nothing. The outcome is worth two seconds.
     */
    fun showsOutcome(state: TaskState): Boolean = state.isTerminal
}
