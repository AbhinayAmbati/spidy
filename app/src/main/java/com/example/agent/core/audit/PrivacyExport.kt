package com.example.agent.core.audit

import com.example.agent.data.local.AuditEntity
import com.example.agent.data.local.TaskEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Builds the JSON file a user can save: their task history and the audit log, nothing else. */
object PrivacyExport {

    const val FORMAT_VERSION = 1

    private val pretty = Json { prettyPrint = true }

    fun build(
        tasks: List<TaskEntity>,
        audit: List<AuditEntity>,
        exportedAtMillis: Long,
        appVersion: String,
    ): String = pretty.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            put("formatVersion", FORMAT_VERSION)
            put("exportedAtMillis", exportedAtMillis)
            put("appVersion", appVersion)
            put("taskHistory", JsonArray(tasks.map { taskJson(it) }))
            put("auditLog", JsonArray(audit.map { auditJson(it) }))
        },
    )

    private fun taskJson(t: TaskEntity): JsonObject = buildJsonObject {
        put("id", t.id)
        put("prompt", t.prompt)
        put("state", t.state)
        put("createdAtMillis", t.createdAtMillis)
        put("finishedAtMillis", t.finishedAtMillis)
        put("durationMs", t.durationMs)
        put("stepCount", t.stepCount)
        put("appsUsed", t.appsUsed)
        put("summary", t.summary)
        put("steps", t.steps)
        put("inputTokens", t.inputTokens)
        put("outputTokens", t.outputTokens)
        put("modelMillis", t.modelMillis)
    }

    private fun auditJson(a: AuditEntity): JsonObject = buildJsonObject {
        put("id", a.id)
        put("timestampMillis", a.timestampMillis)
        put("requestText", a.requestText)
        put("intentType", a.intentType)
        put("intentSummary", a.intentSummary)
        put("guardVerdict", a.guardVerdict)
        put("confirmed", a.confirmed)
        put("result", a.result)
        put("durationMs", a.durationMs)
        put("appVersion", a.appVersion)
    }
}
