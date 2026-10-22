package com.example.agent

import com.example.agent.core.ai.local.LocalToolEngine
import com.example.agent.core.ai.local.SpideyPrompt
import com.example.agent.core.ai.local.SpideyProvider
import com.example.agent.core.ai.local.SpideyResponseParser
import com.example.agent.core.intent.SpideyIntent
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SpideyProviderTest {

    private fun call(name: String, args: String) = """{"name":"$name","arguments":$args}"""

    private fun answer(vararg calls: String, confidence: String = "0.9") =
        """{"function_calls":[${calls.joinToString(",")}],"reasoning":"r","confidence":$confidence}"""

    // ---- parser ----------------------------------------------------------------------

    @Test
    fun `each of the five tools becomes its intent`() {
        val parsed = SpideyResponseParser.parse(
            answer(
                call("open_app", """{"app":"Spotify"}"""),
                call("send_message", """{"contact":"Priya","text":"I'll be late"}"""),
                call("call_contact", """{"contact":"Priya"}"""),
                call("set_alarm", """{"time":"6 am"}"""),
                call("set_timer", """{"minutes":10}"""),
            ),
        )

        assertThat(parsed.malformed).isEmpty()
        assertThat(parsed.confidence).isEqualTo(0.9)
        assertThat(parsed.intents).containsExactly(
            SpideyIntent.OpenApp("Spotify"),
            SpideyIntent.SendMessage("Priya", "I'll be late"),
            SpideyIntent.CallContact("Priya"),
            SpideyIntent.SetAlarm("6 am"),
            SpideyIntent.SetTimer(10),
        ).inOrder()
    }

    @Test
    fun `arguments sent as a JSON string are accepted`() {
        val raw = """{"function_calls":[{"name":"call_contact","arguments":"{\"contact\":\"Priya\"}"}]}"""

        val parsed = SpideyResponseParser.parse(raw)

        assertThat(parsed.intents).containsExactly(SpideyIntent.CallContact("Priya"))
        assertThat(parsed.confidence).isNull()
    }

    @Test
    fun `minutes are coerced from a string or a whole double`() {
        val parsed = SpideyResponseParser.parse(
            answer(
                call("set_timer", """{"minutes":"15"}"""),
                call("set_timer", """{"minutes":20.0}"""),
            ),
        )

        assertThat(parsed.intents).containsExactly(SpideyIntent.SetTimer(15), SpideyIntent.SetTimer(20)).inOrder()
    }

    @Test
    fun `a fractional or non-numeric minutes value is malformed`() {
        val parsed = SpideyResponseParser.parse(
            answer(
                call("set_timer", """{"minutes":2.5}"""),
                call("set_timer", """{"minutes":"soon"}"""),
            ),
        )

        assertThat(parsed.intents).isEmpty()
        assertThat(parsed.malformed).hasSize(2)
    }

    @Test
    fun `unknown tools and missing arguments go to the malformed list`() {
        val parsed = SpideyResponseParser.parse(
            answer(
                call("launch_url", """{"url":"x"}"""),
                call("send_message", """{"contact":"Priya"}"""),
                call("open_app", "{}"),
                call("call_contact", """{"contact":"  "}"""),
                call("set_alarm", "null"),
                call("set_alarm", """{"time":"7:30 pm"}"""),
            ),
        )

        assertThat(parsed.intents).containsExactly(SpideyIntent.SetAlarm("7:30 pm"))
        assertThat(parsed.malformed).hasSize(5)
        assertThat(parsed.malformed.first()).contains("unknown tool")
    }

    @Test
    fun `an empty call list is no intents and nothing malformed`() {
        val parsed = SpideyResponseParser.parse(
            """{"function_calls":[],"reasoning":"no matching tool","confidence":0.4}""",
        )

        assertThat(parsed.intents).isEmpty()
        assertThat(parsed.malformed).isEmpty()
        assertThat(parsed.reasoning).isEqualTo("no matching tool")
    }

    @Test
    fun `output that is not JSON is reported, not thrown`() {
        val parsed = SpideyResponseParser.parse("garbled")

        assertThat(parsed.intents).isEmpty()
        assertThat(parsed.malformed).hasSize(1)
    }

    // ---- prompt ----------------------------------------------------------------------

    @Test
    fun `the input is the trimmed sentence and nothing else`() {
        val prompt = SpideyPrompt("[]")

        assertThat(prompt.input("  text Priya I'll be late \n")).isEqualTo("text Priya I'll be late")
        assertThat(SpideyPrompt.SYSTEM_PROMPT).isEmpty()
    }

    // ---- legacy adapter --------------------------------------------------------------

    @Test
    fun `the legacy provider answers with a message and never calls the engine`() = runTest {
        val engine = object : LocalToolEngine {
            override fun start(systemPrompt: String, toolsJson: String) = error("not used")
            override fun reset() = error("not used")
            override fun complete(input: String, maxNewTokens: Int): String = error("not used")
        }

        val response = SpideyProvider(engine).generateResponse(emptyList(), emptyList())

        assertThat(response.calls).isEmpty()
        assertThat(response.message).isNotNull()
    }
}
