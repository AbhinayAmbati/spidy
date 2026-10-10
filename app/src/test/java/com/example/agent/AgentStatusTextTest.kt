package com.example.agent

import com.example.agent.core.agent.AgentStatusText
import com.example.agent.domain.model.AwaitingKind
import com.example.agent.domain.model.StepStatus
import com.example.agent.domain.model.TaskProgress
import com.example.agent.domain.model.TaskState
import com.example.agent.domain.model.TaskStep
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The floating overlay's one line of status.
 *
 * It exists because the task screen is only visible while the app is in front, and the agent
 * spends its time inside other apps. It must never be blank while a task is working - a blank
 * pill is indistinguishable from the overlay being broken - and it must never show the model's
 * reasoning.
 */
class AgentStatusTextTest {

    private fun progress(
        state: TaskState,
        steps: List<TaskStep> = emptyList(),
        currentStep: Int = 3,
        totalSteps: Int = 30,
        awaitingKind: AwaitingKind = AwaitingKind.None,
    ) = TaskProgress(
        taskId = "t",
        prompt = "open chrome",
        state = state,
        steps = steps,
        currentStep = currentStep,
        totalSteps = totalSteps,
        awaitingKind = awaitingKind,
    )

    private fun step(label: String, status: StepStatus) =
        TaskStep(index = 0, label = label, status = status)

    @Test
    fun `idle shows nothing at all`() {
        assertThat(AgentStatusText.line(progress(TaskState.IDLE))).isNull()
    }

    @Test
    fun `planning says it is thinking`() {
        assertThat(AgentStatusText.line(progress(TaskState.PLANNING))).startsWith("Thinking")
    }

    @Test
    fun `executing names the step that is actually running`() {
        val line = AgentStatusText.line(
            progress(
                TaskState.EXECUTING,
                steps = listOf(
                    step("Opening com.android.chrome", StepStatus.Done),
                    step("Typing \"Kubernetes\"", StepStatus.Running),
                ),
            ),
        )

        assertThat(line).contains("Typing")
        assertThat(line).doesNotContain("Opening")
    }

    @Test
    fun `executing falls back to the newest step when none is marked running`() {
        val line = AgentStatusText.line(
            progress(
                TaskState.EXECUTING,
                steps = listOf(step("Opening com.android.chrome", StepStatus.Done)),
            ),
        )

        assertThat(line).contains("Opening com.android.chrome")
    }

    @Test
    fun `executing with no steps at all still says something`() {
        // A blank pill looks like a broken overlay, so this must never come back empty.
        assertThat(AgentStatusText.line(progress(TaskState.EXECUTING))).isNotEmpty()
    }

    @Test
    fun `observing says it is reading the screen`() {
        assertThat(AgentStatusText.line(progress(TaskState.OBSERVING)))
            .startsWith("Reading the screen")
    }

    @Test
    fun `a confirmation and a question read differently`() {
        val confirm = AgentStatusText.line(
            progress(TaskState.WAITING_FOR_CONFIRMATION, awaitingKind = AwaitingKind.Confirmation),
        )
        val question = AgentStatusText.line(
            progress(TaskState.WAITING_FOR_CONFIRMATION, awaitingKind = AwaitingKind.Question),
        )

        assertThat(confirm).contains("confirm")
        assertThat(question).contains("answer")
    }

    @Test
    fun `paused says so`() {
        assertThat(AgentStatusText.line(progress(TaskState.PAUSED))).startsWith("Paused")
    }

    @Test
    fun `every terminal state reads differently, so the outcome is unambiguous`() {
        val done = AgentStatusText.line(progress(TaskState.COMPLETED))
        val failed = AgentStatusText.line(progress(TaskState.FAILED))
        val stopped = AgentStatusText.line(progress(TaskState.CANCELLED))

        assertThat(done).isEqualTo("Done")
        assertThat(failed).isEqualTo("Failed")
        assertThat(stopped).isEqualTo("Stopped")
        assertThat(setOf(done, failed, stopped)).hasSize(3)
    }

    @Test
    fun `the step counter appears only while there is something to count`() {
        val working = AgentStatusText.line(progress(TaskState.EXECUTING, currentStep = 7))
        val done = AgentStatusText.line(progress(TaskState.COMPLETED, currentStep = 18))

        assertThat(working).contains("7/30")
        assertThat(done).doesNotContain("/")
    }

    @Test
    fun `a finished task is shown briefly so the outcome is not missed`() {
        assertThat(AgentStatusText.showsOutcome(TaskState.COMPLETED)).isTrue()
        assertThat(AgentStatusText.showsOutcome(TaskState.FAILED)).isTrue()
        assertThat(AgentStatusText.showsOutcome(TaskState.CANCELLED)).isTrue()
        assertThat(AgentStatusText.showsOutcome(TaskState.EXECUTING)).isFalse()
        assertThat(AgentStatusText.showsOutcome(TaskState.PAUSED)).isFalse()
    }

    @Test
    fun `no state produces a line carrying the model's reasoning`() {
        // The overlay is an observable-status surface. Nothing here may look like a thought.
        val all = TaskState.entries.mapNotNull { AgentStatusText.line(progress(it)) }

        for (line in all) {
            assertThat(line.length).isLessThan(60)
            assertThat(line).doesNotContain("because")
            assertThat(line).doesNotContain("I think")
        }
    }
}
