package com.example.agent.data.local

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
)
