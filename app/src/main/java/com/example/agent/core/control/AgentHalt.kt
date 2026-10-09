package com.example.agent.core.control

import java.util.concurrent.atomic.AtomicBoolean

/**
 * The emergency stop.
 *
 * Cancelling the agent's coroutine is not enough on its own: an action already in flight
 * still finishes, and some device operations (launching an app, opening a URL) are not
 * suspendable at all. When the user presses Stop they mean *now*, wherever the agent
 * happens to be — so every device-touching operation checks this gate first and refuses to
 * start if it is set.
 *
 * One task runs at a time, so process-wide state is the right shape here.
 */
object AgentHalt {

    private val stopped = AtomicBoolean(false)

    /** Set when the user stops the task. No further device action will begin. */
    fun request() {
        stopped.set(true)
    }

    /** Called when a new task starts. */
    fun clear() {
        stopped.set(false)
    }

    val isStopped: Boolean get() = stopped.get()
}
