package com.example.agent.automation.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.view.View

/**
 * A translucent blue glow along all four screen edges, like the assistant "listening" ring.
 * It draws nothing but gradients, so it never needs touch or focus.
 */
class GlowEdgeView(context: Context) : View(context) {

    private val paints = Array(4) { Paint(Paint.ANTI_ALIAS_FLAG) }
    private val thickness = 64f * context.resources.displayMetrics.density

    /** 0..1; set by the overlay on each tick. */
    var glowAlpha: Float = 0f
        set(value) {
            val clamped = value.coerceIn(0f, 1f)
            if (clamped == field) return
            field = clamped
            invalidate()
        }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val w0 = w.toFloat()
        val h0 = h.toFloat()
        paints[0].shader = LinearGradient(0f, 0f, 0f, thickness, EDGE, CLEAR, Shader.TileMode.CLAMP)
        paints[1].shader = LinearGradient(0f, h0, 0f, h0 - thickness, EDGE, CLEAR, Shader.TileMode.CLAMP)
        paints[2].shader = LinearGradient(0f, 0f, thickness, 0f, EDGE, CLEAR, Shader.TileMode.CLAMP)
        paints[3].shader = LinearGradient(w0, 0f, w0 - thickness, 0f, EDGE, CLEAR, Shader.TileMode.CLAMP)
    }

    override fun onDraw(canvas: Canvas) {
        if (glowAlpha <= 0f) return
        val a = (glowAlpha * 255).toInt()
        paints.forEach { it.alpha = a }
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawRect(0f, 0f, w, thickness, paints[0])
        canvas.drawRect(0f, h - thickness, w, h, paints[1])
        canvas.drawRect(0f, 0f, thickness, h, paints[2])
        canvas.drawRect(w - thickness, 0f, w, h, paints[3])
    }

    private companion object {
        const val EDGE = 0xFF2F7BFF.toInt()
        const val CLEAR = 0x002F7BFF
    }
}
