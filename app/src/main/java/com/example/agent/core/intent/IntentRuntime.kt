package com.example.agent.core.intent

import com.example.agent.core.agent.AgentClock
import com.example.agent.core.agent.TaskJournal
import com.example.agent.core.control.AgentHalt
import com.example.agent.domain.model.AwaitingKind
import com.example.agent.domain.model.RecordedStep
import com.example.agent.domain.model.StepStatus
import com.example.agent.domain.model.TaskProgress
import com.example.agent.domain.model.TaskRecord
import com.example.agent.domain.model.TaskState
import com.example.agent.domain.model.TaskStep
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/** A runtime permission the task screen is asked to request on the runtime's behalf. */
data class PermissionRequest(val permission: String) {
    companion object {
        const val READ_CONTACTS = "android.permission.READ_CONTACTS"
    }
}

/**
 * The default way a sentence is run:
 *
 *   IntentRouter -> IntentGuard -> ConfirmGate -> ActionExecutor
 *
 * The model only names intents; every value it produced is checked by [IntentGuard] against the
 * real device before anything happens, and send and call are confirmed first. Several intents
 * in one sentence run in order and stop at the first failure or decline.
 *
 * It reports through the same [TaskProgress] / [TaskState] / [TaskJournal] the old loop used,
 * so Home, History and Routines need no changes.
 *
 * @param confirmGate who asks the user; when null the runtime asks through [progress] (the task
 *   screen shows its confirmation sheet) and waits for [resolveConfirmation].
 */
class IntentRuntime(
    private val router: IntentRouter,
    private val guard: IntentGuard,
    private val executor: ActionExecutor,
    private val journal: TaskJournal,
    private val clock: AgentClock,
    private val scope: CoroutineScope,
    confirmGate: ConfirmGate? = null,
) {

    private val _progress = MutableStateFlow(idleProgress())
    val progress: StateFlow<TaskProgress> = _progress.asStateFlow()

    private val _permissionRequest = MutableStateFlow<PermissionRequest?>(null)

    /** Set while the runtime waits for the user to answer a permission prompt. */
    val permissionRequest: StateFlow<PermissionRequest?> = _permissionRequest.asStateFlow()

    private var job: Job? = null
    private var confirmation: CompletableDeferred<Boolean>? = null
    private var permission: CompletableDeferred<Boolean>? = null
    private val gate: ConfirmGate = confirmGate ?: ConfirmGate { summary -> askThroughProgress(summary) }

    val isRunning: Boolean get() = job?.isActive == true

    var startedAtMillis: Long = 0L
        private set

    fun start(sentence: String) {
        if (isRunning) return
        startedAtMillis = clock.uptimeMillis()
        AgentHalt.clear()
        job = scope.launch { runTask(sentence) }
    }

    /** Stops immediately: the halt gate first, so nothing already queued can begin. */
    fun stop(reason: String = STOPPED_BY_USER) {
        AgentHalt.request()
        job?.cancel()
        job = null
        confirmation?.cancel()
        permission?.cancel()
        _permissionRequest.value = null
        _progress.update {
            it.copy(state = TaskState.CANCELLED, message = reason, awaitingKind = AwaitingKind.None)
        }
    }

    /** Called by the confirmation sheet. */
    fun resolveConfirmation(allow: Boolean) {
        confirmation?.complete(allow)
    }

    /** Called by the task screen once the user answered the permission prompt. */
    fun resolvePermission(granted: Boolean) {
        permission?.complete(granted)
    }

    // ------------------------------------------------------------------ the run

    private suspend fun runTask(sentence: String) {
        val startedAt = clock.currentTimeMillis()
        val taskId = UUID.randomUUID().toString()
        val steps = mutableListOf<TaskStep>()

        _progress.value = TaskProgress(
            taskId = taskId,
            prompt = sentence,
            state = TaskState.PLANNING,
            steps = emptyList(),
            currentStep = 0,
            totalSteps = 1,
            message = "Understanding your request…",
        )

        try {
            val routed = router.route(sentence)
            if (AgentHalt.isStopped) throw CancellationException("Stopped by the user")

            if (routed.intents.isEmpty()) {
                finish(taskId, sentence, startedAt, steps, TaskState.FAILED, CANNOT_DO_THAT)
                return
            }

            val intents = routed.intents
            for ((index, requested) in intents.withIndex()) {
                if (AgentHalt.isStopped) throw CancellationException("Stopped by the user")

                // The routed intent is shown first, as the row the result is written onto.
                steps += TaskStep(index, describe(requested), StepStatus.Running)
                _progress.value = _progress.value.copy(
                    state = TaskState.EXECUTING,
                    steps = steps.toList(),
                    currentStep = index,
                    totalSteps = intents.size,
                    message = describe(requested),
                )

                var verdict = guard.check(requested)
                if (verdict is GuardVerdict.Reject && needsContactsPermission(verdict.reason)) {
                    // Asked once: if the user refuses, the guard's own reason stands.
                    if (askPermission(PermissionRequest.READ_CONTACTS)) {
                        verdict = guard.check(requested)
                    }
                }

                when (verdict) {
                    is GuardVerdict.Reject -> {
                        mark(steps, index, StepStatus.Failed, verdict.reason)
                        finish(taskId, sentence, startedAt, steps, TaskState.FAILED, verdict.reason)
                        return
                    }
                    is GuardVerdict.Allow -> {
                        steps[index] = steps[index].copy(label = describe(verdict.intent))
                        if (verdict.needsConfirmation) {
                            val allowed = gate.confirm(verdict.summary)
                            restoreRunningState()
                            if (!allowed) {
                                mark(steps, index, StepStatus.Failed, DECLINED)
                                finish(taskId, sentence, startedAt, steps, TaskState.CANCELLED, DECLINED)
                                return
                            }
                        }

                        // Last gate before anything touches the device.
                        if (AgentHalt.isStopped) throw CancellationException("Stopped by the user")

                        when (val result = executor.execute(verdict.intent)) {
                            is ActionResult.Done -> mark(steps, index, StepStatus.Done, result.message)
                            is ActionResult.Failed -> {
                                mark(steps, index, StepStatus.Failed, result.reason)
                                finish(taskId, sentence, startedAt, steps, TaskState.FAILED, result.reason)
                                return
                            }
                        }
                    }
                }
            }

            val summary = steps.mapNotNull { it.detail }.joinToString(" ").ifBlank { "Done." }
            finish(taskId, sentence, startedAt, steps, TaskState.COMPLETED, summary)
        } catch (e: CancellationException) {
            // Stop pressed. stop() has already set CANCELLED; reporting this as a failure
            // would overwrite it.
            throw e
        } catch (e: Exception) {
            finish(
                taskId, sentence, startedAt, steps, TaskState.FAILED,
                "Something went wrong: ${e.message ?: "unknown error"}",
            )
        }
    }

    // ------------------------------------------------------------------ helpers

    /** The built-in gate: the task screen's confirmation sheet shows [summary]. */
    private suspend fun askThroughProgress(summary: String): Boolean {
        _progress.value = _progress.value.copy(
            state = TaskState.WAITING_FOR_CONFIRMATION,
            message = summary,
            awaitingKind = AwaitingKind.Confirmation,
        )
        val deferred = CompletableDeferred<Boolean>()
        confirmation = deferred
        return try {
            deferred.await()
        } finally {
            confirmation = null
        }
    }

    private suspend fun askPermission(name: String): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        permission = deferred
        _permissionRequest.value = PermissionRequest(name)
        return try {
            deferred.await()
        } finally {
            permission = null
            _permissionRequest.value = null
        }
    }

    /** Does nothing once the task is over: a Stop must not be flipped back to running. */
    private fun restoreRunningState() {
        if (_progress.value.state.isTerminal) return
        _progress.value = _progress.value.copy(
            state = TaskState.EXECUTING,
            awaitingKind = AwaitingKind.None,
        )
    }

    private fun mark(steps: MutableList<TaskStep>, index: Int, status: StepStatus, detail: String?) {
        if (index in steps.indices) steps[index] = steps[index].copy(status = status, detail = detail)
        _progress.value = _progress.value.copy(steps = steps.toList())
    }

    private suspend fun finish(
        taskId: String,
        prompt: String,
        startedAt: Long,
        steps: List<TaskStep>,
        state: TaskState,
        message: String,
    ) {
        val finishedAt = clock.currentTimeMillis()
        _progress.value = _progress.value.copy(
            state = state,
            message = message,
            error = if (state == TaskState.FAILED) message else null,
            steps = steps.toList(),
            awaitingKind = AwaitingKind.None,
        )
        journal.record(
            TaskRecord(
                id = taskId,
                prompt = prompt,
                state = state,
                createdAtMillis = startedAt,
                finishedAtMillis = finishedAt,
                durationMs = finishedAt - startedAt,
                stepCount = steps.count { it.status == StepStatus.Done },
                appsUsed = emptyList(),
                summary = message,
                steps = steps.map {
                    RecordedStep(
                        label = it.label,
                        status = it.status.name,
                        detail = it.detail,
                        modelMillis = 0,
                        settleMillis = 0,
                        observeMillis = 0,
                        actionMillis = 0,
                        inputTokens = 0,
                        outputTokens = 0,
                    )
                },
            ),
        )
    }

    private fun needsContactsPermission(reason: String): Boolean {
        val lower = reason.lowercase()
        return "contacts" in lower && "permission" in lower
    }

    companion object {
        const val STOPPED_BY_USER = "Stopped by you."
        const val DECLINED = "You declined this, so nothing was done."
        const val CANNOT_DO_THAT =
            "I can't do that yet. I can open apps, send a message, call someone, set an alarm " +
                "or set a timer."

        private fun idleProgress() = TaskProgress(
            taskId = "",
            prompt = "",
            state = TaskState.IDLE,
            steps = emptyList(),
            currentStep = 0,
            totalSteps = 1,
        )

        /** The routed intent as one row of the conversation. */
        fun describe(intent: SpideyIntent): String = when (intent) {
            is SpideyIntent.OpenApp -> "Open ${intent.app}"
            is SpideyIntent.SendMessage -> "Message ${intent.contact}: \"${intent.text}\""
            is SpideyIntent.CallContact -> "Call ${intent.contact}"
            is SpideyIntent.SetAlarm -> "Set an alarm for ${intent.time}"
            is SpideyIntent.SetTimer ->
                "Set a timer for ${intent.minutes} ${if (intent.minutes == 1) "minute" else "minutes"}"
        }
    }
}
