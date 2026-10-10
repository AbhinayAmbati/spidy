package com.example.agent.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: String,
    val prompt: String,
    val state: String,
    val createdAtMillis: Long,
    val finishedAtMillis: Long?,
    val durationMs: Long,
    val stepCount: Int,
    val appsUsed: String,
    val summary: String?,
    /** JSON-encoded list of the actions the agent took. */
    val steps: String,
    /**
     * Totals for the whole task.
     *
     * These were on TaskRecord but not on the entity, so the mapper dropped them and every
     * read came back as zero - the diagnostics reported "model 0 ms / tokens 0 in / 0 out"
     * while the per-step numbers, stored inside [steps], were perfectly correct.
     */
    @ColumnInfo(defaultValue = "0") val inputTokens: Int = 0,
    @ColumnInfo(defaultValue = "0") val outputTokens: Int = 0,
    @ColumnInfo(defaultValue = "0") val modelMillis: Long = 0,
)
