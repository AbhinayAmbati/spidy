package com.example.agent.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val SPOKES = 6

/**
 * The Spidey mark: a hexagonal web of six spokes and two rings, drawn with strokes so it
 * stays sharp at any size and can draw itself in. [progress] runs from 0 (nothing) to 1.
 */
@Composable
fun SpideyMark(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    progress: Float = 1f,
) {
    Canvas(modifier = modifier.size(size)) {
        val radius = this.size.minDimension / 2f * 0.92f
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val stroke = Stroke(
            width = this.size.minDimension * 0.06f,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        val p = progress.coerceIn(0f, 1f)

        fun corner(index: Int, r: Float): Offset {
            val angle = (-PI / 2.0) + index * (2.0 * PI / SPOKES)
            return Offset(
                x = center.x + (r * cos(angle)).toFloat(),
                y = center.y + (r * sin(angle)).toFloat(),
            )
        }

        // Spokes grow out from the centre.
        for (i in 0 until SPOKES) {
            val tip = corner(i, radius * p)
            drawLine(color = color, start = center, end = tip, strokeWidth = stroke.width, cap = StrokeCap.Round)
        }

        // Rings fade in once the spokes are most of the way out.
        val ringAlpha = ((p - 0.4f) / 0.6f).coerceIn(0f, 1f)
        if (ringAlpha > 0f) {
            for (ringScale in listOf(0.5f, 1f)) {
                val ring = Path()
                for (i in 0 until SPOKES) {
                    val point = corner(i, radius * ringScale)
                    if (i == 0) ring.moveTo(point.x, point.y) else ring.lineTo(point.x, point.y)
                }
                ring.close()
                drawPath(path = ring, color = color.copy(alpha = ringAlpha), style = stroke)
            }
        }
    }
}
