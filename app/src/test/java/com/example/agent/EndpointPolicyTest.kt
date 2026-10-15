package com.example.agent

import com.example.agent.core.security.EndpointPolicy
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The endpoint is a security boundary, not a text field.
 *
 * The app sends the user's screen content and prompts to whatever is configured here, so an
 * endpoint that sends them in the clear has to be refused - and refused with a reason the user
 * can act on, because the alternative is a request that fails with nothing to explain it.
 */
class EndpointPolicyTest {

    @Test
    fun `a blank endpoint means use the provider default`() {
        assertThat(EndpointPolicy.rejectionReason("")).isNull()
        assertThat(EndpointPolicy.rejectionReason("   ")).isNull()
        assertThat(EndpointPolicy.rejectionReason(null)).isNull()
    }

    @Test
    fun `every shipped provider default is acceptable`() {
        for (type in com.example.agent.domain.model.ProviderType.entries) {
            if (type.defaultBaseUrl.isBlank()) continue
            assertThat(EndpointPolicy.rejectionReason(type.defaultBaseUrl))
                .isNull()
        }
    }

    @Test
    fun `a plain https endpoint is fine`() {
        assertThat(EndpointPolicy.rejectionReason("https://api.example.com/v1")).isNull()
    }

    @Test
    fun `a path and a port are fine`() {
        assertThat(EndpointPolicy.rejectionReason("https://api.example.com:8443/openai/v1")).isNull()
    }

    @Test
    fun `http is refused, and the reason says why`() {
        val reason = EndpointPolicy.rejectionReason("http://api.example.com/v1")

        assertThat(reason).isNotNull()
        assertThat(reason).contains("unencrypted")
    }

    @Test
    fun `a missing scheme is refused rather than guessed at`() {
        // The common typo, and the one that would otherwise become a request to a URL the user
        // never meant.
        val reason = EndpointPolicy.rejectionReason("api.example.com/v1")

        assertThat(reason).isNotNull()
        assertThat(reason).contains("https://")
    }

    @Test
    fun `an unsupported scheme is refused`() {
        assertThat(EndpointPolicy.rejectionReason("ftp://api.example.com")).isNotNull()
        assertThat(EndpointPolicy.rejectionReason("file:///etc/passwd")).isNotNull()
        assertThat(EndpointPolicy.rejectionReason("javascript:alert(1)")).isNotNull()
    }

    @Test
    fun `https with no host is refused`() {
        assertThat(EndpointPolicy.rejectionReason("https://")).isNotNull()
    }

    @Test
    fun `something that is not a URL at all is refused`() {
        assertThat(EndpointPolicy.rejectionReason("not a url")).isNotNull()
    }

    @Test
    fun `surrounding whitespace is tolerated, because a paste usually has it`() {
        assertThat(EndpointPolicy.rejectionReason("  https://api.example.com/v1  ")).isNull()
    }

    @Test
    fun `scheme matching is case insensitive`() {
        assertThat(EndpointPolicy.rejectionReason("HTTPS://api.example.com/v1")).isNull()
        assertThat(EndpointPolicy.rejectionReason("HTTP://api.example.com/v1")).isNotNull()
    }

    @Test
    fun `isAcceptable agrees with rejectionReason`() {
        assertThat(EndpointPolicy.isAcceptable("https://api.example.com/v1")).isTrue()
        assertThat(EndpointPolicy.isAcceptable("http://api.example.com/v1")).isFalse()
    }
}
