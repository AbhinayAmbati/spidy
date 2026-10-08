package com.example.agent

import com.example.agent.automation.tools.ToolRegistry
import com.example.agent.core.agent.ActionValidator
import com.example.agent.core.ai.AgentTool
import com.example.agent.core.ai.ValidationResult
import com.example.agent.domain.model.AgentAction
import com.example.agent.domain.model.ToolNames
import com.example.agent.domain.model.ToolResult
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ActionValidatorTest {

    private class FakeTool(
        override val name: String,
        override val parametersSchema: String = "{}",
        private val validator: (AgentAction) -> ValidationResult = { ValidationResult.Valid },
    ) : AgentTool {
        override val description = "fake"
        override fun validate(action: AgentAction) = validator(action)
        override suspend fun execute(action: AgentAction) = ToolResult.Success("ok")
    }

    private fun validator(vararg tools: AgentTool) = ActionValidator(ToolRegistry(tools.toList()))

    @Test
    fun `rejects an unknown tool`() {
        val outcome = validator().validate(AgentAction(action = "make_me_a_sandwich"), 0)
        assertThat(outcome).isInstanceOf(ActionValidator.Outcome.Rejected::class.java)
    }

    @Test
    fun `rejects when the tool's own validation fails`() {
        val tool = FakeTool(ToolNames.CLICK) { ValidationResult.Invalid("A target is required to click") }
        val outcome = validator(tool).validate(AgentAction(action = ToolNames.CLICK), 0)
        assertThat((outcome as ActionValidator.Outcome.Rejected).reason).contains("target")
    }

    @Test
    fun `allows a safe click`() {
        val tool = FakeTool(ToolNames.CLICK)
        val outcome = validator(tool).validate(
            AgentAction(action = ToolNames.CLICK, target = com.example.agent.domain.model.ActionTarget(text = "Images")),
            0,
        )
        assertThat(outcome).isEqualTo(ActionValidator.Outcome.Allowed)
    }

    @Test
    fun `requires confirmation for a sensitive tap`() {
        val tool = FakeTool(ToolNames.CLICK)
        val outcome = validator(tool).validate(
            AgentAction(action = ToolNames.CLICK, target = com.example.agent.domain.model.ActionTarget(text = "Send")),
            0,
        )
        assertThat(outcome).isInstanceOf(ActionValidator.Outcome.RequiresConfirmation::class.java)
    }

    @Test
    fun `rejects once the step limit is reached`() {
        val tool = FakeTool(ToolNames.CLICK)
        val outcome = validator(tool).validate(AgentAction(action = ToolNames.CLICK), stepIndex = 30)
        assertThat(outcome).isInstanceOf(ActionValidator.Outcome.Rejected::class.java)
    }
}
