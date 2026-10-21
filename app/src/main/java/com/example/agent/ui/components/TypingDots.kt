package com.example.agent.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.agent.ui.theme.LocalMotionEnabled
import com.example.agent.ui.theme.spideyColors

private const val DOT_COUNT = 3
private const val CYCLE_MS = 1200
private const val STAGGER_MS = 160

/** Three dots that pulse in sequence while the agent is working. Static when motion is off. */
@Composable
fun TypingDots(modifier: Modifier = Modifier) {
    val motion = LocalMotionEnabled.current
    val transition = rememberInfiniteTransition(label = "typing")
    Row(
        modifier = modifier
            .padding(vertical = 6.dp)
            .semantics { contentDescription = "Working" },
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        for (index in 0 until DOT_COUNT) {
            val alpha by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 0.25f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = CYCLE_MS
                        0.25f at index * STAGGER_MS
                        1f at index * STAGGER_MS + 280
                        0.25f at index * STAGGER_MS + 560
                    },
                    repeatMode = RepeatMode.Restart,
                ),
                label = "dot$index",
            )
            Dot(alpha = if (motion) alpha else 0.6f)
        }
    }
}

@Composable
private fun Dot(alpha: Float) {
    Box(
        modifier = Modifier
            .size(7.dp)
            .alpha(alpha)
            .clip(CircleShape)
            .background(MaterialTheme.spideyColors.textSecondary),
    )
}
