package com.example.agent.core.agent

import com.example.agent.automation.tools.ToolRegistry
import com.example.agent.core.ai.ValidationResult
import com.example.agent.domain.model.ActionRisk
import com.example.agent.domain.model.AgentAction
import com.example.agent.domain.model.TaskProgress
import com.example.agent.domain.model.ToolNames

/**
 * Every model-proposed action passes through here before anything touches the
 * device. The model is treated as untrusted input: if it is not explicitly
 * allowed by a registered tool and its arguments, it does not run.
 */
class ActionValidator(private val registry: ToolRegistry) {

    sealed interface Outcome {
        data object Allowed : Outcome
        data class Rejected(val reason: String) : Outcome
        data class RequiresConfirmation(val reason: String) : Outcome
    }

    fun validate(action: AgentAction, stepIndex: Int): Outcome {
        if (stepIndex >= TaskProgress.MAX_AGENT_STEPS) {
            return Outcome.Rejected("Step limit of ${TaskProgress.MAX_AGENT_STEPS} reached.")
        }

        val tool = registry.find(action.action)
            ?: return Outcome.Rejected("Unknown action \"${action.action}\".")

        when (val result = tool.validate(action)) {
            is ValidationResult.Invalid -> return Outcome.Rejected(result.reason)
            is ValidationResult.NeedsConfirmation -> return Outcome.RequiresConfirmation(result.reason)
            ValidationResult.Valid -> Unit
        }

        // Opening a URL is for the web. Messaging and calling links are refused here so
        // the model is told why, rather than silently launching an app mid-task.
        UrlPolicy.rejectionReason(action.url)?.let { return Outcome.Rejected(it) }

        if (tool.risk == ActionRisk.Sensitive) {
            return Outcome.RequiresConfirmation("This action is marked as sensitive.")
        }

        sensitiveTargetReason(action)?.let { return Outcome.RequiresConfirmation(it) }

        return Outcome.Allowed
    }

    /**
     * Heuristic guard: tapping a control whose label implies an irreversible or
     * consequential outcome must be confirmed, even though the *tool* is generic.
     */
    private fun sensitiveTargetReason(action: AgentAction): String? {
        if (action.action != ToolNames.CLICK && action.action != ToolNames.LONG_CLICK) return null
        val label = listOfNotNull(action.target?.text, action.target?.contentDescription)
            .joinToString(" ").lowercase()
        if (label.isBlank()) return null
        val hit = SENSITIVE_WORDS.firstOrNull { label.contains(it) } ?: return null
        return "Tapping \"$hit\" may be hard to undo."
    }

    private companion object {
        val SENSITIVE_WORDS = listOf(
            "send", "pay", "payment", "buy", "purchase", "checkout", "order now", "place order",
            "delete", "remove account", "uninstall", "install", "transfer", "confirm", "submit",
            "call", "dial", "subscribe", "book", "erase", "format", "sign out", "log out",
        )
    }
}
