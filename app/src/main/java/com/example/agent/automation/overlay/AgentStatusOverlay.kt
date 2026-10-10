package com.example.agent.automation.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.example.agent.R
import com.example.agent.domain.model.TaskState

/**
 * A small, quiet pill pinned above the bottom edge showing what the agent is doing.
 *
 * It exists because the task screen is only visible when the app is in front, and the agent
 * spends almost all of its time inside *other* apps. Without this there is no way to tell
 * whether it is still working.
 *
 * Two window flags are load-bearing, not stylistic:
 *
 * [WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE] - the observer reads
 * `service.rootInActiveWindow`. A focusable overlay becomes the active window, and the agent
 * would then read its own status pill as the screen.
 *
 * [WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE] - taps are dispatched to nodes by their
 * bounds, so anything sitting on top of a control swallows the tap the agent needs to make.
 * A non-touchable pill can never steal an action. Stop stays in the notification.
 */
class AgentStatusOverlay(private val context: Context) {

    private val windowManager: WindowManager? =
        context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager

    private var view: TextView? = null

    /** Whether the user has granted "Display over other apps". */
    fun canShow(): Boolean = Settings.canDrawOverlays(context)

    fun show(text: String, state: TaskState) {
        if (!canShow()) return
        val manager = windowManager ?: return

        val existing = view
        if (existing != null) {
            existing.text = text
            existing.setTextColor(colorFor(state))
            return
        }

        val created = buildView(text, state)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dp(28)
        }

        runCatching { manager.addView(created, params) }
            .onSuccess { view = created }
        // A window that cannot be added must not take the task down with it.
    }

    fun hide() {
        val manager = windowManager ?: return
        view?.let { runCatching { manager.removeView(it) } }
        view = null
    }

    private fun buildView(text: String, state: TaskState): TextView = TextView(context).apply {
        this.text = text
        setTextColor(colorFor(state))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        ResourcesCompat.getFont(context, R.font.inter_medium)?.let { typeface = it }
        val paddingX = dp(16)
        val paddingY = dp(9)
        setPadding(paddingX, paddingY, paddingX, paddingY)
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
        background = GradientDrawable().apply {
            cornerRadius = dp(22).toFloat()
            setColor(PILL_COLOR)
        }
        // Belt and braces alongside FLAG_NOT_FOCUSABLE: never appear in the accessibility tree.
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }

    private fun colorFor(state: TaskState): Int = when (state) {
        TaskState.FAILED, TaskState.CANCELLED -> OUTCOME_BAD
        TaskState.COMPLETED -> OUTCOME_GOOD
        TaskState.PAUSED, TaskState.WAITING_FOR_CONFIRMATION -> MUTED
        else -> WORKING
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private companion object {
        /** The app's near-black, at 92% so a sliver of whatever is behind it shows through. */
        const val PILL_COLOR = 0xEB0D0D0D.toInt()
        const val WORKING = 0xFFF5F5F5.toInt()
        const val MUTED = 0xFFA5A5A0.toInt()
        const val OUTCOME_GOOD = 0xFFA77B5A.toInt()
        const val OUTCOME_BAD = 0xFFD32F2F.toInt()
    }
}
