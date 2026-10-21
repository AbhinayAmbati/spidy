package com.example.agent.ui.preview

import com.example.agent.domain.model.AwaitingKind
import com.example.agent.domain.model.MemoryEntry
import com.example.agent.domain.model.RecordedStep
import com.example.agent.domain.model.Routine
import com.example.agent.domain.model.RoutineStep
import com.example.agent.domain.model.StepStatus
import com.example.agent.domain.model.TaskProgress
import com.example.agent.domain.model.TaskRecord
import com.example.agent.domain.model.TaskState
import com.example.agent.domain.model.TaskStep

/** Sample data used only by `@Preview` functions. Nothing in the running app reads it. */
internal object PreviewData {

    val steps = listOf(
        TaskStep(index = 1, label = "Open Spotify", status = StepStatus.Done),
        TaskStep(index = 2, label = "Tap Liked Songs", status = StepStatus.Done),
        TaskStep(index = 3, label = "Press play", status = StepStatus.Running),
        TaskStep(index = 4, label = "Confirm playback started", status = StepStatus.Pending),
    )

    fun progress(
        state: TaskState,
        awaitingKind: AwaitingKind = AwaitingKind.None,
        error: String? = null,
    ) = TaskProgress(
        taskId = "preview",
        prompt = "Open Spotify and play my liked songs",
        state = state,
        steps = steps,
        currentStep = 3,
        totalSteps = 8,
        message = "Pressing play on Liked Songs",
        error = error,
        awaitingKind = awaitingKind,
    )

    val records = listOf(
        TaskRecord(
            id = "1",
            prompt = "Open Spotify and play my liked songs",
            state = TaskState.COMPLETED,
            createdAtMillis = 0L,
            finishedAtMillis = 42_000L,
            durationMs = 42_000L,
            stepCount = 6,
            appsUsed = listOf("com.spotify.music"),
            summary = "Playing Liked Songs.",
            steps = listOf(
                RecordedStep(label = "Open Spotify", status = "Done"),
                RecordedStep(label = "Tap Liked Songs", status = "Done"),
                RecordedStep(label = "Press play", status = "Failed", detail = "Button not found"),
            ),
            modelMillis = 30_000L,
        ),
        TaskRecord(
            id = "2",
            prompt = "Send a WhatsApp message to Sam saying I am running late",
            state = TaskState.FAILED,
            createdAtMillis = 0L,
            finishedAtMillis = 12_000L,
            durationMs = 12_000L,
            stepCount = 3,
            appsUsed = listOf("com.whatsapp"),
            summary = null,
        ),
        TaskRecord(
            id = "3",
            prompt = "Set an alarm for 7 am",
            state = TaskState.CANCELLED,
            createdAtMillis = 0L,
            finishedAtMillis = 5_000L,
            durationMs = 5_000L,
            stepCount = 1,
            appsUsed = emptyList(),
            summary = null,
        ),
    )

    val routines = listOf(
        Routine(
            id = "r1",
            name = "Open Spotify and play my liked songs",
            prompt = "Open Spotify and play my liked songs",
            createdAtMillis = 0L,
            steps = listOf(
                RoutineStep(actionJson = "{}", label = "open_app"),
                RoutineStep(actionJson = "{}", label = "tap"),
            ),
            runCount = 3,
        ),
        Routine(
            id = "r2",
            name = "Turn on do not disturb",
            prompt = "Turn on do not disturb",
            createdAtMillis = 0L,
            steps = listOf(RoutineStep(actionJson = "{}", label = "tap")),
        ),
    )

    val memory = listOf(
        MemoryEntry(key = "spotify", value = "com.spotify.music"),
        MemoryEntry(key = "whatsapp", value = "com.whatsapp"),
    )
}
