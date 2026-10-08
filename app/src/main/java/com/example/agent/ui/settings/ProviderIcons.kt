package com.example.agent.ui.settings

import androidx.annotation.DrawableRes
import com.example.agent.R
import com.example.agent.domain.model.ProviderType

/**
 * Original line-art marks, drawn for this app rather than copied from the vendors'
 * logos — the vendor marks are trademarks and redistributing them would need brand
 * permission. Swap in official assets here if you obtain it.
 */
@DrawableRes
fun ProviderType.iconRes(): Int = when (this) {
    ProviderType.OPENAI -> R.drawable.ic_provider_openai
    ProviderType.ANTHROPIC -> R.drawable.ic_provider_anthropic
    ProviderType.GEMINI -> R.drawable.ic_provider_gemini
    ProviderType.SARVAM -> R.drawable.ic_provider_sarvam
    ProviderType.OPENROUTER -> R.drawable.ic_provider_openrouter
    ProviderType.CUSTOM -> R.drawable.ic_provider_custom
}
