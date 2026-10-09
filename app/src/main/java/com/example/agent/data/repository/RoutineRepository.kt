package com.example.agent.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.agent.core.ai.AgentJson
import dagger.hilt.android.qualifiers.ApplicationContext
import com.example.agent.domain.model.Routine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.routineStore: DataStore<Preferences> by preferencesDataStore(name = "spidey_routines")

/**
 * Routines are few and small, so they live as one JSON list rather than a Room table — no
 * migration, and reading them all is what every screen wants anyway.
 */
@Singleton
class RoutineRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val key = stringPreferencesKey("routines")

    val routines: Flow<List<Routine>> = context.routineStore.data.map { prefs ->
        decode(prefs[key])
    }

    suspend fun all(): List<Routine> = routines.first()

    suspend fun get(id: String): Routine? = all().firstOrNull { it.id == id }

    suspend fun save(routine: Routine) {
        context.routineStore.edit { prefs ->
            val current = decode(prefs[key]).filterNot { it.id == routine.id }
            prefs[key] = AgentJson.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(Routine.serializer()),
                (current + routine).sortedByDescending { it.createdAtMillis },
            )
        }
    }

    suspend fun delete(id: String) {
        context.routineStore.edit { prefs ->
            prefs[key] = AgentJson.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(Routine.serializer()),
                decode(prefs[key]).filterNot { it.id == id },
            )
        }
    }

    /** Records that a routine ran, so the list can show what is actually used. */
    suspend fun markRun(id: String, atMillis: Long) {
        val existing = get(id) ?: return
        save(existing.copy(runCount = existing.runCount + 1, lastRunAtMillis = atMillis))
    }

    /** A stored list that cannot be read must not take the app down with it. */
    private fun decode(raw: String?): List<Routine> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            AgentJson.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(Routine.serializer()),
                raw,
            )
        }.getOrDefault(emptyList())
    }
}
