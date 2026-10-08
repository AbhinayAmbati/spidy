package com.example.agent.core.permissions

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.example.agent.automation.accessibility.SpideyAccessibilityService
import com.example.agent.automation.accessibility.SpideyNotificationListenerService

/** Small helpers around the two runtime permissions Spidey actually needs. */
object PermissionUtils {

    fun isAccessibilityEnabled(context: Context): Boolean {
        val expected = ComponentName(context, SpideyAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        return enabled.split(':').any {
            ComponentName.unflattenFromString(it) == expected
        }
    }

    /**
     * Whether the user granted "notification access" (a separate switch from the
     * POST_NOTIFICATIONS runtime permission).
     */
    fun isNotificationListenerEnabled(context: Context): Boolean {
        val expected = ComponentName(context, SpideyNotificationListenerService::class.java)
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners",
        ) ?: return false
        return enabled.split(':').any {
            ComponentName.unflattenFromString(it) == expected
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun status(context: Context) = PermissionStatus(
        accessibilityEnabled = isAccessibilityEnabled(context),
        notificationsGranted = hasNotificationPermission(context),
    )

    /** Opens the system Accessibility settings page. The user enables the service themselves. */
    fun accessibilitySettingsIntent(): Intent =
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    /** Opens the notification-access page used to grant the (optional) notification reader. */
    fun notificationListenerSettingsIntent(): Intent =
        Intent("android.settings.NOTIFICATION_LISTENER_SETTINGS")
}
