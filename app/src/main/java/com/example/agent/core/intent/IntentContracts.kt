package com.example.agent.core.intent

/**
 * What the model answered for one sentence. An empty [intents] list means the request is not
 * something Spidey can do: it is reported to the user, never retried or guessed at.
 *
 * @property confidence the engine's calibrated score, or null when the model gave none.
 * @property raw the engine's JSON, kept for the audit log and diagnostics.
 */
data class RoutedRequest(
    val intents: List<SpideyIntent>,
    val confidence: Double?,
    val raw: String,
)

/** Turns one sentence into intents using the on-device model. */
interface IntentRouter {
    suspend fun route(sentence: String): RoutedRequest
}

/** The guard's decision about one intent. */
sealed interface GuardVerdict {

    /**
     * The intent is safe to run once confirmed (if [needsConfirmation]).
     *
     * @property intent the intent with its values resolved (a real contact, an installed app).
     * @property summary one line for the confirmation sheet and the audit log.
     */
    data class Allow(
        val intent: SpideyIntent,
        val summary: String,
        val needsConfirmation: Boolean,
    ) : GuardVerdict

    /** The intent must not run; [reason] is shown to the user. */
    data class Reject(val reason: String) : GuardVerdict
}

/**
 * Checks a model-chosen intent against the real device before anything happens: the app must be
 * installed, the contact must exist, a time or duration must make sense. The model is untrusted.
 */
interface IntentGuard {
    suspend fun check(intent: SpideyIntent): GuardVerdict
}

/** Asks the user to approve an intent before it runs. */
fun interface ConfirmGate {
    suspend fun confirm(summary: String): Boolean
}

/** What happened when an intent was run. */
sealed interface ActionResult {
    data class Done(val message: String) : ActionResult
    data class Failed(val reason: String) : ActionResult
}

/** Carries out an approved, resolved intent with Android intents. */
interface ActionExecutor {
    suspend fun execute(intent: SpideyIntent): ActionResult
}
