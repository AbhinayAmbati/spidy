package com.example.agent.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.agent.core.permissions.PermissionStatus
import com.example.agent.core.permissions.PermissionUtils
import com.example.agent.data.preferences.SettingsRepository
import com.example.agent.data.repository.TaskHistoryRepository
import com.example.agent.domain.model.AppearanceMode
import com.example.agent.domain.model.ProviderConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val history: TaskHistoryRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val appearance: StateFlow<AppearanceMode> = settings.appearance
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppearanceMode.System)

    val providerConfig: StateFlow<ProviderConfig> = settings.providerConfig
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProviderConfig())

    val memoryEnabled: StateFlow<Boolean> = settings.memoryEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    private val _permissions = MutableStateFlow(PermissionUtils.status(context))
    val permissions: StateFlow<PermissionStatus> = _permissions

    fun refreshPermissions() {
        _permissions.value = PermissionUtils.status(context)
    }

    fun setAppearance(mode: AppearanceMode) = viewModelScope.launch { settings.setAppearance(mode) }

    fun setMemory(enabled: Boolean) = viewModelScope.launch { settings.setMemoryEnabled(enabled) }

    fun clearHistory() = viewModelScope.launch { history.clear() }
}
