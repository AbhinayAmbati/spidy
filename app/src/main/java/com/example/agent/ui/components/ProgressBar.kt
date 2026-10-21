package com.example.agent.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.agent.ui.theme.LocalMotionEnabled
import com.example.agent.ui.theme.PillShape
import com.example.agent.ui.theme.spideyColors

/** A thin rounded progress track whose fill glides to each new value. */
@Composable
fun ProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    thickness: Dp = 3.dp,
    track: Color = MaterialTheme.spideyColors.muted,
    fill: Color = MaterialTheme.colorScheme.primary,
) {
    val motion = LocalMotionEnabled.current
    val animated by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(if (motion) 450 else 0),
        label = "progress",
    )
    Box(
        modifier = modifier
            .height(thickness)
            .clip(PillShape)
            .background(track)
            .semantics { contentDescription = "Progress" },
    ) {
        Box(
            Modifier
                .fillMaxWidth(animated.coerceIn(0f, 1f))
                .height(thickness)
                .clip(PillShape)
                .background(fill),
        )
    }
}
