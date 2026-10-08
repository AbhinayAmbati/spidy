package com.example.agent.domain.model

/**
 * Task state machine.
 *
 *   IDLE -> PLANNING -> EXECUTING -> OBSERVING -> (EXECUTING | WAITING_FOR_CONFIRMATION)
 *   ... -> COMPLETED | FAILED | CANCELLED
 *   PAUSED is reachable from any working state.
 */
enum class TaskState {
    IDLE,
    PLANNING,
    EXECUTING,
    OBSERVING,
    WAITING_FOR_CONFIRMATION,
    COMPLETED,
    FAILED,
    CANCELLED,
    PAUSED;

    val isTerminal: Boolean get() = this == COMPLETED || this == FAILED || this == CANCELLED
    val isWorking: Boolean get() = this == PLANNING || this == EXECUTING || this == OBSERVING
}

/** A single observable step shown to the user (never chain-of-thought). */
data class TaskStep(
    val index: Int,
    val label: String,
    val status: StepStatus,
    /** Why the step failed, or a short note. Shown in the UI so failures are not silent. */
    val detail: String? = null,
)

enum class StepStatus { Pending, Running, Done, Failed }

/** What the agent is paused on, if anything. */
enum class AwaitingKind { None, Confirmation, Question }

data class TaskProgress(
    val taskId: String,
    val prompt: String,
    val state: TaskState,
    val steps: List<TaskStep>,
    val currentStep: Int,
    val totalSteps: Int,
    val message: String? = null,
    val error: String? = null,
    val awaitingKind: AwaitingKind = AwaitingKind.None,
) {
    companion object {
        const val MAX_AGENT_STEPS = 30
    }
}
