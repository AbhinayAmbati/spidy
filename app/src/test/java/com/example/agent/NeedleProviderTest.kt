package com.example.agent

import com.example.agent.core.ai.AIProviderException
import com.example.agent.core.ai.AgentToolSpec
import com.example.agent.core.ai.local.LocalEngineException
import com.example.agent.core.ai.local.LocalToolEngine
import com.example.agent.core.ai.local.NeedlePrompt
import com.example.agent.core.ai.local.NeedleProvider
import com.example.agent.core.ai.local.NeedleResponseParser
import com.example.agent.domain.model.AgentMessage
import com.example.agent.domain.model.MessageRole
import com.example.agent.domain.model.ToolCall
import com.example.agent.domain.model.ToolNames
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test

class NeedleProviderTest {

    private class FakeEngine(private val answers: ArrayDeque<String>) : LocalToolEngine {
        var starts = 0
        var resets = 0
        val inputs = mutableListOf<String>()
        var failWith: String? = null

        override fun start(systemPrompt: String, toolsJson: String) {
            failWith?.let { throw LocalEngineException(it) }
            starts++
        }

        override fun reset() {
            resets++
        }

        override fun complete(input: String, maxNewTokens: Int): String {
            inputs += input
            return answers.removeFirst()
        }
    }

    private val tools = listOf(
        AgentToolSpec(ToolNames.CLICK, "Tap an element.", """{"type":"object","properties":{"target":{"type":"object"}}}"""),
        AgentToolSpec(ToolNames.FINISH, "Declare the task complete.", """{"type":"object","properties":{}}"""),
    )

    private fun conversation() = listOf(
        AgentMessage(MessageRole.System, "ignored"),
        AgentMessage(MessageRole.User, "Task: open the settings\n\nCurrent screen:\n[k1] \"Search\" <EditText>"),
    )

    private val clickAnswer =
        """{"function_calls":[{"name":"click","arguments":{"target":{"text":"Settings"}}}],"reasoning":"tap it","confidence":0.93}"""

    // ---- parser ----------------------------------------------------------------------

    @Test
    fun `a function call becomes an action`() {
        val response = NeedleResponseParser.parse(clickAnswer)

        assertThat(response.calls).hasSize(1)
        assertThat(response.calls.single().action.action).isEqualTo(ToolNames.CLICK)
        assertThat(response.calls.single().action.target?.text).isEqualTo("Settings")
    }

    @Test
    fun `arguments sent as a JSON string are accepted`() {
        val raw = """{"function_calls":[{"name":"click","arguments":"{\"target\":{\"text\":\"Wi-Fi\"}}"}],"confidence":0.9}"""

        assertThat(NeedleResponseParser.parse(raw).calls.single().action.target?.text).isEqualTo("Wi-Fi")
    }

    @Test
    fun `an empty call list becomes a reply with no actions`() {
        val response = NeedleResponseParser.parse("""{"function_calls":[],"reasoning":"nothing fits","confidence":0.4}""")

        assertThat(response.calls).isEmpty()
        assertThat(response.message).isEqualTo("nothing fits")
    }

    @Test
    fun `a call below the confidence floor is dropped`() {
        val response = NeedleResponseParser.parse(clickAnswer, minConfidence = 0.95)

        assertThat(response.calls).isEmpty()
        assertThat(response.message).contains("not confident")
    }

    @Test
    fun `output that is not JSON is a parse failure`() {
        val failure = assertThrows(AIProviderException::class.java) { NeedleResponseParser.parse("garbled") }

        assertThat(failure.kind).isEqualTo(AIProviderException.Kind.Parse)
    }

    // ---- prompt ----------------------------------------------------------------------

    @Test
    fun `the input carries the task and the screen but not the system prompt`() {
        val input = NeedlePrompt.input(conversation())

        assertThat(input).contains("Task: open the settings")
        assertThat(input).contains("[k1] \"Search\" <EditText>")
        assertThat(input).doesNotContain("ignored")
    }

    @Test
    fun `the input shows recent results and the newest screen`() {
        val messages = conversation() + listOf(
            AgentMessage(MessageRole.Assistant, "", toolCalls = listOf(ToolCall("c1", "click", """{"target":{"text":"Search"}}"""))),
            AgentMessage(MessageRole.Tool, "Observation:\n[k9] \"Settings\" <TextView>", toolCallId = "c1", name = "click"),
        )

        val input = NeedlePrompt.input(messages)

        assertThat(input).contains("Done so far:")
        assertThat(input).contains("click")
        assertThat(input).contains("[k9] \"Settings\" <TextView>")
        assertThat(input).doesNotContain("[k1]")
    }

    @Test
    fun `a long screen is capped`() {
        val listing = (1..200).joinToString("\n") { "[k$it] \"Item $it\"" }
        val messages = listOf(AgentMessage(MessageRole.User, "Task: scroll\n\nCurrent screen:\n$listing"))

        val lines = NeedlePrompt.input(messages).lines().count { it.startsWith("[k") }

        assertThat(lines).isEqualTo(NeedlePrompt.MAX_SCREEN_LINES)
    }

    @Test
    fun `tools are described as name, description and parameters`() {
        val json = NeedlePrompt.toolsJson(tools)

        assertThat(json).contains("\"name\":\"click\"")
        assertThat(json).contains("\"description\":\"Tap an element.\"")
        assertThat(json).contains("\"parameters\":{\"type\":\"object\"")
    }

    // ---- provider --------------------------------------------------------------------

    @Test
    fun `the engine is started once per tool set and reset before every turn`() = runTest {
        val engine = FakeEngine(ArrayDeque(listOf(clickAnswer, clickAnswer)))
        val provider = NeedleProvider(engine)

        provider.generateResponse(conversation(), tools)
        provider.generateResponse(conversation(), tools)

        assertThat(engine.starts).isEqualTo(1)
        assertThat(engine.resets).isEqualTo(2)
    }

    @Test
    fun `a different tool set restarts the engine`() = runTest {
        val engine = FakeEngine(ArrayDeque(listOf(clickAnswer, clickAnswer)))
        val provider = NeedleProvider(engine)

        provider.generateResponse(conversation(), tools)
        provider.generateResponse(conversation(), tools.take(1))

        assertThat(engine.starts).isEqualTo(2)
    }

    @Test
    fun `an engine failure surfaces as a provider error with the engine's reason`() = runTest {
        val engine = FakeEngine(ArrayDeque()).apply { failWith = "context too small" }
        val provider = NeedleProvider(engine)

        val failure = runCatching { provider.generateResponse(conversation(), tools) }.exceptionOrNull()

        assertThat(failure).isInstanceOf(AIProviderException::class.java)
        assertThat(failure?.message).contains("context too small")
    }
}
