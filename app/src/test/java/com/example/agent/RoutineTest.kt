package com.example.agent

import com.example.agent.core.ai.AgentJson
import com.example.agent.domain.model.Routine
import com.example.agent.domain.model.RoutineStep
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Test

/**
 * A routine is only useful if it survives storage. The failure mode that matters is a stored
 * routine that cannot be read back — which would silently lose the user's saved work, or take
 * the app down on the list screen.
 */
class RoutineTest {

    private val routine = Routine(
        id = "r-1",
        name = "open youtube and play a song",
        prompt = "open youtube and play a song",
        createdAtMillis = 1_700_000_000_000,
        steps = listOf(
            RoutineStep(
                actionJson = """{"action":"open_app","packageName":"com.google.android.youtube"}""",
                label = "open_app",
            ),
            RoutineStep(
                actionJson = """{"action":"click","target":{"text":"Search"}}""",
                label = "click",
            ),
            RoutineStep(actionJson = """{"action":"finish","reason":"Playing."}""", label = "finish"),
        ),
        runCount = 3,
        lastRunAtMillis = 1_700_000_500_000,
    )

    private val serializer = ListSerializer(Routine.serializer())

    @Test
    fun `a routine survives a storage round trip`() {
        val restored = AgentJson.decodeFromString(serializer, AgentJson.encodeToString(serializer, listOf(routine)))

        assertThat(restored).hasSize(1)
        assertThat(restored[0]).isEqualTo(routine)
    }

    @Test
    fun `the action JSON is stored verbatim, not reconstructed`() {
        // A replay must run byte-for-byte the action that worked.
        val restored = AgentJson.decodeFromString(serializer, AgentJson.encodeToString(serializer, listOf(routine)))

        assertThat(restored[0].steps[0].actionJson)
            .isEqualTo("""{"action":"open_app","packageName":"com.google.android.youtube"}""")
        assertThat(restored[0].steps[2].actionJson)
            .isEqualTo("""{"action":"finish","reason":"Playing."}""")
    }

    @Test
    fun `optional counters default so an older stored routine still loads`() {
        // Written before runCount existed: it must still decode rather than fail.
        val older = """
            [{"id":"r-2","name":"n","prompt":"p","createdAtMillis":1,
              "steps":[{"actionJson":"{\"action\":\"press_back\"}","label":"press_back"}]}]
        """.trimIndent()

        val restored = AgentJson.decodeFromString(serializer, older)

        assertThat(restored).hasSize(1)
        assertThat(restored[0].runCount).isEqualTo(0)
        assertThat(restored[0].lastRunAtMillis).isNull()
        assertThat(restored[0].steps).hasSize(1)
    }

    @Test
    fun `an empty list round trips`() {
        assertThat(AgentJson.decodeFromString(serializer, "[]")).isEmpty()
    }
}
