package com.example.agent.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.Shape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import com.example.agent.ui.theme.SpideyElevation
import com.example.agent.ui.theme.SpideySpacing
import com.example.agent.ui.theme.spideyColors

/**
 * A translucent "glass" surface. Used deliberately for hierarchy — status, floating
 * controls, dialogs, tool cards — never for every component.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    elevation: Dp = SpideyElevation.flat,
    contentPadding: PaddingValues = PaddingValues(SpideySpacing.cardPadding),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.spideyColors
    Column(
        modifier = modifier
            .shadow(elevation, shape, clip = false)
            .clip(shape)
            .background(colors.glass)
            .border(1.dp, colors.border, shape)
            .padding(contentPadding),
        content = content,
    )
}
