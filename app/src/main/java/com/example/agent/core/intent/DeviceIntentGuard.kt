package com.example.agent.core.intent

import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Checks each model-chosen intent against the real device. Apps and contacts are resolved to
 * real ones (an [SpideyIntent.OpenApp] comes back carrying the package name, the others carry
 * the contact's saved name and a canonical 24-hour time). Sending and calling always need the
 * user's confirmation; opening an app, an alarm or a timer do not.
 */
class DeviceIntentGuard @Inject constructor(
    private val apps: AppResolver,
    private val contacts: ContactResolver,
) : IntentGuard {

    override suspend fun check(intent: SpideyIntent): GuardVerdict = withContext(Dispatchers.IO) {
        when (intent) {
            is SpideyIntent.OpenApp -> checkOpenApp(intent)
            is SpideyIntent.SendMessage -> checkSendMessage(intent)
            is SpideyIntent.CallContact -> checkCall(intent)
            is SpideyIntent.SetAlarm -> checkAlarm(intent)
            is SpideyIntent.SetTimer -> checkTimer(intent)
        }
    }

    private fun checkOpenApp(intent: SpideyIntent.OpenApp): GuardVerdict =
        when (val match = apps.resolve(intent.app)) {
            is AppMatch.Found -> GuardVerdict.Allow(
                intent = SpideyIntent.OpenApp(match.app.packageName),
                summary = "Open ${match.app.label}",
                needsConfirmation = false,
            )
            is AppMatch.NotFound ->
                GuardVerdict.Reject("I could not find an app called \"${intent.app}\" on this phone.")
            is AppMatch.Ambiguous -> GuardVerdict.Reject(
                "\"${intent.app}\" matches several apps: " +
                    match.candidates.joinToString { it.label } + ". Please be more specific.",
            )
        }

    private fun checkSendMessage(intent: SpideyIntent.SendMessage): GuardVerdict {
        if (intent.text.isBlank()) return GuardVerdict.Reject("The message is empty.")
        return when (val match = contacts.resolve(intent.contact)) {
            is ContactMatch.Found -> GuardVerdict.Allow(
                intent = SpideyIntent.SendMessage(match.contact.name, intent.text),
                summary = "Message ${match.contact.name}: \"${intent.text}\"",
                needsConfirmation = true,
            )
            else -> rejectContact(match)
        }
    }

    private fun checkCall(intent: SpideyIntent.CallContact): GuardVerdict =
        when (val match = contacts.resolve(intent.contact)) {
            is ContactMatch.Found -> GuardVerdict.Allow(
                intent = SpideyIntent.CallContact(match.contact.name),
                summary = "Call ${match.contact.name}",
                needsConfirmation = true,
            )
            else -> rejectContact(match)
        }

    private fun rejectContact(match: ContactMatch): GuardVerdict.Reject = when (match) {
        is ContactMatch.PermissionNeeded -> GuardVerdict.Reject(CONTACTS_PERMISSION_REASON)
        is ContactMatch.NotFound ->
            GuardVerdict.Reject("I could not find a contact called \"${match.spoken}\".")
        is ContactMatch.Ambiguous -> GuardVerdict.Reject(
            "\"${match.spoken}\" matches several contacts: ${match.names.joinToString()}. " +
                "Please say the full name.",
        )
        is ContactMatch.Found -> GuardVerdict.Reject("Unexpected contact result.")
    }

    private fun checkAlarm(intent: SpideyIntent.SetAlarm): GuardVerdict {
        val time = TimeParser.parse(intent.time)
            ?: return GuardVerdict.Reject("I could not understand the time \"${intent.time}\".")
        return GuardVerdict.Allow(
            intent = SpideyIntent.SetAlarm(time.canonical()),
            summary = "Set an alarm for ${time.display()}",
            needsConfirmation = false,
        )
    }

    private fun checkTimer(intent: SpideyIntent.SetTimer): GuardVerdict {
        if (!TimeParser.isValidTimerMinutes(intent.minutes)) {
            return GuardVerdict.Reject(
                "A timer must be between ${TimeParser.MIN_TIMER_MINUTES} and " +
                    "${TimeParser.MAX_TIMER_MINUTES} minutes, not ${intent.minutes}.",
            )
        }
        val unit = if (intent.minutes == 1) "minute" else "minutes"
        return GuardVerdict.Allow(
            intent = intent,
            summary = "Set a timer for ${intent.minutes} $unit",
            needsConfirmation = false,
        )
    }

    companion object {
        /**
         * The reason of the rejection when READ_CONTACTS is missing. The UI compares against this
         * (or [isPermissionRejection]) to offer the permission prompt instead of an error.
         */
        const val CONTACTS_PERMISSION_REASON =
            "Spidey needs permission to read your contacts. Allow it, then try again."

        fun isPermissionRejection(verdict: GuardVerdict): Boolean =
            verdict is GuardVerdict.Reject && verdict.reason == CONTACTS_PERMISSION_REASON
    }
}
