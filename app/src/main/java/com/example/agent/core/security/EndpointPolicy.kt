package com.example.agent.core.security

import java.net.URI

/**
 * What counts as an acceptable provider endpoint.
 *
 * The app sends the user's screen content and prompts to whatever endpoint is configured, so
 * the endpoint is a security boundary rather than a text field. Two things follow:
 *
 *  - **Cleartext is refused.** `android:usesCleartextTraffic` is off and the network security
 *    config forbids it, so an `http://` endpoint would fail at request time with an opaque
 *    error. Saying why up front is the difference between a bug report and a fix.
 *  - **A malformed endpoint is refused** rather than silently producing a request to a URL
 *    that was never intended. `"api.example.com"` with no scheme is the common case.
 *
 * A self-hosted endpoint on a local network is a real use case, and this does block it over
 * plain http. That is deliberate - the alternative is sending screen content in the clear -
 * and the fix is a tunnel or a certificate, not an exception here.
 *
 * Free of Android types, so the rules are unit-testable.
 */
object EndpointPolicy {

    /** @return the reason to refuse this endpoint, or null when it may be used. */
    fun rejectionReason(baseUrl: String?): String? {
        val raw = baseUrl?.trim().orEmpty()
        if (raw.isEmpty()) return null // blank means "use the provider default"

        val uri = runCatching { URI(raw) }.getOrNull()
            ?: return "\"$raw\" is not a valid URL. It should look like https://api.example.com/v1"

        val scheme = uri.scheme?.lowercase()
        return when {
            scheme == null || scheme.isBlank() ->
                "The endpoint needs a scheme. Write it as https://api.example.com/v1"

            scheme == "https" -> {
                if (uri.host.isNullOrBlank()) {
                    "\"$raw\" has no host. It should look like https://api.example.com/v1"
                } else {
                    null
                }
            }

            scheme == "http" ->
                "http:// sends your screen content unencrypted, so it is refused. Use https://, " +
                    "or put the endpoint behind a tunnel."

            else ->
                "Only https endpoints can be used. \"$scheme://\" is not supported."
        }
    }

    /** Whether an endpoint is usable. */
    fun isAcceptable(baseUrl: String?): Boolean = rejectionReason(baseUrl) == null
}
