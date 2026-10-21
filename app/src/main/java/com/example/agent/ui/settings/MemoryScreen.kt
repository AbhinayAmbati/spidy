package com.example.agent.ui.settings

import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.agent.domain.model.MemoryEntry
import com.example.agent.ui.components.ConfirmDialog
import com.example.agent.ui.components.EmptyState
import com.example.agent.ui.components.ListRow
import com.example.agent.ui.components.RowDivider
import com.example.agent.ui.components.ScreenTopBar
import com.example.agent.ui.components.SecondaryButton
import com.example.agent.ui.components.SectionLabel
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
    var confirmClear by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(title = "Memory", onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = SpideySpacing.lg),
        ) {
            SwitchRow(
                title = "Remember preferences",
                subtitle = "When off, Spidey stops learning new app shortcuts. Existing entries stay until you delete them.",
                checked = enabled,
                onCheckedChange = onEnabledChange,
            )

            SectionLabel(text = "Learned shortcuts")

            if (entries.isEmpty()) {
                EmptyState(
                    title = "Nothing remembered yet",
                    body = "Run a task that opens an app and the shortcut will appear here.",
                )
            }

            entries.forEachIndexed { index, entry ->
                if (index > 0) RowDivider()
                ListRow(
                    title = entry.key,
                    subtitle = entry.value,
                    trailing = {
                        IconButton(onClick = { onForget(entry.key) }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Forget ${entry.key}",
                                tint = MaterialTheme.spideyColors.textSecondary,
                            )
                        }
                    },
                )
            }
        }

        if (entries.isNotEmpty()) {
            StickyActionBar {
                SecondaryButton(
                    text = "Clear all memory",
                    onClick = { confirmClear = true },
                    contentColor = MaterialTheme.spideyColors.error,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            title = "Clear all memory?",
            message = "Spidey will forget every app shortcut it has learned.",
            confirmLabel = "Clear",
            onConfirm = {
                confirmClear = false
                onClearAll()
            },
            onDismiss = { confirmClear = false },
        )
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
