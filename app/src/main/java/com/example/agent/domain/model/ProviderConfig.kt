package com.example.agent.domain.model

/** Which backend the agent talks to. Adding a provider here is the only wiring step. */
enum class ProviderType(
    val displayName: String,
    val defaultBaseUrl: String,
    val defaultModel: String,
    val requiresKey: Boolean = true,
) {
    OPENAI("OpenAI", "https://api.openai.com/v1", "gpt-4o-mini"),
    ANTHROPIC("Anthropic", "https://api.anthropic.com/v1", "claude-3-5-sonnet-latest"),
    GEMINI("Google Gemini", "https://generativelanguage.googleapis.com/v1beta", "gemini-1.5-flash"),
    OPENROUTER("OpenRouter", "https://openrouter.ai/api/v1", "openai/gpt-4o-mini"),
    CUSTOM("Custom (OpenAI-compatible)", "", "", requiresKey = true);

    companion object {
        fun fromKey(key: String?): ProviderType =
            entries.firstOrNull { it.name.equals(key, ignoreCase = true) } ?: OPENAI
    }
}

/**
 * Non-secret provider settings. The API key is NEVER part of this object — it lives
 * only in encrypted storage and is read directly by the provider adapter.
 */
data class ProviderConfig(
    val type: ProviderType = ProviderType.OPENAI,
    val model: String = ProviderType.OPENAI.defaultModel,
    val baseUrl: String = ProviderType.OPENAI.defaultBaseUrl,
    val hasApiKey: Boolean = false,
)
