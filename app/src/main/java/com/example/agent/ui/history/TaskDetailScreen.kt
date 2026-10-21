package com.example.agent.ui.history

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.agent.BuildConfig
import com.example.agent.core.diagnostics.TaskDiagnostics
import com.example.agent.domain.model.RecordedStep
import com.example.agent.domain.model.TaskRecord
import com.example.agent.ui.components.EmptyState
import com.example.agent.ui.components.LabelValueRow
import com.example.agent.ui.components.PrimaryButton
import com.example.agent.ui.components.ScreenTopBar
import com.example.agent.ui.components.SecondaryButton
import com.example.agent.ui.components.SectionHeader
import com.example.agent.ui.components.SpideyCard
import com.example.agent.ui.components.StatusDot
import com.example.agent.ui.components.StatusPill
import com.example.agent.ui.components.StickyActionBar
import com.example.agent.ui.components.taskStateColor
import com.example.agent.ui.components.taskStateLabel
import com.example.agent.ui.preview.PreviewData
import com.example.agent.ui.theme.SpideySpacing
import com.example.agent.ui.theme.SpideyTheme
import com.example.agent.ui.theme.spideyColors

@Composable
fun TaskDetailScreen(
    onBack: () -> Unit,
    viewModel: TaskDetailViewModel = hiltViewModel(),
) {
    val task by viewModel.task.collectAsStateWithLifecycle()
    val provider by viewModel.provider.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }

    TaskDetailContent(
        record = task,
        copied = copied,
        onBack = onBack,
        onRunAgain = viewModel::runAgain,
        onDelete = { viewModel.delete(onBack) },
        onCopyDiagnostics = { record ->
            val report = TaskDiagnostics.report(
                record = record,
                appVersion = BuildConfig.VERSION_NAME,
                device = "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}",
                provider = provider,
            )
            val clipboard = context.getSystemService(ClipboardManager::class.java)
            clipboard?.setPrimaryClip(ClipData.newPlainText("Spidey diagnostics", report))
            copied = true
        },
    )
}

@Composable
private fun TaskDetailContent(
    record: TaskRecord?,
    copied: Boolean,
    onBack: () -> Unit,
    onRunAgain: () -> Unit,
    onDelete: () -> Unit,
    onCopyDiagnostics: (TaskRecord) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(title = "Task details", onBack = onBack)

        if (record == null) {
            EmptyState(
                title = "Task not found",
                body = "This task is no longer in your history.",
                modifier = Modifier.padding(horizontal = SpideySpacing.screenGutter),
            )
            return@Column
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SpideySpacing.screenGutter)
                .padding(bottom = SpideySpacing.lg),
            verticalArrangement = Arrangement.spacedBy(SpideySpacing.md),
        ) {
            Text(
                text = record.prompt,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )

            StatusPill(label = taskStateLabel(record.state), color = taskStateColor(record.state))

            StatTiles(record)

            record.summary?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.spideyColors.textSecondary,
                )
            }

            // Where the time actually went. Without this an optimisation is unverifiable.
            if (record.durationMs > 0) {
                SectionHeader(title = "Performance", modifier = Modifier.fillMaxWidth())
                SpideyCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(SpideySpacing.xs)) {
                        LabelValueRow(label = "Waiting on the model", value = formatDuration(record.modelMillis))
                        LabelValueRow(
                            label = "On the device",
                            value = formatDuration(record.overheadMillis),
                        )
                        if (record.inputTokens > 0 || record.outputTokens > 0) {
                            LabelValueRow(
                                label = "Tokens",
                                value = "${record.inputTokens} in / ${record.outputTokens} out",
                            )
                        }
                    }
                }
            }

            if (record.appsUsed.isNotEmpty()) {
                SpideyCard(modifier = Modifier.fillMaxWidth()) {
                    LabelValueRow(
                        label = "Apps used",
                        value = record.appsUsed.joinToString(", ") { it.substringAfterLast('.') },
                    )
                }
            }

            if (record.steps.isNotEmpty()) {
                SectionHeader(title = "Actions performed", modifier = Modifier.fillMaxWidth())
                SpideyCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(SpideySpacing.sm)) {
                        record.steps.forEach { RecordedStepRow(it) }
                    }
                }
            }
        }

        StickyActionBar {
            PrimaryButton(text = "Run again", onClick = onRunAgain, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(SpideySpacing.xs)) {
                SecondaryButton(
                    text = if (copied) "Copied" else "Copy diagnostics",
                    onClick = { onCopyDiagnostics(record) },
                    modifier = Modifier.weight(1f),
                )
                SecondaryButton(text = "Delete", onClick = onDelete, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatTiles(record: TaskRecord) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SpideySpacing.sm),
    ) {
        StatTile(label = "Duration", value = formatDuration(record.durationMs), modifier = Modifier.weight(1f))
        StatTile(label = "Steps", value = record.stepCount.toString(), modifier = Modifier.weight(1f))
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    SpideyCard(modifier = modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.spideyColors.textSecondary,
        )
    }
}

@Composable
private fun RecordedStepRow(step: RecordedStep) {
    val color = when (step.status) {
        "Done" -> MaterialTheme.spideyColors.success
        "Failed" -> MaterialTheme.spideyColors.error
        "Running" -> MaterialTheme.spideyColors.blue
        else -> MaterialTheme.spideyColors.border
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        StatusDot(color = color, label = step.status)
        Spacer(Modifier.width(SpideySpacing.sm))
        Text(
            text = step.label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0) return "—"
    val seconds = ms / 1000
    return when {
        seconds < 60 -> "${seconds}s"
        else -> "${seconds / 60}m ${seconds % 60}s"
    }
}

@Preview(name = "Task details", showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun TaskDetailPreview() {
    SpideyTheme {
        TaskDetailContent(
            record = PreviewData.records.first(),
            copied = false,
            onBack = {},
            onRunAgain = {},
            onDelete = {},
            onCopyDiagnostics = {},
        )
    }
}

@Preview(name = "Task details - missing", showBackground = true, widthDp = 360, heightDp = 400)
@Composable
private fun TaskDetailMissingPreview() {
    SpideyTheme {
        TaskDetailContent(
            record = null,
            copied = false,
            onBack = {},
            onRunAgain = {},
            onDelete = {},
            onCopyDiagnostics = {},
        )
    }
}
