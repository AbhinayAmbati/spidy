package com.example.agent.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.agent.domain.model.MemoryEntry
import com.example.agent.ui.components.EmptyState
import com.example.agent.ui.components.ScreenTopBar
import com.example.agent.ui.components.SecondaryButton
import com.example.agent.ui.components.SectionHeader
import com.example.agent.ui.components.SpideyCard
import com.example.agent.ui.components.StickyActionBar
import com.example.agent.ui.components.SwitchRow
import com.example.agent.ui.preview.PreviewData
import com.example.agent.ui.theme.SpideySpacing
import com.example.agent.ui.theme.SpideyTheme
import com.example.agent.ui.theme.spideyColors

/**
 * Everything Spidey has learned, in one place, with a delete button on every line.
 * Nothing here is ever synced anywhere.
 */
@Composable
fun MemoryScreen(
    onBack: () -> Unit,
    viewModel: MemoryViewModel = hiltViewModel(),
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val enabled by viewModel.memoryEnabled.collectAsStateWithLifecycle()

    MemoryContent(
        entries = entries,
        enabled = enabled,
        onBack = onBack,
        onEnabledChange = viewModel::setMemoryEnabled,
        onForget = viewModel::forget,
        onClearAll = viewModel::clearAll,
    )
}

@Composable
private fun MemoryContent(
    entries: List<MemoryEntry>,
    enabled: Boolean,
    onBack: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onForget: (String) -> Unit,
    onClearAll: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(title = "Memory", onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SpideySpacing.screenGutter)
                .padding(bottom = SpideySpacing.lg),
            verticalArrangement = Arrangement.spacedBy(SpideySpacing.md),
        ) {
            SpideyCard(modifier = Modifier.fillMaxWidth()) {
                SwitchRow(
                    title = "Remember preferences",
                    description = "When off, Spidey stops learning new app shortcuts. Existing entries stay until you delete them.",
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                )
            }

            SectionHeader(
                title = "Learned shortcuts",
                subtitle = "App names Spidey can resolve next time.",
                modifier = Modifier.fillMaxWidth(),
            )

            if (entries.isEmpty()) {
                EmptyState(
                    title = "Nothing remembered yet",
                    body = "Run a task that opens an app and the shortcut will appear here.",
                )
            } else {
                entries.forEach { entry -> MemoryRow(entry = entry, onForget = { onForget(entry.key) }) }
            }
        }

        if (entries.isNotEmpty()) {
            StickyActionBar {
                SecondaryButton(
                    text = "Clear all memory",
                    onClick = onClearAll,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun MemoryRow(entry: MemoryEntry, onForget: () -> Unit) {
    SpideyCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = entry.key,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = entry.value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.spideyColors.textSecondary,
                )
            }
            IconButton(onClick = onForget) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Forget ${entry.key}",
                    tint = MaterialTheme.spideyColors.textSecondary,
                )
            }
        }
    }
}

@Preview(name = "Memory", showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun MemoryPreview() {
    SpideyTheme {
        MemoryContent(
            entries = PreviewData.memory,
            enabled = true,
            onBack = {},
            onEnabledChange = {},
            onForget = {},
            onClearAll = {},
        )
    }
}

@Preview(name = "Memory - empty", showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun MemoryEmptyPreview() {
    SpideyTheme {
        MemoryContent(
            entries = emptyList(),
            enabled = false,
            onBack = {},
            onEnabledChange = {},
            onForget = {},
            onClearAll = {},
        )
    }
}
