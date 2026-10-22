package com.example.agent.core.audit

import com.example.agent.core.intent.SpideyIntent
import java.security.MessageDigest

/** What actually gets stored for one event. Contains no message body and no phone number. */
data class AuditRecord(
    val id: String,
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

/** Turns an [AuditEvent] into the [AuditRecord] allowed by an [AuditPolicy]. */
object AuditSanitizer {

    const val CONTACT_PLACEHOLDER = "[contact]"
    const val NO_INTENT = "none"
    const val TRUNCATE_AT = 24
    private const val HASH_CHARS = 16

    fun sanitize(event: AuditEvent, policy: AuditPolicy, appVersion: String): AuditRecord =
        AuditRecord(
            id = event.id,
            timestampMillis = event.timestampMillis,
            requestText = requestText(event.requestText, policy.requestTextMode),
            intentType = intentType(event.intent),
            intentSummary = summary(event.intent, policy.keepContactNames),
            guardVerdict = event.guardVerdict.name.lowercase(),
            confirmed = event.confirmed,
            result = event.result.name.lowercase(),
            durationMs = event.durationMs.coerceAtLeast(0),
            appVersion = event.appVersion.ifBlank { appVersion },
        )

    fun requestText(raw: String, mode: RequestTextMode): String = when (mode) {
        RequestTextMode.NONE -> ""
        RequestTextMode.HASH -> sha256Prefix(raw.trim())
        RequestTextMode.TRUNCATED -> {
            val text = raw.trim()
            if (text.length <= TRUNCATE_AT) text else text.take(TRUNCATE_AT) + "..."
        }
    }

    fun intentType(intent: SpideyIntent?): String = when (intent) {
        null -> NO_INTENT
        is SpideyIntent.OpenApp -> "open_app"
        is SpideyIntent.SendMessage -> "send_message"
        is SpideyIntent.CallContact -> "call_contact"
        is SpideyIntent.SetAlarm -> "set_alarm"
        is SpideyIntent.SetTimer -> "set_timer"
    }

    /**
     * One line describing the intent. A message body is never part of it, and a contact is a
     * placeholder unless the user opted in to keeping names. Phone numbers are never known here.
     */
    fun summary(intent: SpideyIntent?, keepContactNames: Boolean): String {
        fun contact(name: String) = if (keepContactNames && name.isNotBlank()) name.trim() else CONTACT_PLACEHOLDER
        return when (intent) {
            null -> NO_INTENT
            is SpideyIntent.OpenApp -> "open ${intent.app.trim()}"
            is SpideyIntent.SendMessage -> "message ${contact(intent.contact)}"
            is SpideyIntent.CallContact -> "call ${contact(intent.contact)}"
            is SpideyIntent.SetAlarm -> "alarm ${intent.time.trim()}"
            is SpideyIntent.SetTimer -> "timer ${intent.minutes} min"
        }
    }

    private fun sha256Prefix(text: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }.take(HASH_CHARS)
    }
}
