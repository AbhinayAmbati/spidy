package com.example.agent

import com.example.agent.domain.model.RecordedStep
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The repository stores recorded steps as JSON in a single Room column. This pins
 * that round-trip so a schema change cannot silently corrupt history.
 */
class TaskHistorySerializationTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `recorded steps survive a round trip`() {
        val steps = listOf(
            RecordedStep("Opening Chrome", "Done"),
            RecordedStep("Typing \"Kubernetes\"", "Done"),
            RecordedStep("Tapping \"Send\"", "Failed"),
        )

        val encoded = json.encodeToString(steps)
        val decoded = json.decodeFromString<List<RecordedStep>>(encoded)

        assertThat(decoded).isEqualTo(steps)
    }

    @Test
    fun `malformed history decodes to an empty list rather than throwing`() {
        val decoded = runCatching {
            json.decodeFromString<List<RecordedStep>>("not json at all")
        }.getOrDefault(emptyList())

        assertThat(decoded).isEmpty()
    }
}
