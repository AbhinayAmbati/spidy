package com.example.agent.automation.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.example.agent.R
import com.example.agent.core.voice.AsciiFace
import com.example.agent.core.voice.GlowModel
import com.example.agent.core.voice.VoiceState
import com.example.agent.core.voice.VoiceStateMapper
import com.example.agent.domain.model.TaskState

/**
 * A small, quiet pill pinned above the bottom edge showing what the agent is doing, with an
 * optional ASCII character beside the text and an optional blue glow around the screen edges
 * while the microphone is listening.
 *
 * It exists because the task screen is only visible when the app is in front, and the agent
 * spends almost all of its time inside *other* apps. Without this there is no way to tell
 * whether it is still working.
 *
 * Two window flags are load-bearing, not stylistic, and both windows (pill and glow) use them:
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

    private val handler = Handler(Looper.getMainLooper())

    private var pill: LinearLayout? = null
    private var faceView: TextView? = null
    private var textView: TextView? = null
    private var glow: GlowEdgeView? = null

    private var glowEnabled = true
    private var faceEnabled = true

    private var state: VoiceState = VoiceState.Idle
    private var text = ""
    private var textColor = WORKING
    private var level = 0f
    private var tick = 0
    private var shownAlpha = 0f

    private val ticker = object : Runnable {
        override fun run() {
            tick++
            render()
            handler.postDelayed(this, TICK_MILLIS)
        }
    }

    /** Whether the user has granted "Display over other apps". */
    fun canShow(): Boolean = Settings.canDrawOverlays(context)

    /** The two optional visuals; the status pill itself is always shown. */
    fun setOptions(glow: Boolean, face: Boolean) {
        glowEnabled = glow
        faceEnabled = face
        if (pill != null) render()
    }

    /** Shows a task's status, as before. */
    fun show(text: String, state: TaskState) {
        present(VoiceStateMapper.fromTask(state), text, colorFor(state))
    }

    /** Shows the voice flow's own status. */
    fun showVoice(state: VoiceState) {
        present(state, VoiceStateMapper.label(state), colorFor(state))
    }

    /** Microphone loudness 0..1; read on the next tick. */
    fun setLevel(value: Float) {
        level = value
    }

    fun hide() {
        handler.removeCallbacks(ticker)
        val manager = windowManager
        if (manager != null) {
            pill?.let { runCatching { manager.removeView(it) } }
            glow?.let { runCatching { manager.removeView(it) } }
        }
        pill = null
        faceView = null
        textView = null
        glow = null
        shownAlpha = 0f
    }

    private fun present(next: VoiceState, nextText: String, color: Int) {
        if (!canShow()) return
        val manager = windowManager ?: return
        state = next
        text = nextText
        textColor = color

        if (pill == null) {
            val created = buildPill()
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType(),
                FLAGS,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                y = dp(28)
            }
            // A window that cannot be added must not take the task down with it.
            runCatching { manager.addView(created, params) }.onSuccess {
                pill = created
                handler.removeCallbacks(ticker)
                handler.postDelayed(ticker, TICK_MILLIS)
            }
        }
        render()
    }

    private fun render() {
        val face = faceView ?: return
        val label = textView ?: return
        face.visibility = if (faceEnabled) View.VISIBLE else View.GONE
        face.text = AsciiFace.frame(state, tick / FACE_TICKS)
        face.setTextColor(textColor)
        label.text = text
        label.setTextColor(textColor)

        val target = if (glowEnabled) GlowModel.alpha(state, level, tick * TICK_MILLIS / 1000f) else 0f
        shownAlpha += (target - shownAlpha) * EASE
        if (shownAlpha > MIN_ALPHA) {
            ensureGlow()
            glow?.glowAlpha = shownAlpha
        } else {
            shownAlpha = 0f
            removeGlow()
        }
    }

    private fun ensureGlow() {
        if (glow != null) return
        val manager = windowManager ?: return
        val view = GlowEdgeView(context)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType(),
            FLAGS or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        runCatching { manager.addView(view, params) }.onSuccess { glow = view }
    }

    private fun removeGlow() {
        val view = glow ?: return
        windowManager?.let { runCatching { it.removeView(view) } }
        glow = null
    }

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

    private fun buildPill(): LinearLayout {
        val face = TextView(context).apply {
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            maxLines = 1
            minEms = FACE_EMS
            setPadding(0, 0, dp(8), 0)
        }
        val label = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            ResourcesCompat.getFont(context, R.font.inter_medium)?.let { typeface = it }
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        faceView = face
        textView = label
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(face)
            addView(label)
            val paddingX = dp(16)
            val paddingY = dp(9)
            setPadding(paddingX, paddingY, paddingX, paddingY)
            background = GradientDrawable().apply {
                cornerRadius = dp(22).toFloat()
                setColor(PILL_COLOR)
            }
            // Belt and braces alongside FLAG_NOT_FOCUSABLE: never appear in the accessibility tree.
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
    }

    private fun colorFor(state: TaskState): Int = when (state) {
        TaskState.FAILED, TaskState.CANCELLED -> OUTCOME_BAD
        TaskState.COMPLETED -> OUTCOME_GOOD
        TaskState.PAUSED, TaskState.WAITING_FOR_CONFIRMATION -> MUTED
        else -> WORKING
    }

    private fun colorFor(state: VoiceState): Int = when (state) {
        is VoiceState.Error -> OUTCOME_BAD
        VoiceState.Done -> OUTCOME_GOOD
        else -> WORKING
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private companion object {
        const val FLAGS = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

        const val TICK_MILLIS = 80L
        const val FACE_TICKS = 3
        const val FACE_EMS = 8
        const val EASE = 0.35f
        const val MIN_ALPHA = 0.01f

        /** The app's near-black, at 92% so a sliver of whatever is behind it shows through. */
        const val PILL_COLOR = 0xEB0D0D0D.toInt()
        const val WORKING = 0xFFF5F5F5.toInt()
        const val MUTED = 0xFFA5A5A0.toInt()
        const val OUTCOME_GOOD = 0xFFA77B5A.toInt()
        const val OUTCOME_BAD = 0xFFD32F2F.toInt()
    }
}
