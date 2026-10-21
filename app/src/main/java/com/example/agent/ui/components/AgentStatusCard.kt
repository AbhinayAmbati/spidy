package com.example.agent.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.agent.domain.model.TaskState
import com.example.agent.ui.theme.LocalMotionEnabled
import com.example.agent.ui.theme.PillShape
import com.example.agent.ui.theme.SpideySpacing
import com.example.agent.ui.theme.spideyColors

/**
 * The live status banner. It shows what the agent is *doing* — never its private
 * chain-of-thought. Blue while working, green when done, red when it failed.
 */
@Composable
fun AgentStatusCard(
    state: TaskState,
    message: String?,
    step: Int,
    total: Int,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.spideyColors
    val motion = LocalMotionEnabled.current
    val working = state.isWorking ||
        state == TaskState.WAITING_FOR_CONFIRMATION ||
        state == TaskState.PAUSED

    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulseAlpha",
    )
    val accent = when (state) {
        TaskState.FAILED -> colors.error
        TaskState.COMPLETED -> colors.success
        TaskState.WAITING_FOR_CONFIRMATION -> colors.warning
        else -> colors.blue
    }

    HeroCard(
        title = stateLabel(state),
        body = message ?: "Ready when you are.",
        accent = accent,
        modifier = modifier,
    ) {
        ProgressBar(
            fraction = if (total <= 0) 0f else (step.toFloat() / total.toFloat()).coerceIn(0f, 1f),
            modifier = Modifier.fillMaxWidth(),
            track = Color.White.copy(alpha = 0.3f),
            fill = Color.White,
        )
        Spacer(Modifier.height(SpideySpacing.sm))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .alpha(if (working && motion) pulse else 1f)
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .semantics { contentDescription = stateLabel(state) },
                )
                Spacer(Modifier.width(SpideySpacing.xs))
                Text(
                    text = "Step ${step.coerceAtLeast(0)} of $total",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                )
            }
            if (working) {
                DarkPillButton(text = "Stop", onClick = onStop)
            }
        }
    }
}

@Composable
fun ProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    track: Color = MaterialTheme.spideyColors.border,
    fill: Color = MaterialTheme.colorScheme.primary,
) {
    val motion = LocalMotionEnabled.current
    val animated by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(if (motion) 400 else 0),
        label = "progress",
    )
    Box(
        modifier = modifier
            .height(8.dp)
            .clip(PillShape)
            .background(track)
            .semantics { contentDescription = "Progress" },
    ) {
        Box(
            Modifier
                .fillMaxWidth(animated.coerceIn(0f, 1f))
                .height(8.dp)
                .clip(PillShape)
                .background(fill),
        )
    }
}

private fun stateLabel(state: TaskState): String = when (state) {
    TaskState.IDLE -> "Agent idle"
    TaskState.PLANNING -> "Agent thinking"
    TaskState.EXECUTING -> "Agent working"
    TaskState.OBSERVING -> "Agent observing"
    TaskState.WAITING_FOR_CONFIRMATION -> "Waiting for you"
    TaskState.COMPLETED -> "Task completed"
    TaskState.FAILED -> "Task failed"
    TaskState.CANCELLED -> "Task stopped"
    TaskState.PAUSED -> "Paused"
}
