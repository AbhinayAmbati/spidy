package com.example.agent

import com.example.agent.domain.model.TaskProgress
import com.example.agent.domain.model.TaskState
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TaskStateMachineTest {

    @Test
    fun `terminal states are recognised`() {
        assertThat(TaskState.COMPLETED.isTerminal).isTrue()
        assertThat(TaskState.FAILED.isTerminal).isTrue()
        assertThat(TaskState.CANCELLED.isTerminal).isTrue()
        assertThat(TaskState.EXECUTING.isTerminal).isFalse()
    }

    @Test
    fun `working states are recognised`() {
        assertThat(TaskState.PLANNING.isWorking).isTrue()
        assertThat(TaskState.EXECUTING.isWorking).isTrue()
        assertThat(TaskState.OBSERVING.isWorking).isTrue()
        assertThat(TaskState.WAITING_FOR_CONFIRMATION.isWorking).isFalse()
    }

    @Test
    fun `step limit is a positive constant`() {
        assertThat(TaskProgress.MAX_AGENT_STEPS).isGreaterThan(0)
    }
}
