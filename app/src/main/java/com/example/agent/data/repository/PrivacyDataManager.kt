package com.example.agent.data.repository

import android.content.Context
import android.net.Uri
import com.example.agent.BuildConfig
import com.example.agent.core.audit.PrivacyExport
import com.example.agent.data.local.TaskDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Export and erase everything Spidey keeps about the user. All of it is on this device. */
@Singleton
class PrivacyDataManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tasks: TaskDao,
    private val audit: AuditRepository,
    private val memory: MemoryRepository,
    private val routines: RoutineRepository,
) {

    /**
     * Writes task history and the audit log as JSON to [target], a document the user chose
     * through the system file picker. Returns false when it could not be written.
     */
    suspend fun export(target: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val json = PrivacyExport.build(
                tasks = tasks.observeAll().first(),
                audit = audit.all(),
                exportedAtMillis = System.currentTimeMillis(),
                appVersion = BuildConfig.VERSION_NAME,
            )
            val stream = context.contentResolver.openOutputStream(target, "wt")
                ?: error("no output stream")
            stream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
        }.isSuccess
    }

    /** Clears task history, audit log, memory and routines. Settings and keys are untouched. */
    suspend fun deleteAll() {
        tasks.clear()
        audit.clear()
        memory.clear()
        routines.all().forEach { routines.delete(it.id) }
    }
}
