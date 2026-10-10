package com.example.agent.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.agent.core.permissions.AccessibilityWatch
import com.example.agent.core.permissions.PermissionStatus
import com.example.agent.core.permissions.PermissionUtils
import com.example.agent.data.preferences.SettingsRepository
import com.example.agent.data.repository.TaskHistoryRepository
import com.example.agent.domain.model.AppearanceMode
import com.example.agent.domain.model.ProviderConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    val touchToStop: StateFlow<Boolean> = settings.touchToStop
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    private val accessibility = AccessibilityWatch(context, viewModelScope)

    val permissions: StateFlow<PermissionStatus> = accessibility.status

    /** True only when accessibility has stayed disconnected long enough to be a real fault. */
    val accessibilityStuck: StateFlow<Boolean> = accessibility.stuck

    /**
     * Whether the status overlay is allowed to draw over other apps.
     *
     * Granted on a *system* screen, not a dialog, so this has to be re-read when the user
     * comes back rather than assumed.
     */
    private val _overlayAllowed = MutableStateFlow(Settings.canDrawOverlays(context))
    val overlayAllowed: StateFlow<Boolean> = _overlayAllowed.asStateFlow()

    fun refreshOverlayPermission() {
        _overlayAllowed.value = Settings.canDrawOverlays(context)
    }

    fun openOverlaySettings() {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            .setData(Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    fun refreshPermissions() = accessibility.refresh()

    fun setAppearance(mode: AppearanceMode) = viewModelScope.launch { settings.setAppearance(mode) }

    fun setMemory(enabled: Boolean) = viewModelScope.launch { settings.setMemoryEnabled(enabled) }

    fun setTouchToStop(enabled: Boolean) = viewModelScope.launch { settings.setTouchToStop(enabled) }

    fun clearHistory() = viewModelScope.launch { history.clear() }
}
