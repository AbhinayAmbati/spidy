package com.example.agent.core.diagnostics

import com.example.agent.domain.model.TaskRecord

/**
 * Renders one task as plain text worth pasting into a bug report.
 *
 * Every diagnosis so far has cost a screenshot, a description and a guess. This is the
 * evidence: where the time went, what it cost, and why each step failed — in one block.
 *
 * Pure, so its shape is testable, and it carries no screen content beyond the failure reasons
 * the app already shows the user.
 */
object TaskDiagnostics {

    fun report(
        record: TaskRecord,
        appVersion: String,
        device: String,
        provider: String,
    ): String = buildString {
        appendLine("Spidey diagnostics")
        appendLine("app      $appVersion")
        appendLine("device   $device")
        appendLine("provider $provider")
        appendLine()

        appendLine("task")
        appendLine("  id       ${record.id}")
        appendLine("  prompt   ${record.prompt}")
        appendLine("  result   ${record.state}")
        appendLine("  duration ${record.durationMs} ms")
        appendLine("  steps    ${record.stepCount}")
        if (record.appsUsed.isNotEmpty()) {
            appendLine("  apps     ${record.appsUsed.joinToString(", ")}")
        }
        record.summary?.takeIf { it.isNotBlank() }?.let { appendLine("  summary  $it") }
        appendLine()

        appendLine("time")
        appendLine("  model    ${record.modelMillis} ms")
        appendLine("  overhead ${record.overheadMillis} ms   (settle + screen reads + actions)")
        appendLine("  tokens   ${record.inputTokens} in / ${record.outputTokens} out")
        appendLine()

        if (record.steps.isEmpty()) {
            appendLine("no steps recorded")
            return@buildString
        }

        appendLine("steps")
        record.steps.forEachIndexed { i, step ->
            appendLine("  ${i + 1}. [${step.status}] ${step.label}")
            appendLine(
                "     model ${step.modelMillis}ms  settle ${step.settleMillis}ms  " +
                    "observe ${step.observeMillis}ms  action ${step.actionMillis}ms  " +
                    "tokens ${step.inputTokens}/${step.outputTokens}",
            )
            step.detail?.takeIf { it.isNotBlank() }?.let { appendLine("     reason: $it") }
        }
    }
}
