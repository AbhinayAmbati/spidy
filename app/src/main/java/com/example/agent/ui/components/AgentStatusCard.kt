package com.example.agent.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.agent.domain.model.TaskState
import com.example.agent.ui.theme.LocalMotionEnabled
import com.example.agent.ui.theme.SpideySpacing
import com.example.agent.ui.theme.spideyColors

/**
 * The live status panel. It shows what the agent is *doing* — never its private
 * chain-of-thought. Animation is a slow pulse, nothing distracting.
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
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulseAlpha",
    )
    val dotColor by animateColorAsState(
        targetValue = when {
            state == TaskState.FAILED -> colors.error
            state == TaskState.COMPLETED -> colors.success
            state == TaskState.WAITING_FOR_CONFIRMATION -> colors.warning
            else -> colors.brownAccent
        },
        label = "dotColor",
    )

    GlassCard(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .alpha(if (working && motion) pulse else 1f)
                    .width(10.dp)
                    .height(10.dp)
                    .clip(RoundedCornerShape(50))
                    .background(dotColor)
                    .semantics { contentDescription = stateLabel(state) },
            )
            Spacer(Modifier.width(SpideySpacing.sm))
            Text(
                text = stateLabel(state),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        Spacer(Modifier.height(SpideySpacing.sm))

        Text(
            text = message ?: "Ready when you are.",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(Modifier.height(SpideySpacing.md))

        ProgressBar(
            fraction = if (total <= 0) 0f else (step.toFloat() / total.toFloat()).coerceIn(0f, 1f),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(SpideySpacing.xs))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Step ${step.coerceAtLeast(0)} of $total",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
            )
            if (working) {
                SecondaryButton(text = "Stop", onClick = onStop)
            }
        }
    }
}

@Composable
fun ProgressBar(fraction: Float, modifier: Modifier = Modifier) {
    val motion = LocalMotionEnabled.current
    val animated by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(if (motion) 400 else 0),
        label = "progress",
    )
    val track = MaterialTheme.spideyColors.border
    val fill = MaterialTheme.spideyColors.brownAccent
    Box(
        modifier = modifier
            .height(6.dp)
            .clip(RoundedCornerShape(50))
            .background(track)
            .semantics { contentDescription = "Progress" },
    ) {
        Box(
            Modifier
                .fillMaxWidth(animated.coerceIn(0f, 1f))
                .height(6.dp)
                .clip(RoundedCornerShape(50))
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
