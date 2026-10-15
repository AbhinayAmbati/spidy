package com.example.agent.core.agent

/** What came of an attempt. */
enum class AttemptOutcome { Succeeded, NoChange, Failed, Refused }

/** One thing the agent tried, and what came of it. */
data class Attempt(
    val label: String,
    val outcome: AttemptOutcome,
    val detail: String? = null,
    val count: Int = 1,
)

/**
 * What the agent has already tried, and what came of it.
 *
 * The loop's guards refuse a repeat, but refusing is *reactive*: the model only learns it was
 * wrong after it has proposed the same thing again, and a run can spend its whole budget
 * proposing the same three actions. Real diagnostics show exactly that — opening the same app
 * three times, typing the same text four times, tapping the same suggestion three times — and
 * the run then ends with "nothing has changed, tell me the exact element".
 *
 * That last sentence is the real failure. The agent had not run out of things to do; it had
 * run out of ideas, because nothing ever told it what it had already spent. This renders the
 * ledger it was missing, so choosing something new is a decision it can actually make.
 *
 * Pure and free of Android types, so it is unit-testable.
 */
object AttemptLedger {

    /** How many attempts to list. Enough to be useful, short enough to send every step. */
    const val MAX_LISTED = 6

    /**
     * @param attempts everything tried so far, oldest first.
     * @param untriedTools tools this task has not used at all.
     * @return a block for the model, or null when there is nothing worth saying.
     */
    fun render(attempts: Collection<Attempt>, untriedTools: List<String>): String? {
        if (attempts.isEmpty() && untriedTools.isEmpty()) return null

        return buildString {
            if (attempts.isNotEmpty()) {
                appendLine("ALREADY ATTEMPTED — do NOT do these again, they will be refused:")
                attempts.toList().takeLast(MAX_LISTED).forEach { attempt ->
                    append("- ")
                    append(attempt.label)
                    if (attempt.count > 1) append(" (tried ${attempt.count} times)")
                    append(" — ")
                    appendLine(describe(attempt))
                }
            }
            if (untriedTools.isNotEmpty()) {
                appendLine()
                append("NOT YET TRIED in this task: ")
                appendLine(untriedTools.joinToString(", "))
                append("If what you are doing is not working, use one of those instead.")
            }
        }.trimEnd()
    }

    private fun describe(attempt: Attempt): String = when (attempt.outcome) {
        AttemptOutcome.Succeeded -> "already done, and the screen shows it"
        AttemptOutcome.NoChange -> "nothing changed on screen"
        AttemptOutcome.Failed -> attempt.detail?.takeIf { it.isNotBlank() }
            ?.let { "failed — ${it.take(80)}" }
            ?: "failed"
        AttemptOutcome.Refused -> "refused, no progress"
    }
}
