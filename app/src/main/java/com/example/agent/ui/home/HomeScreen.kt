package com.example.agent.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.agent.core.permissions.PermissionUtils
import com.example.agent.domain.model.AppearanceMode
import com.example.agent.domain.model.Routine
import com.example.agent.domain.model.TaskRecord
import com.example.agent.domain.model.TaskState
import com.example.agent.ui.components.Composer
import com.example.agent.ui.components.HomeTopBar
import com.example.agent.ui.components.NoticeBanner
import com.example.agent.ui.components.RoutineStories
import com.example.agent.ui.components.SectionHeader
import com.example.agent.ui.components.SpideyMark
import com.example.agent.ui.components.SuggestionRow
import com.example.agent.ui.components.TaskRow
import com.example.agent.ui.components.taskSubtitle
import com.example.agent.ui.preview.PreviewData
import com.example.agent.ui.theme.SpideySpacing
import com.example.agent.ui.theme.SpideyTheme
import com.example.agent.ui.theme.spideyColors
import androidx.compose.ui.unit.dp
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
    val routines by viewModel.routinesList.collectAsStateWithLifecycle()
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()
    val accessibilityStuck by viewModel.accessibilityStuck.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshPermissions() }

    // Jump to the live conversation the moment a task starts.
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
        recent = recent,
        routines = routines,
        accessibilityReady = permissions.accessibilityReady,
        accessibilityStuck = accessibilityStuck,
        onEnableAccessibility = {
            context.startActivity(PermissionUtils.accessibilitySettingsIntent())
        },
        onRunRoutine = viewModel::runRoutine,
        onOpenHistory = onOpenHistory,
    )
}

@Composable
private fun HomeContent(
    greeting: String,
    input: String,
    onInputChange: (String) -> Unit,
    onSubmit: () -> Unit,
    recent: List<TaskRecord>,
    routines: List<Routine>,
    accessibilityReady: Boolean,
    accessibilityStuck: Boolean,
    onEnableAccessibility: () -> Unit,
    onRunRoutine: (Routine) -> Unit,
    onOpenHistory: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        HomeTopBar()

        if (!accessibilityReady) {
            NoticeBanner(
                text = if (accessibilityStuck) {
                    "Accessibility is on but not connected. Turn it off and on again."
                } else {
                    "Turn on accessibility so Spidey can control the screen."
                },
                actionLabel = "Enable",
                onAction = onEnableAccessibility,
                isError = accessibilityStuck,
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(top = SpideySpacing.md, bottom = SpideySpacing.md),
            verticalArrangement = Arrangement.spacedBy(SpideySpacing.lg),
        ) {
            if (routines.isNotEmpty()) {
                RoutineStories(routines = routines, onRun = onRunRoutine)
            }

            Column(
                modifier = Modifier.padding(horizontal = SpideySpacing.screenGutter),
                verticalArrangement = Arrangement.spacedBy(SpideySpacing.xs),
            ) {
                SpideyMark(color = MaterialTheme.colorScheme.primary, size = 44.dp)
                Spacer(Modifier.height(SpideySpacing.xs))
                Text(
                    text = greeting,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.spideyColors.textSecondary,
                )
                Text(
                    text = "What should I do?",
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            Column(
                modifier = Modifier.padding(horizontal = SpideySpacing.screenGutter),
                verticalArrangement = Arrangement.spacedBy(SpideySpacing.xs),
            ) {
                SUGGESTIONS.forEach { suggestion ->
                    SuggestionRow(text = suggestion, onClick = { onInputChange(suggestion) })
                }
            }

            if (recent.isNotEmpty()) {
                Column {
                    SectionHeader(
                        title = "Recent",
                        modifier = Modifier.fillMaxWidth(),
                        actionLabel = "See all",
                        onAction = onOpenHistory,
                    )
                    recent.forEach { record ->
                        TaskRow(
                            prompt = record.prompt,
                            detail = taskSubtitle(record),
                            state = record.state,
                            onClick = onOpenHistory,
                        )
                    }
                }
            }
        }

        Composer(
            value = input,
            onValueChange = onInputChange,
            onSubmit = onSubmit,
            modifier = Modifier.padding(
                horizontal = SpideySpacing.screenGutter,
                vertical = SpideySpacing.sm,
            ),
        )
    }
}

private fun greeting(): String = when (LocalTime.now().hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    in 17..21 -> "Good evening"
    else -> "Hello"
}

@Preview(name = "Home", showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun HomePreview() {
    SpideyTheme {
        HomeContent(
            greeting = "Good morning",
            input = "",
            onInputChange = {},
            onSubmit = {},
            recent = PreviewData.records,
            routines = PreviewData.routines,
            accessibilityReady = false,
            accessibilityStuck = false,
            onEnableAccessibility = {},
            onRunRoutine = {},
            onOpenHistory = {},
        )
    }
}

@Preview(name = "Home - dark, typing", showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun HomeDarkPreview() {
    SpideyTheme(appearance = AppearanceMode.Dark) {
        HomeContent(
            greeting = "Good evening",
            input = "Open Spotify and play my liked songs",
            onInputChange = {},
            onSubmit = {},
            recent = emptyList(),
            routines = emptyList(),
            accessibilityReady = true,
            accessibilityStuck = false,
            onEnableAccessibility = {},
            onRunRoutine = {},
            onOpenHistory = {},
        )
    }
}
