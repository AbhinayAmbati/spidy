package com.example.agent

import com.example.agent.automation.tools.ToolSchemas
import com.example.agent.core.ai.AgentJson
import com.example.agent.domain.model.ActionTarget
import com.example.agent.domain.model.AgentAction
import com.example.agent.domain.model.ToolNames
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test

/**
 * Pins the contract between the JSON schemas we hand the model and the [AgentAction]
 * the code decodes into.
 *
 * This is the test that would have caught the bug that stopped every task: the tool
 * *name* is the action, so no schema may ask the model to supply `action` itself.
 */
class ToolSchemaContractTest {

    private val schemas: Map<String, String> = mapOf(
        ToolNames.OPEN_APP to ToolSchemas.OPEN_APP,
        ToolNames.CLOSE_APP to ToolSchemas.CLOSE_APP,
        ToolNames.READ_SCREEN to ToolSchemas.READ_SCREEN,
        ToolNames.FIND_TEXT to ToolSchemas.FIND_TEXT,
        ToolNames.CLICK to ToolSchemas.CLICK,
        ToolNames.LONG_CLICK to ToolSchemas.LONG_CLICK,
        ToolNames.TYPE_TEXT to ToolSchemas.TYPE_TEXT,
        ToolNames.SCROLL to ToolSchemas.SCROLL,
        ToolNames.SWIPE to ToolSchemas.SWIPE,
        ToolNames.PRESS_BACK to ToolSchemas.PRESS_BACK,
        ToolNames.PRESS_HOME to ToolSchemas.PRESS_HOME,
        ToolNames.WAIT to ToolSchemas.WAIT,
        ToolNames.LAUNCH_URL to ToolSchemas.LAUNCH_URL,
        ToolNames.GET_NOTIFICATIONS to ToolSchemas.GET_NOTIFICATIONS,
        ToolNames.ASK_USER to ToolSchemas.ASK_USER,
        ToolNames.FINISH to ToolSchemas.FINISH,
    )

    private fun obj(json: String) = AgentJson.parseToJsonElement(json) as JsonObject
    private fun props(o: JsonObject) = (o["properties"] as? JsonObject)?.keys.orEmpty()
    private fun required(o: JsonObject) =
        (o["required"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }.orEmpty()

    /** Field names straight off the serializers — no reflection needed. */
    private val actionFields: Set<String> =
        AgentAction.serializer().descriptor.elementNames.toSet()
    private val targetFields: Set<String> =
        ActionTarget.serializer().descriptor.elementNames.toSet()

    @Test
    fun `every tool in the catalogue has a schema`() {
        assertThat(schemas).hasSize(16)
        assertThat(schemas.keys).containsExactly(
            ToolNames.OPEN_APP, ToolNames.CLOSE_APP, ToolNames.READ_SCREEN,
            ToolNames.FIND_TEXT, ToolNames.CLICK, ToolNames.LONG_CLICK,
            ToolNames.TYPE_TEXT, ToolNames.SCROLL, ToolNames.SWIPE,
            ToolNames.PRESS_BACK, ToolNames.PRESS_HOME, ToolNames.WAIT,
            ToolNames.LAUNCH_URL, ToolNames.GET_NOTIFICATIONS, ToolNames.ASK_USER,
            ToolNames.FINISH,
        )
    }

    @Test
    fun `every schema is a well-formed object schema`() {
        schemas.forEach { (tool, json) ->
            val o = obj(json) // throws if the schema is not valid JSON
            assertThat((o["type"] as? JsonPrimitive)?.content).isEqualTo("object")
            assertThat(o.containsKey("properties")).isTrue()
        }
    }

    @Test
    fun `no schema asks the model for the action field`() {
        // The tool name IS the action, and it arrives separately from the arguments.
        // A schema declaring `action` would make the model repeat it — and it could then
        // contradict the function it actually called.
        schemas.forEach { (tool, json) ->
            assertThat(props(obj(json))).doesNotContain("action")
        }
    }

    @Test
    fun `every schema property is a field the action model can actually read`() {
        schemas.forEach { (tool, json) ->
            props(obj(json)).forEach { prop ->
                assertThat(actionFields).contains(prop)
            }
        }
    }

    @Test
    fun `every required field is a declared property`() {
        schemas.forEach { (tool, json) ->
            val o = obj(json)
            required(o).forEach { r -> assertThat(props(o)).contains(r) }
        }
    }

    @Test
    fun `the target object offers exactly the fields the matcher understands`() {
        val click = obj(ToolSchemas.CLICK)
        val target = (click["properties"] as JsonObject)["target"] as JsonObject

        assertThat((target["properties"] as JsonObject).keys)
            .containsExactlyElementsIn(targetFields)
    }

    @Test
    fun `tools that need an argument require it`() {
        assertThat(required(obj(ToolSchemas.OPEN_APP))).contains("packageName")
        assertThat(required(obj(ToolSchemas.FIND_TEXT))).contains("text")
        assertThat(required(obj(ToolSchemas.CLICK))).contains("target")
        assertThat(required(obj(ToolSchemas.TYPE_TEXT))).contains("text")
        assertThat(required(obj(ToolSchemas.LAUNCH_URL))).contains("url")
        assertThat(required(obj(ToolSchemas.FINISH))).contains("reason")
    }
}
