package com.example.agent.automation.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.View
import kotlin.math.sin

/**
 * A soft, translucent blue aurora that rises from the bottom of the screen, like the assistant
 * "listening" glow: a few blurred blobs of blue drift sideways along the bottom edge, with a
 * brighter rim hugging the edge and a faint glow down the sides. It only draws gradients, so it
 * never needs touch or focus.
 */
class GlowEdgeView(context: Context) : View(context) {

    private val density = context.resources.displayMetrics.density
    private val blobPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sidePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var intensity = 0f
    private var phase = 0f

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }

    /** [alpha] 0..1 is how strong the glow is; [seconds] moves the blobs. */
    fun update(alpha: Float, seconds: Float) {
        intensity = alpha.coerceIn(0f, 1f)
        phase = seconds
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (intensity <= 0f || width == 0 || height == 0) return
        val w = width.toFloat()
        val h = height.toFloat()
        val a = (intensity * 255).toInt()

        // Blobs: deep blue, bright blue, cyan-blue, indigo; each drifts at its own pace.
        blobPaint.alpha = a
        for (blob in BLOBS) {
            val cx = w * (blob.x + blob.sway * sin(phase * blob.speed + blob.offset))
            val radius = w * blob.radius
            val squash = (h * blob.height) / radius
            blobPaint.shader = RadialGradient(
                cx, h, radius,
                intArrayOf(blob.color, (blob.color and 0x00FFFFFF) or 0x55000000, 0x00000000),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP,
            )
            canvas.save()
            canvas.scale(1f, squash, cx, h)
            canvas.drawCircle(cx, h, radius, blobPaint)
            canvas.restore()
        }

        // Bright rim along the bottom edge.
        val rim = RIM_DP * density
        rimPaint.shader = LinearGradient(0f, h, 0f, h - rim, RIM, 0x00000000, Shader.TileMode.CLAMP)
        rimPaint.alpha = (a * 0.9f).toInt()
        canvas.drawRect(0f, h - rim, w, h, rimPaint)

        // Faint glow down both sides so the light wraps the screen.
        val side = SIDE_DP * density
        sidePaint.alpha = (a * 0.35f).toInt()
        sidePaint.shader = LinearGradient(0f, 0f, side, 0f, SIDE, 0x00000000, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, side, h, sidePaint)
        sidePaint.shader = LinearGradient(w, 0f, w - side, 0f, SIDE, 0x00000000, Shader.TileMode.CLAMP)
        canvas.drawRect(w - side, 0f, w, h, sidePaint)
    }

    private class Blob(
        val x: Float,
        val sway: Float,
        val speed: Float,
        val offset: Float,
        val radius: Float,
        val height: Float,
        val color: Int,
    )

    private companion object {
        val BLOBS = listOf(
            Blob(0.25f, 0.18f, 1.1f, 0.0f, 0.65f, 0.26f, 0xFF1E5BFF.toInt()),
            Blob(0.55f, 0.20f, 0.8f, 2.1f, 0.60f, 0.30f, 0xFF3D8BFF.toInt()),
            Blob(0.80f, 0.16f, 1.3f, 4.0f, 0.55f, 0.24f, 0xFF4B3DFF.toInt()),
            Blob(0.45f, 0.22f, 0.6f, 5.3f, 0.45f, 0.20f, 0xFF4FD0FF.toInt()),
        )
        const val RIM = 0xFFBFE0FF.toInt()
        const val SIDE = 0xFF2F7BFF.toInt()
        const val RIM_DP = 6f
        const val SIDE_DP = 28f
    }
}
