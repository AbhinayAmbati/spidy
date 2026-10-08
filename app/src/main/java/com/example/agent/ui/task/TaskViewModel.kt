package com.example.agent.ui.task

import androidx.lifecycle.ViewModel
import com.example.agent.core.agent.AgentRuntime
import com.example.agent.domain.model.TaskProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class TaskViewModel @Inject constructor(
    private val runtime: AgentRuntime,
) : ViewModel() {

    val progress: StateFlow<TaskProgress> = runtime.progress
    val paused: StateFlow<Boolean> = runtime.paused

    fun stop() = runtime.stop()
    fun pause() = runtime.pause()
    fun resume() = runtime.resume()
    fun allow() = runtime.resolveConfirmation(true)
    fun deny() = runtime.resolveConfirmation(false)
    fun answer(text: String) = runtime.answerQuestion(text)
    fun retry() = runtime.start(runtime.progress.value.prompt)
}
