package com.example.agent.automation.tools

import com.example.agent.core.ai.AgentTool
import com.example.agent.core.ai.AgentToolSpec

/**
 * The catalogue of everything the agent may do. The runtime resolves actions
 * through this registry and nowhere else, so an unknown tool name can never run.
 */
class ToolRegistry(tools: List<AgentTool>) {

    private val byName: Map<String, AgentTool> = tools.associateBy { it.name }

    val all: List<AgentTool> get() = byName.values.toList()

    fun find(name: String): AgentTool? = byName[name]

    fun contains(name: String): Boolean = byName.containsKey(name)

    fun specs(): List<AgentToolSpec> = all.map {
        AgentToolSpec(name = it.name, description = it.description, parametersJson = it.parametersSchema)
    }
}
