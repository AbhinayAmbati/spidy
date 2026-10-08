package com.example.agent.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.agent.automation.AgentForegroundService
import com.example.agent.core.agent.AgentRuntime
import com.example.agent.core.permissions.PermissionUtils
import com.example.agent.data.repository.TaskHistoryRepository
import com.example.agent.domain.model.TaskProgress
import com.example.agent.domain.model.TaskRecord
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val runtime: AgentRuntime,
    private val history: TaskHistoryRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val progress: StateFlow<TaskProgress> = runtime.progress

    val recentTasks: StateFlow<List<TaskRecord>> = history.observeRecent(3)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _accessibilityEnabled = MutableStateFlow(PermissionUtils.isAccessibilityEnabled(context))
    val accessibilityEnabled: StateFlow<Boolean> = _accessibilityEnabled

    fun refreshPermissions() {
        _accessibilityEnabled.value = PermissionUtils.isAccessibilityEnabled(context)
    }

    fun submit(prompt: String) {
        val trimmed = prompt.trim()
        if (trimmed.isEmpty() || runtime.isRunning) return
        AgentForegroundService.start(context)
        runtime.start(trimmed)
    }
}
