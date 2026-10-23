package com.example.agent.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.agent.automation.AgentForegroundService
import com.example.agent.core.agent.AgentRuntime
import com.example.agent.core.voice.VoiceController
import com.example.agent.core.voice.VoiceState
import com.example.agent.data.preferences.SettingsRepository
import com.example.agent.data.repository.RoutineRepository
import com.example.agent.data.repository.TaskHistoryRepository
import com.example.agent.domain.model.Routine
import com.example.agent.domain.model.TaskProgress
import com.example.agent.domain.model.TaskRecord
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val runtime: AgentRuntime,
    private val history: TaskHistoryRepository,
    private val routines: RoutineRepository,
    @ApplicationContext private val context: Context,
    private val voice: VoiceController,
    settings: SettingsRepository,
) : ViewModel() {

    val progress: StateFlow<TaskProgress> = runtime.progress

    val voiceState: StateFlow<VoiceState> = voice.state

    val voiceLevel: StateFlow<Float> = voice.level

    val voiceGlow: StateFlow<Boolean> = settings.voiceGlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val voiceFace: StateFlow<Boolean> = settings.voiceFace
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun startListening() = voice.startListening()

    /** Ends the recording early and transcribes what was heard. */
    fun stopListening() = voice.stopListening()

    /** Stop the running task. The Home status card offered this button but it was inert. */
    fun stop() {
        runtime.stop()
        AgentForegroundService.stop(context)
    }

    val recentTasks: StateFlow<List<TaskRecord>> = history.observeRecent(3)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val routinesList: StateFlow<List<Routine>> = routines.routines
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Replays a saved routine from its ring on Home. Needs no model. */
    fun runRoutine(routine: Routine) {
        if (runtime.isRunning) return
        viewModelScope.launch {
            routines.markRun(routine.id, System.currentTimeMillis())
            runtime.startRoutine(routine)
        }
    }

    fun submit(prompt: String) {
        val trimmed = prompt.trim()
        if (trimmed.isEmpty() || runtime.isRunning) return
        AgentForegroundService.start(context)
        runtime.start(trimmed)
    }
}
