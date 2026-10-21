package com.example.agent.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.agent.ui.theme.PillShape
import com.example.agent.ui.theme.SpideySpacing
import com.example.agent.ui.theme.spideyColors

/** A pill that is solid blue when selected and outlined white otherwise. */
@Composable
fun SpideyChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.spideyColors
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = PillShape,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onBackground
        },
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else colors.border,
        ),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

/**
 * A horizontally scrolling row of chips. Pass a null [selected] for rows whose chips are
 * plain suggestions rather than a filter.
 */
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
