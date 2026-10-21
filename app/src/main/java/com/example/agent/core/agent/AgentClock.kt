package com.example.agent.core.agent

import android.os.SystemClock

/** Time as the agent loop reads it, so a test can supply its own. */
interface AgentClock {
    fun uptimeMillis(): Long
    fun currentTimeMillis(): Long
}

object DeviceClock : AgentClock {
    override fun uptimeMillis(): Long = SystemClock.uptimeMillis()
    override fun currentTimeMillis(): Long = System.currentTimeMillis()
}
