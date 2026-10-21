package com.example.agent.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.example.agent.ui.theme.SpideySpacing
import com.example.agent.ui.theme.spideyColors

/**
 * The standard white tile: large rounded corners, a hairline border, no shadow.
 * Every grouped block of content in the app sits on one of these.
 */
@Composable
fun SpideyCard(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    contentPadding: PaddingValues = PaddingValues(SpideySpacing.cardPadding),
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.spideyColors.border),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}

/** A [SpideyCard] that can be tapped, with the ripple clipped to the card shape. */
@Composable
fun ClickableSpideyCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    contentPadding: PaddingValues = PaddingValues(SpideySpacing.cardPadding),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.spideyColors.border),
    ) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}
