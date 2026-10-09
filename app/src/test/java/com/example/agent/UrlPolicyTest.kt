package com.example.agent

import com.example.agent.core.agent.UrlPolicy
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Asking the agent to send a message made it open deep links that launch a messaging app
 * with the message already composed. Those are refused now, so the agent is pushed to use
 * the app's own interface, where the message is visible before it is sent.
 */
class UrlPolicyTest {

    @Test
    fun `ordinary web links are allowed`() {
        listOf(
            "https://www.google.com/search?q=kubernetes",
            "https://www.youtube.com/results?search_query=karan+aujla",
            "http://example.com",
            "https://en.wikipedia.org/wiki/Kubernetes",
        ).forEach { assertThat(UrlPolicy.rejectionReason(it)).isNull() }
    }

    @Test
    fun `messaging deep links are refused`() {
        listOf(
            "https://wa.me/919999999999?text=hello",
            "https://api.whatsapp.com/send?text=hi",
            "https://t.me/someone",
            "https://telegram.me/someone",
            "https://m.me/someone",
        ).forEach { assertThat(UrlPolicy.rejectionReason(it)).isNotNull() }
    }

    @Test
    fun `messaging and calling schemes are refused`() {
        listOf(
            "sms:+919999999999",
            "smsto:+919999999999",
            "tel:+919999999999",
            "mailto:someone@example.com",
            "whatsapp://send?text=hi",
            "telegram://resolve?domain=x",
        ).forEach { assertThat(UrlPolicy.rejectionReason(it)).isNotNull() }
    }

    @Test
    fun `a subdomain of a messaging host is still refused`() {
        assertThat(UrlPolicy.rejectionReason("https://web.whatsapp.com/send?text=hi")).isNotNull()
        assertThat(UrlPolicy.rejectionReason("https://www.messenger.com/t/someone")).isNotNull()
    }

    @Test
    fun `a lookalike host is not caught by accident`() {
        // "notwa.me" must not be treated as "wa.me".
        assertThat(UrlPolicy.rejectionReason("https://notwa.me/page")).isNull()
        assertThat(UrlPolicy.rejectionReason("https://example.com/wa.me")).isNull()
    }

    @Test
    fun `non-web schemes are refused`() {
        assertThat(UrlPolicy.rejectionReason("file:///etc/passwd")).isNotNull()
        assertThat(UrlPolicy.rejectionReason("intent://scan/#Intent;scheme=zxing;end")).isNotNull()
        assertThat(UrlPolicy.rejectionReason("content://media/external/images/1")).isNotNull()
    }

    @Test
    fun `an empty url is left to the tool's own validation`() {
        assertThat(UrlPolicy.rejectionReason(null)).isNull()
        assertThat(UrlPolicy.rejectionReason("")).isNull()
        assertThat(UrlPolicy.rejectionReason("   ")).isNull()
    }

    @Test
    fun `case does not matter`() {
        assertThat(UrlPolicy.rejectionReason("HTTPS://WA.ME/919999999999")).isNotNull()
        assertThat(UrlPolicy.rejectionReason("HTTPS://EXAMPLE.COM")).isNull()
    }
}
