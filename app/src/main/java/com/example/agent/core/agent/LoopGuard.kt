package com.example.agent.core.agent

import com.example.agent.domain.model.AgentAction

/**
 * Loop protection for one task.
 *
 * The most common failure of an agent is running the same action over and over - opening an
 * app that is already on screen, or retyping text that is already there. The prompt forbids
 * it, but that cannot be relied on, so these guards decide, from what has already happened,
 * whether the next step or the next call may go ahead.
 *
 * Pure and free of Android types, so the behaviour is unit-testable. One instance per task.
 */
class LoopGuard {

    sealed interface StepCheck {
        data object Proceed : StepCheck

        /** Nothing has changed for a while: tell the model what it has spent, then go again. */
        data object Redirect : StepCheck

        /** Out of ideas: end the task with [summary]. */
        data class GiveUp(val summary: String) : StepCheck
    }

    sealed interface CallCheck {
        data object Allowed : CallCheck

        /** Do not run this call. [toModel] tells the model why, [stepDetail] tells the user. */
        data class Refused(val stepDetail: String, val toModel: String) : CallCheck

        /** The model keeps insisting: end the task with [summary]. */
        data class GiveUp(val stepDetail: String, val summary: String) : CallCheck
    }

    /** The screen as last observed, so a nudge can show the model what it is looking at. */
    var lastObservation: String? = null
        private set

    private var consecutiveUnchanged = 0
    private var stallRecoveries = 0
    private var consecutiveSameTool = 0
    private var lastTool: String? = null

    // How many times each exact action has run this task. See ActionSignature.NEVER_REPEATED_TOOLS.
    private val signatureRuns = mutableMapOf<String, Int>()

    // Per-tool counts, which ignore the arguments. See ActionSignature.TOOL_CAPS.
    private val toolRuns = mutableMapOf<String, Int>()

    private val observationAfter = mutableMapOf<String, String>()
    private val repeatRefusals = mutableMapOf<String, Int>()

    /**
     * Whether another step may start.
     *
     * A stall used to end the task. That was the wrong call: nothing on screen changing does
     * not mean the task is impossible, it means the agent has run out of ideas - and it had
     * never been told what it had already spent. So it is told, and given a few goes at a
     * different route before giving up.
     */
    fun checkBeforeStep(): StepCheck {
        if (consecutiveUnchanged >= MAX_STALLED_STEPS) {
            if (stallRecoveries < MAX_STALL_RECOVERIES) {
                stallRecoveries++
                consecutiveUnchanged = 0
                return StepCheck.Redirect
            }
            return StepCheck.GiveUp(
                "Nothing on screen changed for several steps and I had no untried route " +
                    "left, so I stopped rather than keep repeating myself. Tell me the " +
                    "exact element to use, or rephrase the task.",
            )
        }

        if (consecutiveSameTool >= MAX_SAME_TOOL_STREAK) {
            return StepCheck.GiveUp(
                "I used ${lastTool ?: "the same tool"} $consecutiveSameTool times in a row " +
                    "without finishing, so I stopped. That usually means the element I " +
                    "need is not on screen - name it exactly, or rephrase the task.",
            )
        }

        return StepCheck.Proceed
    }

    /**
     * Counts a call towards the streak of one tool in a row. Repeatable tools (scroll, wait,
     * back) are exempt, because using them several times in a row is legitimate.
     */
    fun trackTool(tool: String) {
        if (tool in ActionSignature.REPEATABLE_TOOLS) {
            lastTool = null
            consecutiveSameTool = 0
        } else if (tool == lastTool) {
            consecutiveSameTool++
        } else {
            lastTool = tool
            consecutiveSameTool = 1
        }
    }

    /** An action left the screen exactly as it was: no window change arrived at all. */
    fun noteScreenUnchanged() {
        consecutiveUnchanged++
    }

    /**
     * Records the screen after the action with signature [signature].
     *
     * @return true when it renders identically to the screen before. A change event can still
     * render identically (an animation, say).
     */
    fun noteObservation(rendered: String, signature: String?): Boolean {
        val same = rendered == lastObservation
        consecutiveUnchanged = if (same) consecutiveUnchanged + 1 else 0
        lastObservation = rendered
        signature?.let { observationAfter[it] = rendered }
        return same
    }

    /** Whether the call may run, in the order the guards were added. */
    fun checkCall(action: AgentAction, signature: String?): CallCheck {
        identicalRepeat(action, signature)?.let { return it }
        toolCap(action)?.let { return it }
        return repeatWithNoChange(signature)
    }

    // An action that has already run with exactly these arguments and got nowhere must not run
    // again. Checked by signature rather than by whether the screen changed, because the screen
    // almost always *does* change - and two runs that accomplished the same nothing still look
    // different to a snapshot.
    private fun identicalRepeat(action: AgentAction, signature: String?): CallCheck? {
        if (signature == null) return null
        val cap = if (action.action in ActionSignature.NEVER_REPEATED_TOOLS) {
            MAX_IDENTICAL_RUNS
        } else {
            MAX_IDENTICAL_RUNS_GENERIC
        }
        val runs = (signatureRuns[signature] ?: 0) + 1
        signatureRuns[signature] = runs
        if (runs <= cap) return null

        return CallCheck.Refused(
            stepDetail = "Already done — nothing gained",
            toModel = "\"${action.action}\" with exactly these arguments has already " +
                "run and got you nowhere. Running it again will not help. " +
                "Read the screen and pick a different element, or call " +
                "finish if the task is already done.",
        )
    }

    // The same tool, over and over, with the arguments changed each time. A search that has
    // already returned results is the case that matters: the model rephrases the query and
    // searches again, and the page it gets back is the same page it already had.
    private fun toolCap(action: AgentAction): CallCheck? {
        val cap = ActionSignature.TOOL_CAPS[action.action] ?: return null
        val runs = (toolRuns[action.action] ?: 0) + 1
        toolRuns[action.action] = runs
        if (runs <= cap) return null

        return CallCheck.Refused(
            stepDetail = "Stuck repeating ${action.action}",
            toModel = "You have now called ${action.action} $runs times in this task " +
                "and the screen is no closer to the goal. Do NOT call it " +
                "again. If search results are already showing, tap the one " +
                "you want, or call finish if what the task asked for is " +
                "already on screen.",
        )
    }

    // Refuse to re-run an action whose arguments are identical to one that already ran and left
    // the screen exactly as it is. If the model keeps insisting, stop rather than spin.
    private fun repeatWithNoChange(signature: String?): CallCheck {
        if (signature == null) return CallCheck.Allowed
        val previous = observationAfter[signature] ?: return CallCheck.Allowed
        if (previous != lastObservation) return CallCheck.Allowed

        val refusals = (repeatRefusals[signature] ?: 0) + 1
        repeatRefusals[signature] = refusals

        if (refusals >= MAX_REPEAT_REFUSALS) {
            return CallCheck.GiveUp(
                stepDetail = "Repeated with no progress",
                summary = "I kept repeating the same action and the screen never changed, so I " +
                    "stopped instead of looping. Name the exact element you want tapped, " +
                    "or try rephrasing.",
            )
        }

        return CallCheck.Refused(
            stepDetail = "Already tried — nothing changed",
            toModel = "You already ran this exact action and the screen has NOT changed. " +
                "Running it again will not help. Do something different now: " +
                "read_screen, scroll to reveal the element, choose a different " +
                "element, or call finish if the task is already done.",
        )
    }

    companion object {
        /**
         * How many times one exact action may run in a single task before it is refused.
         *
         * Two is enough for anything real: a third identical call has never been the thing
         * that unblocked a task, and the screenshots that prompted this show five and six.
         */
        const val MAX_IDENTICAL_RUNS = 2

        /**
         * The same cap for everything else that is not legitimately repeatable.
         *
         * Higher than [MAX_IDENTICAL_RUNS], because tapping the same "Next" button through a
         * paginated list is a real task. Four taps is the point at which it has stopped being
         * paging and started being a loop.
         */
        const val MAX_IDENTICAL_RUNS_GENERIC = 4

        /** How many times a refused repeat is tolerated before ending the task instead. */
        const val MAX_REPEAT_REFUSALS = 3

        /** Consecutive steps with an unchanged screen before we stop for lack of progress. */
        const val MAX_STALLED_STEPS = 4

        /**
         * How many times a stalled run is redirected before it is given up on.
         *
         * A stall means the agent has run out of ideas, not that the task is impossible, so it
         * is told what it has already spent and given another go. Two redirections is enough
         * to prove it is genuinely stuck rather than momentarily out of step.
         */
        const val MAX_STALL_RECOVERIES = 2

        /** Consecutive uses of one tool before we stop - the agent is hammering it. */
        const val MAX_SAME_TOOL_STREAK = 4
    }
}
