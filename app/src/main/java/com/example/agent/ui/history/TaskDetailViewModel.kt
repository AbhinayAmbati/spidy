package com.example.agent.ui.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.agent.core.agent.AgentRuntime
import com.example.agent.data.repository.TaskHistoryRepository
import com.example.agent.domain.model.TaskRecord
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TaskDetailViewModel @Inject constructor(
    private val history: TaskHistoryRepository,
    private val runtime: AgentRuntime,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val taskId: String = savedStateHandle.get<String>("taskId").orEmpty()

    private val _task = MutableStateFlow<TaskRecord?>(null)
    val task: StateFlow<TaskRecord?> = _task.asStateFlow()

    init {
        viewModelScope.launch { _task.value = history.getById(taskId) }
    }

    /** Re-runs the same prompt as a fresh task. */
    fun runAgain() {
        _task.value?.prompt?.takeIf { it.isNotBlank() }?.let { runtime.start(it) }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            history.delete(taskId)
            onDeleted()
        }
    }
}
