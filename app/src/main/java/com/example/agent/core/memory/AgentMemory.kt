package com.example.agent.core.memory

import com.example.agent.domain.model.MemoryEntry

/**
 * Pure matching logic for long-term memory. Persistence lives in
 * [com.example.agent.data.preferences.MemoryRepository]; this class stays free of
 * Android types so it can be unit-tested directly.
 */
class AgentMemory(private val entries: List<MemoryEntry> = emptyList()) {

    /** Resolves a spoken/typed alias (e.g. "wa") to a stored value (e.g. "com.whatsapp"). */
    fun resolve(query: String): String? {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return null
        return entries.firstOrNull { it.key.lowercase() == q }?.value
            ?: entries.firstOrNull { q.contains(it.key.lowercase()) }?.value
    }

    fun isEmpty(): Boolean = entries.isEmpty()

    companion object {
        val EMPTY = AgentMemory()
    }
}
