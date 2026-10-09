package com.example.agent.ui.history

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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import com.example.agent.BuildConfig
import com.example.agent.core.diagnostics.TaskDiagnostics
import com.example.agent.domain.model.RecordedStep
import com.example.agent.domain.model.TaskRecord
import com.example.agent.domain.model.TaskState
import com.example.agent.ui.components.EmptyState
import com.example.agent.ui.components.GlassCard
import com.example.agent.ui.components.LabelValueRow
import com.example.agent.ui.components.PrimaryButton
import com.example.agent.ui.components.SecondaryButton
import com.example.agent.ui.components.SectionHeader
import com.example.agent.ui.components.StatusDot
import com.example.agent.ui.theme.SpideySpacing
import com.example.agent.ui.theme.spideyColors

@Composable
fun TaskDetailScreen(
    onBack: () -> Unit,
    viewModel: TaskDetailViewModel = hiltViewModel(),
) {
    val task by viewModel.task.collectAsStateWithLifecycle()
    val provider by viewModel.provider.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val copied = remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = SpideySpacing.screenGutter)
            .padding(top = SpideySpacing.xl, bottom = SpideySpacing.xxl),
    ) {
        val record = task
        if (record == null) {
            EmptyState(
                title = "Task not found",
                body = "This task is no longer in your history.",
            )
            Spacer(Modifier.height(SpideySpacing.lg))
            SecondaryButton(text = "Back", onClick = onBack, modifier = Modifier.fillMaxWidth())
            return@Column
        }

        Text(
            text = record.prompt,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(Modifier.height(SpideySpacing.lg))

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            LabelValueRow(label = "Result", value = statusLabel(record.state))
            Spacer(Modifier.height(SpideySpacing.xs))
            LabelValueRow(label = "Duration", value = formatDuration(record.durationMs))
            Spacer(Modifier.height(SpideySpacing.xs))
            LabelValueRow(label = "Steps", value = record.stepCount.toString())
            if (record.appsUsed.isNotEmpty()) {
                Spacer(Modifier.height(SpideySpacing.xs))
                LabelValueRow(
                    label = "Apps used",
                    value = record.appsUsed.joinToString(", ") { it.substringAfterLast('.') },
                )
            }
        }

        // Where the time actually went. Without this an optimisation is unverifiable.
        if (record.durationMs > 0) {
            Spacer(Modifier.height(SpideySpacing.lg))
            SectionHeader(title = "Performance", modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(SpideySpacing.sm))
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                LabelValueRow(label = "Waiting on the model", value = formatDuration(record.modelMillis))
                Spacer(Modifier.height(SpideySpacing.xs))
                LabelValueRow(
                    label = "On the device",
                    value = formatDuration(record.overheadMillis) + "  (settle, screen reads, actions)",
                )
                if (record.inputTokens > 0 || record.outputTokens > 0) {
                    Spacer(Modifier.height(SpideySpacing.xs))
                    LabelValueRow(
                        label = "Tokens",
                        value = "${record.inputTokens} in / ${record.outputTokens} out",
                    )
                }
            }
        }

        record.summary?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(SpideySpacing.lg))
            Text(
                text = it,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.spideyColors.textSecondary,
            )
        }

        if (record.steps.isNotEmpty()) {
            Spacer(Modifier.height(SpideySpacing.sectionGap))
            SectionHeader(title = "Actions performed", modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(SpideySpacing.md))
            Column(verticalArrangement = Arrangement.spacedBy(SpideySpacing.sm)) {
                record.steps.forEach { RecordedStepRow(it) }
            }
        }

        Spacer(Modifier.height(SpideySpacing.lg))

        SecondaryButton(
            text = if (copied.value) "Diagnostics copied" else "Copy diagnostics",
            onClick = {
                val report = TaskDiagnostics.report(
                    record = record,
                    appVersion = BuildConfig.VERSION_NAME,
                    device = "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}",
                    provider = provider,
                )
                val clipboard = context.getSystemService(ClipboardManager::class.java)
                clipboard?.setPrimaryClip(ClipData.newPlainText("Spidey diagnostics", report))
                copied.value = true
            },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(SpideySpacing.md))

        Row(horizontalArrangement = Arrangement.spacedBy(SpideySpacing.md), modifier = Modifier.fillMaxWidth()) {
            PrimaryButton(text = "Run again", onClick = viewModel::runAgain, modifier = Modifier.weight(1f))
            SecondaryButton(
                text = "Delete",
                onClick = { viewModel.delete(onBack) },
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(SpideySpacing.md))
        SecondaryButton(text = "Back", onClick = onBack, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun RecordedStepRow(step: RecordedStep) {
    val color = when (step.status) {
        "Done" -> MaterialTheme.spideyColors.success
        "Failed" -> MaterialTheme.spideyColors.error
        "Running" -> MaterialTheme.spideyColors.brownAccent
        else -> MaterialTheme.spideyColors.border
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        StatusDot(color = color, label = step.status)
        Spacer(Modifier.width(SpideySpacing.sm))
        Text(
            text = step.label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

private fun statusLabel(state: TaskState): String = when (state) {
    TaskState.COMPLETED -> "Completed"
    TaskState.FAILED -> "Failed"
    TaskState.CANCELLED -> "Stopped"
    else -> state.name.lowercase().replaceFirstChar { it.uppercase() }
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0) return "—"
    val seconds = ms / 1000
    return when {
        seconds < 60 -> "${seconds}s"
        else -> "${seconds / 60}m ${seconds % 60}s"
    }
}
