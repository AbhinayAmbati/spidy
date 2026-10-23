package com.example.agent.automation

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.example.agent.MainActivity
import com.example.agent.R
import com.example.agent.automation.overlay.AgentStatusOverlay
import com.example.agent.core.agent.AgentRuntime
import com.example.agent.core.agent.AgentStatusText
import com.example.agent.core.voice.VoiceController
import com.example.agent.core.voice.VoiceState
import com.example.agent.core.voice.VoiceStateMapper
import com.example.agent.data.preferences.SettingsRepository
import com.example.agent.domain.model.TaskState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Keeps the agent loop alive and visible while it works, so a long multi-step task
 * is not killed when the user leaves the app. It mirrors the runtime's progress
 * into a single, quiet notification.
 */
@AndroidEntryPoint
class AgentForegroundService : Service() {

    @Inject lateinit var runtime: AgentRuntime
    @Inject lateinit var voice: VoiceController
    @Inject lateinit var settings: SettingsRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var overlay: AgentStatusOverlay? = null
    private var progressJob: Job? = null
    private var optionsJob: Job? = null
    private var levelJob: Job? = null
    private var finishing = false
    private var afterVoice = false

    private fun finish() {
        finishing = true
        overlay?.hide()
        stopSelf()
    }

    /**
     * Android 14 refuses a microphone-type foreground service without RECORD_AUDIO, and this
     * service also runs typed tasks, so the type is only claimed while actually listening.
     */
    private fun enterForeground(listening: Boolean) {
        val notification = buildNotification("Working…")
        val mic = listening &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or
                    (if (mic) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0),
            )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> startForeground(
                NOTIFICATION_ID,
                notification,
                if (mic) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0,
            )
            else -> startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Stop straight from the notification shade. The user should never have to find
        // the app to halt a task that is driving their phone.
        if (intent?.action == ACTION_STOP) {
            runtime.stop()
            stopSelf()
            return START_NOT_STICKY
        }

        createChannel()
        enterForeground(listening = intent?.getBooleanExtra(EXTRA_LISTENING, false) == true)
        // A second start must not stack another overlay and collector on the first.
        progressJob?.cancel()
        optionsJob?.cancel()
        levelJob?.cancel()
        overlay?.hide()
        finishing = false
        afterVoice = false
        val shown = AgentStatusOverlay(this)
        overlay = shown

        optionsJob = scope.launch {
            combine(settings.voiceGlow, settings.voiceFace) { glow, face -> glow to face }
                .collect { (glow, face) -> shown.setOptions(glow, face) }
        }
        levelJob = scope.launch { voice.level.collect { shown.setLevel(it) } }

        progressJob = scope.launch {
            combine(runtime.progress, voice.state) { progress, voiceState -> progress to voiceState }
                .collectLatest { (progress, voiceState) ->
                if (finishing) return@collectLatest

                if (voiceState.isVoicePhase) {
                    afterVoice = true
                    overlay?.showVoice(voiceState)
                    notify(buildNotification(VoiceStateMapper.label(voiceState)))
                    if (voiceState is VoiceState.Error) {
                        delay(AgentStatusText.LINGER_MILLIS)
                        voice.acknowledgeError()
                        if (!runtime.isRunning) finish()
                    }
                    return@collectLatest
                }
                if (afterVoice) {
                    // The voice flow is over. Unless it started a task there is nothing left to
                    // show, and the last task's finished state must not be shown again.
                    afterVoice = false
                    if (!runtime.isRunning) {
                        finish()
                        return@collectLatest
                    }
                }

                val status = AgentStatusText.line(progress)

                if (progress.state.isTerminal) {
                    // Show the outcome briefly, then go. A task that finished while the user
                    // was in another app used to make the overlay just vanish, which tells
                    // them nothing at all.
                    if (status != null && AgentStatusText.showsOutcome(progress.state)) {
                        overlay?.show(status, progress.state)
                        notify(buildNotification(status))
                        delay(AgentStatusText.LINGER_MILLIS)
                    }
                    stopSelf()
                    return@collectLatest
                }

                if (status == null) {
                    overlay?.hide()
                } else {
                    overlay?.show(status, progress.state)
                }
                notify(buildNotification(progress.message ?: status ?: "Working…"))
            }
        }
        return START_NOT_STICKY
    }

    /**
     * The user swiped the app out of recents.
     *
     * The overlay is a *system* window, so it belongs to nothing that the task being gone
     * removes - it would sit over whatever the user opened next, for ever. Stopping the task
     * is also the right reading of the gesture: they dismissed the app, so they want it to
     * stop.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        overlay?.hide()
        overlay = null
        runtime.stop(STOPPED_APP_CLOSED)
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        overlay?.hide()
        overlay = null
        scope.cancel()
        super.onDestroy()
    }

    private fun notify(notification: Notification) {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, AgentForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.fgs_notification_title))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(open)
            .addAction(0, getString(R.string.fgs_stop), stop)
            .setOngoing(true)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.fgs_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = getString(R.string.fgs_channel_desc) }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "spidey_agent"
        private const val ACTION_STOP = "com.example.agent.action.STOP"
        private const val NOTIFICATION_ID = 42
        private const val STOPPED_APP_CLOSED = "Stopped: the app was closed."

        private const val EXTRA_LISTENING = "com.example.agent.extra.LISTENING"

        /** [listening] is true when the microphone is about to be used. */
        fun start(context: Context, listening: Boolean = false) {
            val intent = Intent(context, AgentForegroundService::class.java)
                .putExtra(EXTRA_LISTENING, listening)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AgentForegroundService::class.java))
        }
    }
}
