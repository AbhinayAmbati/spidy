package com.example.agent.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.agent.ui.theme.PillShape
import com.example.agent.ui.theme.SpideySpacing
import com.example.agent.ui.theme.spideyColors

/** A pill that fades to solid ink when selected and sits on a muted fill otherwise. */
@Composable
fun SpideyChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.spideyColors
    val tick = rememberTick()
    val container by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onBackground else colors.muted,
        label = "chipContainer",
    )
    val content by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onBackground,
        label = "chipContent",
    )
    Surface(
        onClick = {
            if (!selected) tick()
            onClick()
        },
        modifier = modifier,
        shape = PillShape,
        color = container,
        contentColor = content,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
        )
    }
}

/** A horizontally scrolling row of chips acting as a single-choice filter. */
@Composable
fun ChipRow(
    labels: List<String>,
    selected: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = SpideySpacing.screenGutter),
) {
    LazyRow(
        modifier = modifier,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(SpideySpacing.xs),
    ) {
        items(labels) { label ->
            SpideyChip(
                label = label,
                selected = label == selected,
                onClick = { onSelect(label) },
            )
        }
    }
}
