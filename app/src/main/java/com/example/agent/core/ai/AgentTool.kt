package com.example.agent.core.ai

import com.example.agent.domain.model.ActionRisk
import com.example.agent.domain.model.AgentAction
import com.example.agent.domain.model.ToolResult

/**
 * The contract every device action implements. The agent runtime only ever knows
 * about tools through this interface, so new capabilities are added without
 * touching the runtime.
 */
interface AgentTool {
    val name: String
    val description: String

    /** Whether this tool must be confirmed by the user before it runs. */
    val risk: ActionRisk get() = ActionRisk.Safe

    /**
     * JSON Schema (as a string) describing the arguments this tool accepts. It is
     * handed to the provider so the model knows how to call the tool.
     */
    val parametersSchema: String

    /** Validate the incoming action's arguments before execution. */
    fun validate(action: AgentAction): ValidationResult

    /** Execute. Implementations must not throw; return [ToolResult.Failure] instead. */
    suspend fun execute(action: AgentAction): ToolResult
}

/** Result of static validation, before any device interaction happens. */
sealed interface ValidationResult {
    data object Valid : ValidationResult
    data class Invalid(val reason: String) : ValidationResult
    data class NeedsConfirmation(val reason: String) : ValidationResult
}
