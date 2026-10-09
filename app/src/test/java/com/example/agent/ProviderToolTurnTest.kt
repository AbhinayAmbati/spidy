package com.example.agent

import com.example.agent.core.ai.AgentJson
import com.example.agent.core.ai.provider.toAnthropicMessage
import com.example.agent.core.ai.provider.toGeminiContents
import com.example.agent.core.ai.provider.toAnthropicMessages
import com.example.agent.core.ai.provider.toGeminiContent
import com.example.agent.core.ai.provider.toOpenAiMessage
import com.example.agent.domain.model.AgentMessage
import com.example.agent.domain.model.MessageRole
import com.example.agent.domain.model.ToolCall
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test

/**
 * The conversation has to read as *real* tool use to the model: the assistant's call
 * and its result must be paired, or the model loses track of what it already did.
 *
 * The loop used to send a plain-text "action: click" line instead, which is not
 * something any of these APIs recognise as a tool call.
 */
class ProviderToolTurnTest {

    private val call = AgentMessage(
        role = MessageRole.Assistant,
        content = "",
        toolCalls = listOf(
            ToolCall(id = "call_3", name = "click", arguments = """{"target":{"text":"Search"}}"""),
        ),
    )

    private val result = AgentMessage(
        role = MessageRole.Tool,
        content = "Tapped \"Search\".",
        toolCallId = "call_3",
        name = "click",
    )

    private fun str(o: JsonObject, k: String) = (o[k] as? JsonPrimitive)?.content
    private fun arr(o: JsonObject, k: String) = o[k] as? JsonArray

    // ------------------------------------------------------------------ OpenAI

    @Test
    fun `openai assistant turn carries a real tool_call`() {
        val o = call.toOpenAiMessage()

        assertThat(str(o, "role")).isEqualTo("assistant")
        val tc = arr(o, "tool_calls")!!.first() as JsonObject
        assertThat(str(tc, "id")).isEqualTo("call_3")
        assertThat(str(tc, "type")).isEqualTo("function")
        val fn = tc["function"] as JsonObject
        assertThat(str(fn, "name")).isEqualTo("click")
        assertThat(str(fn, "arguments")).isEqualTo("""{"target":{"text":"Search"}}""")
    }

    @Test
    fun `openai tool result uses the tool role and answers the right call`() {
        val o = result.toOpenAiMessage()

        assertThat(str(o, "role")).isEqualTo("tool")
        assertThat(str(o, "tool_call_id")).isEqualTo("call_3")
        assertThat(str(o, "content")).isEqualTo("Tapped \"Search\".")
    }

    @Test
    fun `openai plain messages are unchanged`() {
        val o = AgentMessage(MessageRole.User, "hello").toOpenAiMessage()

        assertThat(str(o, "role")).isEqualTo("user")
        assertThat(str(o, "content")).isEqualTo("hello")
        assertThat(arr(o, "tool_calls")).isNull()
    }

    // ------------------------------------------------------------------ Anthropic

    @Test
    fun `anthropic assistant turn carries a tool_use block`() {
        val o = call.toAnthropicMessage()

        assertThat(str(o, "role")).isEqualTo("assistant")
        val block = arr(o, "content")!!.first() as JsonObject
        assertThat(str(block, "type")).isEqualTo("tool_use")
        assertThat(str(block, "id")).isEqualTo("call_3")
        assertThat(str(block, "name")).isEqualTo("click")
        assertThat((block["input"] as JsonObject)["target"]).isNotNull()
    }

    @Test
    fun `anthropic delivers a tool result inside a user turn`() {
        val o = result.toAnthropicMessage()

        assertThat(str(o, "role")).isEqualTo("user")
        val block = arr(o, "content")!!.first() as JsonObject
        assertThat(str(block, "type")).isEqualTo("tool_result")
        assertThat(str(block, "tool_use_id")).isEqualTo("call_3")
        assertThat(str(block, "content")).isEqualTo("Tapped \"Search\".")
    }

    // ------------------------------------------------------------------ Gemini

    @Test
    fun `gemini model turn carries a functionCall part`() {
        val o = call.toGeminiContent()

        assertThat(str(o, "role")).isEqualTo("model")
        val part = arr(o, "parts")!!.first() as JsonObject
        val fc = part["functionCall"] as JsonObject
        assertThat(str(fc, "name")).isEqualTo("click")
        assertThat((fc["args"] as JsonObject)["target"]).isNotNull()
    }

    @Test
    fun `gemini pairs the result by function name`() {
        val o = result.toGeminiContent()

        assertThat(str(o, "role")).isEqualTo("user")
        val part = arr(o, "parts")!!.first() as JsonObject
        val fr = part["functionResponse"] as JsonObject
        assertThat(str(fr, "name")).isEqualTo("click")
        assertThat((fr["response"] as JsonObject)["result"]).isNotNull()
    }

    // ------------------------------------------------------------------ batching

    private val batch = AgentMessage(
        role = MessageRole.Assistant,
        content = "",
        toolCalls = listOf(
            ToolCall("call_1", "open_app", """{"packageName":"com.google.android.youtube"}"""),
            ToolCall("call_2", "click", """{"target":{"text":"Search"}}"""),
        ),
    )

    @Test
    fun `openai carries every call of a batch`() {
        val tc = arr(batch.toOpenAiMessage(), "tool_calls")!!

        assertThat(tc).hasSize(2)
        assertThat(str(tc[0] as JsonObject, "id")).isEqualTo("call_1")
        assertThat(str(((tc[0] as JsonObject)["function"] as JsonObject), "name")).isEqualTo("open_app")
        assertThat(str(tc[1] as JsonObject, "id")).isEqualTo("call_2")
    }

    @Test
    fun `anthropic carries every call of a batch`() {
        val blocks = arr(batch.toAnthropicMessage(), "content")!!

        assertThat(blocks).hasSize(2)
        assertThat(str(blocks[0] as JsonObject, "type")).isEqualTo("tool_use")
        assertThat(str(blocks[0] as JsonObject, "name")).isEqualTo("open_app")
        assertThat(str(blocks[1] as JsonObject, "name")).isEqualTo("click")
    }

    @Test
    fun `gemini carries every call of a batch`() {
        val parts = arr(batch.toGeminiContent(), "parts")!!

        assertThat(parts).hasSize(2)
        assertThat(((parts[0] as JsonObject)["functionCall"] as JsonObject)["name"]).isNotNull()
        assertThat(((parts[1] as JsonObject)["functionCall"] as JsonObject)["name"]).isNotNull()
    }

    @Test
    fun `anthropic merges a batch's results into ONE user turn`() {
        // Anthropic expects every tool_result for an assistant turn inside a single user
        // message; separate user turns risk a same-role sequence it rejects.
        val results = listOf(
            AgentMessage(MessageRole.Tool, "opened", toolCallId = "call_1", name = "open_app"),
            AgentMessage(MessageRole.Tool, "tapped", toolCallId = "call_2", name = "click"),
        )

        val messages = (listOf(batch) + results).toAnthropicMessages()

        assertThat(messages).hasSize(2) // the assistant turn, then one merged user turn
        val blocks = arr(messages[1], "content")!!
        assertThat(blocks).hasSize(2)
        assertThat(str(blocks[0] as JsonObject, "tool_use_id")).isEqualTo("call_1")
        assertThat(str(blocks[1] as JsonObject, "tool_use_id")).isEqualTo("call_2")
    }

    @Test
    fun `gemini merges a batch's results into ONE user turn`() {
        val results = listOf(
            AgentMessage(MessageRole.Tool, "opened", toolCallId = "call_1", name = "open_app"),
            AgentMessage(MessageRole.Tool, "tapped", toolCallId = "call_2", name = "click"),
        )

        val contents = (listOf(batch) + results).toGeminiContents()

        assertThat(contents).hasSize(2)
        val parts = arr(contents[1], "parts")!!
        assertThat(parts).hasSize(2)
        assertThat(str((parts[0] as JsonObject)["functionResponse"] as JsonObject, "name"))
            .isEqualTo("open_app")
    }

    // ------------------------------------------------------------------ forcing

    @Test
    fun `a tool call with empty arguments still emits a usable object`() {
        val o = AgentMessage(
            role = MessageRole.Assistant,
            content = "",
            toolCalls = listOf(ToolCall(id = "call_1", name = "press_back", arguments = "{}")),
        ).toAnthropicMessage()

        val block = arr(o, "content")!!.first() as JsonObject
        assertThat(block["input"] as JsonObject).isEmpty()
    }
}
