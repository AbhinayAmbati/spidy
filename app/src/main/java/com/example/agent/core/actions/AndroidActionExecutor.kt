package com.example.agent.core.actions

import com.example.agent.core.intent.ActionResult
import com.example.agent.core.intent.ActionExecutor
import com.example.agent.core.intent.ContactMatch
import com.example.agent.core.intent.ContactResolver
import com.example.agent.core.intent.SpideyIntent
import com.example.agent.core.intent.TimeParser
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Runs a guard-approved intent through Android intents only. Messages open the SMS composer and
 * calls open the dialer, so the user always makes the final tap; nothing is ever sent or dialled
 * silently and neither SEND_SMS nor CALL_PHONE is requested.
 */
class AndroidActionExecutor @Inject constructor(
    private val launcher: ActionLauncher,
    private val contacts: ContactResolver,
) : ActionExecutor {

    override suspend fun execute(intent: SpideyIntent): ActionResult = withContext(Dispatchers.IO) {
        try {
            run(intent)
        } catch (e: SecurityException) {
            ActionResult.Failed("Android did not allow that action.")
        }
    }

    private fun run(intent: SpideyIntent): ActionResult = when (intent) {
        is SpideyIntent.OpenApp ->
            launch(LaunchRequest.OpenPackage(intent.app), "Opened ${intent.app}.")
        is SpideyIntent.SendMessage -> withNumber(intent.contact) { number ->
            launch(
                LaunchRequest.ComposeSms(number, intent.text),
                "Message to ${intent.contact} is ready. Tap send in your messages app.",
            )
        }
        is SpideyIntent.CallContact -> withNumber(intent.contact) { number ->
            launch(LaunchRequest.Dial(number), "Dialer opened for ${intent.contact}. Tap call to place it.")
        }
        is SpideyIntent.SetAlarm -> {
            val time = TimeParser.parse(intent.time)
            if (time == null) {
                ActionResult.Failed("I could not understand the time \"${intent.time}\".")
            } else {
                launch(LaunchRequest.Alarm(time.hour, time.minute), "Alarm set for ${time.display()}.")
            }
        }
        is SpideyIntent.SetTimer ->
            if (!TimeParser.isValidTimerMinutes(intent.minutes)) {
                ActionResult.Failed("A timer must be between 1 and 1440 minutes.")
            } else {
                launch(LaunchRequest.Timer(intent.minutes * SECONDS_PER_MINUTE), "Timer started for ${intent.minutes} min.")
            }
    }

    private fun withNumber(name: String, block: (String) -> ActionResult): ActionResult =
        when (val match = contacts.resolve(name)) {
            is ContactMatch.Found -> block(match.contact.number)
            is ContactMatch.PermissionNeeded ->
                ActionResult.Failed("Spidey needs permission to read your contacts.")
            else -> ActionResult.Failed("I could not find a single contact called \"$name\".")
        }

    private fun launch(request: LaunchRequest, doneMessage: String): ActionResult {
        val error = launcher.launch(request)
        return if (error == null) ActionResult.Done(doneMessage) else ActionResult.Failed(error)
    }

    private companion object {
        const val SECONDS_PER_MINUTE = 60
    }
}
