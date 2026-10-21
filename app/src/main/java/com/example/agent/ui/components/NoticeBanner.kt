package com.example.agent.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.example.agent.ui.theme.SpideySpacing
import com.example.agent.ui.theme.spideyColors

/**
 * A slim notice under the top bar for something the user needs to fix, with one action.
 * It replaces a full card so the screen stays about the conversation, not about setup.
 */
@Composable
fun NoticeBanner(
    text: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
) {
    val colors = MaterialTheme.spideyColors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SpideySpacing.screenGutter)
            .clip(MaterialTheme.shapes.medium)
            .background(colors.muted)
            .padding(start = SpideySpacing.md, end = SpideySpacing.xxs, top = SpideySpacing.xxs, bottom = SpideySpacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError) colors.error else MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        TextAction(text = actionLabel, onClick = onAction)
    }
}
