package com.example.agent

import com.example.agent.core.agent.AgentClock
import com.example.agent.core.agent.TaskJournal
import com.example.agent.core.control.AgentHalt
import com.example.agent.core.intent.ActionExecutor
import com.example.agent.core.intent.ActionResult
import com.example.agent.core.intent.ConfirmGate
import com.example.agent.core.intent.GuardVerdict
import com.example.agent.core.intent.IntentGuard
import com.example.agent.core.intent.IntentRouter
import com.example.agent.core.intent.IntentRuntime
import com.example.agent.core.intent.PermissionRequest
import com.example.agent.core.intent.RoutedRequest
import com.example.agent.core.intent.SpideyIntent
import com.example.agent.domain.model.AwaitingKind
import com.example.agent.domain.model.MemoryEntry
import com.example.agent.domain.model.StepStatus
import com.example.agent.domain.model.TaskRecord
import com.example.agent.domain.model.TaskState
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test

/**
 * The intent pipeline (router, guard, confirm, executor) driven end to end with fakes, in the
 * style of [AgentRuntimeTest].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class IntentRuntimeTest {

    // ---- fakes -----------------------------------------------------------------------

    private class FakeRouter(private val intents: List<SpideyIntent>) : IntentRouter {
        val sentences = mutableListOf<String>()
        override suspend fun route(sentence: String): RoutedRequest {
            sentences += sentence
            return RoutedRequest(intents, 0.9, "{}")
        }
    }

    private class BlockingRouter : IntentRouter {
        val entered = CompletableDeferred<Unit>()
        override suspend fun route(sentence: String): RoutedRequest {
            entered.complete(Unit)
            awaitCancellation()
        }
    }

    /** Allows everything, confirming send and call; [rules] override per intent. */
    private class FakeGuard(
        private val rules: (SpideyIntent, Int) -> GuardVerdict? = { _, _ -> null },
    ) : IntentGuard {
        val checks = mutableListOf<SpideyIntent>()
        override suspend fun check(intent: SpideyIntent): GuardVerdict {
            checks += intent
            val attempt = checks.count { it == intent }
            return rules(intent, attempt) ?: GuardVerdict.Allow(
                intent = intent,
                summary = "do $intent",
                needsConfirmation = intent is SpideyIntent.SendMessage ||
                    intent is SpideyIntent.CallContact,
            )
        }
    }

    private class FakeExecutor(
        private val result: (SpideyIntent) -> ActionResult = { ActionResult.Done("done $it") },
    ) : ActionExecutor {
        val executed = mutableListOf<SpideyIntent>()
        override suspend fun execute(intent: SpideyIntent): ActionResult {
            executed += intent
            return result(intent)
        }
    }

    private class FakeJournal : TaskJournal {
        val records = mutableListOf<TaskRecord>()
        override suspend fun knownApps(): List<MemoryEntry> = emptyList()
        override suspend fun learnApps(packages: Set<String>) = Unit
        override suspend fun record(record: TaskRecord) {
            records += record
        }
    }

    private class FakeClock : AgentClock {
        private var now = 0L
        override fun uptimeMillis() = now++
        override fun currentTimeMillis() = now++
    }

    private class Rig(
        scope: CoroutineScope,
        router: IntentRouter,
        val guard: FakeGuard = FakeGuard(),
        val executor: FakeExecutor = FakeExecutor(),
        gate: ConfirmGate? = null,
    ) {
        val journal = FakeJournal()
        val runtime = IntentRuntime(
            router = router,
            guard = guard,
            executor = executor,
            journal = journal,
            clock = FakeClock(),
            scope = scope,
            confirmGate = gate,
        )
    }

    private fun TestScope.appScope() =
        CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))

    @After
    fun clearTheProcessWideStopFlag() {
        AgentHalt.clear()
    }

    private val open = SpideyIntent.OpenApp("chrome")
    private val call = SpideyIntent.CallContact("mom")
    private val message = SpideyIntent.SendMessage("sam", "on my way")

    // ---- tests -----------------------------------------------------------------------

    @Test
    fun `an empty route ends with the can't-do-that message and nothing runs`() = runTest {
        val scope = appScope()
        val rig = Rig(scope, FakeRouter(emptyList()))

        rig.runtime.start("book me a flight")
        val final = rig.runtime.progress.first { it.state.isTerminal }

        assertThat(final.state).isEqualTo(TaskState.FAILED)
        assertThat(final.message).isEqualTo(IntentRuntime.CANNOT_DO_THAT)
        assertThat(final.message).isEqualTo(
            "I can't do that yet. I can open apps, send a message, call someone, set an alarm " +
                "or set a timer.",
        )
        assertThat(rig.guard.checks).isEmpty()
        assertThat(rig.executor.executed).isEmpty()
        assertThat(rig.journal.records).hasSize(1)
        scope.cancel()
    }

    @Test
    fun `an allowed intent without confirmation runs straight away`() = runTest {
        val scope = appScope()
        val router = FakeRouter(listOf(open))
        val rig = Rig(scope, router)

        rig.runtime.start("open chrome")
        val final = rig.runtime.progress.first { it.state.isTerminal }

        assertThat(final.state).isEqualTo(TaskState.COMPLETED)
        assertThat(router.sentences).containsExactly("open chrome")
        assertThat(rig.executor.executed).containsExactly(open)
        assertThat(final.steps.single().status).isEqualTo(StepStatus.Done)
        assertThat(rig.journal.records.map { it.state }).containsExactly(TaskState.COMPLETED)
        scope.cancel()
    }

    @Test
    fun `a rejection shows its reason and executes nothing`() = runTest {
        val scope = appScope()
        val guard = FakeGuard { _, _ -> GuardVerdict.Reject("Chrome is not installed.") }
        val rig = Rig(scope, FakeRouter(listOf(open)), guard = guard)

        rig.runtime.start("open chrome")
        val final = rig.runtime.progress.first { it.state.isTerminal }

        assertThat(final.state).isEqualTo(TaskState.FAILED)
        assertThat(final.message).isEqualTo("Chrome is not installed.")
        assertThat(final.steps.single().status).isEqualTo(StepStatus.Failed)
        assertThat(rig.executor.executed).isEmpty()
        scope.cancel()
    }

    @Test
    fun `a confirmed call asks with the guard summary then runs`() = runTest {
        val scope = appScope()
        val asked = mutableListOf<String>()
        val rig = Rig(scope, FakeRouter(listOf(call)), gate = ConfirmGate { asked += it; true })

        rig.runtime.start("call mom")
        val final = rig.runtime.progress.first { it.state.isTerminal }

        assertThat(asked).containsExactly("do $call")
        assertThat(rig.executor.executed).containsExactly(call)
        assertThat(final.state).isEqualTo(TaskState.COMPLETED)
        scope.cancel()
    }

    @Test
    fun `a declined confirmation cancels and executes nothing`() = runTest {
        val scope = appScope()
        val rig = Rig(scope, FakeRouter(listOf(call)), gate = ConfirmGate { false })

        rig.runtime.start("call mom")
        val final = rig.runtime.progress.first { it.state.isTerminal }

        assertThat(final.state).isEqualTo(TaskState.CANCELLED)
        assertThat(rig.executor.executed).isEmpty()
        assertThat(rig.journal.records.map { it.state }).containsExactly(TaskState.CANCELLED)
        scope.cancel()
    }

    @Test
    fun `the built-in gate shows the summary and waits for the sheet`() = runTest {
        val scope = appScope()
        val rig = Rig(scope, FakeRouter(listOf(message)))

        rig.runtime.start("text sam")
        val waiting = rig.runtime.progress.first { it.state == TaskState.WAITING_FOR_CONFIRMATION }
        assertThat(waiting.awaitingKind).isEqualTo(AwaitingKind.Confirmation)
        assertThat(waiting.message).isEqualTo("do $message")
        assertThat(rig.executor.executed).isEmpty()

        rig.runtime.resolveConfirmation(true)
        val final = rig.runtime.progress.first { it.state.isTerminal }

        assertThat(final.state).isEqualTo(TaskState.COMPLETED)
        assertThat(rig.executor.executed).containsExactly(message)
        scope.cancel()
    }

    @Test
    fun `the built-in gate declined by the user executes nothing`() = runTest {
        val scope = appScope()
        val rig = Rig(scope, FakeRouter(listOf(message)))

        rig.runtime.start("text sam")
        rig.runtime.progress.first { it.state == TaskState.WAITING_FOR_CONFIRMATION }
        rig.runtime.resolveConfirmation(false)
        val final = rig.runtime.progress.first { it.state.isTerminal }

        assertThat(final.state).isEqualTo(TaskState.CANCELLED)
        assertThat(rig.executor.executed).isEmpty()
        scope.cancel()
    }

    @Test
    fun `several intents run in order`() = runTest {
        val scope = appScope()
        val rig = Rig(scope, FakeRouter(listOf(open, SpideyIntent.SetTimer(5))))

        rig.runtime.start("open chrome and set a timer for 5 minutes")
        val final = rig.runtime.progress.first { it.state.isTerminal }

        assertThat(final.state).isEqualTo(TaskState.COMPLETED)
        assertThat(rig.executor.executed).containsExactly(open, SpideyIntent.SetTimer(5)).inOrder()
        assertThat(final.steps.map { it.status }).containsExactly(StepStatus.Done, StepStatus.Done)
        scope.cancel()
    }

    @Test
    fun `a failure stops the remaining intents`() = runTest {
        val scope = appScope()
        val executor = FakeExecutor { ActionResult.Failed("no alarm app") }
        val rig = Rig(
            scope,
            FakeRouter(listOf(SpideyIntent.SetAlarm("6 am"), open)),
            executor = executor,
        )

        rig.runtime.start("alarm then chrome")
        val final = rig.runtime.progress.first { it.state.isTerminal }

        assertThat(final.state).isEqualTo(TaskState.FAILED)
        assertThat(final.message).isEqualTo("no alarm app")
        assertThat(executor.executed).containsExactly(SpideyIntent.SetAlarm("6 am"))
        assertThat(rig.guard.checks).doesNotContain(open)
        scope.cancel()
    }

    @Test
    fun `a declined intent stops the remaining intents`() = runTest {
        val scope = appScope()
        val rig = Rig(scope, FakeRouter(listOf(call, open)), gate = ConfirmGate { false })

        rig.runtime.start("call mom then open chrome")
        val final = rig.runtime.progress.first { it.state.isTerminal }

        assertThat(final.state).isEqualTo(TaskState.CANCELLED)
        assertThat(rig.executor.executed).isEmpty()
        assertThat(rig.guard.checks).doesNotContain(open)
        scope.cancel()
    }

    @Test
    fun `a contacts permission rejection asks for the permission and retries once`() = runTest {
        val scope = appScope()
        val guard = FakeGuard { _, attempt ->
            if (attempt == 1) GuardVerdict.Reject("I need contacts permission to find Mom.") else null
        }
        val rig = Rig(scope, FakeRouter(listOf(call)), guard = guard, gate = ConfirmGate { true })

        rig.runtime.start("call mom")
        val request = rig.runtime.permissionRequest.first { it != null }
        assertThat(request).isEqualTo(PermissionRequest(PermissionRequest.READ_CONTACTS))

        rig.runtime.resolvePermission(true)
        val final = rig.runtime.progress.first { it.state.isTerminal }

        assertThat(final.state).isEqualTo(TaskState.COMPLETED)
        assertThat(guard.checks).hasSize(2)
        assertThat(rig.executor.executed).containsExactly(call)
        assertThat(rig.runtime.permissionRequest.value).isNull()
        scope.cancel()
    }

    @Test
    fun `a refused contacts permission keeps the guard's reason and does not retry`() = runTest {
        val scope = appScope()
        val reason = "I need contacts permission to find Mom."
        val guard = FakeGuard { _, _ -> GuardVerdict.Reject(reason) }
        val rig = Rig(scope, FakeRouter(listOf(call)), guard = guard)

        rig.runtime.start("call mom")
        rig.runtime.permissionRequest.first { it != null }
        rig.runtime.resolvePermission(false)
        val final = rig.runtime.progress.first { it.state.isTerminal }

        assertThat(final.state).isEqualTo(TaskState.FAILED)
        assertThat(final.message).isEqualTo(reason)
        assertThat(guard.checks).hasSize(1)
        scope.cancel()
    }

    @Test
    fun `a rejection that persists after the permission is granted is not asked again`() = runTest {
        val scope = appScope()
        val guard = FakeGuard { _, _ -> GuardVerdict.Reject("Contacts permission is still missing.") }
        val rig = Rig(scope, FakeRouter(listOf(call)), guard = guard)

        rig.runtime.start("call mom")
        rig.runtime.permissionRequest.first { it != null }
        rig.runtime.resolvePermission(true)
        val final = rig.runtime.progress.first { it.state.isTerminal }

        assertThat(final.state).isEqualTo(TaskState.FAILED)
        assertThat(guard.checks).hasSize(2)
        assertThat(rig.executor.executed).isEmpty()
        scope.cancel()
    }

    @Test
    fun `Stop during routing leaves the task cancelled, not failed`() = runTest {
        val scope = appScope()
        val router = BlockingRouter()
        val rig = Rig(scope, router)

        rig.runtime.start("open chrome")
        router.entered.await()
        rig.runtime.stop()
        advanceUntilIdle()

        assertThat(rig.runtime.progress.value.state).isEqualTo(TaskState.CANCELLED)
        assertThat(rig.runtime.isRunning).isFalse()
        assertThat(rig.executor.executed).isEmpty()
        scope.cancel()
    }

    @Test
    fun `Stop while a confirmation is showing cancels and executes nothing`() = runTest {
        val scope = appScope()
        val rig = Rig(scope, FakeRouter(listOf(call)))

        rig.runtime.start("call mom")
        rig.runtime.progress.first { it.state == TaskState.WAITING_FOR_CONFIRMATION }
        rig.runtime.stop()
        advanceUntilIdle()

        assertThat(rig.runtime.progress.value.state).isEqualTo(TaskState.CANCELLED)
        assertThat(rig.executor.executed).isEmpty()
        scope.cancel()
    }
}
