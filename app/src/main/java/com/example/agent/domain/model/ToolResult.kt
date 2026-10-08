package com.example.agent.domain.model

/** Outcome of executing a single tool. Never throws across the tool boundary. */
sealed interface ToolResult {
    data class Success(
        val observation: String,
        val data: Map<String, Any?> = emptyMap(),
    ) : ToolResult

    data class Failure(
        val message: String,
        val recoverable: Boolean = true,
    ) : ToolResult

    /** Tool paused the loop until the user confirms or denies. */
    data class NeedsConfirmation(
        val prompt: String,
        val action: AgentAction,
    ) : ToolResult
}

/** Whether an action is safe to run unattended or must be confirmed first. */
enum class ActionRisk { Safe, Sensitive }
