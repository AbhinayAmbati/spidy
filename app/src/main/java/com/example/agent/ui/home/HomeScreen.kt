package com.example.agent.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.agent.core.voice.VoiceState
import com.example.agent.domain.model.AppearanceMode
import com.example.agent.domain.model.Routine
import com.example.agent.domain.model.TaskRecord
import com.example.agent.domain.model.TaskState
import com.example.agent.ui.components.Composer
import com.example.agent.ui.components.HomeTopBar
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
    "Set a timer for 10 minutes",
    "Call Mom",
)

@Composable
fun HomeScreen(
    onOpenTask: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val recent by viewModel.recentTasks.collectAsStateWithLifecycle()
    val routines by viewModel.routinesList.collectAsStateWithLifecycle()
    val voiceState by viewModel.voiceState.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }

    val context = LocalContext.current
    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) viewModel.startListening() }
    val onMic = {
        if (voiceState == VoiceState.Listening) {
            viewModel.stopListening()
        } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            viewModel.startListening()
        } else {
            // Asked the first time the microphone is used, not at launch.
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Jump to the live conversation the moment a task starts.
    LaunchedEffect(progress.state) {
        if (progress.state.isWorking || progress.state == TaskState.WAITING_FOR_CONFIRMATION) {
            onOpenTask()
        }
    }

    val voiceLevel by viewModel.voiceLevel.collectAsStateWithLifecycle()
    val voiceGlow by viewModel.voiceGlow.collectAsStateWithLifecycle()
    val voiceFace by viewModel.voiceFace.collectAsStateWithLifecycle()
    // Over other apps the overlay window shows the glow and character; without that permission
    // they are drawn here instead, so listening is never invisible.
    val overlayAllowed = remember(voiceState) { Settings.canDrawOverlays(context) }

    Box {
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
            onRunRoutine = viewModel::runRoutine,
            onOpenHistory = onOpenHistory,
            voiceState = voiceState,
            onMic = onMic,
        )
        if (!overlayAllowed && voiceState.isVoicePhase) {
            InAppVoiceVisuals(
                state = voiceState,
                level = voiceLevel,
                glow = voiceGlow,
                face = voiceFace,
            )
        }
    }
}

@Composable
private fun HomeContent(
    greeting: String,
    input: String,
    onInputChange: (String) -> Unit,
    onSubmit: () -> Unit,
    recent: List<TaskRecord>,
    routines: List<Routine>,
    onRunRoutine: (Routine) -> Unit,
    onOpenHistory: () -> Unit,
    voiceState: VoiceState = VoiceState.Idle,
    onMic: (() -> Unit)? = null,
) {
    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        HomeTopBar()

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

        val voiceNote = when (voiceState) {
            VoiceState.Listening -> "Listening… tap the mic to finish"
            VoiceState.Transcribing -> "Transcribing…"
            is VoiceState.Error -> voiceState.reason
            else -> null
        }
        if (voiceNote != null) {
            Text(
                text = voiceNote,
                style = MaterialTheme.typography.bodySmall,
                color = if (voiceState is VoiceState.Error) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.spideyColors.textSecondary
                },
                modifier = Modifier.padding(horizontal = SpideySpacing.screenGutter),
            )
        }
        Composer(
            value = input,
            onValueChange = onInputChange,
            onSubmit = onSubmit,
            modifier = Modifier.padding(
                horizontal = SpideySpacing.screenGutter,
                vertical = SpideySpacing.sm,
            ),
            onMicClick = onMic,
            listening = voiceState == VoiceState.Listening,
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
            input = "Message Sam that I'm on my way",
            onInputChange = {},
            onSubmit = {},
            recent = emptyList(),
            routines = emptyList(),
            onRunRoutine = {},
            onOpenHistory = {},
        )
    }
}
