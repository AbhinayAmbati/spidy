package com.example.agent.data.repository

import com.example.agent.data.local.TaskDao
import com.example.agent.data.local.TaskEntity
import com.example.agent.domain.model.RecordedStep
import com.example.agent.domain.model.TaskRecord
import com.example.agent.domain.model.TaskState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Local-only task history. Nothing here ever leaves the device. */
class TaskHistoryRepository(private val dao: TaskDao) {

    fun observeRecent(limit: Int = 20): Flow<List<TaskRecord>> =
        dao.observeRecent(limit).map { list -> list.map { it.toDomain() } }

    suspend fun getById(id: String): TaskRecord? = dao.getById(id)?.toDomain()

    suspend fun record(record: TaskRecord) = dao.upsert(record.toEntity())

    suspend fun clear() = dao.clear()

    suspend fun delete(id: String) = dao.deleteById(id)
}

// The mappers live at the top level rather than as members so they can be exercised without a
// database. They are the pair that broke - a field on one side with nowhere to live on the
// other - and a test that needs Room to check them is a test that never gets written.

private val historyJson = Json { ignoreUnknownKeys = true }

internal fun TaskEntity.toDomain() = TaskRecord(
    id = id,
    prompt = prompt,
    state = runCatching { TaskState.valueOf(state) }.getOrDefault(TaskState.COMPLETED),
    createdAtMillis = createdAtMillis,
    finishedAtMillis = finishedAtMillis,
    durationMs = durationMs,
    stepCount = stepCount,
    appsUsed = appsUsed.split(",").filter { it.isNotBlank() },
    summary = summary,
    steps = decodeSteps(steps),
    inputTokens = inputTokens,
    outputTokens = outputTokens,
    modelMillis = modelMillis,
)

internal fun TaskRecord.toEntity() = TaskEntity(
    id = id,
    prompt = prompt,
    state = state.name,
    createdAtMillis = createdAtMillis,
    finishedAtMillis = finishedAtMillis,
    durationMs = durationMs,
    stepCount = stepCount,
    appsUsed = appsUsed.joinToString(","),
    summary = summary,
    steps = encodeSteps(steps),
    inputTokens = inputTokens,
    outputTokens = outputTokens,
    modelMillis = modelMillis,
)

private fun encodeSteps(steps: List<RecordedStep>): String =
    runCatching { historyJson.encodeToString(steps) }.getOrDefault("[]")

private fun decodeSteps(raw: String): List<RecordedStep> =
    runCatching { historyJson.decodeFromString<List<RecordedStep>>(raw) }.getOrDefault(emptyList())
