package com.example.agent.automation

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.agent.automation.accessibility.AccessibilityController
import com.example.agent.automation.accessibility.AccessibilityController.ActionResult
import com.example.agent.automation.accessibility.NotificationBridge
import com.example.agent.automation.accessibility.TargetMatcher
import com.example.agent.core.control.AgentHalt
import com.example.agent.domain.model.ScreenSnapshot

/**
 * High-level device operations. Tools call this; it decides whether an operation
 * is best served by an Intent, the PackageManager, or the accessibility layer.
 *
 *   Agent Tool -> AndroidAutomationManager -> AccessibilityController -> Service -> UI
 */
class AndroidAutomationManager(
    private val context: Context,
    private val controller: AccessibilityController,
) {

    // ---------------------------------------------------------------- apps

    fun isAppInstalled(packageName: String): Boolean =
        context.packageManager.getLaunchIntentForPackage(packageName) != null

    fun appLabel(packageName: String?): String? {
        if (packageName.isNullOrBlank()) return null
        return runCatching {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(info).toString()
        }.getOrNull()
    }

    /**
     * Resolves a user/model-supplied app name ("Chrome", "WhatsApp") to a package
     * name by matching launchable app labels. Returns null if nothing matches.
     */
    fun resolvePackage(query: String): String? {
        val q = query.trim()
        if (q.isEmpty()) return null
        if (isAppInstalled(q)) return q
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = runCatching { pm.queryIntentActivities(intent, 0) }.getOrDefault(emptyList())
        val needle = q.lowercase()
        return apps.firstOrNull { it.loadLabel(pm).toString().lowercase() == needle }?.activityInfo?.packageName
            ?: apps.firstOrNull { it.loadLabel(pm).toString().lowercase().contains(needle) }?.activityInfo?.packageName
    }

    /** Launchable apps, for the "open an app" affordance and package suggestions. */
    fun installedLaunchableApps(): List<Pair<String, String>> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return runCatching { pm.queryIntentActivities(intent, 0) }
            .getOrDefault(emptyList())
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .distinctBy { it.first }
            .sortedBy { it.second }
    }

    /** Launches an installed app by package name using an official launch intent. */
    fun openApp(packageName: String): LaunchOutcome {
        if (AgentHalt.isStopped) return LaunchOutcome.Failed("Stopped by the user.")
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return LaunchOutcome.NotInstalled
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(intent)
            LaunchOutcome.Launched
        }.getOrElse { LaunchOutcome.Failed(it.message) }
    }

    /**
     * Android does not allow one app to force-stop another (that would be a
     * security hole). We can only bring the user back to the home screen, which
     * is the safe, documented alternative.
     */
    suspend fun closeApp(packageName: String): String {
        if (AgentHalt.isStopped) return "Stopped by the user."
        controller.pressHome()
        return "Android does not allow apps to force-stop other apps. Returned to the home screen instead."
    }

    fun launchUrl(url: String): LaunchOutcome {
        if (AgentHalt.isStopped) return LaunchOutcome.Failed("Stopped by the user.")
        val normalized = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(normalized))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(intent)
            LaunchOutcome.Launched
        }.getOrElse { LaunchOutcome.Failed(it.message) }
    }

    // ---------------------------------------------------------------- screen

    suspend fun readScreen(): ScreenSnapshot = controller.snapshot()

    /** Waits for the UI to stop changing, so the next observation is stable. */
    suspend fun awaitUiSettled(quietMs: Long = 500, timeoutMs: Long = 4_000): Boolean =
        controller.awaitIdle(quietMs, timeoutMs)

    fun isAccessibilityReady(): Boolean = controller.isAvailable()

    /** Package of the app currently on screen, or null if unknown. */
    suspend fun currentPackage(): String? = controller.currentPackage()

    suspend fun findText(query: String): Boolean {
        val snapshot = controller.snapshot()
        return snapshot.elements.any {
            it.text?.contains(query, ignoreCase = true) == true ||
                it.contentDescription?.contains(query, ignoreCase = true) == true
        }
    }

    suspend fun click(target: TargetMatcher): ActionResult = controller.click(target)
    suspend fun longClick(target: TargetMatcher): ActionResult = controller.longClick(target)
    suspend fun typeText(target: TargetMatcher, text: String): ActionResult =
        controller.setText(target, text)
    suspend fun scroll(direction: String?): ActionResult = controller.scroll(direction)
    suspend fun swipe(fromX: Int, fromY: Int, toX: Int, toY: Int, durationMs: Long): ActionResult =
        controller.swipe(fromX, fromY, toX, toY, durationMs)
    suspend fun pressBack(): ActionResult = controller.pressBack()
    suspend fun pressHome(): ActionResult = controller.pressHome()

    // ---------------------------------------------------------------- notifications

    fun notificationsAvailable(): Boolean = NotificationBridge.isConnected()
    fun recentNotifications() = NotificationBridge.recent()

    sealed interface LaunchOutcome {
        data object Launched : LaunchOutcome
        data object NotInstalled : LaunchOutcome
        data class Failed(val reason: String?) : LaunchOutcome
    }
}
