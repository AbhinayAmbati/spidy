package com.example.agent.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.agent.ui.theme.HeroShape
import com.example.agent.ui.theme.SpideySpacing
import com.example.agent.ui.theme.spideyColors

/**
 * The big rounded banner, in the spirit of Instamart's hero carousel: a bold headline,
 * a supporting line and room for one call to action in [actions].
 *
 * [accent] is the banner's base colour — blue by default, green or red for outcomes.
 */
@Composable
fun HeroCard(
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    accent: Color = MaterialTheme.spideyColors.blue,
    actions: (@Composable ColumnScope.() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(HeroShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(accent, accent.darker()),
                ),
            )
            .padding(SpideySpacing.lg),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
        )
        if (body != null) {
            Spacer(Modifier.height(SpideySpacing.xs))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.9f),
            )
        }
        if (actions != null) {
            Spacer(Modifier.height(SpideySpacing.md))
            actions()
        }
    }
}

/** Darkens a colour slightly so the banner gradient has depth on any accent. */
private fun Color.darker(): Color =
    Color(red = red * 0.78f, green = green * 0.78f, blue = blue * 0.78f, alpha = 1f)
