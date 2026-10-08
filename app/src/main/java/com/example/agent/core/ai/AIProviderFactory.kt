package com.example.agent.core.ai

import com.example.agent.core.ai.provider.AnthropicProvider
import com.example.agent.core.ai.provider.GeminiProvider
import com.example.agent.core.ai.provider.OpenAICompatibleProvider
import com.example.agent.core.security.SecureCredentialStore
import com.example.agent.domain.model.ProviderConfig
import com.example.agent.domain.model.ProviderType

/**
 * Builds the correct [AIProvider] for the current settings. This is the *only*
 * place that knows about concrete provider classes.
 */
class AIProviderFactory(
    private val credentials: SecureCredentialStore,
) {
    fun create(config: ProviderConfig): AIProvider {
        val key = credentials.getApiKey(config.type)
        return when (config.type) {
            ProviderType.OPENAI,
            ProviderType.OPENROUTER,
            ProviderType.CUSTOM ->
                OpenAICompatibleProvider(
                    id = config.type.name.lowercase(),
                    baseUrl = config.baseUrl.ifBlank { config.type.defaultBaseUrl },
                    model = config.model.ifBlank { config.type.defaultModel },
                    apiKey = key,
                )

            ProviderType.ANTHROPIC ->
                AnthropicProvider(
                    baseUrl = config.baseUrl.ifBlank { config.type.defaultBaseUrl },
                    model = config.model.ifBlank { config.type.defaultModel },
                    apiKey = key,
                )

            ProviderType.GEMINI ->
                GeminiProvider(
                    baseUrl = config.baseUrl.ifBlank { config.type.defaultBaseUrl },
                    model = config.model.ifBlank { config.type.defaultModel },
                    apiKey = key,
                )
        }
    }
}
