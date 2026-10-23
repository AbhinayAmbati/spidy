package com.example.agent.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.agent.automation.overlay.GlowEdgeView
import com.example.agent.automation.overlay.MascotView
import com.example.agent.core.voice.GlowModel
import com.example.agent.core.voice.MascotAnimator
import com.example.agent.core.voice.VoiceState
import com.example.agent.core.voice.VoiceStateMapper
import com.example.agent.ui.theme.spideyColors

/**
 * The listening glow and the blue character drawn inside the app itself, so they show even when
 * "Show over other apps" has not been granted. The overlay window does the same job over other
 * apps; the screen only uses this one while that permission is missing.
 */
@Composable
fun InAppVoiceVisuals(
    state: VoiceState,
    level: Float,
    glow: Boolean,
    face: Boolean,
    modifier: Modifier = Modifier,
) {
    val currentState by rememberUpdatedState(state)
    val currentLevel by rememberUpdatedState(level)
    val currentGlow by rememberUpdatedState(glow)
    var seconds by remember { mutableFloatStateOf(0f) }
    var shown by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        var start = 0L
        while (true) {
            withFrameNanos { now ->
                if (start == 0L) start = now
                seconds = (now - start) / 1_000_000_000f
                val target = if (currentGlow) GlowModel.alpha(currentState, currentLevel, seconds) else 0f
                shown += (target - shown) * 0.12f
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (shown > 0.01f) {
            AndroidView(
                factory = { GlowEdgeView(it) },
                update = { it.update(shown, seconds) },
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (face && state != VoiceState.Idle) {
            Column(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 104.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AndroidView(
                    factory = { MascotView(it) },
                    update = { it.update(MascotAnimator.pose(state, level, seconds), seconds) },
                    modifier = Modifier.size(72.dp),
                )
                Text(
                    text = VoiceStateMapper.label(state),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.spideyColors.textSecondary,
                )
            }
        }
    }
}
