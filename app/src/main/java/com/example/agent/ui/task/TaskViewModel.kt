package com.example.agent.ui.task

import android.content.Context
import androidx.lifecycle.ViewModel
import com.example.agent.automation.AgentForegroundService
import com.example.agent.core.agent.AgentRuntime
import com.example.agent.domain.model.TaskProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class TaskViewModel @Inject constructor(
    private val runtime: AgentRuntime,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val progress: StateFlow<TaskProgress> = runtime.progress
    val paused: StateFlow<Boolean> = runtime.paused

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
