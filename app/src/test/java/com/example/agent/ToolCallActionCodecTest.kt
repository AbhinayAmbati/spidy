package com.example.agent

import com.example.agent.core.ai.AgentActionCodec
import com.example.agent.core.ai.DecodedCall
import com.example.agent.domain.model.ToolNames
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.JsonObject
import org.junit.Test

/**
 * Regression tests for the tool-call -> [com.example.agent.domain.model.AgentAction] path.
 *
 * Two distinct failures live here.
 *
 * **The name is separate from the arguments.** Every provider sends the tool's *name*
 * separately, and the arguments object never repeats the action, so decoding the arguments
 * alone failed with "Field 'action' is required" on every step — the app could never get past
 * step 0.
 *
 * **Models do not send the shape the schema asked for.** `{"target": "Search"}` — a string
 * where an object belongs — used to throw, which killed the entire task on the first step
 * with "The model returned something I couldn't understand". There is a real screenshot of
 * that. It is now coerced, because the intent is obvious, and anything genuinely unreadable
 * comes back as [DecodedCall.Malformed] so the model can be asked to try again instead of the
 * run ending.
 */
class ToolCallActionCodecTest {

    private fun ok(name: String?, json: String): DecodedCall.Ok {
        val decoded = AgentActionCodec.decode(name, obj(json))
        assertThat(decoded).isInstanceOf(DecodedCall.Ok::class.java)
        return decoded as DecodedCall.Ok
    }

    // ------------------------------------------------------------------ name merge

    @Test
    fun `merges the tool name into an open_app call`() {
        val action = ok("open_app", """{"packageName":"com.android.chrome"}""").action

        assertThat(action.action).isEqualTo(ToolNames.OPEN_APP)
        assertThat(action.packageName).isEqualTo("com.android.chrome")
    }

    @Test
    fun `keeps a nested target when the name is merged in`() {
        val action = ok("click", """{"target":{"text":"Google Search"}}""").action

        assertThat(action.action).isEqualTo(ToolNames.CLICK)
        assertThat(action.target?.text).isEqualTo("Google Search")
    }

    @Test
    fun `handles a tool that takes no arguments`() {
        assertThat(ok("press_back", "{}").action.action).isEqualTo(ToolNames.PRESS_BACK)
    }

    @Test
    fun `handles finish with only a reason`() {
        val action = ok("finish", """{"reason":"Searched for Kubernetes"}""").action

        assertThat(action.action).isEqualTo(ToolNames.FINISH)
        assertThat(action.reason).isEqualTo("Searched for Kubernetes")
    }

    @Test
    fun `the function name wins over an action inside the arguments`() {
        val action = ok("click", """{"action":"press_home","target":{"text":"Go"}}""").action

        assertThat(action.action).isEqualTo(ToolNames.CLICK)
    }

    @Test
    fun `falls back to an action embedded in the arguments when no name is given`() {
        assertThat(ok(null, """{"action":"read_screen"}""").action.action)
            .isEqualTo(ToolNames.READ_SCREEN)
    }

    @Test
    fun `returns null when there is neither a name nor an embedded action`() {
        assertThat(AgentActionCodec.decode(null, obj("""{"text":"hello"}"""))).isNull()
    }

    @Test
    fun `parses the OpenAI arguments-as-a-string form`() {
        val decoded = AgentActionCodec.decodeJson("type_text", """{"text":"Kubernetes"}""")

        assertThat(decoded).isInstanceOf(DecodedCall.Ok::class.java)
        val action = (decoded as DecodedCall.Ok).action
        assertThat(action.action).isEqualTo(ToolNames.TYPE_TEXT)
        assertThat(action.text).isEqualTo("Kubernetes")
    }

    @Test
    fun `unparseable arguments with a known tool name degrade instead of crashing`() {
        // The name alone is enough to build an action; the tool's own validator then rejects
        // it with a reason the model can act on ("target is required").
        val decoded = AgentActionCodec.decodeJson("click", "not json at all")

        assertThat(decoded).isInstanceOf(DecodedCall.Ok::class.java)
        val action = (decoded as DecodedCall.Ok).action
        assertThat(action.action).isEqualTo(ToolNames.CLICK)
        assertThat(action.target).isNull()
    }

    // ------------------------------------------------------------------ coercion

    @Test
    fun `a target sent as a bare string is coerced into an object`() {
        // The exact shape from the step-0 failure screenshot. This used to throw and end the
        // task; a human reading `{"target":"Search"}` knows exactly what was meant.
        val action = ok("click", """{"target":"Search"}""").action

        assertThat(action.action).isEqualTo(ToolNames.CLICK)
        assertThat(action.target?.text).isEqualTo("Search")
    }

    @Test
    fun `a target nested one level too deep is flattened`() {
        val action = ok("click", """{"target":{"target":{"text":"Search"}}}""").action

        assertThat(action.target?.text).isEqualTo("Search")
    }

    @Test
    fun `numbers where strings belong are coerced`() {
        val action = ok("type_text", """{"text":123}""").action

        assertThat(action.text).isEqualTo("123")
    }

    @Test
    fun `strings where numbers belong are coerced`() {
        val scroll = ok("scroll", """{"amount":"3"}""").action
        assertThat(scroll.amount).isEqualTo(3)

        val wait = ok("wait", """{"durationMs":"1500"}""").action
        assertThat(wait.durationMs).isEqualTo(1500L)
    }

    @Test
    fun `a fractional amount is rounded rather than rejected`() {
        assertThat(ok("scroll", """{"amount":2.7}""").action.amount).isEqualTo(2)
    }

    @Test
    fun `null fields are dropped rather than failing the decode`() {
        val action = ok("click", """{"target":{"text":"Go"},"text":null,"reason":null}""").action

        assertThat(action.target?.text).isEqualTo("Go")
        assertThat(action.text).isNull()
    }

    @Test
    fun `a target that is a list is dropped, and the call still decodes`() {
        // Nothing sensible to coerce, so the action survives with no target and the tool's
        // own validator explains what is missing.
        val action = ok("click", """{"target":[1,2]}""").action

        assertThat(action.action).isEqualTo(ToolNames.CLICK)
        assertThat(action.target).isNull()
    }

    @Test
    fun `extra fields the schema never declared are ignored`() {
        val action = ok("click", """{"target":{"text":"Go"},"confidence":0.9,"note":"sure"}""").action

        assertThat(action.target?.text).isEqualTo("Go")
    }

    @Test
    fun `a nonsense action value is ignored in favour of the tool name`() {
        val action = ok("click", """{"action":{"nested":"object"},"target":{"text":"Go"}}""").action

        assertThat(action.action).isEqualTo(ToolNames.CLICK)
        assertThat(action.target?.text).isEqualTo("Go")
    }

    @Test
    fun `decoding never throws, whatever the model sends`() {
        // The property that actually matters: a fumbled call must never end the task. Every
        // one of these is a shape a model has been seen to send, and every one of them used
        // to be capable of throwing out of parsing and killing the run on step 0.
        //
        // Coercion is deliberately total - every field AgentAction declares is either coerced
        // or dropped - so in practice DecodedCall.Malformed is a safety net for a future
        // required field rather than a path taken today.
        val nasty = listOf(
            "{}",
            """{"target":"Search"}""",
            """{"target":[1,2,3]}""",
            """{"target":{"text":{"deep":true}}}""",
            """{"target":{"ref":"seven"}}""",
            """{"target":{"target":{"target":{"text":"x"}}}}""",
            """{"amount":"lots"}""",
            """{"amount":null}""",
            """{"durationMs":{}}""",
            """{"text":[1,2]}""",
            """{"action":42}""",
            """{"action":null,"text":null}""",
        )

        for (json in nasty) {
            val decoded = AgentActionCodec.decode("click", obj(json))
            assertThat(decoded is DecodedCall.Ok || decoded is DecodedCall.Malformed).isTrue()
        }
    }

    private fun obj(json: String): JsonObject =
        com.example.agent.core.ai.AgentJson.parseToJsonElement(json) as JsonObject
}
