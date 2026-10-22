package com.example.agent.core.intent

/**
 * The five things the Spidey model can ask for, exactly as it was trained
 * (`assets/spidey_tools.json`). Values are what the user said, not yet resolved: an app name is
 * not a package, a contact name is not a phone number. [IntentGuard] does that resolution.
 */
sealed interface SpideyIntent {

    /** `open_app(app)`: the app name as the user says it, lowercase. */
    data class OpenApp(val app: String) : SpideyIntent

    /** `send_message(contact, text)`: the body is exactly what the user wants to say. */
    data class SendMessage(val contact: String, val text: String) : SpideyIntent

    /** `call_contact(contact)`. */
    data class CallContact(val contact: String) : SpideyIntent

    /** `set_alarm(time)`: the time as the user says it, for example "6 am" or "7:30 pm". */
    data class SetAlarm(val time: String) : SpideyIntent

    /** `set_timer(minutes)`. */
    data class SetTimer(val minutes: Int) : SpideyIntent
}
