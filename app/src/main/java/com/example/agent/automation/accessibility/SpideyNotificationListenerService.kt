package com.example.agent.automation.accessibility

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * Optional: lets the agent read notifications. Requires the user to grant
 * "Notification access" separately (Settings > Notifications > Device & app
 * notifications > Spidey). If it is not granted, [NotificationBridge] simply
 * stays empty and the get_notifications tool reports the limitation.
 */
class SpideyNotificationListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        NotificationBridge.attach(this)
        refresh()
    }

    override fun onListenerDisconnected() {
        NotificationBridge.detach()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        refresh()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        refresh()
    }

    private fun refresh() {
        val current = runCatching { activeNotifications?.toList().orEmpty() }.getOrDefault(emptyList())
        NotificationBridge.update(current)
    }
}

/** Process-wide, read-only view of the user's recent notifications. */
object NotificationBridge {

    private val items = ConcurrentLinkedDeque<NotificationItem>()
    @Volatile private var connected = false

    data class NotificationItem(
        val packageName: String,
        val title: String?,
        val text: String?,
        val postedAtMillis: Long,
    )

    fun attach(service: NotificationListenerService) { connected = true }
    fun detach() { connected = false }

    fun update(list: List<StatusBarNotification>) {
        items.clear()
        list.sortedByDescending { it.postTime }.take(30).forEach { sbn ->
            val extras = sbn.notification?.extras
            items.addLast(
                NotificationItem(
                    packageName = sbn.packageName ?: "",
                    title = extras?.getCharSequence("android.title")?.toString(),
                    text = extras?.getCharSequence("android.text")?.toString(),
                    postedAtMillis = sbn.postTime,
                )
            )
        }
    }

    fun isConnected(): Boolean = connected
    fun recent(): List<NotificationItem> = items.toList()
}
