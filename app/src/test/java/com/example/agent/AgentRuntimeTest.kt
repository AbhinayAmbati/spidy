package com.example.agent

import com.example.agent.automation.observer.ScreenObserver
import com.example.agent.automation.tools.FinishTool
import com.example.agent.automation.tools.ToolRegistry
import com.example.agent.core.agent.ActionValidator
import com.example.agent.core.agent.AgentClock
import com.example.agent.core.agent.AgentRuntime
import com.example.agent.core.agent.AgentScreen
import com.example.agent.core.agent.ModelSource
import com.example.agent.core.agent.SkippedCalls
import com.example.agent.core.agent.TaskJournal
import com.example.agent.core.ai.AIProvider
import com.example.agent.core.ai.AgentTool
import com.example.agent.core.ai.AgentToolSpec
import com.example.agent.core.ai.ValidationResult
import com.example.agent.core.control.AgentHalt
import com.example.agent.domain.model.ActionTarget
import com.example.agent.domain.model.AgentAction
import com.example.agent.domain.model.AgentActionCall
import com.example.agent.domain.model.AgentMessage
import com.example.agent.domain.model.AgentResponse
import com.example.agent.domain.model.AwaitingKind
import com.example.agent.domain.model.MemoryEntry
import com.example.agent.domain.model.MessageRole
import com.example.agent.domain.model.ScreenSnapshot
import com.example.agent.domain.model.TaskRecord
import com.example.agent.domain.model.TaskState
import com.example.agent.domain.model.ToolNames
import com.example.agent.domain.model.ToolResult
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
 * The agent loop, driven end to end with fakes. Every bug fixed in the loop's recent history
 * (a stopped batch, Stop during a model call, Stop while awaiting) lived in code that no test
 * could reach, because the loop needed a real phone.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AgentRuntimeTest {

    // ---- fakes -----------------------------------------------------------------------

    private class ScriptedProvider(responses: List<AgentResponse>) : AIProvider {
        override val id = "scripted"
        private val remaining = ArrayDeque(responses)
        val requests = mutableListOf<List<AgentMessage>>()

        override suspend fun generateResponse(
            messages: List<AgentMessage>,
            tools: List<AgentToolSpec>,
        ): AgentResponse {
            requests += messages.toList()
            return remaining.removeFirstOrNull() ?: AgentResponse(message = "out of script")
        }
    }

    private class BlockingProvider : AIProvider {
        override val id = "blocking"
        val entered = CompletableDeferred<Unit>()

        override suspend fun generateResponse(
            messages: List<AgentMessage>,
            tools: List<AgentToolSpec>,
        ): AgentResponse {
            entered.complete(Unit)
            awaitCancellation()
        }
    }

    private class FakeTool(
        override val name: String,
        private val result: ToolResult = ToolResult.Success("ok"),
    ) : AgentTool {
        override val description = "fake"
        override val parametersSchema = "{}"
        var runs = 0
        override fun validate(action: AgentAction) = ValidationResult.Valid
        override suspend fun execute(action: AgentAction): ToolResult {
            runs++
            return result
        }
    }

    private class FakeScreen(private val ready: Boolean = true) : AgentScreen {
        private var reads = 0
        override fun isReady() = ready
        override suspend fun awaitSettled() = true
        override suspend fun observe(): ScreenObserver.Observation {
            reads++
            return ScreenObserver.Observation(ScreenSnapshot.EMPTY, "screen $reads")
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
        provider: AIProvider,
        screen: FakeScreen = FakeScreen(),
        tools: List<AgentTool> = listOf(FakeTool(ToolNames.CLICK), FinishTool()),
    ) {
        val journal = FakeJournal()
        private val registry = ToolRegistry(tools)
        val runtime = AgentRuntime(
            models = ModelSource { provider },
            registry = registry,
            validator = ActionValidator(registry),
            screen = screen,
            journal = journal,
            clock = FakeClock(),
            scope = scope,
        )
    }

    private fun click(id: String, label: String) = AgentActionCall(
        id,
        AgentAction(action = ToolNames.CLICK, target = ActionTarget(text = label)),
    )

    private fun finishCall(id: String = "fin") =
        AgentActionCall(id, AgentAction(action = ToolNames.FINISH, reason = "all done"))

    private fun TestScope.appScope() =
        CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))

    @After
    fun clearTheProcessWideStopFlag() {
        AgentHalt.clear()
    }

    // ---- tests -----------------------------------------------------------------------

    @Test
    fun `a task completes when the model calls finish`() = runTest {
        val scope = appScope()
        val rig = Rig(scope, ScriptedProvider(listOf(AgentResponse(calls = listOf(finishCall())))))

        rig.runtime.start("do it")
        val final = rig.runtime.progress.first { it.state.isTerminal }

        assertThat(final.state).isEqualTo(TaskState.COMPLETED)
        assertThat(final.message).isEqualTo("all done")
        assertThat(rig.journal.records.map { it.state }).containsExactly(TaskState.COMPLETED)
        scope.cancel()
    }

    @Test
    fun `a task fails with an explanation when accessibility is off`() = runTest {
        val scope = appScope()
        val rig = Rig(scope, ScriptedProvider(emptyList()), screen = FakeScreen(ready = false))

        rig.runtime.start("do it")
        val final = rig.runtime.progress.first { it.state.isTerminal }

        assertThat(final.state).isEqualTo(TaskState.FAILED)
        assertThat(final.message).contains("Accessibility")
        scope.cancel()
    }

    @Test
    fun `a batch stopped by a failing call still answers every call in it`() = runTest {
        val scope = appScope()
        val provider = ScriptedProvider(
            listOf(
                AgentResponse(calls = listOf(click("c1", "A"), click("c2", "B"), click("c3", "C"))),
                AgentResponse(calls = listOf(finishCall())),
            ),
        )
        val failingClick = FakeTool(ToolNames.CLICK, ToolResult.Failure("no such element"))
        val rig = Rig(scope, provider, tools = listOf(failingClick, FinishTool()))

        rig.runtime.start("do it")
        rig.runtime.progress.first { it.state.isTerminal }

        // The second request is the one OpenAI and Anthropic would reject if a call had no result.
        val secondRequest = provider.requests[1]
        val results = secondRequest.filter { it.role == MessageRole.Tool }.associateBy { it.toolCallId }
        assertThat(results.keys).containsExactly("c1", "c2", "c3")
        assertThat(results.getValue("c2").content).isEqualTo(SkippedCalls.NOT_RUN_RESULT)
        assertThat(results.getValue("c3").content).isEqualTo(SkippedCalls.NOT_RUN_RESULT)
        assertThat(failingClick.runs).isEqualTo(1)
        scope.cancel()
    }

    @Test
    fun `Stop during a model call leaves the task cancelled, not failed`() = runTest {
        val scope = appScope()
        val provider = BlockingProvider()
        val rig = Rig(scope, provider)

        rig.runtime.start("do it")
        provider.entered.await()
        rig.runtime.stop()
        advanceUntilIdle()

        assertThat(rig.runtime.progress.value.state).isEqualTo(TaskState.CANCELLED)
        assertThat(rig.runtime.isRunning).isFalse()
        scope.cancel()
    }

    @Test
    fun `a sensitive tap waits for the user and a refusal is reported to the model`() = runTest {
        val scope = appScope()
        val provider = ScriptedProvider(
            listOf(
                AgentResponse(calls = listOf(click("c1", "Send"))),
                AgentResponse(calls = listOf(finishCall())),
            ),
        )
        val tap = FakeTool(ToolNames.CLICK)
        val rig = Rig(scope, provider, tools = listOf(tap, FinishTool()))

        rig.runtime.start("do it")
        rig.runtime.progress.first { it.awaitingKind == AwaitingKind.Confirmation }
        rig.runtime.resolveConfirmation(false)
        rig.runtime.progress.first { it.state.isTerminal }

        assertThat(tap.runs).isEqualTo(0)
        val declined = provider.requests[1].last { it.role == MessageRole.Tool }
        assertThat(declined.content).contains("declined")
        scope.cancel()
    }

    @Test
    fun `Stop while a confirmation is showing leaves the task cancelled`() = runTest {
        val scope = appScope()
        val provider = ScriptedProvider(listOf(AgentResponse(calls = listOf(click("c1", "Send")))))
        val rig = Rig(scope, provider)

        rig.runtime.start("do it")
        rig.runtime.progress.first { it.awaitingKind == AwaitingKind.Confirmation }
        rig.runtime.stop()
        advanceUntilIdle()

        assertThat(rig.runtime.progress.value.state).isEqualTo(TaskState.CANCELLED)
        scope.cancel()
    }
}
