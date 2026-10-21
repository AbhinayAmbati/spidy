package com.example.agent.core.agent

import com.example.agent.core.ai.AIProvider
import com.example.agent.core.ai.AIProviderFactory
import com.example.agent.data.preferences.SettingsRepository

/** Where the agent loop gets its model, resolved on demand because a replay needs none. */
fun interface ModelSource {
    suspend fun provider(): AIProvider
}

class ConfiguredModelSource(
    private val providerFactory: AIProviderFactory,
    private val settings: SettingsRepository,
) : ModelSource {
    override suspend fun provider(): AIProvider =
        providerFactory.create(settings.currentProviderConfig())
}
