package com.example.agent.core.agent

/**
 * What `launch_url` is allowed to open.
 *
 * Reported problem: asking the agent to send a message made it open deep links instead —
 * links that launch a messaging app with the message already composed. That is both
 * surprising and hard to undo, so it is refused outright. Opening a URL is for the web.
 *
 * Kept free of Android types so the rules are unit-testable.
 */
object UrlPolicy {

    private val ALLOWED_SCHEMES = setOf("http", "https")

    /**
     * Hosts that open a messaging app with a pre-composed message. Sending a message must
     * go through the app's own interface, where it is visible before it is sent.
     */
    private val MESSAGING_HOSTS = setOf(
        "wa.me",
        "api.whatsapp.com",
        "web.whatsapp.com",
        "t.me",
        "telegram.me",
        "telegram.dog",
        "m.me",
        "messenger.com",
        "line.me",
        "signal.me",
        "viber.com",
    )

    /** Schemes that place a call or send a message rather than opening a page. */
    private val MESSAGING_SCHEMES = setOf(
        "sms", "smsto", "mms", "mmsto", "tel", "callto", "mailto",
        "whatsapp", "telegram", "viber", "signal",
    )

    /**
     * @return the reason to refuse this URL, or null when it may be opened.
     */
    fun rejectionReason(url: String?): String? {
        val raw = url?.trim().orEmpty()
        if (raw.isEmpty()) return null // the tool's own validation reports the empty case

        val lower = raw.lowercase()
        val scheme = lower.substringBefore(':')

        if (scheme in MESSAGING_SCHEMES) {
            return "\"$scheme:\" links send a message or place a call. That is not allowed " +
                "through launch_url — open the app and use its own interface instead."
        }
        if (scheme !in ALLOWED_SCHEMES) {
            return "Only http and https links can be opened. \"$scheme:\" is not allowed."
        }

        val host = hostOf(lower)
        if (MESSAGING_HOSTS.any { host == it || host.endsWith(".$it") }) {
            return "That link opens a messaging app with the message already written. Open " +
                "the app and use its own interface instead, so the message is visible first."
        }
        return null
    }

    /** Best-effort host extraction; only used to match a small denylist. */
    private fun hostOf(url: String): String {
        val afterScheme = url.substringAfter("://", "")
        val authority = afterScheme
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
        return authority.substringAfterLast('@').substringBefore(':')
    }
}
