package com.example.agent.data.repository

import com.example.agent.BuildConfig
import com.example.agent.core.audit.AuditEvent
import com.example.agent.core.audit.AuditLog
import com.example.agent.core.audit.AuditPolicy
import com.example.agent.core.audit.AuditSanitizer
import com.example.agent.core.audit.ManagedConfig
import com.example.agent.data.local.AgentDatabase
import com.example.agent.data.local.AuditEntity
import com.example.agent.data.preferences.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed [AuditLog]. Local only: nothing here is ever sent anywhere, and the user can
 * export or delete it from Settings.
 *
 * Every write goes through [AuditSanitizer] under the effective [AuditPolicy], so a caller
 * cannot store more than the user allowed. With auditing off, [record] stores nothing.
 */
@Singleton
class AuditRepository @Inject constructor(
    database: AgentDatabase,
    private val settings: SettingsRepository,
    private val managed: ManagedConfig,
) : AuditLog {

    private val dao = database.auditDao()

    @Volatile private var lastPruneMillis = 0L

    /** The rules in force right now: the user's settings, overridden by an administrator. */
    suspend fun policy(): AuditPolicy = AuditPolicy.resolve(
        userRetentionDays = settings.auditRetentionDays.first(),
        managedRetentionDays = managed.snapshot().auditRetentionDays,
        keepContactNames = settings.auditKeepContactNames.first(),
        requestTextMode = settings.auditRequestTextMode.first(),
    )

    override suspend fun record(event: AuditEvent) {
        val policy = policy()
        if (!policy.enabled) return
        val record = AuditSanitizer.sanitize(event, policy, BuildConfig.VERSION_NAME)
        dao.insert(
            AuditEntity(
                id = record.id,
                timestampMillis = record.timestampMillis,
                requestText = record.requestText,
                intentType = record.intentType,
                intentSummary = record.intentSummary,
                guardVerdict = record.guardVerdict,
                confirmed = record.confirmed,
                result = record.result,
                durationMs = record.durationMs,
                appVersion = record.appVersion,
            ),
        )
        // Pruning on write keeps the log bounded even if the app is never cold-started.
        if (record.timestampMillis - lastPruneMillis >= PRUNE_INTERVAL_MILLIS) {
            pruneExpired(record.timestampMillis)
        }
    }

    /**
     * Deletes records older than the retention period (all of them when auditing is off).
     * Call it once on app start; [record] also calls it at most once an hour.
     */
    suspend fun pruneExpired(nowMillis: Long = System.currentTimeMillis()): Int {
        lastPruneMillis = nowMillis
        val days = policy().retentionDays
        if (days <= 0) {
            val count = dao.all().size
            dao.clear()
            return count
        }
        return dao.pruneBefore(AuditPolicy.cutoffMillis(nowMillis, days))
    }

    val count: Flow<Int> = dao.observeCount()

    suspend fun all(): List<AuditEntity> = dao.all()

    suspend fun clear() = dao.clear()

    private companion object {
        const val PRUNE_INTERVAL_MILLIS = 60L * 60 * 1000
    }
}
