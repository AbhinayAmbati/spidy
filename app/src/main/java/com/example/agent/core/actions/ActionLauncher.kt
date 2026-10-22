package com.example.agent.core.actions

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** What the executor wants started. Plain data so tests need no Android classes. */
sealed interface LaunchRequest {
    data class OpenPackage(val packageName: String) : LaunchRequest

    /** The SMS composer, prefilled; the user taps send. */
    data class ComposeSms(val number: String, val body: String) : LaunchRequest

    /** The dialer, prefilled; the user taps call. */
    data class Dial(val number: String) : LaunchRequest

    data class Alarm(val hour: Int, val minute: Int) : LaunchRequest
    data class Timer(val seconds: Int) : LaunchRequest
}

/** Starts a [LaunchRequest]. A port so tests do not need a Context. */
fun interface ActionLauncher {
    /** Returns null when started, otherwise a plain message saying why it could not be. */
    fun launch(request: LaunchRequest): String?
}

/** [ActionLauncher] that builds real Android intents. No SEND_SMS or CALL_PHONE is needed. */
class AndroidActionLauncher @Inject constructor(
    @ApplicationContext private val context: Context,
) : ActionLauncher {

    override fun launch(request: LaunchRequest): String? {
        val intent = buildIntent(request)
            ?: return "That app has no launcher screen."
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            null
        } catch (e: ActivityNotFoundException) {
            "No app on this phone can do that."
        } catch (e: SecurityException) {
            "Android did not allow that action."
        }
    }

    private fun buildIntent(request: LaunchRequest): Intent? = when (request) {
        is LaunchRequest.OpenPackage ->
            context.packageManager.getLaunchIntentForPackage(request.packageName)
        is LaunchRequest.ComposeSms ->
            Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(request.number)))
                .putExtra("sms_body", request.body)
        is LaunchRequest.Dial ->
            Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", request.number, null))
        is LaunchRequest.Alarm ->
            Intent(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_HOUR, request.hour)
                .putExtra(AlarmClock.EXTRA_MINUTES, request.minute)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        is LaunchRequest.Timer ->
            Intent(AlarmClock.ACTION_SET_TIMER)
                .putExtra(AlarmClock.EXTRA_LENGTH, request.seconds)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, false)
    }
}
