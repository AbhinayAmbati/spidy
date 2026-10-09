package com.example.agent

import com.example.agent.core.control.AgentHalt
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The emergency stop. Cancelling the agent's coroutine alone was not enough: an action
 * already in flight still finished, and launching an app is not suspendable at all. The
 * gate is what every device-touching operation checks first.
 */
class AgentHaltTest {

    @Test
    fun `the gate starts open`() {
        AgentHalt.clear()
        assertThat(AgentHalt.isStopped).isFalse()
    }

    @Test
    fun `requesting a stop closes the gate`() {
        AgentHalt.clear()
        AgentHalt.request()
        assertThat(AgentHalt.isStopped).isTrue()
    }

    @Test
    fun `starting a new task reopens it`() {
        AgentHalt.request()
        AgentHalt.clear()
        assertThat(AgentHalt.isStopped).isFalse()
    }

    @Test
    fun `a stop survives repeated reads`() {
        AgentHalt.clear()
        AgentHalt.request()
        repeat(5) { assertThat(AgentHalt.isStopped).isTrue() }
    }
}
