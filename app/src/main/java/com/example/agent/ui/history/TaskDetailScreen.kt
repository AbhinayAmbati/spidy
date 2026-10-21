package com.example.agent.ui.history

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.agent.BuildConfig
import com.example.agent.core.diagnostics.TaskDiagnostics
import com.example.agent.domain.model.RecordedStep
import com.example.agent.domain.model.TaskRecord
import com.example.agent.ui.components.ActionLog
import com.example.agent.ui.components.ConfirmDialog
import com.example.agent.ui.components.EmptyState
import com.example.agent.ui.components.LabelValueRow
import com.example.agent.ui.components.LogEntry
import com.example.agent.ui.components.LogState
import com.example.agent.ui.components.PrimaryButton
import com.example.agent.ui.components.ScreenTopBar
import com.example.agent.ui.components.SecondaryButton
import com.example.agent.ui.components.SectionLabel
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
    var confirmDelete by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(title = "Task details", onBack = onBack)

        if (record == null) {
            EmptyState(
                title = "Task not found",
                body = "This task is no longer in your history.",
            )
            return@Column
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = SpideySpacing.lg),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = SpideySpacing.screenGutter, vertical = SpideySpacing.md),
                verticalArrangement = Arrangement.spacedBy(SpideySpacing.sm),
            ) {
                Text(
                    text = record.prompt,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                StatusPill(label = taskStateLabel(record.state), color = taskStateColor(record.state))
                record.summary?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.spideyColors.textSecondary,
                    )
                }
            }

            StatStrip(
                duration = formatDuration(record.durationMs),
                steps = record.stepCount.toString(),
                apps = record.appsUsed.size.toString(),
            )

            // Where the time actually went. Without this an optimisation is unverifiable.
            if (record.durationMs > 0) {
                SectionLabel(text = "Performance")
                LabelValueRow(label = "Waiting on the model", value = formatDuration(record.modelMillis))
                LabelValueRow(label = "On the device", value = formatDuration(record.overheadMillis))
                if (record.inputTokens > 0 || record.outputTokens > 0) {
                    LabelValueRow(label = "Tokens", value = "${record.inputTokens} in / ${record.outputTokens} out")
                }
            }

            if (record.appsUsed.isNotEmpty()) {
                SectionLabel(text = "Apps")
                LabelValueRow(
                    label = "Used",
                    value = record.appsUsed.joinToString(", ") { it.substringAfterLast('.') },
                )
            }

            if (record.steps.isNotEmpty()) {
                SectionLabel(text = "Actions performed")
                ActionLog(
                    entries = record.steps.map { it.toLogEntry() },
                    modifier = Modifier.padding(horizontal = SpideySpacing.screenGutter),
                )
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
                SecondaryButton(
                    text = "Delete",
                    onClick = { confirmDelete = true },
                    contentColor = MaterialTheme.spideyColors.error,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete this task?",
            message = "It will be removed from your history.",
            confirmLabel = "Delete",
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

/** Three figures side by side with hairlines between them, like the counts on a profile. */
@Composable
private fun StatStrip(duration: String, steps: String, apps: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = SpideySpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Stat(value = duration, label = "Duration", modifier = Modifier.weight(1f))
        StatDivider()
        Stat(value = steps, label = "Steps", modifier = Modifier.weight(1f))
        StatDivider()
        Stat(value = apps, label = "Apps", modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.spideyColors.textSecondary,
        )
    }
}

@Composable
private fun StatDivider() {
    Column(
        modifier = Modifier
            .width(1.dp)
            .height(32.dp)
            .background(MaterialTheme.spideyColors.border),
    ) {}
}

private fun RecordedStep.toLogEntry(): LogEntry = LogEntry(
    label = label,
    state = when (status) {
        "Done" -> LogState.Done
        "Failed" -> LogState.Failed
        "Running" -> LogState.Running
        else -> LogState.Pending
    },
    detail = detail,
)

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
