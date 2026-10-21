package com.example.agent.core.agent

import com.example.agent.automation.AndroidAutomationManager
import com.example.agent.data.repository.MemoryRepository
import com.example.agent.data.repository.TaskHistoryRepository
import com.example.agent.domain.model.MemoryEntry
import com.example.agent.domain.model.TaskRecord

/** What the agent loop remembers: app shortcuts it knows, and the record of each task. */
interface TaskJournal {
    suspend fun knownApps(): List<MemoryEntry>

    /** Learns shortcuts for the apps a task used, when the user allows remembering. */
    suspend fun learnApps(packages: Set<String>)

    suspend fun record(record: TaskRecord)
}

class StoredTaskJournal(
    private val history: TaskHistoryRepository,
    private val memory: MemoryRepository,
    private val automation: AndroidAutomationManager,
) : TaskJournal {
    override suspend fun knownApps(): List<MemoryEntry> =
        runCatching { memory.snapshot() }.getOrDefault(emptyList())

    override suspend fun learnApps(packages: Set<String>) {
        runCatching {
            memory.rememberApps(
                packages.mapNotNull { pkg -> automation.appLabel(pkg)?.let { it to pkg } },
            )
        }
    }

    override suspend fun record(record: TaskRecord) = history.record(record)
}
