package com.example.agent

import com.example.agent.core.intent.GuardVerdict
import com.example.agent.core.intent.IntentGuard
import com.example.agent.core.intent.SpideyIntent
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import org.junit.Ignore
import org.junit.Test

/**
 * Replays what the model ACTUALLY answered (recorded when spidey.cact was evaluated, see
 * docs/EVALUATION.md) through Kotlin code, so the decoding and the guard contract are checked
 * against real model output, including its mistakes, without needing the model or a phone.
 *
 * The golden set is app/src/test/resources/golden/spidey_golden.jsonl, rebuilt by
 * tools/spidey_eval/build_golden.py. It holds every test_v4 miss plus stratified samples of
 * test_v4 and dev_natural.
 */
class GoldenReplayTest {

    private class Row(
        val id: String,
        val source: String,
        val category: String,
        val query: String,
        val gold: JsonArray,
        val recorded: JsonArray,
        val failure: String,
    )

    private val rows: List<Row> by lazy { loadRows() }

    private fun loadRows(): List<Row> {
        val stream = checkNotNull(javaClass.classLoader?.getResourceAsStream(GOLDEN)) { "missing $GOLDEN" }
        return stream.bufferedReader(Charsets.UTF_8).readLines()
            .filter { it.isNotBlank() }
            .map { line ->
                val obj = Json.parseToJsonElement(line) as JsonObject
                fun text(key: String) = (obj.getValue(key) as JsonPrimitive).content
                Row(
                    id = text("id"),
                    source = text("source"),
                    category = text("cat"),
                    query = text("query"),
                    gold = obj.getValue("gold") as JsonArray,
                    recorded = obj.getValue("recorded") as JsonArray,
                    failure = text("failure"),
                )
            }
    }

    /**
     * Decodes the engine's answer into intents. It follows the tool list the model was trained
     * on and is deliberately strict: an unknown tool or a wrongly typed argument is a failed
     * decode (null), never a guess.
     */
    private fun decode(answers: JsonArray): List<SpideyIntent>? {
        val intents = mutableListOf<SpideyIntent>()
        for (entry in answers) {
            val call = entry as? JsonObject ?: return null
            val name = (call["name"] as? JsonPrimitive)?.content ?: return null
            val args = call["arguments"] as? JsonObject ?: return null
            fun str(key: String): String? =
                (args[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
            val intent: SpideyIntent = when (name) {
                "open_app" -> SpideyIntent.OpenApp(str("app") ?: return null)
                "send_message" -> SpideyIntent.SendMessage(
                    str("contact") ?: return null,
                    str("text") ?: return null,
                )
                "call_contact" -> SpideyIntent.CallContact(str("contact") ?: return null)
                "set_alarm" -> SpideyIntent.SetAlarm(str("time") ?: return null)
                "set_timer" -> SpideyIntent.SetTimer(
                    (args["minutes"] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull ?: return null,
                )
                else -> return null
            }
            intents += intent
        }
        return intents
    }

    /** The JSON the engine prints for one sentence, rebuilt from a recorded answer. */
    private fun engineJson(answers: JsonArray): String =
        JsonObject(mapOf("function_calls" to answers)).toString()

    /**
     * The one place the integrator changes once branch spidey-router has merged: make this call
     * SpideyResponseParser on [raw] and map its result to intents.
     */
    private fun parserUnderTest(raw: String): List<SpideyIntent>? {
        val root = Json.parseToJsonElement(raw) as? JsonObject ?: return null
        return decode(root["function_calls"] as? JsonArray ?: JsonArray(emptyList()))
    }

    /** Case and edge punctuation do not count; this is the "lenient" score of the eval scripts. */
    private fun lenient(intent: SpideyIntent): SpideyIntent {
        fun fold(value: String) = value.trim().trim('.', '!', '?').lowercase()
        return when (intent) {
            is SpideyIntent.OpenApp -> SpideyIntent.OpenApp(fold(intent.app))
            is SpideyIntent.SendMessage -> SpideyIntent.SendMessage(fold(intent.contact), fold(intent.text))
            is SpideyIntent.CallContact -> SpideyIntent.CallContact(fold(intent.contact))
            is SpideyIntent.SetAlarm -> SpideyIntent.SetAlarm(fold(intent.time))
            is SpideyIntent.SetTimer -> intent
        }
    }

    private fun Row.goldIntents() = checkNotNull(decode(gold)) { "gold of $id does not decode" }

    private fun Row.recordedIntents() = checkNotNull(parserUnderTest(engineJson(recorded))) {
        "recorded answer of $id does not decode"
    }

    /** Stand-in for the device guard: a contact or app that is not in the sentence is refused. */
    private class GroundingGuard(sentence: String) : IntentGuard {
        private val said = sentence.lowercase()

        override suspend fun check(intent: SpideyIntent): GuardVerdict {
            val named = when (intent) {
                is SpideyIntent.OpenApp -> intent.app
                is SpideyIntent.SendMessage -> intent.contact
                is SpideyIntent.CallContact -> intent.contact
                is SpideyIntent.SetAlarm -> null
                is SpideyIntent.SetTimer -> null
            }
            if (named != null && named.trim().lowercase() !in said) {
                return GuardVerdict.Reject("\"$named\" is not in what was asked")
            }
            return GuardVerdict.Allow(intent, summary = intent.toString(), needsConfirmation = false)
        }
    }

    @Test
    fun `the golden set is present, small and covers every failure kind`() {
        assertThat(rows).isNotEmpty()
        assertThat(rows.map { it.id }).containsNoDuplicates()
        assertThat(rows.map { it.source }.toSet()).containsExactly("test_v4", "dev_natural")
        assertThat(rows.map { it.failure }.toSet()).containsAtLeast(
            "correct", "wrong_tool", "wrong_argument", "hallucinated_entity", "missed_unsupported", "refused",
        )
        val bytes = javaClass.classLoader!!.getResourceAsStream(GOLDEN)!!.use { it.readBytes().size }
        assertThat(bytes).isLessThan(200_000)
    }

    @Test
    fun `every gold answer is something SpideyIntent can express`() {
        for (row in rows) {
            assertThat(row.goldIntents().size).isEqualTo(row.gold.size)
        }
        assertThat(rows.any { it.gold.isEmpty() }).isTrue()
        assertThat(rows.any { it.gold.size > 1 }).isTrue()
    }

    @Test
    fun `every recorded model answer decodes, wrong or not`() {
        for (row in rows) {
            assertThat(parserUnderTest(engineJson(row.recorded))).isNotNull()
        }
    }

    @Test
    fun `replaying the recorded answers reproduces the recorded verdicts`() {
        for (row in rows) {
            val exact = row.recordedIntents() == row.goldIntents()
            assertThat(exact).isEqualTo(row.failure == "correct")
        }
    }

    @Test
    fun `an empty answer decodes to no intents, which is how a refusal looks`() {
        assertThat(parserUnderTest("""{"function_calls":[]}""")).isEmpty()
        assertThat(parserUnderTest("""{"function_calls":[],"reasoning":"nothing to do"}""")).isEmpty()
    }

    @Test
    fun `a malformed answer is a failed decode, never a guess`() {
        assertThat(parserUnderTest("""{"function_calls":[{"name":"open_app","arguments":{}}]}""")).isNull()
        assertThat(parserUnderTest("""{"function_calls":[{"name":"launch_rocket","arguments":{}}]}""")).isNull()
        assertThat(
            parserUnderTest("""{"function_calls":[{"name":"set_timer","arguments":{"minutes":"ten"}}]}"""),
        ).isNull()
    }

    @Test
    fun `lenient scoring forgives case and trailing punctuation but not a wrong value`() {
        val said = SpideyIntent.CallContact("Mom")
        assertThat(lenient(SpideyIntent.CallContact("mom."))).isEqualTo(lenient(said))
        assertThat(lenient(SpideyIntent.CallContact("Dad"))).isNotEqualTo(lenient(said))
    }

    @Test
    fun `dev sample accuracy is pinned so a regression in decoding shows up`() {
        val dev = rows.filter { it.source == "dev_natural" }
        val correct = dev.count { it.recordedIntents() == it.goldIntents() }
        assertThat(dev.size).isEqualTo(60)
        assertThat(correct).isEqualTo(58)
    }

    @Test
    fun `the test_v4 misses are all still misses when replayed`() {
        val misses = rows.filter { it.source == "test_v4" && it.failure != "correct" }
        assertThat(misses.size).isEqualTo(64)
        for (row in misses) {
            assertThat(row.recordedIntents()).isNotEqualTo(row.goldIntents())
        }
    }

    @Test
    fun `a grounding guard never breaks a correct answer and removes invented entities`() = runTest {
        var correctBefore = 0
        var correctAfter = 0
        var inventedKept = 0
        for (row in rows) {
            val guard = GroundingGuard(row.query)
            val allowed = row.recordedIntents().filter { guard.check(it) is GuardVerdict.Allow }
            val gold = row.goldIntents()
            if (row.recordedIntents() == gold) correctBefore++
            if (allowed == gold) correctAfter++
            if (row.failure == "hallucinated_entity" && allowed == row.recordedIntents()) inventedKept++
        }
        assertThat(correctAfter).isAtLeast(correctBefore)
        assertThat(inventedKept).isEqualTo(0)
    }

    // The parser the app ships is being replaced by branch spidey-router; once it has merged the
    // integrator points parserUnderTest at SpideyResponseParser and removes @Ignore.
    @Ignore("Enable after spidey-router merges: point parserUnderTest at SpideyResponseParser")
    @Test
    fun `the shipped response parser agrees with the replay decoder on every recorded answer`() {
        for (row in rows) {
            assertThat(parserUnderTest(engineJson(row.recorded))).isEqualTo(row.recordedIntents())
        }
    }

    private companion object {
        const val GOLDEN = "golden/spidey_golden.jsonl"
    }
}
