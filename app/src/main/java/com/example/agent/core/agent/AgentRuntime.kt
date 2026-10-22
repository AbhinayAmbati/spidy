package com.example.agent.core.agent

import com.example.agent.automation.tools.ToolRegistry
import com.example.agent.core.ai.AIProvider
import com.example.agent.core.ai.AgentJson
import com.example.agent.core.control.AgentHalt
import com.example.agent.core.ai.AIProviderException
import com.example.agent.domain.model.ActionTarget
import com.example.agent.domain.model.AwaitingKind
import com.example.agent.domain.model.AgentAction
import com.example.agent.domain.model.AgentActionCall
import com.example.agent.domain.model.AgentMessage
import com.example.agent.domain.model.AgentResponse
import com.example.agent.domain.model.MessageRole
import com.example.agent.domain.model.RecordedStep
import com.example.agent.domain.model.Routine
import com.example.agent.domain.model.StepMetrics
import com.example.agent.domain.model.StepStatus
import com.example.agent.domain.model.TaskProgress
import com.example.agent.domain.model.TaskRecord
import com.example.agent.domain.model.TaskState
import com.example.agent.domain.model.TaskStep
import com.example.agent.domain.model.ToolCall
import com.example.agent.domain.model.ToolNames
import com.example.agent.domain.model.ToolResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.coroutines.coroutineContext

/**
 * The agent loop.
 *
 *   OBSERVE -> PLAN -> SELECT TOOL -> VALIDATE -> EXECUTE -> OBSERVE -> ...
 *
 * It is the only component that holds the conversation and drives the model. It
 * never executes anything itself: every action goes through [ActionValidator] and
 * then a registered tool.
 */
class AgentRuntime(
    private val models: ModelSource,
    private val registry: ToolRegistry,
    private val validator: ActionValidator,
    private val screen: AgentScreen,
    private val journal: TaskJournal,
    private val clock: AgentClock,
    private val scope: CoroutineScope,
) {

    private val _progress = MutableStateFlow(
        TaskProgress(
            taskId = "",
            prompt = "",
            state = TaskState.IDLE,
            steps = emptyList(),
            currentStep = 0,
            totalSteps = TaskProgress.MAX_AGENT_STEPS,
        )
    )
    val progress: StateFlow<TaskProgress> = _progress.asStateFlow()

    private val _paused = MutableStateFlow(false)
    val paused: StateFlow<Boolean> = _paused.asStateFlow()

    private var job: Job? = null
    private var confirmation: CompletableDeferred<Boolean>? = null
    private var question: CompletableDeferred<String>? = null
    private var pauseGate: CompletableDeferred<Unit>? = null

    val isRunning: Boolean get() = job?.isActive == true

    private var taskStartedAtMillis: Long = 0L

    /** When the current task began, on the clock the touch gate compares against. */
    val startedAtMillis: Long get() = taskStartedAtMillis

    /** Actions of the most recent completed task, so it can be saved as a routine. */
    @Volatile
    private var lastRecordedActions: List<AgentAction> = emptyList()

    fun start(prompt: String) {
        if (isRunning) return
        taskStartedAtMillis = clock.uptimeMillis()
        AgentHalt.clear()
        job = scope.launch { runTask(prompt, routine = null) }
    }

    /**
     * Replays a recorded routine with no model involved.
     *
     * The cheapest and most reliable path the app has: no round trips, no tokens. It is also
     * the most fragile, because it assumes the screens still look the way they did when it
     * was recorded - so every step is verified as it runs and the agent hands over to the
     * model the moment one does not match.
     */
    fun startRoutine(routine: Routine) {
        if (isRunning) return
        taskStartedAtMillis = clock.uptimeMillis()
        AgentHalt.clear()
        job = scope.launch { runTask(routine.prompt, routine = routine) }
    }

    /** Actions of the most recent task that completed, for saving as a routine. */
    fun lastSuccessfulActions(): List<AgentAction> = lastRecordedActions.toList()

    /**
     * Stops the task immediately, wherever it is.
     *
     * The halt gate is set *before* cancelling, so an action already in flight cannot
     * start the next device operation, and any operation about to begin is refused.
     * Cancelling the job alone left launches and gestures running.
     */
    fun stop(reason: String = STOPPED_BY_USER) {
        AgentHalt.request()
        job?.cancel()
        job = null
        confirmation?.cancel()
        question?.cancel()
        pauseGate?.cancel()
        pauseGate = null
        _paused.value = false
        _progress.update {
            it.copy(state = TaskState.CANCELLED, message = reason, awaitingKind = AwaitingKind.None)
        }
    }

    /** Pauses between steps. Safe to call when nothing is running. */
    fun pause() {
        if (!isRunning || _paused.value) return
        // The gate first: awaitResumeIfPaused reads the flag and then awaits the gate, so
        // the other order let a pause slip through with no gate to wait on.
        pauseGate = CompletableDeferred()
        _paused.value = true
        _progress.update { it.copy(state = TaskState.PAUSED, message = "Paused.") }
    }

    fun resume() {
        if (!_paused.value) return
        _paused.value = false
        pauseGate?.complete(Unit)
        pauseGate = null
        _progress.update { it.copy(state = TaskState.EXECUTING, message = "Continuing…") }
    }

    /** Called by the confirmation sheet. */
    fun resolveConfirmation(allow: Boolean) {
        confirmation?.complete(allow)
    }

    /** Called when the agent asked the user a question. */
    fun answerQuestion(answer: String) {
        question?.complete(answer)
    }

    // ------------------------------------------------------------------ the loop

    private suspend fun runTask(prompt: String, routine: Routine?) {
        val startedAt = clock.currentTimeMillis()
        val taskId = UUID.randomUUID().toString()
        val appsUsed = linkedSetOf<String>()
        val steps = mutableListOf<TaskStep>()
        val actionsTaken = mutableListOf<AgentAction>()
        var stepCount = 0

        _progress.value = TaskProgress(
            taskId = taskId,
            prompt = prompt,
            state = TaskState.PLANNING,
            steps = emptyList(),
            currentStep = 0,
            totalSteps = TaskProgress.MAX_AGENT_STEPS,
            message = "Understanding your request…",
        )

        if (!screen.isReady()) {
            finish(taskId, prompt, startedAt, stepCount, appsUsed, steps, TaskState.FAILED,
                "Accessibility access is off, so I can't see or control the screen. Enable it in Settings.")
            return
        }

        val knownApps = journal.knownApps()

        // Created on demand: a replay needs no model at all, and therefore no key.
        var provider: AIProvider? = null
        suspend fun ensureProvider(): AIProvider {
            provider?.let { return it }
            return models.provider().also { provider = it }
        }

        // Replay until a step does not match, then hand over to the model from that point.
        var replayIndex = 0
        var replaying = routine != null

        var nudges = 0
        // What the model last said, so a task that ends because it never acted can show it.
        var lastReply: String? = null
        val loopGuard = LoopGuard()
        // Everything tried this task, keyed by action signature, so the model can be shown
        // what it has already spent instead of being refused after the fact.
        val attempts = LinkedHashMap<String, Attempt>()
        val toolsUsed = linkedSetOf<String>()

        /**
         * The tools this task has not touched at all.
         *
         * Declared here rather than beside the other helpers because the stall check at the
         * top of the loop needs it, and a local function must be declared before it is used.
         *
         * Sent with every observation, because "what have I not tried" is the question the
         * agent could not answer for itself - and the answer is almost always something more
         * useful than the fourth repeat of the same action.
         */
        fun ledgerBlock(): String? = AttemptLedger.render(
            attempts.values,
            registry.specs().map { it.name }.filterNot { it in toolsUsed },
        )
        val messages = mutableListOf<AgentMessage>()
        var conversationStarted = false

        // A replay begins with no conversation at all; this builds one only when the model is
        // actually needed, with the screen as it is then rather than as it was.
        suspend fun beginConversation() {
            if (conversationStarted) return
            conversationStarted = true
            messages += AgentMessage(MessageRole.System, PromptBuilder.systemPrompt(knownApps))
            val opening = screen.observe()
            opening.snapshot.packageName?.let { appsUsed.add(it) }
            messages += AgentMessage(
                MessageRole.User,
                PromptBuilder.initialUserMessage(prompt, opening.rendered),
            )
        }

        try {
            for (step in 0 until TaskProgress.MAX_AGENT_STEPS) {
                if (AgentHalt.isStopped) throw kotlinx.coroutines.CancellationException("Stopped by the user")

                when (val check = loopGuard.checkBeforeStep()) {
                    LoopGuard.StepCheck.Proceed -> Unit
                    LoopGuard.StepCheck.Redirect -> {
                        messages += AgentMessage(
                            MessageRole.User,
                            PromptBuilder.observationMessage(
                                buildString {
                                    appendLine(
                                        "Nothing on screen has changed for several steps, so " +
                                            "you are stuck. Do NOT repeat what you have been " +
                                            "doing — it will be refused.",
                                    )
                                    appendLine()
                                    ledgerBlock()?.let { append(it) }
                                },
                            ),
                        )
                        trim(messages)
                        continue
                    }
                    is LoopGuard.StepCheck.GiveUp -> {
                        finish(
                            taskId, prompt, startedAt, stepCount, appsUsed, steps,
                            TaskState.FAILED, check.summary,
                        )
                        return
                    }
                }

                coroutineContext.ensureActive()
                awaitResumeIfPaused()
                coroutineContext.ensureActive()

                _progress.value = _progress.value.copy(
                    state = TaskState.PLANNING,
                    currentStep = step,
                    message = "Thinking…",
                )

                val calls: List<AgentActionCall>
                var modelMillis = 0L
                var response: AgentResponse? = null

                val replayed = if (replaying) routine?.steps?.getOrNull(replayIndex) else null
                if (replaying && replayed == null) {
                    finish(
                        taskId, prompt, startedAt, stepCount, appsUsed, steps, TaskState.COMPLETED,
                        "Replayed the routine with no model involved.",
                    )
                    return
                }

                if (replaying) {
                    val decoded = runCatching {
                        AgentJson.decodeFromString(AgentAction.serializer(), replayed!!.actionJson)
                    }.getOrNull()
                    if (decoded == null) {
                        // A stored step we cannot read is a mismatch like any other.
                        replaying = false
                        continue
                    }
                    calls = listOf(AgentActionCall(null, decoded))
                    replayIndex++
                } else {
                    val modelStartedAt = clock.uptimeMillis()
                    beginConversation()
                    val generated = try {
                        ensureProvider().generateResponse(messages, registry.specs())
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        // Stop pressed mid-call. Reporting it as a provider failure overwrote
                        // the CANCELLED state that stop() had just set.
                        throw e
                    } catch (e: Exception) {
                        finish(taskId, prompt, startedAt, stepCount, appsUsed, steps, TaskState.FAILED, friendly(e))
                        return
                    }
                    modelMillis = clock.uptimeMillis() - modelStartedAt
                    response = generated
                    // The raw reply, not the parsed message: it shows the calls and confidence too.
                    lastReply = generated.raw?.takeIf { it.isNotBlank() } ?: generated.message
                    calls = generated.calls
                }

                if (calls.isEmpty()) {
                    val said = response?.message?.takeIf { it.isNotBlank() }
                    if (said != null) messages += AgentMessage(MessageRole.Assistant, said)

                    // A call that could not be read is shown back to the model rather than
                    // ending the task. This used to throw out of parsing and kill the whole
                    // run on step 0, before the phone had done anything at all - but a model
                    // that fumbles one call is usually fine on the next one.
                    val malformed = response?.malformed.orEmpty()
                    if (malformed.isNotEmpty() && nudges < MAX_TEXT_NUDGES) {
                        nudges++
                        messages += AgentMessage(
                            MessageRole.User,
                            PromptBuilder.observationMessage(
                                "Your last tool call could not be read:\n" +
                                    malformed.joinToString("\n") +
                                    "\nSend that call again, with the arguments exactly as the " +
                                    "tool's schema describes them. In particular a target is an " +
                                    "object, not a string: {\"target\":{\"text\":\"the visible " +
                                    "label\"}}.",
                            ),
                        )
                        trim(messages)
                        continue
                    }

                    // A reply with no tool call is NOT a finished task: completion always
                    // arrives through the `finish` tool. Reporting the model's prose as a
                    // successful result was quietly wrong, so nudge it back on track first.
                    if (nudges < MAX_TEXT_NUDGES) {
                        nudges++
                        // The screen goes with the nudge. Without it the model was told off
                        // for talking but given nothing to act on, so it talked again - which
                        // is exactly how the nudge loop used to exhaust itself.
                        val currentScreen = loopGuard.lastObservation?.takeIf { it.isNotBlank() }
                        messages += AgentMessage(
                            MessageRole.User,
                            PromptBuilder.observationMessage(
                                buildString {
                                    appendLine(
                                        "You replied with text instead of calling a tool. " +
                                            "Reply again, and this time call a tool.",
                                    )
                                    appendLine("Use finish when the task is complete.")
                                    if (currentScreen != null) {
                                        appendLine()
                                        appendLine("The screen right now:")
                                        append(currentScreen)
                                    }
                                },
                            ),
                        )
                        trim(messages)
                        continue
                    }

                    finish(
                        taskId, prompt, startedAt, stepCount, appsUsed, steps, TaskState.FAILED,
                        "The model never chose an action, so I stopped." +
                            (lastReply?.let { " Its last answer was: ${it.take(300)}" } ?: ""),
                    )
                    return
                }

                // One assistant turn carrying every call it made, so the history replays as
                // real tool use and each result can be paired back by id. Batching is what
                // keeps a multi-action task to a single round trip.
                val callRefs = calls.mapIndexed { i, call ->
                    ToolCall(
                        id = call.id ?: "call_${step}_$i",
                        name = call.action.action,
                        arguments = encodeAction(call.action),
                    )
                }
                messages += AgentMessage(MessageRole.Assistant, "", toolCalls = callRefs)

                for ((callIndex, call) in calls.withIndex()) {
                    val action = call.action
                    val callRef = callRefs[callIndex]
                    // Computed here, not further down: recordAttempt below needs it, and a
                    // local must be declared before it is used.
                    val sig = ActionSignature.of(action)

                    loopGuard.trackTool(action.action)

                    val label = describe(action)
                    val index = steps.size
                    steps += TaskStep(index, label, StepStatus.Running)
                    _progress.value = _progress.value.copy(
                        state = TaskState.EXECUTING,
                        steps = steps.toList(),
                        currentStep = step,
                        message = label,
                    )

                    fun recordAttempt(outcome: AttemptOutcome, detail: String? = null) {
                        // Repeatable tools (scroll, wait, back) are excluded: using them again
                        // is the point of them, and listing them as spent would be wrong.
                        val key = sig ?: return
                        toolsUsed += action.action
                        val label = describe(action)
                        val existing = attempts[key]
                        attempts[key] = existing?.copy(
                            outcome = outcome,
                            detail = detail ?: existing.detail,
                            count = existing.count + 1,
                        ) ?: Attempt(label, outcome, detail)
                    }

                    fun recordResult(text: String) {
                        val body = ledgerBlock()?.let { "$text\n\n$it" } ?: text
                        messages += AgentMessage(
                            role = MessageRole.Tool,
                            content = body,
                            toolCallId = callRef.id,
                            name = callRef.name,
                        )
                        trim(messages)
                    }

                    // Declared before the local functions below: a local function can only see
                    // variables declared above it, and observeAfterAction needs this.
                    data class AfterAction(val text: String, val settleMillis: Long, val observeMillis: Long)

                    // Read-only tools cannot change the screen, so there is nothing to wait for. A
                    // real 21-second run spent 8.8 s in settle - 41% of the wall clock - and the
                    // read-only steps paid the full quiet period for a screen they had just read.
                    val nonMutating = action.action in NON_MUTATING_TOOLS

                    /**
                     * The one place that decides what the model is told about the screen after an
                     * action.
                     *
                     * When no window-change event arrived, the screen is byte-for-byte what the
                     * model already has, so both the traversal and the listing are skipped — the
                     * single biggest per-step saving, and it applies to every no-op action.
                     */
                    suspend fun observeAfterAction(): AfterAction {
                        val settleStartedAt = clock.uptimeMillis()
                        val changed = screen.awaitSettled()
                        val settleMillis = clock.uptimeMillis() - settleStartedAt

                        if (!changed) {
                            loopGuard.noteScreenUnchanged()
                            return AfterAction(UNCHANGED_NOTE, settleMillis, 0)
                        }

                        val observeStartedAt = clock.uptimeMillis()
                        val next = screen.observe()
                        val observeMillis = clock.uptimeMillis() - observeStartedAt
                        next.snapshot.packageName?.let { appsUsed.add(it) }

                        val rendered = next.rendered
                        val same = loopGuard.noteObservation(rendered, sig)

                        val text = if (same) UNCHANGED_NOTE else PromptBuilder.observationMessage(rendered)
                        return AfterAction(text, settleMillis, observeMillis)
                    }

                    fun metricsFor(after: AfterAction, actionMillis: Long) = StepMetrics(
                        modelMillis = modelMillis,
                        settleMillis = after.settleMillis,
                        observeMillis = after.observeMillis,
                        actionMillis = actionMillis,
                        inputTokens = response?.inputTokens ?: 0,
                        outputTokens = response?.outputTokens ?: 0,
                    )

                    when (val check = loopGuard.checkCall(action, sig)) {
                        LoopGuard.CallCheck.Allowed -> Unit
                        is LoopGuard.CallCheck.Refused -> {
                            markStep(steps, index, StepStatus.Failed, check.stepDetail)
                            recordAttempt(AttemptOutcome.Refused)
                            recordResult(check.toModel)
                            break
                        }
                        is LoopGuard.CallCheck.GiveUp -> {
                            markStep(steps, index, StepStatus.Failed, check.stepDetail)
                            recordAttempt(AttemptOutcome.Refused)
                            finish(
                                taskId, prompt, startedAt, stepCount, appsUsed, steps,
                                TaskState.FAILED, check.summary,
                            )
                            return
                        }
                    }

                    when (val outcome = validator.validate(action, step)) {
                        is ActionValidator.Outcome.Rejected -> {
                            markStep(steps, index, StepStatus.Failed, outcome.reason)
                            recordResult("That action was not allowed: ${outcome.reason}")
                            recordAttempt(AttemptOutcome.Failed, outcome.reason)
                            break
                        }
                        is ActionValidator.Outcome.RequiresConfirmation -> {
                            val allowed = awaitConfirmation(action, outcome.reason)
                            if (!allowed) {
                                markStep(steps, index, StepStatus.Failed, "You declined this action.")
                                recordResult(
                                    "The user declined this action. Do not repeat it; try another " +
                                        "route, or use ask_user if you need a decision.",
                                )
                                break
                            }
                    }
                    ActionValidator.Outcome.Allowed -> Unit
                }

                val tool = registry.find(action.action)
                if (tool == null) {
                    markStep(steps, index, StepStatus.Failed, "Unknown tool: ${action.action}")
                    recordResult("There is no tool called ${action.action}.")
                    recordAttempt(AttemptOutcome.Failed, "no such tool")
                    break
                }

                // Last gate before anything touches the device.
                if (AgentHalt.isStopped) throw kotlinx.coroutines.CancellationException("Stopped by the user")

                val actionStartedAt = clock.uptimeMillis()
                val result = tool.execute(action)
                val actionMillis = clock.uptimeMillis() - actionStartedAt
                stepCount++

                when (result) {
                    is ToolResult.Success -> {
                        markStep(steps, index, StepStatus.Done)
                        // Recorded on success only, and for replayed steps too: a routine that
                        // hands over to the model must keep the prefix that was replayed, and
                        // an action that failed must not be replayed next time.
                        actionsTaken += action
                        if (nonMutating) {
                            // The tool's own result already carries the screen, and nothing it
                            // did can have changed it.
                            recordAttempt(AttemptOutcome.Succeeded)
                            recordResult(result.observation)
                            setMetrics(
                                steps, index,
                                metricsFor(AfterAction("", 0, 0), actionMillis),
                            )
                        } else {
                            val after = observeAfterAction()
                            // Recorded after observing, because a call that "succeeded" and
                            // moved nothing is still a spent attempt - and "nothing changed" is
                            // the framing that stops the model proposing it again.
                            recordAttempt(
                                if (after.text == UNCHANGED_NOTE) {
                                    AttemptOutcome.NoChange
                                } else {
                                    AttemptOutcome.Succeeded
                                },
                            )
                            recordResult(after.text)
                            setMetrics(steps, index, metricsFor(after, actionMillis))
                        }

                        if (action.action == ToolNames.FINISH) {
                            // Only a model-driven run becomes a routine; replaying an existing
                            // one is not a new recording.
                            if (!replaying) lastRecordedActions = actionsTaken.toList()
                            finish(taskId, prompt, startedAt, stepCount, appsUsed, steps, TaskState.COMPLETED, result.observation)
                            return
                        }
                    }
                    is ToolResult.Failure -> {
                        markStep(steps, index, StepStatus.Failed, result.message)
                        recordAttempt(AttemptOutcome.Failed, result.message)
                        // A replay step that no longer matches means the screens have moved
                        // on. Hand the task to the model from here rather than failing.
                        if (replaying) replaying = false
                        // The screen is included even on failure. Withholding it left the
                        // model nothing to reason about, so it stalled and answered with
                        // prose instead of trying something else.
                        val after = observeAfterAction()
                        recordResult(result.message + "\n\n" + after.text)
                        setMetrics(steps, index, metricsFor(after, actionMillis))
                        // The rest of the batch was planned against a screen that no longer
                        // holds, so re-planning beats pushing on.
                        break
                    }
                    is ToolResult.NeedsConfirmation -> {
                        markStep(steps, index, StepStatus.Done)
                        if (action.action == ToolNames.ASK_USER) {
                            val answer = awaitQuestion(result.prompt)
                            recordResult("The user answered: $answer")
                        } else {
                            recordResult("Waiting for the user to confirm.")
                        }
                    }
                }
                }

                // A batch that stopped early leaves calls with no result, which providers reject.
                messages += SkippedCalls.resultsFor(messages, callRefs)
                trim(messages)
            }

            finish(taskId, prompt, startedAt, stepCount, appsUsed, steps, TaskState.FAILED,
                "I reached the ${TaskProgress.MAX_AGENT_STEPS}-step limit without finishing. You can refine the request and try again.")
        } catch (e: AIProviderException) {
            finish(taskId, prompt, startedAt, stepCount, appsUsed, steps, TaskState.FAILED, friendly(e))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            finish(taskId, prompt, startedAt, stepCount, appsUsed, steps, TaskState.FAILED, friendly(e))
        }
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Keeps the conversation bounded. We always keep the system prompt and the
     * original task (with its first screen), then the most recent exchanges. Without
     * this a 30-step task would carry 30 full screen dumps and blow the context window.
     */
    private fun trim(messages: MutableList<AgentMessage>) {
        val compacted = ContextCompactor.compact(messages)
        messages.clear()
        messages.addAll(compacted)
    }

    private suspend fun awaitResumeIfPaused() {
        if (_paused.value) pauseGate?.await()
    }

    private suspend fun awaitConfirmation(
        action: com.example.agent.domain.model.AgentAction,
        reason: String,
    ): Boolean {
        _progress.value = _progress.value.copy(
            state = TaskState.WAITING_FOR_CONFIRMATION,
            message = reason,
            awaitingKind = com.example.agent.domain.model.AwaitingKind.Confirmation,
        )
        val deferred = CompletableDeferred<Boolean>()
        confirmation = deferred
        return try {
            deferred.await()
        } finally {
            confirmation = null
            // Only restore the running state if the task is still running. Stopping while a
            // confirmation is on screen sets CANCELLED, and this ran on the way out and wrote
            // EXECUTING back over it - so a task the user had just stopped went on claiming to
            // be working, with nothing driving it.
            restoreRunningState()
        }
    }

    private suspend fun awaitQuestion(prompt: String): String {
        _progress.value = _progress.value.copy(
            state = TaskState.WAITING_FOR_CONFIRMATION,
            message = prompt,
            awaitingKind = com.example.agent.domain.model.AwaitingKind.Question,
        )
        val deferred = CompletableDeferred<String>()
        question = deferred
        return try {
            deferred.await()
        } finally {
            question = null
            // Same guard as awaitConfirmation: a cancelled task must not be flipped back to
            // running by the cleanup of the question it was waiting on.
            restoreRunningState()
        }
    }

    /**
     * Returns the task to its running state after a confirmation or a question.
     *
     * Does nothing once the task has finished, failed or been cancelled - writing EXECUTING
     * unconditionally is what made Stop while awaiting report a running task.
     */
    private fun restoreRunningState() {
        if (_progress.value.state.isTerminal) return
        _progress.value = _progress.value.copy(
            state = TaskState.EXECUTING,
            awaitingKind = AwaitingKind.None,
        )
    }

    private fun markStep(
        steps: MutableList<TaskStep>,
        index: Int,
        status: StepStatus,
        detail: String? = null,
    ) {
        if (index in steps.indices) steps[index] = steps[index].copy(status = status, detail = detail)
        _progress.value = _progress.value.copy(steps = steps.toList())
    }

    /** The action as the JSON the model produced, so its call can be replayed verbatim. */
    private fun encodeAction(action: com.example.agent.domain.model.AgentAction): String =
        runCatching { AgentJson.encodeToString(AgentAction.serializer(), action) }.getOrDefault("{}")

    private fun setMetrics(steps: MutableList<TaskStep>, index: Int, metrics: StepMetrics) {
        if (index in steps.indices) steps[index] = steps[index].copy(metrics = metrics)
        _progress.value = _progress.value.copy(steps = steps.toList())
    }

    private suspend fun finish(
        taskId: String,
        prompt: String,
        startedAt: Long,
        stepCount: Int,
        appsUsed: Set<String>,
        steps: List<TaskStep>,
        state: TaskState,
        message: String,
    ) {
        val finishedAt = clock.currentTimeMillis()
        _paused.value = false
        _progress.value = _progress.value.copy(
            state = state,
            message = message,
            error = if (state == TaskState.FAILED) message else null,
            steps = steps.toList(),
        )

        // Learn app shortcuts from this task, if the user allows remembering.
        journal.learnApps(appsUsed)

        // Every step already carries its own metrics, so the totals need no plumbing.
        val inputTokens = steps.sumOf { it.metrics?.inputTokens ?: 0 }
        val outputTokens = steps.sumOf { it.metrics?.outputTokens ?: 0 }
        val modelMillis = steps.sumOf { it.metrics?.modelMillis ?: 0 }

        journal.record(
            TaskRecord(
                id = taskId,
                prompt = prompt,
                state = state,
                createdAtMillis = startedAt,
                finishedAtMillis = finishedAt,
                durationMs = finishedAt - startedAt,
                stepCount = stepCount,
                appsUsed = appsUsed.toList(),
                summary = message,
                steps = steps.map {
                    RecordedStep(
                        label = it.label,
                        status = it.status.name,
                        detail = it.detail,
                        modelMillis = it.metrics?.modelMillis ?: 0,
                        settleMillis = it.metrics?.settleMillis ?: 0,
                        observeMillis = it.metrics?.observeMillis ?: 0,
                        actionMillis = it.metrics?.actionMillis ?: 0,
                        inputTokens = it.metrics?.inputTokens ?: 0,
                        outputTokens = it.metrics?.outputTokens ?: 0,
                    )
                },
                inputTokens = inputTokens,
                outputTokens = outputTokens,
                modelMillis = modelMillis,
            )
        )
    }

    private fun friendly(e: Throwable): String = when (e) {
        is AIProviderException -> {
            // Providers often say exactly what is wrong ("model X has been
            // deprecated", "invalid API key"). Prefer that over canned text.
            val detail = e.message?.takeIf { it.isNotBlank() }
            when (e.kind) {
                AIProviderException.Kind.NoKey -> "No API key is set. Add one in Settings to run tasks."
                AIProviderException.Kind.Unauthorized ->
                    detail ?: "The API key was rejected. Check it in Settings."
                AIProviderException.Kind.RateLimited -> "The provider is rate-limiting requests. Try again shortly."
                AIProviderException.Kind.Network -> "I couldn't reach the AI provider. Check your connection."
                AIProviderException.Kind.Server -> detail ?: "The AI provider had a server error. Try again."
                // Providers (and our own parser) usually say exactly what was wrong.
                // Swallowing that is what made this failure undiagnosable.
                AIProviderException.Kind.Parse -> detail?.take(300)
                    ?: "The model returned something I couldn't understand. Try rephrasing."
                AIProviderException.Kind.Unknown -> detail ?: "Something went wrong talking to the provider."
            }
        }
        else -> "Something went wrong: ${e.message ?: "unknown error"}"
    }

    private fun describe(action: com.example.agent.domain.model.AgentAction): String =
        when (action.action) {
            ToolNames.OPEN_APP -> "Opening ${action.packageName ?: "app"}"
            ToolNames.CLOSE_APP -> "Leaving ${action.packageName ?: "the app"}"
            ToolNames.READ_SCREEN -> "Reading the screen"
            ToolNames.FIND_TEXT -> "Looking for " + quoted(action.text)
            ToolNames.CLICK -> "Tapping " + targetLabel(action.target)
            ToolNames.LONG_CLICK -> "Long-pressing " + targetLabel(action.target)
            ToolNames.TYPE_TEXT -> "Typing " + quoted(action.text?.take(40))
            ToolNames.SCROLL -> "Scrolling ${action.direction ?: "forward"}"
            ToolNames.SWIPE -> "Swiping ${action.direction ?: "up"}"
            ToolNames.PRESS_BACK -> "Going back"
            ToolNames.PRESS_HOME -> "Going home"
            ToolNames.WAIT -> "Waiting for the screen"
            ToolNames.LAUNCH_URL -> "Opening " + quoted(action.url?.take(60))
            ToolNames.GET_NOTIFICATIONS -> "Reading notifications"
            ToolNames.ASK_USER -> "Asking you a question"
            ToolNames.FINISH -> "Finishing up"
            else -> action.action.replace('_', ' ')
        }

    /** Names the target, so a step reads "Tapping "Search"" rather than "Tapping an element". */
    private fun targetLabel(target: ActionTarget?): String = when {
        target == null -> "an element"
        !target.text.isNullOrBlank() -> quoted(target.text)
        !target.contentDescription.isNullOrBlank() -> quoted(target.contentDescription)
        !target.key.isNullOrBlank() -> "element ${target.key}"
        target.ref != null -> "element #${target.ref}"
        !target.viewId.isNullOrBlank() -> "the ${target.viewId.substringAfterLast('/')} field"
        !target.className.isNullOrBlank() -> "the ${target.className.substringAfterLast('.')} element"
        else -> "an element"
    }

    private fun quoted(value: String?): String =
        if (value == null) "an element" else "\"" + value + "\""

    private companion object {
        const val STOPPED_BY_USER = "Stopped by you."

        /**
         * How many times the model is asked again after replying with prose instead of a call.
         *
         * Two was too few. A model that is momentarily chatty - often right after a failure it
         * could not resolve - would spend both nudges and end the task, which is what two of
         * the reported screenshots show. Four costs at most two extra calls and lets it
         * recover.
         */
        const val MAX_TEXT_NUDGES = 4

        /**
         * Tools that cannot change the screen.
         *
         * Waiting for these to "settle" is waiting for something that cannot happen, and then
         * re-reading the screen they have just read.
         */
        private val NON_MUTATING_TOOLS = setOf(
            ToolNames.READ_SCREEN,
            ToolNames.FIND_TEXT,
            ToolNames.GET_NOTIFICATIONS,
        )

        /**
         * Sent instead of a full listing when an action left the screen exactly as it was.
         * The listing is already in the conversation; repeating it costs thousands of tokens
         * and says nothing new.
         */
        const val UNCHANGED_NOTE =
            // No "Observation:" prefix: ContextCompactor counts that as a screen listing and
            // would evict a real one to make room for this note.
            "The screen is unchanged, so the listing above is still current. " +
                "Repeating that action will not help - do something different, or call " +
                "finish if the task is already done."


    }
}
