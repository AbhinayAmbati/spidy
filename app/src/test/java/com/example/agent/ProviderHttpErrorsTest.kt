package com.example.agent

import com.example.agent.core.ai.AIProviderException
import com.example.agent.core.ai.provider.ProviderHttpErrors
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ProviderHttpErrorsTest {

    @Test
    fun `a 400 is a request failure that carries the provider's explanation, not a bad key`() {
        val error = ProviderHttpErrors.exceptionFor(400, """{"error":{"message":"Invalid schema for tool x"}}""")

        assertThat(error.kind).isEqualTo(AIProviderException.Kind.Unknown)
        assertThat(error.message).contains("Invalid schema for tool x")
    }

    @Test
    fun `401 is unauthorized and 429 is rate limited`() {
        assertThat(ProviderHttpErrors.exceptionFor(401, "").kind).isEqualTo(AIProviderException.Kind.Unauthorized)
        assertThat(ProviderHttpErrors.exceptionFor(429, "").kind).isEqualTo(AIProviderException.Kind.RateLimited)
    }

    @Test
    fun `an unreadable body falls back to the status code`() {
        assertThat(ProviderHttpErrors.exceptionFor(502, "<html>").message).contains("502")
    }
}
