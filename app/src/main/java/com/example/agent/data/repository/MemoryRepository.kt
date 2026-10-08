package com.example.agent.data.repository

import com.example.agent.core.memory.AgentMemory
import com.example.agent.data.preferences.MemoryStore
import com.example.agent.data.preferences.SettingsRepository
import com.example.agent.domain.model.MemoryEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * The single door to long-term memory. Every write is gated on the user's
 * "Remember preferences" setting, so turning it off genuinely stops Spidey from
 * storing anything new — the toggle is not decorative.
 */
class MemoryRepository(
    private val store: MemoryStore,
    private val settings: SettingsRepository,
) {

    val entries: Flow<List<MemoryEntry>> = store.entries

    /** Reads memory for prompt-building. Always allowed — reading stores nothing. */
    suspend fun snapshot(): List<MemoryEntry> = store.snapshot()

    suspend fun agentMemory(): AgentMemory = AgentMemory(store.snapshot())

    /** Learns app shortcuts from a task, but only when the user allows remembering. */
    suspend fun rememberApps(aliases: List<Pair<String, String>>) {
        if (aliases.isEmpty()) return
        if (!settings.memoryEnabled.first()) return
        aliases.forEach { (label, packageName) ->
            val key = label.trim().lowercase()
            if (key.isNotBlank() && packageName.isNotBlank()) store.remember(key, packageName)
        }
    }

    suspend fun remember(key: String, value: String) {
        if (!settings.memoryEnabled.first()) return
        store.remember(key, value)
    }

    suspend fun forget(key: String) = store.forget(key)

    suspend fun clear() = store.clear()
}
