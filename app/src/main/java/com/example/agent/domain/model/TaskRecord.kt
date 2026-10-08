package com.example.agent.domain.model

/** A completed (or failed) task, persisted to local history. */
data class TaskRecord(
    val id: String,
    val prompt: String,
    val state: TaskState,
    val createdAtMillis: Long,
    val finishedAtMillis: Long?,
    val durationMs: Long,
    val stepCount: Int,
    val appsUsed: List<String>,
    val summary: String?,
    val steps: List<RecordedStep> = emptyList(),
)
