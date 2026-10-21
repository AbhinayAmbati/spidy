package com.example.agent.ui.task

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.agent.R
import com.example.agent.domain.model.AwaitingKind
import com.example.agent.domain.model.StepStatus
import com.example.agent.domain.model.TaskProgress
import com.example.agent.domain.model.TaskState
import com.example.agent.domain.model.TaskStep
import com.example.agent.ui.components.AgentStatusCard
import com.example.agent.ui.components.ConfirmationSheet
import com.example.agent.ui.components.PrimaryButton
import com.example.agent.ui.components.PromptBar
import com.example.agent.ui.components.ScreenTopBar
import com.example.agent.ui.components.SecondaryButton
import com.example.agent.ui.components.SectionHeader
import com.example.agent.ui.components.SpideyCard
import com.example.agent.ui.components.StatusDot
import com.example.agent.ui.components.StickyActionBar
import com.example.agent.ui.preview.PreviewData
import com.example.agent.ui.theme.SpideySpacing
import com.example.agent.ui.theme.SpideyTheme
import com.example.agent.ui.theme.spideyColors

@Composable
fun TaskScreen(
    onBack: () -> Unit,
    viewModel: TaskViewModel = hiltViewModel(),
) {
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val paused by viewModel.paused.collectAsStateWithLifecycle()
    val routineSaved by viewModel.routineSaved.collectAsStateWithLifecycle()

    TaskContent(
        progress = progress,
        paused = paused,
        routineSaved = routineSaved,
        onBack = onBack,
        onStop = viewModel::stop,
        onPause = viewModel::pause,
        onResume = viewModel::resume,
        onRetry = viewModel::retry,
        onSaveRoutine = viewModel::saveRoutine,
        onAnswer = viewModel::answer,
    )

    if (progress.state == TaskState.WAITING_FOR_CONFIRMATION &&
        progress.awaitingKind == AwaitingKind.Confirmation
    ) {
        ConfirmationSheet(
            title = stringResource(R.string.confirm_title),
            prompt = progress.message ?: "Proceed with this action?",
            detail = null,
            allowLabel = stringResource(R.string.confirm_allow),
            cancelLabel = stringResource(R.string.confirm_cancel),
            onAllow = viewModel::allow,
            onCancel = viewModel::deny,
        )
    }
}

@Composable
private fun TaskContent(
    progress: TaskProgress,
    paused: Boolean,
    routineSaved: Boolean,
    onBack: () -> Unit,
    onStop: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onSaveRoutine: () -> Unit,
    onAnswer: (String) -> Unit,
) {
    var answerText by remember { mutableStateOf("") }
    val failedOrStopped = progress.state == TaskState.FAILED || progress.state == TaskState.CANCELLED
    val completed = progress.state == TaskState.COMPLETED
    val running = progress.state.isWorking || progress.state == TaskState.PAUSED

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(title = "Task", onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SpideySpacing.screenGutter)
                .padding(bottom = SpideySpacing.lg),
            verticalArrangement = Arrangement.spacedBy(SpideySpacing.md),
        ) {
            Text(
                text = progress.prompt.ifBlank { "Task" },
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )

            AgentStatusCard(
                state = progress.state,
                message = progress.message,
                step = progress.currentStep,
                total = progress.totalSteps,
                onStop = onStop,
            )

            // A question from the agent needs a text answer, not a yes/no.
            if (progress.state == TaskState.WAITING_FOR_CONFIRMATION &&
                progress.awaitingKind == AwaitingKind.Question
            ) {
                PromptBar(
                    value = answerText,
                    onValueChange = { answerText = it },
                    onSubmit = {
                        if (answerText.isNotBlank()) {
                            onAnswer(answerText.trim())
                            answerText = ""
                        }
                    },
                    hint = "Your answer…",
                )
            }

            // Failure / cancellation states are shown plainly, with a way forward below.
            if (failedOrStopped) {
                progress.error?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.spideyColors.error,
                    )
                }
            }

            SectionHeader(title = "What I'm doing", modifier = Modifier.fillMaxWidth())

            if (progress.steps.isEmpty()) {
                Text(
                    text = "Getting started…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.spideyColors.textSecondary,
                )
            } else {
                SpideyCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(SpideySpacing.sm)) {
                        progress.steps.forEach { step -> StepRow(step) }
                    }
                }
            }
        }

        if (running || failedOrStopped || completed) {
            StickyActionBar {
                if (running) {
                    if (paused) {
                        PrimaryButton(text = "Resume", onClick = onResume, modifier = Modifier.fillMaxWidth())
                    } else {
                        SecondaryButton(text = "Pause", onClick = onPause, modifier = Modifier.fillMaxWidth())
                    }
                }
                if (failedOrStopped) {
                    PrimaryButton(
                        text = stringResource(R.string.task_retry),
                        onClick = onRetry,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SecondaryButton(text = "Back", onClick = onBack, modifier = Modifier.fillMaxWidth())
                }
                if (completed) {
                    PrimaryButton(text = "Done", onClick = onBack, modifier = Modifier.fillMaxWidth())
                    SecondaryButton(
                        text = if (routineSaved) "Saved as a routine" else "Save as routine",
                        onClick = onSaveRoutine,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun StepRow(step: TaskStep) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepIcon(step.status)
            Spacer(Modifier.width(SpideySpacing.sm))
            Text(
                text = step.label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (step.status == StepStatus.Pending) {
                    MaterialTheme.spideyColors.textSecondary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
        // A failed step used to be a bare red cross. Say why it failed.
        step.detail?.takeIf { it.isNotBlank() && step.status == StepStatus.Failed }?.let { detail ->
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.spideyColors.textSecondary,
                modifier = Modifier.padding(start = 32.dp, top = 2.dp),
            )
        }
    }
}

@Composable
private fun StepIcon(status: StepStatus) {
    val colors = MaterialTheme.spideyColors
    when (status) {
        StepStatus.Done -> Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = "Completed",
            tint = colors.success,
            modifier = Modifier.size(20.dp),
        )
        StepStatus.Failed -> Icon(
            imageVector = Icons.Filled.Close,
            contentDescription = "Failed",
            tint = colors.error,
            modifier = Modifier.size(20.dp),
        )
        StepStatus.Running -> StatusDot(
            color = colors.blue,
            label = "In progress",
            modifier = Modifier.padding(5.dp),
        )
        StepStatus.Pending -> StatusDot(
            color = colors.border,
            label = "Pending",
            modifier = Modifier.padding(5.dp),
        )
    }
}

@Preview(name = "Task - working", showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun TaskWorkingPreview() {
    SpideyTheme {
        TaskContent(
            progress = PreviewData.progress(TaskState.EXECUTING),
            paused = false,
            routineSaved = false,
            onBack = {},
            onStop = {},
            onPause = {},
            onResume = {},
            onRetry = {},
            onSaveRoutine = {},
            onAnswer = {},
        )
    }
}

@Preview(name = "Task - completed", showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun TaskCompletedPreview() {
    SpideyTheme {
        TaskContent(
            progress = PreviewData.progress(TaskState.COMPLETED),
            paused = false,
            routineSaved = false,
            onBack = {},
            onStop = {},
            onPause = {},
            onResume = {},
            onRetry = {},
            onSaveRoutine = {},
            onAnswer = {},
        )
    }
}

@Preview(name = "Task - failed", showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun TaskFailedPreview() {
    SpideyTheme {
        TaskContent(
            progress = PreviewData.progress(TaskState.FAILED, error = "Could not find the Play button."),
            paused = false,
            routineSaved = false,
            onBack = {},
            onStop = {},
            onPause = {},
            onResume = {},
            onRetry = {},
            onSaveRoutine = {},
            onAnswer = {},
        )
    }
}
