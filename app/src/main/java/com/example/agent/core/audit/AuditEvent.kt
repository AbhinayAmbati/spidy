package com.example.agent.core.audit

import com.example.agent.core.intent.SpideyIntent

/** How an intent ended. */
enum class AuditResult { DONE, FAILED, REJECTED, CANCELLED }

/** What the guard decided before anything ran. */
enum class AuditVerdict { ALLOW, REJECT, NONE }

/**
 * One request, from the sentence the user said to what happened. This is the in-memory event
 * callers build; it may hold the raw sentence and the intent's values. What is written to
 * disk is decided by [AuditSanitizer] and the user's privacy settings, never by the caller.
 *
 * @property intent the routed intent, or null when the model found nothing Spidey can do.
 * @property confirmed null when no confirmation was asked, otherwise the user's answer.
 * @property appVersion filled in by the log when blank.
 */
data class AuditEvent(
    val id: String,
    val timestampMillis: Long,
    val requestText: String,
    val intent: SpideyIntent?,
    val guardVerdict: AuditVerdict,
    val confirmed: Boolean?,
    val result: AuditResult,
    val durationMs: Long,
    val appVersion: String = "",
)

/** The audit trail. Implementations decide what is kept; callers only report what happened. */
interface AuditLog {
    suspend fun record(event: AuditEvent)
}
