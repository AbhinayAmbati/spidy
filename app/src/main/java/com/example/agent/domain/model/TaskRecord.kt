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
    /** Totals for the whole task, so a run can be measured rather than guessed at. */
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val modelMillis: Long = 0,
) {
    /** Time not spent waiting on the model: settle, screen reads, actions. */
    val overheadMillis: Long get() = (durationMs - modelMillis).coerceAtLeast(0)
}
