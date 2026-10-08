package com.example.agent.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.agent.domain.model.MemoryEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.memoryDataStore: DataStore<Preferences> by preferencesDataStore(name = "spidey_memory")

/**
 * Durable, device-local storage for long-term memory. Kept separate from
 * [SettingsRepository] so "clear my memory" can never touch configuration, and
 * vice versa.
 */
class MemoryStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    val entries: Flow<List<MemoryEntry>> = context.memoryDataStore.data.map { prefs ->
        decode(prefs[KEY])
    }

    suspend fun snapshot(): List<MemoryEntry> = entries.first()

    suspend fun remember(key: String, value: String) {
        val now = System.currentTimeMillis()
        context.memoryDataStore.edit { prefs ->
            val current = decode(prefs[KEY])
            val updated = current.filterNot { it.key.equals(key, ignoreCase = true) } +
                MemoryEntry(key = key, value = value, createdAtMillis = now)
            prefs[KEY] = encode(updated.takeLast(MAX_ENTRIES))
        }
    }

    suspend fun forget(key: String) {
        context.memoryDataStore.edit { prefs ->
            val updated = decode(prefs[KEY]).filterNot { it.key.equals(key, ignoreCase = true) }
            prefs[KEY] = encode(updated)
        }
    }

    suspend fun clear() {
        context.memoryDataStore.edit { it.remove(KEY) }
    }

    private fun encode(entries: List<MemoryEntry>): String =
        runCatching { json.encodeToString(entries) }.getOrDefault("[]")

    private fun decode(raw: String?): List<MemoryEntry> =
        raw?.let { runCatching { json.decodeFromString<List<MemoryEntry>>(it) }.getOrNull() }
            ?: emptyList()

    private companion object {
        val KEY = stringPreferencesKey("entries")
        const val MAX_ENTRIES = 100
    }
}
