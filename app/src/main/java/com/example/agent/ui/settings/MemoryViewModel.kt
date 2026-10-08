package com.example.agent.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.agent.data.preferences.SettingsRepository
import com.example.agent.data.repository.MemoryRepository
import com.example.agent.domain.model.MemoryEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MemoryViewModel @Inject constructor(
    private val memory: MemoryRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    val entries: StateFlow<List<MemoryEntry>> = memory.entries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val memoryEnabled: StateFlow<Boolean> = settings.memoryEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setMemoryEnabled(enabled: Boolean) = viewModelScope.launch {
        settings.setMemoryEnabled(enabled)
    }

    fun forget(key: String) = viewModelScope.launch { memory.forget(key) }

    fun clearAll() = viewModelScope.launch { memory.clear() }
}
