package com.example.agent.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.agent.R
import com.example.agent.core.permissions.PermissionUtils
import com.example.agent.domain.model.TaskState
import com.example.agent.ui.components.AgentInput
import com.example.agent.ui.components.AgentStatusCard
import com.example.agent.ui.components.EmptyState
import com.example.agent.ui.components.PermissionCard
import com.example.agent.ui.components.SectionHeader
import com.example.agent.ui.components.TaskCard
import com.example.agent.ui.theme.SpideySpacing
import com.example.agent.ui.theme.spideyColors
import java.time.LocalTime

@Composable
fun HomeScreen(
    onOpenTask: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val recent by viewModel.recentTasks.collectAsStateWithLifecycle()
    val accessibilityEnabled by viewModel.accessibilityEnabled.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshPermissions() }

    // Jump to the live execution view the moment a task starts.
    LaunchedEffect(progress.state) {
        if (progress.state.isWorking || progress.state == TaskState.WAITING_FOR_CONFIRMATION) {
            onOpenTask()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = SpideySpacing.screenGutter)
            .padding(top = SpideySpacing.xxl, bottom = SpideySpacing.xxl),
    ) {
        Text(
            text = greeting(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.spideyColors.textSecondary,
        )
        Spacer(Modifier.height(SpideySpacing.xs))
        Text(
            text = stringResource(R.string.home_prompt),
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(Modifier.height(SpideySpacing.xl))

        AgentInput(
            value = input,
            onValueChange = { input = it },
            onSubmit = {
                viewModel.submit(input)
                input = ""
            },
        )

        if (!accessibilityEnabled) {
            Spacer(Modifier.height(SpideySpacing.lg))
            PermissionCard(
                title = "Accessibility access",
                body = "Spidey needs accessibility to see and control the screen. You can stop it any time.",
                granted = false,
                actionLabel = "Enable accessibility",
                onAction = { context.startActivity(PermissionUtils.accessibilitySettingsIntent()) },
            )
        }

        if (progress.state.isWorking || progress.state == TaskState.WAITING_FOR_CONFIRMATION) {
            Spacer(Modifier.height(SpideySpacing.lg))
            AgentStatusCard(
                state = progress.state,
                message = progress.message,
                step = progress.currentStep,
                total = progress.totalSteps,
                onStop = { },
            )
        }

        Spacer(Modifier.height(SpideySpacing.sectionGap))

        SectionHeader(
            title = stringResource(R.string.home_recent_tasks),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(SpideySpacing.md))

        if (recent.isEmpty()) {
            EmptyState(
                title = "No tasks yet",
                body = "Ask your agent to do something and it will show up here.",
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(SpideySpacing.md)) {
                recent.forEach { record ->
                    TaskCard(
                        prompt = record.prompt,
                        appName = record.appsUsed.firstOrNull(),
                        statusLabel = record.state.name.lowercase().replaceFirstChar { it.uppercase() },
                        onClick = onOpenHistory,
                    )
                }
            }
        }
    }
}

private fun greeting(): String = when (LocalTime.now().hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    in 17..21 -> "Good evening"
    else -> "Hello"
}
