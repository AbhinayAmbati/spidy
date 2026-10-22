package com.example.agent

import com.example.agent.core.ai.AIProviderException
import com.example.agent.core.ai.local.LocalEngineException
import com.example.agent.core.ai.local.LocalToolEngine
import com.example.agent.core.ai.local.SpideyPrompt
import com.example.agent.core.intent.SpideyIntent
import com.example.agent.core.intent.SpideyRouter
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SpideyRouterTest {

    private class FakeEngine(private val answers: ArrayDeque<String>) : LocalToolEngine {
        val starts = mutableListOf<Pair<String, String>>()
        var resets = 0
        val inputs = mutableListOf<String>()
        var failWith: String? = null

        override fun start(systemPrompt: String, toolsJson: String) {
            failWith?.let { throw LocalEngineException(it) }
            starts += systemPrompt to toolsJson
        }

        override fun reset() {
            resets++
        }

        override fun complete(input: String, maxNewTokens: Int): String {
            inputs += input
            return answers.removeFirst()
        }
    }

    private val tools = SpideyPrompt("""[{"name":"open_app"}]""")

    private fun routerFor(vararg answers: String): Pair<SpideyRouter, FakeEngine> {
        val engine = FakeEngine(ArrayDeque(answers.toList()))
        return SpideyRouter(engine, tools) to engine
    }

    private fun single(name: String, args: String, confidence: String = "0.9") =
        """{"function_calls":[{"name":"$name","arguments":$args}],"reasoning":"r","confidence":$confidence}"""

    // Rows follow eval_v4_dev_all.jsonl in the fine-tune repo.
    @Test
    fun `every intent type routes from the engine answer`() = runTest {
        val cases = listOf(
            "open Spotify" to (single("open_app", """{"app":"Spotify"}""") to SpideyIntent.OpenApp("Spotify")),
            "text Priya I'll be late" to (
                single("send_message", """{"contact":"Priya","text":"I'll be late"}""") to
                    SpideyIntent.SendMessage("Priya", "I'll be late")
                ),
            "call Priya" to (single("call_contact", """{"contact":"Priya"}""") to SpideyIntent.CallContact("Priya")),
            "I'd like an alarm for 6 am" to (single("set_alarm", """{"time":"6 am"}""") to SpideyIntent.SetAlarm("6 am")),
            "can you start a 10 minute timer for me" to (single("set_timer", """{"minutes":10}""") to SpideyIntent.SetTimer(10)),
        )
        val (router, _) = routerFor(*cases.map { it.second.first }.toTypedArray())

        for ((sentence, expected) in cases) {
            val routed = router.route(sentence)

            assertThat(routed.intents).containsExactly(expected.second)
            assertThat(routed.confidence).isEqualTo(0.9)
            assertThat(routed.raw).isEqualTo(expected.first)
        }
    }

    @Test
    fun `an empty call list is an unsupported request`() = runTest {
        val (router, _) = routerFor("""{"function_calls":[],"reasoning":"no matching tool","confidence":null}""")

        val routed = router.route("schedule a meeting with Ananya at 4 pm")

        assertThat(routed.intents).isEmpty()
        assertThat(routed.confidence).isNull()
    }

    @Test
    fun `malformed args are dropped and read as unsupported`() = runTest {
        val (router, _) = routerFor(single("send_message", """{"contact":"Priya"}"""))

        assertThat(router.route("text Priya").intents).isEmpty()
    }

    @Test
    fun `string arguments and string minutes are accepted`() = runTest {
        val (router, _) = routerFor(
            """{"function_calls":[{"name":"set_timer","arguments":"{\"minutes\":\"7\"}"}],"confidence":0.8}""",
        )

        assertThat(router.route("timer 7 minutes").intents).containsExactly(SpideyIntent.SetTimer(7))
    }

    @Test
    fun `the engine gets exactly the sentence and an empty system prompt`() = runTest {
        val (router, engine) = routerFor(single("call_contact", """{"contact":"Priya"}"""))

        router.route("  call Priya ")

        assertThat(engine.inputs).containsExactly("call Priya")
        assertThat(engine.starts.single().first).isEmpty()
        assertThat(engine.starts.single().second).isEqualTo(tools.toolsJson)
    }

    @Test
    fun `the engine starts once per tool set and resets before every call`() = runTest {
        val a = single("open_app", """{"app":"x"}""")
        val (router, engine) = routerFor(a, a, a)

        router.route("open x")
        router.route("open x")
        assertThat(engine.starts).hasSize(1)
        assertThat(engine.resets).isEqualTo(2)

        router.useTools(SpideyPrompt(tools.toolsJson))
        router.route("open x")
        assertThat(engine.starts).hasSize(1)

        router.useTools(SpideyPrompt("[]"))
        router.route("open x")
        assertThat(engine.starts).hasSize(2)
    }

    @Test
    fun `a blank sentence never reaches the engine`() = runTest {
        val (router, engine) = routerFor()

        assertThat(router.route("   ").intents).isEmpty()
        assertThat(engine.inputs).isEmpty()
        assertThat(engine.starts).isEmpty()
    }

    @Test
    fun `an engine failure surfaces as a provider error with the engine's reason`() = runTest {
        val (router, engine) = routerFor()
        engine.failWith = "context too small"

        val failure = runCatching { router.route("open x") }.exceptionOrNull()

        assertThat(failure).isInstanceOf(AIProviderException::class.java)
        assertThat(failure?.message).contains("context too small")
    }
}
