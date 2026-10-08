package com.example.agent

import com.example.agent.domain.model.ProviderType
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Guards the provider catalogue: a new entry that forgets a base URL, a default
 * model, or its place in the suggestion list fails here rather than at runtime.
 */
class ProviderTypeTest {

    private val hosted get() = ProviderType.entries.filter { it != ProviderType.CUSTOM }

    @Test
    fun `every hosted provider has a base url and a default model`() {
        hosted.forEach { type ->
            assertThat(type.defaultBaseUrl).isNotEmpty()
            assertThat(type.defaultModel).isNotEmpty()
        }
    }

    @Test
    fun `the default model is always offered as a suggestion`() {
        hosted.forEach { type ->
            assertThat(type.suggestedModels).contains(type.defaultModel)
        }
    }

    @Test
    fun `custom provider requires a typed model`() {
        assertThat(ProviderType.CUSTOM.requiresCustomModel).isTrue()
        assertThat(ProviderType.CUSTOM.suggestedModels).isEmpty()
    }

    @Test
    fun `sarvam points at its openai-compatible endpoint`() {
        val sarvam = ProviderType.SARVAM
        assertThat(sarvam.defaultBaseUrl).isEqualTo("https://api.sarvam.ai/v1")
        assertThat(sarvam.defaultModel).isEqualTo("sarvam-105b")
        assertThat(sarvam.suggestedModels).contains("sarvam-105b-conversations")
    }

    @Test
    fun `fromKey round trips every provider`() {
        ProviderType.entries.forEach { type ->
            assertThat(ProviderType.fromKey(type.name)).isEqualTo(type)
        }
    }
}
