package com.example.agent.automation.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import com.example.agent.core.voice.EyeStyle
import com.example.agent.core.voice.MascotPose
import com.example.agent.core.voice.MouthStyle
import kotlin.math.sin

/**
 * Spidey's little blue character: a glossy blue bean with two eyes, a mouth and four stubby legs.
 * It only draws a [MascotPose]; how it behaves in each state lives in `MascotAnimator`.
 */
class MascotView(context: Context) : View(context) {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shine = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66FFFFFF }
    private val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = INK
        style = Paint.Style.FILL
    }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = INK
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val legs = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = LEG
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = DOT }

    private val body = RectF()
    private val oval = RectF()

    private var pose: MascotPose? = null
    private var seconds = 0f

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }

    fun update(next: MascotPose, time: Float) {
        pose = next
        seconds = time
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val s = minOf(w, h).toFloat()
        val bodyW = s * 0.80f
        val bodyH = s * 0.66f
        val left = (w - bodyW) / 2f
        val top = h - s * 0.16f - bodyH
        body.set(left, top, left + bodyW, top + bodyH)
        fill.shader = RadialGradient(
            body.left + bodyW * 0.32f, body.top + bodyH * 0.25f, bodyW * 0.95f,
            intArrayOf(0xFF9CD0FF.toInt(), 0xFF3D83FF.toInt(), 0xFF1D3FD8.toInt()),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP,
        )
    }

    override fun onDraw(canvas: Canvas) {
        val p = pose ?: return
        val s = minOf(width, height).toFloat()
        if (s <= 0f) return
        val cx = body.centerX()
        val feet = body.bottom

        canvas.save()
        canvas.translate(0f, -p.hop * s)
        canvas.rotate(p.tiltDegrees, cx, feet)
        canvas.scale(1f - (p.scale - 1f) * 0.5f, p.scale, cx, feet)

        drawLegs(canvas, p, s)
        canvas.drawRoundRect(body, body.height() * 0.46f, body.height() * 0.46f, fill)
        oval.set(
            body.left + body.width() * 0.16f, body.top + body.height() * 0.10f,
            body.left + body.width() * 0.46f, body.top + body.height() * 0.30f,
        )
        canvas.drawOval(oval, shine)
        drawEyes(canvas, p, s)
        drawMouth(canvas, p, s)
        canvas.restore()

        drawDots(canvas, p, s)
    }

    private fun drawLegs(canvas: Canvas, p: MascotPose, s: Float) {
        legs.strokeWidth = s * 0.045f
        val y = body.bottom - s * 0.02f
        val reach = s * 0.12f
        for (i in 0 until 4) {
            val side = if (i < 2) -1f else 1f
            val x = body.centerX() + side * body.width() * (if (i % 2 == 0) 0.18f else 0.34f)
            val swing = p.legSwing * sin(seconds * 14f + i * 1.6f) * reach * 0.8f
            canvas.drawLine(x, y, x + side * reach * 0.4f + swing, y + reach, legs)
        }
    }

    private fun drawEyes(canvas: Canvas, p: MascotPose, s: Float) {
        val eyeY = body.top + body.height() * 0.42f
        val gap = body.width() * 0.21f
        for (side in intArrayOf(-1, 1)) {
            val ex = body.centerX() + side * gap
            when (p.eyes) {
                EyeStyle.Open, EyeStyle.Wide -> {
                    val r = s * (if (p.eyes == EyeStyle.Wide) 0.115f else 0.095f)
                    val open = if (p.eyes == EyeStyle.Open) 1f - p.blink * 0.92f else 1f
                    oval.set(ex - r, eyeY - r * open, ex + r, eyeY + r * open)
                    canvas.drawOval(oval, white)
                    val pr = r * 0.55f
                    canvas.drawCircle(ex + p.lookX * r * 0.4f, eyeY + p.lookY * r * 0.4f * open, pr, ink)
                }
                EyeStyle.Squint -> {
                    val r = s * 0.095f
                    oval.set(ex - r, eyeY - r * 0.4f, ex + r, eyeY + r * 0.4f)
                    canvas.drawOval(oval, white)
                    canvas.drawCircle(ex + p.lookX * r * 0.5f, eyeY, r * 0.38f, ink)
                }
                EyeStyle.Happy -> {
                    stroke.strokeWidth = s * 0.05f
                    val r = s * 0.085f
                    oval.set(ex - r, eyeY - r * 0.6f, ex + r, eyeY + r * 1.4f)
                    canvas.drawArc(oval, 200f, 140f, false, stroke)
                }
                EyeStyle.Cross -> {
                    stroke.strokeWidth = s * 0.045f
                    val r = s * 0.07f
                    canvas.drawLine(ex - r, eyeY - r, ex + r, eyeY + r, stroke)
                    canvas.drawLine(ex - r, eyeY + r, ex + r, eyeY - r, stroke)
                }
            }
        }
    }

    private fun drawMouth(canvas: Canvas, p: MascotPose, s: Float) {
        val cx = body.centerX()
        val y = body.top + body.height() * 0.74f
        val w = s * 0.12f
        stroke.strokeWidth = s * 0.04f
        when (p.mouth) {
            MouthStyle.Flat -> canvas.drawLine(cx - w * 0.6f, y, cx + w * 0.6f, y, stroke)
            MouthStyle.Open -> {
                val h = s * (0.03f + 0.10f * p.mouthOpen)
                oval.set(cx - w * 0.55f, y - h, cx + w * 0.55f, y + h)
                canvas.drawOval(oval, ink)
            }
            MouthStyle.Smile -> {
                oval.set(cx - w, y - w * 0.8f, cx + w, y + w * 0.8f)
                canvas.drawArc(oval, 20f, 140f, false, stroke)
            }
            MouthStyle.Frown -> {
                oval.set(cx - w * 0.8f, y, cx + w * 0.8f, y + w * 1.4f)
                canvas.drawArc(oval, 200f, 140f, false, stroke)
            }
        }
    }

    private fun drawDots(canvas: Canvas, p: MascotPose, s: Float) {
        if (p.dots <= 0) return
        val r = s * 0.035f
        val y = s * 0.07f
        val startX = body.centerX() + body.width() * 0.12f
        for (i in 0 until p.dots.coerceAtMost(3)) {
            canvas.drawCircle(startX + i * r * 3.2f, y - i * r * 0.6f, r, dot)
        }
    }

    private companion object {
        const val INK = 0xFF0B1B4D.toInt()
        const val LEG = 0xFF2A5BE8.toInt()
        const val DOT = 0xFF8FC4FF.toInt()
    }
}
