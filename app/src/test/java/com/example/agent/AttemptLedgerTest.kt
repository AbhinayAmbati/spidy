package com.example.agent

import com.example.agent.core.agent.Attempt
import com.example.agent.core.agent.AttemptLedger
import com.example.agent.core.agent.AttemptOutcome
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The ledger the agent was missing.
 *
 * The loop's guards refuse a repeat, but that is reactive: the model only learns it was wrong
 * after proposing the same thing again. Real diagnostics show the same app opened three times,
 * the same text typed four times, the same suggestion tapped three times, and then the run
 * ending with "nothing has changed, tell me the exact element" - which was never true. The
 * agent had not run out of things to do, it had run out of ideas, because nothing ever told it
 * what it had already spent.
 */
class AttemptLedgerTest {

    private fun attempt(label: String, outcome: AttemptOutcome, detail: String? = null, count: Int = 1) =
        Attempt(label = label, outcome = outcome, detail = detail, count = count)

    @Test
    fun `nothing to say when nothing has been tried`() {
        assertThat(AttemptLedger.render(emptyList(), emptyList())).isNull()
    }

    @Test
    fun `a tried action is listed with what came of it`() {
        val block = AttemptLedger.render(
            listOf(attempt("Opening com.android.youtube", AttemptOutcome.NoChange)),
            emptyList(),
        )!!

        assertThat(block).contains("Opening com.android.youtube")
        assertThat(block).contains("nothing changed")
        assertThat(block).contains("do NOT do these again")
    }

    @Test
    fun `a failure carries its reason, so the model can fix it rather than repeat it`() {
        val block = AttemptLedger.render(
            listOf(attempt("Typing \"a query\"", AttemptOutcome.Failed, "The field could not be filled.")),
            emptyList(),
        )!!

        assertThat(block).contains("The field could not be filled.")
    }

    @Test
    fun `a repeated attempt shows how many times it was tried`() {
        val block = AttemptLedger.render(
            listOf(attempt("Typing \"a query\"", AttemptOutcome.NoChange, count = 4)),
            emptyList(),
        )!!

        assertThat(block).contains("tried 4 times")
    }

    @Test
    fun `a success is listed too, because redoing what worked is the other half of the problem`() {
        val block = AttemptLedger.render(
            listOf(attempt("Opening com.android.youtube", AttemptOutcome.Succeeded)),
            emptyList(),
        )!!

        assertThat(block).contains("already done")
    }

    @Test
    fun `untried tools are named, which is the answer to 'what do I do next'`() {
        val block = AttemptLedger.render(
            emptyList(),
            listOf("press_back", "scroll", "launch_url"),
        )!!

        assertThat(block).contains("NOT YET TRIED")
        assertThat(block).contains("press_back")
        assertThat(block).contains("launch_url")
    }

    @Test
    fun `only the most recent attempts are listed, so the block stays cheap to send`() {
        val many = (1..20).map { attempt("action $it", AttemptOutcome.NoChange) }

        val block = AttemptLedger.render(many, emptyList())!!

        assertThat(block).contains("action 20")
        assertThat(block).doesNotContain("action 1 ")
        assertThat(block.lines().count { it.startsWith("- ") })
            .isEqualTo(AttemptLedger.MAX_LISTED)
    }

    @Test
    fun `a refusal is listed, because that is the thing most worth not repeating`() {
        val block = AttemptLedger.render(
            listOf(attempt("Opening com.android.youtube", AttemptOutcome.Refused)),
            emptyList(),
        )!!

        assertThat(block).contains("Opening com.android.youtube")
    }

    @Test
    fun `an empty untried list does not produce a dangling heading`() {
        val block = AttemptLedger.render(
            listOf(attempt("a thing", AttemptOutcome.NoChange)),
            emptyList(),
        )!!

        assertThat(block).doesNotContain("NOT YET TRIED")
    }
}
