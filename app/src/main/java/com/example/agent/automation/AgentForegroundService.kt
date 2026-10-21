package com.example.agent.automation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.example.agent.MainActivity
import com.example.agent.R
import com.example.agent.automation.overlay.AgentStatusOverlay
import com.example.agent.core.agent.AgentRuntime
import com.example.agent.core.agent.AgentStatusText
import com.example.agent.domain.model.TaskState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var overlay: AgentStatusOverlay? = null
    private var progressJob: Job? = null

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
        startForeground(NOTIFICATION_ID, buildNotification("Working…"))
        // A second start must not stack another overlay and collector on the first.
        progressJob?.cancel()
        overlay?.hide()
        overlay = AgentStatusOverlay(this)

        progressJob = scope.launch {
            runtime.progress.collectLatest { progress ->
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

        fun start(context: Context) {
            val intent = Intent(context, AgentForegroundService::class.java)
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
