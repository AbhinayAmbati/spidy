package com.example.agent.domain.model

/**
 * Which backend the agent talks to. Adding a provider here is the only wiring step.
 *
 * [suggestedModels] are offered as a picker in Settings. Model names change often,
 * so the picker always includes a "custom" escape hatch — these are conveniences,
 * not an allow-list.
 */
enum class ProviderType(
    val displayName: String,
    val defaultBaseUrl: String,
    val defaultModel: String,
    val requiresKey: Boolean = true,
    val suggestedModels: List<String> = emptyList(),
) {
    OPENAI(
        displayName = "OpenAI",
        defaultBaseUrl = "https://api.openai.com/v1",
        defaultModel = "gpt-4o-mini",
        suggestedModels = listOf(
            "gpt-4o-mini", "gpt-4o", "gpt-4.1", "gpt-4.1-mini", "o4-mini",
        ),
    ),
    ANTHROPIC(
        displayName = "Anthropic",
        defaultBaseUrl = "https://api.anthropic.com/v1",
        defaultModel = "claude-3-5-sonnet-latest",
        suggestedModels = listOf(
            "claude-3-5-sonnet-latest", "claude-3-5-haiku-latest",
            "claude-sonnet-4-20250514", "claude-opus-4-20250514",
        ),
    ),
    GEMINI(
        displayName = "Google Gemini",
        defaultBaseUrl = "https://generativelanguage.googleapis.com/v1beta",
        defaultModel = "gemini-1.5-flash",
        suggestedModels = listOf(
            "gemini-2.5-flash", "gemini-2.5-pro", "gemini-2.0-flash",
            "gemini-1.5-flash", "gemini-1.5-pro",
        ),
    ),
    SARVAM(
        displayName = "Sarvam AI",
        // OpenAI-compatible chat completions endpoint.
        defaultBaseUrl = "https://api.sarvam.ai/v1",
        defaultModel = "sarvam-105b",
        suggestedModels = listOf(
            "sarvam-105b", "sarvam-105b-conversations",
        ),
    ),
    OPENROUTER(
        displayName = "OpenRouter",
        defaultBaseUrl = "https://openrouter.ai/api/v1",
        defaultModel = "openai/gpt-4o-mini",
        suggestedModels = listOf(
            "openai/gpt-4o-mini", "anthropic/claude-3.5-sonnet",
            "google/gemini-flash-1.5", "meta-llama/llama-3.1-70b-instruct",
        ),
    ),
    CUSTOM(
        displayName = "Custom (OpenAI-compatible)",
        defaultBaseUrl = "",
        defaultModel = "",
        requiresKey = true,
        suggestedModels = emptyList(),
    ),
    ;

    /** True when the user must type a model name rather than pick one. */
    val requiresCustomModel: Boolean get() = suggestedModels.isEmpty()

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
