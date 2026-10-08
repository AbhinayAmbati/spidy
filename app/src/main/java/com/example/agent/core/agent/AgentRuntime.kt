package com.example.agent.core.agent

import com.example.agent.automation.AndroidAutomationManager
import com.example.agent.automation.observer.ScreenObserver
import com.example.agent.automation.tools.ToolRegistry
import com.example.agent.core.ai.AIProvider
import com.example.agent.core.ai.AIProviderException
import com.example.agent.core.ai.AIProviderFactory
import com.example.agent.data.preferences.SettingsRepository
import com.example.agent.data.repository.MemoryRepository
import com.example.agent.data.repository.TaskHistoryRepository
import com.example.agent.domain.model.AgentMessage
import com.example.agent.domain.model.AgentResponse
import com.example.agent.domain.model.MessageRole
import com.example.agent.domain.model.RecordedStep
import com.example.agent.domain.model.StepStatus
import com.example.agent.domain.model.TaskProgress
import com.example.agent.domain.model.TaskRecord
import com.example.agent.domain.model.TaskState
import com.example.agent.domain.model.TaskStep
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

    fun start(prompt: String) {
        if (isRunning) return
        job = scope.launch { runTask(prompt) }
    }

    fun stop() {
        job?.cancel()
        job = null
        confirmation?.cancel()
        question?.cancel()
        pauseGate?.cancel()
        pauseGate = null
        _paused.value = false
        _progress.value = _progress.value.copy(
            state = TaskState.CANCELLED,
            message = "Stopped by you.",
            awaitingKind = com.example.agent.domain.model.AwaitingKind.None,
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

    private suspend fun runTask(prompt: String) {
        val startedAt = System.currentTimeMillis()
        val taskId = UUID.randomUUID().toString()
        val appsUsed = linkedSetOf<String>()
        val steps = mutableListOf<TaskStep>()
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

        val provider: AIProvider
        val knownApps = runCatching { memory.snapshot() }.getOrDefault(emptyList())
        try {
            val config = settings.currentProviderConfig()
            provider = providerFactory.create(config)
        } catch (e: Exception) {
            finish(taskId, prompt, startedAt, stepCount, appsUsed, steps, TaskState.FAILED, friendly(e))
            return
        }

        val messages = mutableListOf<AgentMessage>()
        messages += AgentMessage(MessageRole.System, PromptBuilder.systemPrompt(knownApps))

        val observation = observer.observe()
        observation.snapshot.packageName?.let { appsUsed.add(it) }
        messages += AgentMessage(
            MessageRole.User,
            PromptBuilder.initialUserMessage(prompt, observation.rendered),
        )

        try {
            for (step in 0 until TaskProgress.MAX_AGENT_STEPS) {
                coroutineContext.ensureActive()
                awaitResumeIfPaused()
                coroutineContext.ensureActive()

                _progress.value = _progress.value.copy(
                    state = TaskState.PLANNING,
                    currentStep = step,
                    message = "Thinking…",
                )

                val response: AgentResponse = provider.generateResponse(messages, registry.specs())
                val action = response.action

                if (action == null) {
                    val summary = response.message?.takeIf { it.isNotBlank() } ?: "Done."
                    messages += AgentMessage(MessageRole.Assistant, summary)
                    finish(taskId, prompt, startedAt, stepCount, appsUsed, steps, TaskState.COMPLETED, summary)
                    return
                }

                val label = describe(action.action, action.text, action.packageName, action.target?.text)
                val index = steps.size
                steps += TaskStep(index, label, StepStatus.Running)
                _progress.value = _progress.value.copy(
                    state = TaskState.EXECUTING,
                    steps = steps.toList(),
                    currentStep = step,
                    message = label,
                )

                when (val outcome = validator.validate(action, step)) {
                    is ActionValidator.Outcome.Rejected -> {
                        markStep(steps, index, StepStatus.Failed)
                        messages += AgentMessage(MessageRole.Assistant, "action: ${action.action}")
                        messages += AgentMessage(
                            MessageRole.User,
                            PromptBuilder.observationMessage("That action was not allowed: ${outcome.reason}"),
                        )
                        trim(messages)
                        continue
                    }
                    is ActionValidator.Outcome.RequiresConfirmation -> {
                        val allowed = awaitConfirmation(action, outcome.reason)
                        if (!allowed) {
                            markStep(steps, index, StepStatus.Failed)
                            messages += AgentMessage(MessageRole.User,
                                PromptBuilder.observationMessage("The user declined this action."))
                            trim(messages)
                            continue
                        }
                    }
                    ActionValidator.Outcome.Allowed -> Unit
                }

                val tool = registry.find(action.action)
                if (tool == null) {
                    markStep(steps, index, StepStatus.Failed)
                    continue
                }

                val result = tool.execute(action)
                stepCount++

                when (result) {
                    is ToolResult.Success -> {
                        markStep(steps, index, StepStatus.Done)
                        messages += AgentMessage(MessageRole.Assistant, "action: ${action.action}")
                        automation.awaitUiSettled()
                        val next = observer.observe()
                        next.snapshot.packageName?.let { appsUsed.add(it) }
                        messages += AgentMessage(MessageRole.User, PromptBuilder.observationMessage(next.rendered))
                        trim(messages)

                        if (action.action == ToolNames.FINISH) {
                            finish(taskId, prompt, startedAt, stepCount, appsUsed, steps, TaskState.COMPLETED, result.observation)
                            return
                        }
                    }
                    is ToolResult.Failure -> {
                        markStep(steps, index, StepStatus.Failed)
                        messages += AgentMessage(MessageRole.Assistant, "action: ${action.action}")
                        messages += AgentMessage(MessageRole.User, PromptBuilder.observationMessage(result.message))
                        trim(messages)
                    }
                    is ToolResult.NeedsConfirmation -> {
                        markStep(steps, index, StepStatus.Done)
                        if (action.action == ToolNames.ASK_USER) {
                            val answer = awaitQuestion(result.prompt)
                            messages += AgentMessage(MessageRole.User,
                                PromptBuilder.observationMessage("The user answered: $answer"))
                            trim(messages)
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
    private fun trim(messages: MutableList<AgentMessage>, max: Int = MAX_CONTEXT_MESSAGES) {
        if (messages.size <= max) return
        val head = messages.take(2)
        val tail = messages.takeLast(max - 2)
        messages.clear()
        messages.addAll(head)
        messages.addAll(tail)
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

    private fun markStep(steps: MutableList<TaskStep>, index: Int, status: StepStatus) {
        if (index in steps.indices) steps[index] = steps[index].copy(status = status)
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
                steps = steps.map { RecordedStep(it.label, it.status.name) },
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
                AIProviderException.Kind.Parse -> "The model returned something I couldn't understand. Try rephrasing."
                AIProviderException.Kind.Unknown -> detail ?: "Something went wrong talking to the provider."
            }
        }
        else -> "Something went wrong: ${e.message ?: "unknown error"}"
    }

    private fun describe(
        action: String,
        text: String?,
        packageName: String?,
        targetText: String?,
    ): String = when (action) {
        ToolNames.OPEN_APP -> "Opening ${packageName ?: "app"}"
        ToolNames.READ_SCREEN -> "Reading the screen"
        ToolNames.FIND_TEXT -> "Looking for " + quoted(text)
        ToolNames.CLICK -> "Tapping " + quoted(targetText)
        ToolNames.LONG_CLICK -> "Long-pressing " + quoted(targetText)
        ToolNames.TYPE_TEXT -> "Typing " + quoted(text?.take(40))
        ToolNames.SCROLL -> "Scrolling"
        ToolNames.SWIPE -> "Swiping"
        ToolNames.PRESS_BACK -> "Going back"
        ToolNames.PRESS_HOME -> "Going home"
        ToolNames.WAIT -> "Waiting for the screen"
        ToolNames.LAUNCH_URL -> "Opening a link"
        ToolNames.GET_NOTIFICATIONS -> "Reading notifications"
        ToolNames.ASK_USER -> "Asking you a question"
        ToolNames.FINISH -> "Finishing up"
        else -> action.replace('_', ' ')
    }

    private fun quoted(value: String?): String =
        if (value == null) "an element" else "\"" + value + "\""

    private companion object {
        /** System + task + this many trailing messages are kept. */
        const val MAX_CONTEXT_MESSAGES = 24
    }
}
