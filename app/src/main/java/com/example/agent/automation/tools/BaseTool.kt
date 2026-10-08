package com.example.agent.automation.tools

import com.example.agent.core.ai.AgentTool
import com.example.agent.core.ai.ValidationResult
import com.example.agent.domain.model.ActionRisk
import com.example.agent.domain.model.AgentAction

/** Shared plumbing for the built-in tools. */
abstract class BaseTool : AgentTool {

    override val risk: ActionRisk get() = ActionRisk.Safe

    override fun validate(action: AgentAction): ValidationResult = ValidationResult.Valid

    protected fun textOrNull(action: AgentAction): String? =
        action.text?.takeIf { it.isNotBlank() }
}
