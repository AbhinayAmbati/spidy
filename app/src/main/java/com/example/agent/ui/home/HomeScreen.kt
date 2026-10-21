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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.agent.core.permissions.PermissionUtils
import com.example.agent.domain.model.AppearanceMode
import com.example.agent.domain.model.TaskProgress
import com.example.agent.domain.model.TaskRecord
import com.example.agent.domain.model.TaskState
import com.example.agent.ui.components.AgentStatusCard
import com.example.agent.ui.components.ChipRow
import com.example.agent.ui.components.EmptyState
import com.example.agent.ui.components.HeroCard
import com.example.agent.ui.components.HomeHeader
import com.example.agent.ui.components.PermissionCard
import com.example.agent.ui.components.PromptBar
import com.example.agent.ui.components.SectionHeader
import com.example.agent.ui.components.TaskCard
import com.example.agent.ui.preview.PreviewData
import com.example.agent.ui.theme.SpideySpacing
import com.example.agent.ui.theme.SpideyTheme
import java.time.LocalTime

private val SUGGESTIONS = listOf(
    "Open Settings",
    "Set an alarm for 7 am",
    "Check my notifications",
    "Open YouTube",
)

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
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()
    val accessibilityStuck by viewModel.accessibilityStuck.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshPermissions() }

    // Jump to the live execution view the moment a task starts.
    LaunchedEffect(progress.state) {
        if (progress.state.isWorking || progress.state == TaskState.WAITING_FOR_CONFIRMATION) {
            onOpenTask()
        }
    }

    HomeContent(
        greeting = greeting(),
        input = input,
        onInputChange = { input = it },
        onSubmit = {
            viewModel.submit(input)
            input = ""
        },
        progress = progress,
        recent = recent,
        accessibilityReady = permissions.accessibilityReady,
        accessibilityStuck = accessibilityStuck,
        onEnableAccessibility = {
            context.startActivity(PermissionUtils.accessibilitySettingsIntent())
        },
        onStop = viewModel::stop,
        onOpenHistory = onOpenHistory,
        onOpenSettings = onOpenSettings,
    )
}

@Composable
private fun HomeContent(
    greeting: String,
    input: String,
    onInputChange: (String) -> Unit,
    onSubmit: () -> Unit,
    progress: TaskProgress,
    recent: List<TaskRecord>,
    accessibilityReady: Boolean,
    accessibilityStuck: Boolean,
    onEnableAccessibility: () -> Unit,
    onStop: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val taskRunning = progress.state.isWorking ||
        progress.state == TaskState.WAITING_FOR_CONFIRMATION

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = SpideySpacing.lg),
    ) {
        HomeHeader(
            title = "Spidey",
            subtitle = "$greeting · your on-device agent",
            onProfileClick = onOpenSettings,
        )

        Spacer(Modifier.height(SpideySpacing.xs))

        PromptBar(
            value = input,
            onValueChange = onInputChange,
            onSubmit = onSubmit,
            hint = "Try \"Open Settings\"",
            modifier = Modifier.padding(horizontal = SpideySpacing.screenGutter),
        )

        Spacer(Modifier.height(SpideySpacing.sm))

        ChipRow(labels = SUGGESTIONS, selected = null, onSelect = onInputChange)

        Spacer(Modifier.height(SpideySpacing.lg))

        Column(
            modifier = Modifier.padding(horizontal = SpideySpacing.screenGutter),
            verticalArrangement = Arrangement.spacedBy(SpideySpacing.md),
        ) {
            if (taskRunning) {
                AgentStatusCard(
                    state = progress.state,
                    message = progress.message,
                    step = progress.currentStep,
                    total = progress.totalSteps,
                    onStop = onStop,
                )
            } else {
                HeroCard(
                    title = "What should I do?",
                    body = "Type or speak a request and Spidey does it across your apps.",
                )
            }

            if (!accessibilityReady) {
                PermissionCard(
                    title = "Accessibility access",
                    body = "Spidey needs accessibility to see and control the screen. You can stop it any time.",
                    granted = false,
                    warning = if (accessibilityStuck) {
                        "Accessibility is switched on but the service has not connected, even " +
                            "after waiting. Turn it off and on again in system settings."
                    } else {
                        null
                    },
                    actionLabel = "Enable accessibility",
                    onAction = onEnableAccessibility,
                )
            }

            Spacer(Modifier.height(SpideySpacing.xxs))

            SectionHeader(
                title = "Recent tasks",
                modifier = Modifier.fillMaxWidth(),
                actionLabel = if (recent.isEmpty()) null else "See All",
                onAction = onOpenHistory,
            )

            if (recent.isEmpty()) {
                EmptyState(
                    title = "No tasks yet",
                    body = "Ask your agent to do something and it will show up here.",
                )
            } else {
                recent.forEach { record ->
                    TaskCard(
                        prompt = record.prompt,
                        appName = record.appsUsed.firstOrNull(),
                        state = record.state,
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

@Preview(name = "Home - idle", showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun HomeIdlePreview() {
    SpideyTheme {
        HomeContent(
            greeting = "Good morning",
            input = "",
            onInputChange = {},
            onSubmit = {},
            progress = PreviewData.progress(TaskState.IDLE),
            recent = PreviewData.records,
            accessibilityReady = false,
            accessibilityStuck = false,
            onEnableAccessibility = {},
            onStop = {},
            onOpenHistory = {},
            onOpenSettings = {},
        )
    }
}

@Preview(name = "Home - running (dark)", showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun HomeRunningDarkPreview() {
    SpideyTheme(appearance = AppearanceMode.Dark) {
        HomeContent(
            greeting = "Good evening",
            input = "Open Spotify",
            onInputChange = {},
            onSubmit = {},
            progress = PreviewData.progress(TaskState.EXECUTING),
            recent = emptyList(),
            accessibilityReady = true,
            accessibilityStuck = false,
            onEnableAccessibility = {},
            onStop = {},
            onOpenHistory = {},
            onOpenSettings = {},
        )
    }
}
