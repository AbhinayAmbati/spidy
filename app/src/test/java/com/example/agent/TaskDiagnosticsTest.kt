package com.example.agent

import com.example.agent.core.diagnostics.TaskDiagnostics
import com.example.agent.domain.model.RecordedStep
import com.example.agent.domain.model.TaskRecord
import com.example.agent.domain.model.TaskState
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Every diagnosis so far cost a screenshot, a description and a guess. This is the block that
 * replaces them, so its shape matters.
 */
class TaskDiagnosticsTest {

    private fun record(
        state: TaskState = TaskState.FAILED,
        steps: List<RecordedStep> = listOf(
            RecordedStep(
                label = "Opening com.google.android.youtube",
                status = "Done",
                modelMillis = 1_400,
                settleMillis = 620,
                observeMillis = 55,
                actionMillis = 90,
                inputTokens = 5_100,
                outputTokens = 42,
            ),
            RecordedStep(
                label = "Tapping \"Search\"",
                status = "Failed",
                detail = "Could not find \"Search\" on screen.",
                modelMillis = 1_200,
                settleMillis = 300,
                observeMillis = 60,
                actionMillis = 40,
                inputTokens = 5_400,
                outputTokens = 38,
            ),
        ),
    ) = TaskRecord(
        id = "abc-123",
        prompt = "open youtube and play a song",
        state = state,
        createdAtMillis = 0,
        finishedAtMillis = 4_000,
        durationMs = 4_000,
        stepCount = steps.size,
        appsUsed = listOf("com.google.android.youtube"),
        summary = "Could not find the search box.",
        steps = steps,
        inputTokens = 10_500,
        outputTokens = 80,
        modelMillis = 2_600,
    )

    private fun report(r: TaskRecord = record()) = TaskDiagnostics.report(
        record = r,
        appVersion = "0.1.18",
        device = "Google Pixel 7, Android 14",
        provider = "Sarvam AI / sarvam-105b",
    )

    @Test
    fun `includes what it needs to be actionable`() {
        val text = report()

        assertThat(text).contains("0.1.18")
        assertThat(text).contains("Google Pixel 7, Android 14")
        assertThat(text).contains("Sarvam AI / sarvam-105b")
        assertThat(text).contains("open youtube and play a song")
        assertThat(text).contains("FAILED")
    }

    @Test
    fun `separates model time from device time`() {
        val text = report()

        // The whole point: which half of the wall clock is the model, and which is us.
        assertThat(text).contains("model    2600 ms")
        assertThat(text).contains("overhead 1400 ms")
        assertThat(text).contains("10500 in / 80 out")
    }

    @Test
    fun `reports each step with its timing and its failure reason`() {
        val text = report()

        assertThat(text).contains("[Done] Opening com.google.android.youtube")
        assertThat(text).contains("[Failed] Tapping \"Search\"")
        assertThat(text).contains("model 1400ms  settle 620ms  observe 55ms  action 90ms  tokens 5100/42")
        assertThat(text).contains("reason: Could not find \"Search\" on screen.")
    }

    @Test
    fun `handles a task with no steps`() {
        val text = report(record(steps = emptyList()))

        assertThat(text).contains("no steps recorded")
    }

    @Test
    fun `a cancelled task reads as cancelled`() {
        assertThat(report(record(state = TaskState.CANCELLED))).contains("CANCELLED")
    }

    @Test
    fun `carries no screen content beyond the failure reasons already shown`() {
        val text = report()

        // A diagnostic that leaks the screen would be worse than no diagnostic.
        assertThat(text).doesNotContain("SCREEN_")
        assertThat(text).doesNotContain("Observation:")
    }
}
