package com.example.agent

import com.example.agent.core.ai.AgentActionCodec
import com.example.agent.core.ai.AIProviderException
import com.example.agent.domain.model.ToolNames
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import kotlinx.serialization.json.JsonObject
import org.junit.Test

/**
 * Regression tests for the tool-call -> [com.example.agent.domain.model.AgentAction] path.
 *
 * Every provider sends the tool's *name* separately from its arguments, and the
 * arguments object never repeats the action. Decoding the arguments alone therefore
 * failed with "Field 'action' is required" on every step — the app could never get
 * past step 0. These tests pin the merge behaviour that fixes it.
 */
class ToolCallActionCodecTest {

    @Test
    fun `merges the tool name into an open_app call`() {
        val action = AgentActionCodec.fromToolCall(
            toolName = "open_app",
            parameters = AgentActionCodecTestJson.obj("""{"packageName":"com.android.chrome"}"""),
        )

        assertThat(action).isNotNull()
        assertThat(action!!.action).isEqualTo(ToolNames.OPEN_APP)
        assertThat(action.packageName).isEqualTo("com.android.chrome")
    }

    @Test
    fun `keeps a nested target when the name is merged in`() {
        val action = AgentActionCodec.fromToolCall(
            toolName = "click",
            parameters = AgentActionCodecTestJson.obj("""{"target":{"text":"Google Search"}}"""),
        )

        assertThat(action!!.action).isEqualTo(ToolNames.CLICK)
        assertThat(action.target?.text).isEqualTo("Google Search")
    }

    @Test
    fun `handles a tool that takes no arguments`() {
        val action = AgentActionCodec.fromToolCall(
            toolName = "press_back",
            parameters = AgentActionCodecTestJson.obj("{}"),
        )

        assertThat(action!!.action).isEqualTo(ToolNames.PRESS_BACK)
    }

    @Test
    fun `handles finish with only a reason`() {
        val action = AgentActionCodec.fromToolCall(
            toolName = "finish",
            parameters = AgentActionCodecTestJson.obj("""{"reason":"Searched for Kubernetes"}"""),
        )

        assertThat(action!!.action).isEqualTo(ToolNames.FINISH)
        assertThat(action.reason).isEqualTo("Searched for Kubernetes")
    }

    @Test
    fun `the function name wins over an action inside the arguments`() {
        val action = AgentActionCodec.fromToolCall(
            toolName = "click",
            parameters = AgentActionCodecTestJson.obj("""{"action":"press_home","target":{"text":"Go"}}"""),
        )

        assertThat(action!!.action).isEqualTo(ToolNames.CLICK)
    }

    @Test
    fun `falls back to an action embedded in the arguments when no name is given`() {
        val action = AgentActionCodec.fromToolCall(
            toolName = null,
            parameters = AgentActionCodecTestJson.obj("""{"action":"read_screen"}"""),
        )

        assertThat(action!!.action).isEqualTo(ToolNames.READ_SCREEN)
    }

    @Test
    fun `returns null when there is neither a name nor an embedded action`() {
        val action = AgentActionCodec.fromToolCall(
            toolName = null,
            parameters = AgentActionCodecTestJson.obj("""{"text":"hello"}"""),
        )

        assertThat(action).isNull()
    }

    @Test
    fun `parses the OpenAI arguments-as-a-string form`() {
        val action = AgentActionCodec.fromToolCallJson(
            toolName = "type_text",
            parametersJson = """{"text":"Kubernetes"}""",
        )

        assertThat(action!!.action).isEqualTo(ToolNames.TYPE_TEXT)
        assertThat(action.text).isEqualTo("Kubernetes")
    }

    @Test
    fun `unparseable arguments with a known tool name degrade instead of crashing`() {
        // The name alone is enough to build an action; the tool's own validator will
        // then reject it with a reason the model can act on ("target is required").
        val action = AgentActionCodec.fromToolCallJson(
            toolName = "click",
            parametersJson = "not json at all",
        )

        assertThat(action!!.action).isEqualTo(ToolNames.CLICK)
        assertThat(action.target).isNull()
    }

    @Test
    fun `arguments of the wrong shape are reported, not swallowed`() {
        assertThrows(AIProviderException::class.java) {
            AgentActionCodec.fromToolCall(
                toolName = "click",
                parameters = AgentActionCodecTestJson.obj("""{"target":"should be an object"}"""),
            )
        }
    }
}

/** Small helper so the tests do not depend on a particular JSON entry point. */
internal object AgentActionCodecTestJson {
    fun obj(json: String) =
        com.example.agent.core.ai.AgentJson.parseToJsonElement(json) as JsonObject
}
