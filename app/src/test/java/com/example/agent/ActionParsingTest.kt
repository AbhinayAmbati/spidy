package com.example.agent

import com.example.agent.core.ai.AgentJson
import com.example.agent.domain.model.AgentAction
import com.example.agent.domain.model.ToolNames
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The model's structured action is parsed, never executed as code. */
class ActionParsingTest {

    @Test
    fun `parses a click action with a text target`() {
        val json = """
            {"action":"click","target":{"text":"Google Search"}}
        """.trimIndent()

        val action = AgentJson.decodeFromString(AgentAction.serializer(), json)

        assertThat(action.action).isEqualTo(ToolNames.CLICK)
        assertThat(action.target?.text).isEqualTo("Google Search")
    }

    @Test
    fun `parses a type_text action`() {
        val json = """{"action":"type_text","text":"best laptops under 60000"}"""

        val action = AgentJson.decodeFromString(AgentAction.serializer(), json)

        assertThat(action.action).isEqualTo(ToolNames.TYPE_TEXT)
        assertThat(action.text).isEqualTo("best laptops under 60000")
    }

    @Test
    fun `parses an open_app action`() {
        val json = """{"action":"open_app","packageName":"com.android.chrome"}"""

        val action = AgentJson.decodeFromString(AgentAction.serializer(), json)

        assertThat(action.packageName).isEqualTo("com.android.chrome")
    }

    @Test
    fun `ignores unknown fields the provider may add`() {
        val json = """{"action":"press_back","confidence":0.9,"note":"extra"}"""

        val action = AgentJson.decodeFromString(AgentAction.serializer(), json)

        assertThat(action.action).isEqualTo(ToolNames.PRESS_BACK)
    }
}
