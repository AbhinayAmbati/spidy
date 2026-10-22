package com.example.agent.core.audit

import com.example.agent.core.intent.SpideyIntent
import com.example.agent.data.local.AuditEntity
import com.example.agent.data.local.TaskEntity
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AuditSanitizerTest {

    private fun event(
        intent: SpideyIntent?,
        text: String = "text mom that I am late",
        confirmed: Boolean? = true,
        result: AuditResult = AuditResult.DONE,
    ) = AuditEvent(
        id = "e1",
        timestampMillis = 1_000L,
        requestText = text,
        intent = intent,
        guardVerdict = AuditVerdict.ALLOW,
        confirmed = confirmed,
        result = result,
        durationMs = 42,
    )

    private val message = SpideyIntent.SendMessage(contact = "Mom", text = "secret body about the surprise")

    @Test
    fun messageBodyIsNeverStored() {
        val record = AuditSanitizer.sanitize(event(message), AuditPolicy(keepContactNames = true), "1.0")
        assertThat(record.intentSummary).doesNotContain("secret")
        assertThat(record.intentSummary).doesNotContain("surprise")
        assertThat(record.intentSummary).isEqualTo("message Mom")
    }

    @Test
    fun contactIsAPlaceholderUnlessTheUserOptedIn() {
        val hidden = AuditSanitizer.sanitize(event(message), AuditPolicy(keepContactNames = false), "1.0")
        assertThat(hidden.intentSummary).isEqualTo("message [contact]")
        val call = AuditSanitizer.sanitize(
            event(SpideyIntent.CallContact("Dr Rao")), AuditPolicy(keepContactNames = false), "1.0",
        )
        assertThat(call.intentSummary).isEqualTo("call [contact]")
    }

    @Test
    fun requestTextIsHashedByDefaultAndStable() {
        val a = AuditSanitizer.requestText("call mom", RequestTextMode.HASH)
        assertThat(a).hasLength(16)
        assertThat(a).doesNotContain("mom")
        assertThat(AuditSanitizer.requestText("  call mom ", RequestTextMode.HASH)).isEqualTo(a)
        assertThat(AuditSanitizer.requestText("call dad", RequestTextMode.HASH)).isNotEqualTo(a)
    }

    @Test
    fun requestTextCanBeTruncatedOrDropped() {
        val long = "a".repeat(100)
        val cut = AuditSanitizer.requestText(long, RequestTextMode.TRUNCATED)
        assertThat(cut).isEqualTo("a".repeat(AuditSanitizer.TRUNCATE_AT) + "...")
        assertThat(AuditSanitizer.requestText("short", RequestTextMode.TRUNCATED)).isEqualTo("short")
        assertThat(AuditSanitizer.requestText("anything", RequestTextMode.NONE)).isEmpty()
    }

    @Test
    fun recordCarriesVerdictConfirmationResultAndVersion() {
        val record = AuditSanitizer.sanitize(
            event(SpideyIntent.SetTimer(5), confirmed = null, result = AuditResult.CANCELLED),
            AuditPolicy(), "9.9",
        )
        assertThat(record.intentType).isEqualTo("set_timer")
        assertThat(record.intentSummary).isEqualTo("timer 5 min")
        assertThat(record.guardVerdict).isEqualTo("allow")
        assertThat(record.confirmed).isNull()
        assertThat(record.result).isEqualTo("cancelled")
        assertThat(record.durationMs).isEqualTo(42)
        assertThat(record.appVersion).isEqualTo("9.9")
    }

    @Test
    fun requestWithNoIntentIsRecordedAsNone() {
        val record = AuditSanitizer.sanitize(
            event(null, result = AuditResult.REJECTED).copy(guardVerdict = AuditVerdict.NONE),
            AuditPolicy(), "1.0",
        )
        assertThat(record.intentType).isEqualTo("none")
        assertThat(record.result).isEqualTo("rejected")
    }

    @Test
    fun policyOffMeansDisabledAndManagedValueWins() {
        assertThat(AuditPolicy(retentionDays = 0).enabled).isFalse()
        val resolved = AuditPolicy.resolve(
            userRetentionDays = 90, managedRetentionDays = 7,
            keepContactNames = true, requestTextMode = RequestTextMode.NONE,
        )
        assertThat(resolved.retentionDays).isEqualTo(7)
        assertThat(resolved.keepContactNames).isTrue()
        assertThat(
            AuditPolicy.resolve(30, null, false, RequestTextMode.HASH).retentionDays,
        ).isEqualTo(30)
    }

    @Test
    fun cutoffIsRetentionDaysBeforeNow() {
        val day = 24L * 60 * 60 * 1000
        assertThat(AuditPolicy.cutoffMillis(100 * day, 7)).isEqualTo(93 * day)
    }

    @Test
    fun requestTextModeFallsBackToHash() {
        assertThat(RequestTextMode.fromKey(null)).isEqualTo(RequestTextMode.HASH)
        assertThat(RequestTextMode.fromKey("garbage")).isEqualTo(RequestTextMode.HASH)
        assertThat(RequestTextMode.fromKey("none")).isEqualTo(RequestTextMode.NONE)
    }

    @Test
    fun exportContainsHistoryAndAuditAsJson() {
        val task = TaskEntity(
            id = "t1", prompt = "open maps", state = "COMPLETED", createdAtMillis = 5,
            finishedAtMillis = null, durationMs = 3, stepCount = 1, appsUsed = "maps",
            summary = null, steps = "[]",
        )
        val audit = AuditEntity(
            id = "a1", timestampMillis = 6, requestText = "abc", intentType = "open_app",
            intentSummary = "open maps", guardVerdict = "allow", confirmed = null,
            result = "done", durationMs = 3, appVersion = "1.0",
        )
        val json = PrivacyExport.build(listOf(task), listOf(audit), 99, "1.0")
        assertThat(json).contains("\"taskHistory\"")
        assertThat(json).contains("\"auditLog\"")
        assertThat(json).contains("open maps")
        assertThat(json).contains("\"formatVersion\": 1")
    }
}
