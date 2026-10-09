package com.example.agent.ui.task

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.agent.automation.AgentForegroundService
import com.example.agent.core.agent.AgentRuntime
import com.example.agent.core.ai.AgentJson
import com.example.agent.data.repository.RoutineRepository
import com.example.agent.domain.model.AgentAction
import com.example.agent.domain.model.Routine
import com.example.agent.domain.model.RoutineStep
import com.example.agent.domain.model.TaskProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class TaskViewModel @Inject constructor(
    private val runtime: AgentRuntime,
    private val routines: RoutineRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val progress: StateFlow<TaskProgress> = runtime.progress
    val paused: StateFlow<Boolean> = runtime.paused

    private val _routineSaved = MutableStateFlow(false)
    val routineSaved: StateFlow<Boolean> = _routineSaved.asStateFlow()

    /**
     * Saves the task that just finished as a routine, so it can be replayed with no model.
     *
     * Named after the request, because that is what the user will recognise it by.
     */
    fun saveRoutine() {
        val prompt = runtime.progress.value.prompt
        val actions = runtime.lastSuccessfulActions()
        if (prompt.isBlank() || actions.isEmpty()) return

        val steps = actions.mapNotNull { action ->
            runCatching { AgentJson.encodeToString(AgentAction.serializer(), action) }
                .getOrNull()
                ?.let { RoutineStep(actionJson = it, label = action.action) }
        }
        if (steps.isEmpty()) return

        viewModelScope.launch {
            routines.save(
                Routine(
                    id = UUID.randomUUID().toString(),
                    name = prompt.take(70),
                    prompt = prompt,
                    createdAtMillis = System.currentTimeMillis(),
                    steps = steps,
                ),
            )
            _routineSaved.value = true
        }
    }

    fun stop() {
        runtime.stop()
        AgentForegroundService.stop(context)
    }
    fun pause() = runtime.pause()
    fun resume() = runtime.resume()
    fun allow() = runtime.resolveConfirmation(true)
    fun deny() = runtime.resolveConfirmation(false)
    fun answer(text: String) = runtime.answerQuestion(text)
    fun retry() = runtime.start(runtime.progress.value.prompt)
}
