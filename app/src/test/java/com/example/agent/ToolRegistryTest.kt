package com.example.agent

import com.example.agent.automation.tools.ToolRegistry
import com.example.agent.core.ai.AgentTool
import com.example.agent.core.ai.ValidationResult
import com.example.agent.domain.model.AgentAction
import com.example.agent.domain.model.ToolResult
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ToolRegistryTest {

    private class StubTool(override val name: String) : AgentTool {
        override val description = "stub"
        override val parametersSchema = "{}"
        override fun validate(action: AgentAction) = ValidationResult.Valid
        override suspend fun execute(action: AgentAction) = ToolResult.Success("ok")
    }

    @Test
    fun `looks tools up by exact name`() {
        val registry = ToolRegistry(listOf(StubTool("click"), StubTool("type_text")))
        assertThat(registry.contains("click")).isTrue()
        assertThat(registry.find("type_text")?.name).isEqualTo("type_text")
    }

    @Test
    fun `returns null for an unknown tool`() {
        val registry = ToolRegistry(listOf(StubTool("click")))
        assertThat(registry.find("rm_rf")).isNull()
    }

    @Test
    fun `exposes every tool as a spec for the model`() {
        val registry = ToolRegistry(listOf(StubTool("click"), StubTool("scroll")))
        val specs = registry.specs()
        assertThat(specs).hasSize(2)
        assertThat(specs.map { it.name }).containsExactly("click", "scroll")
    }
}
