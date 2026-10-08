package com.example.agent.ui.task

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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.agent.R
import com.example.agent.domain.model.AwaitingKind
import com.example.agent.domain.model.StepStatus
import com.example.agent.domain.model.TaskState
import com.example.agent.ui.components.AgentInput
import com.example.agent.ui.components.AgentStatusCard
import com.example.agent.ui.components.ConfirmationSheet
import com.example.agent.ui.components.PrimaryButton
import com.example.agent.ui.components.SecondaryButton
import com.example.agent.ui.components.SectionHeader
import com.example.agent.ui.components.StatusDot
import com.example.agent.ui.theme.SpideySpacing
import com.example.agent.ui.theme.spideyColors

@Composable
fun TaskScreen(
    onBack: () -> Unit,
    viewModel: TaskViewModel = hiltViewModel(),
) {
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val paused by viewModel.paused.collectAsStateWithLifecycle()
    var answerText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = SpideySpacing.screenGutter)
            .padding(top = SpideySpacing.xl, bottom = SpideySpacing.xxl),
    ) {
        Text(
            text = progress.prompt.ifBlank { "Task" },
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(Modifier.height(SpideySpacing.lg))

        AgentStatusCard(
            state = progress.state,
            message = progress.message,
            step = progress.currentStep,
            total = progress.totalSteps,
            onStop = viewModel::stop,
        )

        if (progress.state.isWorking || progress.state == TaskState.PAUSED) {
            Spacer(Modifier.height(SpideySpacing.md))
            if (paused) {
                PrimaryButton(
                    text = "Resume",
                    onClick = viewModel::resume,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                SecondaryButton(
                    text = "Pause",
                    onClick = viewModel::pause,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(Modifier.height(SpideySpacing.sectionGap))

        SectionHeader(title = "What I'm doing", modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(SpideySpacing.md))

        if (progress.steps.isEmpty()) {
            Text(
                text = "Getting started…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.spideyColors.textSecondary,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(SpideySpacing.sm)) {
                progress.steps.forEach { step -> StepRow(step) }
            }
        }

        // Failure / cancellation states are shown plainly, with a way forward.
        if (progress.state == TaskState.FAILED || progress.state == TaskState.CANCELLED) {
            Spacer(Modifier.height(SpideySpacing.xl))
            progress.error?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.spideyColors.error,
                )
                Spacer(Modifier.height(SpideySpacing.md))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(SpideySpacing.md)) {
                PrimaryButton(text = stringResource(R.string.task_retry), onClick = viewModel::retry)
                SecondaryButton(text = stringResource(R.string.task_stop), onClick = onBack)
            }
        }

        if (progress.state == TaskState.COMPLETED) {
            Spacer(Modifier.height(SpideySpacing.xl))
            PrimaryButton(
                text = "Done",
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // A question from the agent needs a text answer, not a yes/no.
        if (progress.state == TaskState.WAITING_FOR_CONFIRMATION &&
            progress.awaitingKind == AwaitingKind.Question
        ) {
            Spacer(Modifier.height(SpideySpacing.xl))
            AgentInput(
                value = answerText,
                onValueChange = { answerText = it },
                onSubmit = {
                    if (answerText.isNotBlank()) {
                        viewModel.answer(answerText.trim())
                        answerText = ""
                    }
                },
                hint = "Your answer…",
            )
        }
    }

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
private fun StepRow(step: com.example.agent.domain.model.TaskStep) {
    Column {
        StepRowContent(step)
        // A failed step used to be a bare red cross. Say why it failed.
        step.detail?.takeIf { it.isNotBlank() && step.status == StepStatus.Failed }?.let { detail ->
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.spideyColors.textSecondary,
                modifier = Modifier.padding(start = 26.dp, top = 2.dp),
            )
        }
    }
}

@Composable
private fun StepRowContent(step: com.example.agent.domain.model.TaskStep) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        when (step.status) {
            StepStatus.Done -> Icon(
                Icons.Filled.CheckCircle, contentDescription = "Completed",
                tint = MaterialTheme.spideyColors.success, modifier = Modifier.width(18.dp),
            )
            StepStatus.Failed -> Icon(
                Icons.Filled.Close, contentDescription = "Failed",
                tint = MaterialTheme.spideyColors.error, modifier = Modifier.width(18.dp),
            )
            StepStatus.Running -> StatusDot(
                color = MaterialTheme.spideyColors.brownAccent,
                label = "In progress",
            )
            StepStatus.Pending -> StatusDot(
                color = MaterialTheme.spideyColors.border,
                label = "Pending",
            )
        }
        Spacer(Modifier.width(SpideySpacing.sm))
        Text(
            text = step.label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (step.status == StepStatus.Pending)
                MaterialTheme.spideyColors.textSecondary else MaterialTheme.colorScheme.onBackground,
        )
    }
}
