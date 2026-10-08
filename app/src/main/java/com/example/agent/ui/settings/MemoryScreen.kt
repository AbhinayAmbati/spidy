package com.example.agent.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.agent.ui.components.EmptyState
import com.example.agent.ui.components.GlassCard
import com.example.agent.ui.components.SecondaryButton
import com.example.agent.ui.components.SectionHeader
import com.example.agent.ui.theme.SpideySpacing
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = SpideySpacing.screenGutter)
            .padding(top = SpideySpacing.xl, bottom = SpideySpacing.xxl),
    ) {
        Text(
            text = "Memory",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(Modifier.height(SpideySpacing.lg))

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Remember preferences",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = "When off, Spidey stops learning new app shortcuts. Existing entries stay until you delete them.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.spideyColors.textSecondary,
                    )
                }
                Switch(checked = enabled, onCheckedChange = viewModel::setMemoryEnabled)
            }
        }

        Spacer(Modifier.height(SpideySpacing.sectionGap))

        SectionHeader(
            title = "Learned shortcuts",
            subtitle = "App names Spidey can resolve next time.",
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(SpideySpacing.md))

        if (entries.isEmpty()) {
            EmptyState(
                title = "Nothing remembered yet",
                body = "Run a task that opens an app and the shortcut will appear here.",
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(SpideySpacing.sm)) {
                entries.forEach { entry ->
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = SpideySpacing.md, vertical = SpideySpacing.sm,
                        ),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = entry.key,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onBackground,
                                )
                                Text(
                                    text = entry.value,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.spideyColors.textSecondary,
                                )
                            }
                            IconButton(onClick = { viewModel.forget(entry.key) }) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Forget ${entry.key}",
                                    tint = MaterialTheme.spideyColors.textSecondary,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(SpideySpacing.lg))
            SecondaryButton(
                text = "Clear all memory",
                onClick = viewModel::clearAll,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(SpideySpacing.sectionGap))
        SecondaryButton(text = "Back", onClick = onBack, modifier = Modifier.fillMaxWidth())
    }
}
