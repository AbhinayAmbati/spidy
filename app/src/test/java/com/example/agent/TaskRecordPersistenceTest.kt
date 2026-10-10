package com.example.agent

import com.example.agent.data.local.TaskEntity
import com.example.agent.data.repository.toDomain
import com.example.agent.data.repository.toEntity
import com.example.agent.domain.model.RecordedStep
import com.example.agent.domain.model.TaskRecord
import com.example.agent.domain.model.TaskState
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The task totals have to survive being written and read back.
 *
 * They did not. `TaskRecord` carried `inputTokens`, `outputTokens` and `modelMillis`, but
 * `TaskEntity` had no such columns and the mapper dropped them — so every read returned zero.
 * The diagnostics then reported "model 0 ms / tokens 0 in / 0 out" while the per-step numbers,
 * which live inside the `steps` blob, were perfectly correct. A measurement feature that
 * silently reports zero is worse than no measurement at all, because it looks like an answer.
 */
class TaskRecordPersistenceTest {

    private val record = TaskRecord(
        id = "t-1",
        prompt = "a task",
        state = TaskState.FAILED,
        createdAtMillis = 1_700_000_000_000,
        finishedAtMillis = 1_700_000_021_471,
        durationMs = 21_471,
        stepCount = 19,
        appsUsed = listOf("com.google.android.youtube"),
        summary = "stopped",
        steps = listOf(
            RecordedStep(
                label = "Reading the screen",
                status = "Done",
                modelMillis = 246,
                settleMillis = 306,
                observeMillis = 0,
                actionMillis = 0,
                inputTokens = 4_192,
                outputTokens = 7,
            ),
        ),
        inputTokens = 79_000,
        outputTokens = 402,
        modelMillis = 7_591,
    )

    @Test
    fun `the totals survive a storage round trip`() {
        val restored = record.toEntity().toDomain()

        assertThat(restored.inputTokens).isEqualTo(79_000)
        assertThat(restored.outputTokens).isEqualTo(402)
        assertThat(restored.modelMillis).isEqualTo(7_591)
    }

    @Test
    fun `the per-step metrics survive too`() {
        val restored = record.toEntity().toDomain()

        assertThat(restored.steps).hasSize(1)
        assertThat(restored.steps[0].modelMillis).isEqualTo(246)
        assertThat(restored.steps[0].inputTokens).isEqualTo(4_192)
    }

    @Test
    fun `overhead is the part of the run that was not the model`() {
        // The number that decides whether to work on the model or on the phone.
        val restored = record.toEntity().toDomain()

        assertThat(restored.overheadMillis).isEqualTo(21_471 - 7_591)
    }

    @Test
    fun `a row written before the columns existed reads as zero rather than failing`() {
        // Every field the migration backfills with DEFAULT 0.
        val old = TaskEntity(
            id = "t-2", prompt = "old", state = "COMPLETED",
            createdAtMillis = 1, finishedAtMillis = 2, durationMs = 1,
            stepCount = 0, appsUsed = "", summary = null, steps = "[]",
        )

        val restored = old.toDomain()

        assertThat(restored.modelMillis).isEqualTo(0)
        assertThat(restored.inputTokens).isEqualTo(0)
    }

    @Test
    fun `every total on the record has a column on the entity`() {
        // The bug was a field with nowhere to live. This pins the pair.
        val entity = record.toEntity()
        val restored = entity.toDomain()

        assertThat(restored.inputTokens).isEqualTo(record.inputTokens)
        assertThat(restored.outputTokens).isEqualTo(record.outputTokens)
        assertThat(restored.modelMillis).isEqualTo(record.modelMillis)
    }
}
