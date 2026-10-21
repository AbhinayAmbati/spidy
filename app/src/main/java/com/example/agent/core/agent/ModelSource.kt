package com.example.agent.core.agent

import com.example.agent.core.ai.AIProvider

/** Where the agent loop gets its model, resolved on demand because a replay needs none. */
fun interface ModelSource {
    suspend fun provider(): AIProvider
}
