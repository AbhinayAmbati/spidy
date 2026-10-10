package com.example.agent.core.agent

import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import com.example.agent.automation.AndroidAutomationManager
import com.example.agent.automation.accessibility.AccessibilityBridge
import com.example.agent.automation.accessibility.UserTouchGate
import com.example.agent.automation.observer.ScreenObserver
import com.example.agent.automation.tools.ToolRegistry
import com.example.agent.core.ai.AIProvider
import com.example.agent.core.ai.AgentJson
import com.example.agent.core.control.AgentHalt
import com.example.agent.core.ai.AIProviderException
import com.example.agent.core.ai.AIProviderFactory
import com.example.agent.data.preferences.SettingsRepository
import com.example.agent.data.repository.MemoryRepository
import com.example.agent.data.repository.TaskHistoryRepository
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
    private val providerFactory: AIProviderFactory,
    private val settings: SettingsRepository,
    private val registry: ToolRegistry,
    private val validator: ActionValidator,
    private val observer: ScreenObserver,
    private val automation: AndroidAutomationManager,
    private val history: TaskHistoryRepository,
    private val memory: MemoryRepository,
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

    @Volatile
    private var touchToStopEnabled: Boolean = true

    private var taskStartedAtMillis: Long = 0L

    /** Actions of the most recent completed task, so it can be saved as a routine. */
    @Volatile
    private var lastRecordedActions: List<AgentAction> = emptyList()

    init {
        // Kept hot so the touch handler never has to hit DataStore on a touch.
        scope.launch {
            settings.touchToStop.collect { touchToStopEnabled = it }
        }

        // Touch-to-stop. If the user puts a finger on the screen while the agent is
        // working, they have taken over, so the task stops.
        //
        // Every reason *not* to stop lives in UserTouchGate: disabled by the user, the
        // agent waiting on an answer, and the opening seconds of a task during which the
        // tap that started it is still being delivered.
        scope.launch {
            AccessibilityBridge.userTouches.collect { eventTimeMillis ->
                if (!isRunning) return@collect
                val stop = UserTouchGate.shouldStopForUserTouch(
                    // The platform's own constant, not a mirrored number.
                    eventType = AccessibilityEvent.TYPE_TOUCH_INTERACTION_START,
                    // When the touch happened, not when the event reached us.
                    eventTimeMillis = eventTimeMillis,
                    suppressUntilMillis = AccessibilityBridge.suppressUntilMillis(),
                    taskStartedAtMillis = taskStartedAtMillis,
                    enabled = touchToStopEnabled,
                    awaitingUser = _progress.value.awaitingKind != AwaitingKind.None,
                )
                if (stop) stop(STOPPED_BY_TOUCH)
            }
        }
    }

    fun start(prompt: String) {
        if (isRunning) return
        taskStartedAtMillis = SystemClock.uptimeMillis()
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
        taskStartedAtMillis = SystemClock.uptimeMillis()
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
        _progress.value = _progress.value.copy(
            state = TaskState.CANCELLED,
            message = reason,
            awaitingKind = AwaitingKind.None,
        )
    }

    /** Pauses between steps. Safe to call when nothing is running. */
    fun pause() {
        if (!isRunning || _paused.value) return
        _paused.value = true
        pauseGate = CompletableDeferred()
        _progress.value = _progress.value.copy(state = TaskState.PAUSED, message = "Paused.")
    }

    fun resume() {
        if (!_paused.value) return
        _paused.value = false
        pauseGate?.complete(Unit)
        pauseGate = null
        _progress.value = _progress.value.copy(state = TaskState.EXECUTING, message = "Continuing…")
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
        val startedAt = System.currentTimeMillis()
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

        if (!automation.isAccessibilityReady()) {
            finish(taskId, prompt, startedAt, stepCount, appsUsed, steps, TaskState.FAILED,
                "Accessibility access is off, so I can't see or control the screen. Enable it in Settings.")
            return
        }

        val knownApps = runCatching { memory.snapshot() }.getOrDefault(emptyList())

        // Created on demand: a replay needs no model at all, and therefore no key.
        var provider: AIProvider? = null
        suspend fun ensureProvider(): AIProvider {
            provider?.let { return it }
            return providerFactory.create(settings.currentProviderConfig()).also { provider = it }
        }

        // Replay until a step does not match, then hand over to the model from that point.
        var replayIndex = 0
        var replaying = routine != null

        var nudges = 0
        // How many times each exact action has run this task. See NEVER_REPEATED_TOOLS.
        val signatureRuns = mutableMapOf<String, Int>()
        // Per-tool counts, which ignore the arguments. See ActionSignature.TOOL_CAPS.
        val toolRuns = mutableMapOf<String, Int>()
        // Loop protection. The most common failure is running the same action over and
        // over — opening an app already on screen, or retyping text that is already there.
        // The prompt forbids it, but that cannot be relied on.
        var lastObservation: String? = null
        val sigToObservation = mutableMapOf<String, String>()
        val repeatRefusals = mutableMapOf<String, Int>()
        // Broader no-progress signals. The exact-repeat guard only catches identical
        // arguments; these catch an agent that keeps doing *something* without the screen
        // ever changing, or that hammers one tool.
        var consecutiveUnchanged = 0
        var consecutiveSameTool = 0
        var lastTool: String? = null
        val messages = mutableListOf<AgentMessage>()
        var conversationStarted = false

        // A replay begins with no conversation at all; this builds one only when the model is
        // actually needed, with the screen as it is then rather than as it was.
        suspend fun beginConversation() {
            if (conversationStarted) return
            conversationStarted = true
            messages += AgentMessage(MessageRole.System, PromptBuilder.systemPrompt(knownApps))
            val opening = observer.observe()
            opening.snapshot.packageName?.let { appsUsed.add(it) }
            messages += AgentMessage(
                MessageRole.User,
                PromptBuilder.initialUserMessage(prompt, opening.rendered),
            )
        }

        try {
            for (step in 0 until TaskProgress.MAX_AGENT_STEPS) {
                if (AgentHalt.isStopped) throw kotlinx.coroutines.CancellationException("Stopped by the user")

                if (consecutiveUnchanged >= MAX_STALLED_STEPS) {
                    finish(
                        taskId, prompt, startedAt, stepCount, appsUsed, steps, TaskState.FAILED,
                        "Nothing on screen has changed for the last $consecutiveUnchanged steps, " +
                            "so I stopped rather than keep repeating myself. Tell me the exact " +
                            "element to use, or rephrase the task.",
                    )
                    return
                }

                if (consecutiveSameTool >= MAX_SAME_TOOL_STREAK) {
                    finish(
                        taskId, prompt, startedAt, stepCount, appsUsed, steps, TaskState.FAILED,
                        "I used ${lastTool ?: "the same tool"} $consecutiveSameTool times in a row " +
                            "without finishing, so I stopped. That usually means the element I " +
                            "need is not on screen - name it exactly, or rephrase the task.",
                    )
                    return
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
                    val modelStartedAt = SystemClock.uptimeMillis()
                    beginConversation()
                    val generated = try {
                        ensureProvider().generateResponse(messages, registry.specs())
                    } catch (e: Exception) {
                        finish(taskId, prompt, startedAt, stepCount, appsUsed, steps, TaskState.FAILED, friendly(e))
                        return
                    }
                    modelMillis = SystemClock.uptimeMillis() - modelStartedAt
                    response = generated
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
                        val screen = lastObservation?.takeIf { it.isNotBlank() }
                        messages += AgentMessage(
                            MessageRole.User,
                            PromptBuilder.observationMessage(
                                buildString {
                                    appendLine(
                                        "You replied with text instead of calling a tool. " +
                                            "Reply again, and this time call a tool.",
                                    )
                                    appendLine("Use finish when the task is complete.")
                                    if (screen != null) {
                                        appendLine()
                                        appendLine("The screen right now:")
                                        append(screen)
                                    }
                                },
                            ),
                        )
                        trim(messages)
                        continue
                    }

                    finish(
                        taskId, prompt, startedAt, stepCount, appsUsed, steps, TaskState.FAILED,
                        "The model kept replying with text instead of acting. That usually means " +
                            "it doesn't support tool calling — try a different model in Settings.",
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

                    // Streak of one tool in a row. Repeatable tools (scroll, wait, back) are
                    // exempt, because using them several times in a row is legitimate.
                    if (action.action in ActionSignature.REPEATABLE_TOOLS) {
                        lastTool = null
                        consecutiveSameTool = 0
                    } else if (action.action == lastTool) {
                        consecutiveSameTool++
                    } else {
                        lastTool = action.action
                        consecutiveSameTool = 1
                    }

                    val label = describe(action)
                    val index = steps.size
                    steps += TaskStep(index, label, StepStatus.Running)
                    _progress.value = _progress.value.copy(
                        state = TaskState.EXECUTING,
                        steps = steps.toList(),
                        currentStep = step,
                        message = label,
                    )

                    fun recordResult(text: String) {
                        messages += AgentMessage(
                            role = MessageRole.Tool,
                            content = text,
                            toolCallId = callRef.id,
                            name = callRef.name,
                        )
                        trim(messages)
                    }

                    // Declared before the local functions below: a local function can only see
                    // variables declared above it, and observeAfterAction needs this.
                    val sig = ActionSignature.of(action)

                    data class AfterAction(val text: String, val settleMillis: Long, val observeMillis: Long)

                    /**
                     * The one place that decides what the model is told about the screen after an
                     * action.
                     *
                     * When no window-change event arrived, the screen is byte-for-byte what the
                     * model already has, so both the traversal and the listing are skipped — the
                     * single biggest per-step saving, and it applies to every no-op action.
                     */
                    /**
                     * Tools that cannot change the screen, so there is nothing to wait for.
                     *
                     * A real 21-second run spent 8.8 s in settle - 41% of the wall clock, more
                     * than the model - and the read-only steps paid the full quiet period for a
                     * screen they had just read. The tool already returns the observation, so
                     * settling and re-reading it is pure cost.
                     */
                    val nonMutating = action.action in NON_MUTATING_TOOLS

                    suspend fun observeAfterAction(): AfterAction {
                        val settleStartedAt = SystemClock.uptimeMillis()
                        val changed = automation.awaitUiSettled()
                        val settleMillis = SystemClock.uptimeMillis() - settleStartedAt

                        if (!changed) {
                            consecutiveUnchanged++
                            return AfterAction(UNCHANGED_NOTE, settleMillis, 0)
                        }

                        val observeStartedAt = SystemClock.uptimeMillis()
                        val next = observer.observe()
                        val observeMillis = SystemClock.uptimeMillis() - observeStartedAt
                        next.snapshot.packageName?.let { appsUsed.add(it) }

                        val rendered = next.rendered
                        // A change event can still render identically (an animation, say).
                        val same = rendered == lastObservation
                        consecutiveUnchanged = if (same) consecutiveUnchanged + 1 else 0
                        lastObservation = rendered
                        sig?.let { sigToObservation[it] = rendered }

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

                    // ---- identical-repeat guard ---------------------------------------
                    // An action that has already run with exactly these arguments and got
                    // nowhere must not run again. This is checked by signature rather than by
                    // whether the screen changed, because the screen almost always *does*
                    // change - and two runs that accomplished the same nothing still look
                    // different to a snapshot.
                    val identicalCap = when {
                        sig == null -> null
                        action.action in ActionSignature.NEVER_REPEATED_TOOLS -> MAX_IDENTICAL_RUNS
                        else -> MAX_IDENTICAL_RUNS_GENERIC
                    }
                    if (sig != null && identicalCap != null) {
                        val runs = (signatureRuns[sig] ?: 0) + 1
                        signatureRuns[sig] = runs
                        if (runs > identicalCap) {
                            markStep(steps, index, StepStatus.Failed, "Already done — nothing gained")
                            recordResult(
                                "\"${action.action}\" with exactly these arguments has already " +
                                    "run and got you nowhere. Running it again will not help. " +
                                    "Read the screen and pick a different element, or call " +
                                    "finish if the task is already done.",
                            )
                            break
                        }
                    }

                    // The same tool, over and over, with the arguments changed each time. A
                    // search that has already returned results is the case that matters: the
                    // model rephrases the query and searches again, and the page it gets back
                    // is the same page it already had.
                    val toolCap = ActionSignature.TOOL_CAPS[action.action]
                    if (toolCap != null) {
                        val runs = (toolRuns[action.action] ?: 0) + 1
                        toolRuns[action.action] = runs
                        if (runs > toolCap) {
                            markStep(steps, index, StepStatus.Failed, "Stuck repeating ${action.action}")
                            recordResult(
                                "You have now called ${action.action} $runs times in this task " +
                                    "and the screen is no closer to the goal. Do NOT call it " +
                                    "again. If search results are already showing, tap the one " +
                                    "you want, or call finish if what the task asked for is " +
                                    "already on screen.",
                            )
                            break
                        }
                    }

                    // ---- repeat guard -------------------------------------------------
                    // Refuse to re-run an action whose arguments are identical to one that
                    // already ran and left the screen exactly as it is. If the model keeps
                    // insisting, stop rather than spin.
                    val previousObservation = sig?.let { sigToObservation[it] }
                    if (sig != null && previousObservation != null && previousObservation == lastObservation) {
                        val refusals = (repeatRefusals[sig] ?: 0) + 1
                        repeatRefusals[sig] = refusals

                        if (refusals >= MAX_REPEAT_REFUSALS) {
                            markStep(steps, index, StepStatus.Failed, "Repeated with no progress")
                            finish(
                                taskId, prompt, startedAt, stepCount, appsUsed, steps, TaskState.FAILED,
                                "I kept repeating the same action and the screen never changed, so I " +
                                    "stopped instead of looping. Name the exact element you want tapped, " +
                                    "or try rephrasing.",
                            )
                            return
                        }

                        markStep(steps, index, StepStatus.Failed, "Already tried — nothing changed")
                        recordResult(
                            "You already ran this exact action and the screen has NOT changed. " +
                                "Running it again will not help. Do something different now: " +
                                "read_screen, scroll to reveal the element, choose a different " +
                                "element, or call finish if the task is already done.",
                        )
                        break
                    }

                    when (val outcome = validator.validate(action, step)) {
                        is ActionValidator.Outcome.Rejected -> {
                            markStep(steps, index, StepStatus.Failed, outcome.reason)
                            recordResult("That action was not allowed: ${outcome.reason}")
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
                    break
                }

                // Last gate before anything touches the device.
                if (AgentHalt.isStopped) throw kotlinx.coroutines.CancellationException("Stopped by the user")

                val actionStartedAt = SystemClock.uptimeMillis()
                if (!replaying) actionsTaken += action
                val result = tool.execute(action)
                val actionMillis = SystemClock.uptimeMillis() - actionStartedAt
                stepCount++

                when (result) {
                    is ToolResult.Success -> {
                        markStep(steps, index, StepStatus.Done)
                        if (nonMutating) {
                            // The tool's own result already carries the screen, and nothing it
                            // did can have changed it.
                            recordResult(result.observation)
                            setMetrics(
                                steps, index,
                                metricsFor(AfterAction("", 0, 0), actionMillis),
                            )
                        } else {
                            val after = observeAfterAction()
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
            _progress.value = _progress.value.copy(
                state = TaskState.EXECUTING,
                awaitingKind = com.example.agent.domain.model.AwaitingKind.None,
            )
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
            _progress.value = _progress.value.copy(
                state = TaskState.EXECUTING,
                awaitingKind = com.example.agent.domain.model.AwaitingKind.None,
            )
        }
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
        val finishedAt = System.currentTimeMillis()
        _paused.value = false
        _progress.value = _progress.value.copy(
            state = state,
            message = message,
            error = if (state == TaskState.FAILED) message else null,
            steps = steps.toList(),
        )

        // Learn app shortcuts from this task, if the user allows remembering.
        runCatching {
            memory.rememberApps(
                appsUsed.mapNotNull { pkg -> automation.appLabel(pkg)?.let { it to pkg } }
            )
        }

        // Every step already carries its own metrics, so the totals need no plumbing.
        val inputTokens = steps.sumOf { it.metrics?.inputTokens ?: 0 }
        val outputTokens = steps.sumOf { it.metrics?.outputTokens ?: 0 }
        val modelMillis = steps.sumOf { it.metrics?.modelMillis ?: 0 }

        history.record(
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

        /** Shown when touch-to-stop fires. */
        const val STOPPED_BY_TOUCH = "Stopped: you touched the screen."

        /** How many times we push the model back towards calling a tool before giving up. */
        /**
         * How many times the model is asked again after replying with prose instead of a call.
         *
         * Two was too few. A model that is momentarily chatty - often right after a failure it
         * could not resolve - would spend both nudges and end the task, which is what two of
         * the reported screenshots show. Four costs at most two extra calls and lets it
         * recover.
         */
        const val MAX_TEXT_NUDGES = 4

        /** How many times we refuse a repeat before ending the task instead of looping. */
        const val MAX_REPEAT_REFUSALS = 3

        /**
         * How many times one exact action may run in a single task before it is refused.
         *
         * Two is enough for anything real: a third identical call has never been the thing
         * that unblocked a task, and the screenshots that prompted this show five and six.
         */
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

        const val MAX_IDENTICAL_RUNS = 2

        /**
         * The same cap for everything else that is not legitimately repeatable.
         *
         * Higher than [MAX_IDENTICAL_RUNS], because tapping the same "Next" button through a
         * paginated list is a real task. Four taps is the point at which it has stopped being
         * paging and started being a loop.
         */
        const val MAX_IDENTICAL_RUNS_GENERIC = 4

        /** Consecutive steps with an unchanged screen before we stop for lack of progress. */
        const val MAX_STALLED_STEPS = 4

        /** Consecutive uses of one tool before we stop - the agent is hammering it. */
        const val MAX_SAME_TOOL_STREAK = 4

        /**
         * Sent instead of a full listing when an action left the screen exactly as it was.
         * The listing is already in the conversation; repeating it costs thousands of tokens
         * and says nothing new.
         */
        const val UNCHANGED_NOTE =
            "Observation: the screen is unchanged, so the listing above is still current. " +
                "Repeating that action will not help - do something different, or call " +
                "finish if the task is already done."


    }
}
