package com.example.agent.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.agent.ui.theme.SpideySpacing
import com.example.agent.ui.theme.spideyColors

/**
 * A bar pinned to the bottom of a screen that holds its primary action(s), set off from the
 * content by a hairline instead of a shadow.
 *
 * Place it below a `weight(1f)` scrolling column so it stays put while content scrolls.
 */
@Composable
fun StickyActionBar(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalDivider(color = MaterialTheme.spideyColors.border)
        Column(
            modifier = Modifier.padding(
                horizontal = SpideySpacing.screenGutter,
                vertical = SpideySpacing.sm,
            ),
            verticalArrangement = Arrangement.spacedBy(SpideySpacing.xs),
            content = content,
        )
    }
}
