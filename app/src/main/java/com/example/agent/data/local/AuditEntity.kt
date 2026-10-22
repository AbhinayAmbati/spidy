package com.example.agent.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One audit row. Holds only what [com.example.agent.core.audit.AuditSanitizer] allowed:
 * never a message body, never a phone number, a contact only when the user opted in.
 */
@Entity(
    tableName = "audit_events",
    indices = [Index(value = ["timestampMillis"])],
)
data class AuditEntity(
    @PrimaryKey val id: String,
    val timestampMillis: Long,
    val requestText: String,
    val intentType: String,
    val intentSummary: String,
    val guardVerdict: String,
    val confirmed: Boolean?,
    val result: String,
    val durationMs: Long,
    val appVersion: String,
)
