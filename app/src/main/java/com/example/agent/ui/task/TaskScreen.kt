package com.example.agent.ui.task

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.agent.ui.components.ActionLog
import com.example.agent.ui.components.AgentMessage
import com.example.agent.ui.components.Composer
import com.example.agent.ui.components.ConfirmationSheet
import com.example.agent.ui.components.LogEntry
import com.example.agent.ui.components.LogState
import com.example.agent.ui.components.PrimaryButton
import com.example.agent.ui.components.ProgressBar
import com.example.agent.ui.components.ScreenTopBar
import com.example.agent.ui.components.SecondaryButton
import com.example.agent.ui.components.StickyActionBar
import com.example.agent.ui.components.TextAction
import com.example.agent.ui.components.TypingDots
import com.example.agent.ui.components.UserBubble
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

/**
 * A task as a conversation: what was asked on the right, the agent's progress on the left,
 * and a dock at the bottom whose controls change with the task's state.
 */
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
    val scroll = rememberScrollState()
    val failedOrStopped = progress.state == TaskState.FAILED || progress.state == TaskState.CANCELLED
    val completed = progress.state == TaskState.COMPLETED
    val running = progress.state.isWorking || progress.state == TaskState.PAUSED
    val asking = progress.state == TaskState.WAITING_FOR_CONFIRMATION &&
        progress.awaitingKind == AwaitingKind.Question
    val fraction = if (progress.totalSteps <= 0) 0f else progress.currentStep.toFloat() / progress.totalSteps

    // Keep the newest step in view as the agent works.
    LaunchedEffect(progress.steps.size, progress.state) {
        scroll.animateScrollTo(scroll.maxValue)
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        ScreenTopBar(
            title = "Task",
            onBack = onBack,
            trailing = {
                if (running || asking) {
                    TextAction(text = "Stop", onClick = onStop, color = MaterialTheme.spideyColors.error)
                }
            },
        )
        ProgressBar(
            fraction = if (completed) 1f else fraction.coerceIn(0f, 1f),
            modifier = Modifier.fillMaxWidth(),
            thickness = 2.dp,
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scroll)
                .padding(horizontal = SpideySpacing.screenGutter, vertical = SpideySpacing.md),
            verticalArrangement = Arrangement.spacedBy(SpideySpacing.lg),
        ) {
            UserBubble(text = progress.prompt.ifBlank { "Task" })

            AgentMessage {
                Text(
                    text = statusLine(progress),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (progress.state == TaskState.FAILED) {
                        MaterialTheme.spideyColors.error
                    } else {
                        MaterialTheme.colorScheme.onBackground
                    },
                )
                if (progress.steps.isNotEmpty()) {
                    ActionLog(entries = progress.steps.map { LogEntry(it.label, it.status.toLogState(), it.detail) })
                }
                if (progress.state.isWorking) {
                    TypingDots()
                }
            }
        }

        when {
            asking -> Composer(
                value = answerText,
                onValueChange = { answerText = it },
                onSubmit = {
                    if (answerText.isNotBlank()) {
                        onAnswer(answerText.trim())
                        answerText = ""
                    }
                },
                hint = "Your answer",
                modifier = Modifier.padding(
                    horizontal = SpideySpacing.screenGutter,
                    vertical = SpideySpacing.sm,
                ),
            )
            running -> StickyActionBar {
                if (paused) {
                    PrimaryButton(text = "Resume", onClick = onResume, modifier = Modifier.fillMaxWidth())
                } else {
                    SecondaryButton(text = "Pause", onClick = onPause, modifier = Modifier.fillMaxWidth())
                }
            }
            failedOrStopped -> StickyActionBar {
                Row(horizontalArrangement = Arrangement.spacedBy(SpideySpacing.xs)) {
                    SecondaryButton(text = "Back", onClick = onBack, modifier = Modifier.weight(1f))
                    PrimaryButton(
                        text = stringResource(R.string.task_retry),
                        onClick = onRetry,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            completed -> StickyActionBar {
                Row(horizontalArrangement = Arrangement.spacedBy(SpideySpacing.xs)) {
                    SecondaryButton(
                        text = if (routineSaved) "Saved" else "Save as routine",
                        onClick = onSaveRoutine,
                        enabled = !routineSaved,
                        modifier = Modifier.weight(1f),
                    )
                    PrimaryButton(text = "Done", onClick = onBack, modifier = Modifier.weight(1f))
                }
            }
            else -> Unit
        }
    }
}

/** The one sentence the agent says about where the task stands. */
private fun statusLine(progress: TaskProgress): String = when (progress.state) {
    TaskState.IDLE -> "Ready when you are."
    TaskState.PLANNING -> "Working out the steps."
    TaskState.EXECUTING, TaskState.OBSERVING -> progress.message ?: "Working on it."
    TaskState.WAITING_FOR_CONFIRMATION -> progress.message ?: "Waiting for your answer."
    TaskState.PAUSED -> "Paused."
    TaskState.COMPLETED -> progress.message ?: "Done."
    TaskState.FAILED -> progress.error ?: "That did not work."
    TaskState.CANCELLED -> "Stopped."
}

private fun StepStatus.toLogState(): LogState = when (this) {
    StepStatus.Pending -> LogState.Pending
    StepStatus.Running -> LogState.Running
    StepStatus.Done -> LogState.Done
    StepStatus.Failed -> LogState.Failed
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
