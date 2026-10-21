package com.example.agent.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.agent.domain.model.Routine
import com.example.agent.ui.theme.SpideyBlueDeep
import com.example.agent.ui.theme.SpideySpacing
import com.example.agent.ui.theme.spideyColors

/**
 * Saved routines as a row of rings at the top of Home, the way Instagram shows stories:
 * a gradient ring, the routine's initial inside it, a short name underneath. Tapping one
 * runs it.
 */
@Composable
fun RoutineStories(
    routines: List<Routine>,
    onRun: (Routine) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = SpideySpacing.screenGutter),
        horizontalArrangement = Arrangement.spacedBy(SpideySpacing.md),
    ) {
        items(routines, key = { it.id }) { routine ->
            RoutineStory(routine = routine, onClick = { onRun(routine) })
        }
    }
}

@Composable
private fun RoutineStory(routine: Routine, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(72.dp)
            .pressClickable(onClick = onClick, pressedScale = 0.94f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RingAvatar(text = routine.name, size = 68.dp)
        Text(
            text = routine.name,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = SpideySpacing.xxs),
        )
    }
}

/** A circle holding the first letter of [text], wrapped in the blue gradient ring. */
@Composable
fun RingAvatar(text: String, modifier: Modifier = Modifier, size: Dp = 48.dp) {
    val colors = MaterialTheme.spideyColors
    val ring = Brush.sweepGradient(listOf(colors.blue, SpideyBlueDeep, colors.blue))
    Box(
        modifier = modifier
            .size(size)
            .border(width = 2.5.dp, brush = ring, shape = CircleShape)
            .padding(5.dp)
            .clip(CircleShape)
            .background(colors.muted),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text.trim().take(1).uppercase(),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}
