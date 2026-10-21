package com.example.agent.core.agent

import com.example.agent.automation.AndroidAutomationManager
import com.example.agent.automation.observer.ScreenObserver

/** What the agent loop needs from the phone's screen, and nothing else. */
interface AgentScreen {
    fun isReady(): Boolean

    /** Waits for the screen to stop changing. True when it changed, false when it did not. */
    suspend fun awaitSettled(): Boolean

    suspend fun observe(): ScreenObserver.Observation
}

class DeviceScreen(
    private val automation: AndroidAutomationManager,
    private val observer: ScreenObserver,
) : AgentScreen {
    override fun isReady(): Boolean = automation.isAccessibilityReady()
    override suspend fun awaitSettled(): Boolean = automation.awaitUiSettled()
    override suspend fun observe(): ScreenObserver.Observation = observer.observe()
}
