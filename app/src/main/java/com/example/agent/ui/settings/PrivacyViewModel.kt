package com.example.agent.ui.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.agent.core.audit.AuditPolicy
import com.example.agent.core.audit.ManagedConfig
import com.example.agent.core.audit.ManagedConfigValues
import com.example.agent.core.audit.RequestTextMode
import com.example.agent.data.preferences.SettingsRepository
import com.example.agent.data.repository.AuditRepository
import com.example.agent.data.repository.PrivacyDataManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The outcome of the last export or delete, shown under the matching row. */
enum class PrivacyStatus { None, Exported, ExportFailed, Deleted }

/** Everything the "Privacy and data" group draws. */
data class PrivacyUiState(
    val retentionDays: Int = AuditPolicy.DEFAULT_RETENTION_DAYS,
    val requestTextMode: RequestTextMode = RequestTextMode.HASH,
    val keepContactNames: Boolean = false,
    val managed: ManagedConfigValues = ManagedConfigValues(),
    val auditCount: Int = 0,
    val contactsGranted: Boolean = false,
    val microphoneGranted: Boolean = false,
    val status: PrivacyStatus = PrivacyStatus.None,
) {
    /** The retention in force: an administrator value wins over the user's choice. */
    val effectiveRetentionDays: Int get() = managed.auditRetentionDays ?: retentionDays
}

/** What the group can ask for, so the composables stay free of the view model. */
data class PrivacyActions(
    val onRetentionChange: (Int) -> Unit,
    val onRequestTextModeChange: (RequestTextMode) -> Unit,
    val onKeepContactNamesChange: (Boolean) -> Unit,
    val onExport: (Uri) -> Unit,
    val onDeleteAll: () -> Unit,
    val onRequestContacts: () -> Unit,
    val onRequestMicrophone: () -> Unit,
) {
    companion object {
        val None = PrivacyActions({}, {}, {}, {}, {}, {}, {})
    }
}

@HiltViewModel
class PrivacyViewModel @Inject constructor(
    private val settings: SettingsRepository,
    managedConfig: ManagedConfig,
    private val audit: AuditRepository,
    private val data: PrivacyDataManager,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val status = MutableStateFlow(PrivacyStatus.None)
    private val permissions = MutableStateFlow(readPermissions())

    private val prefs = combine(
        settings.auditRetentionDays,
        settings.auditRequestTextMode,
        settings.auditKeepContactNames,
    ) { days, mode, keep -> Triple(days, mode, keep) }

    private val extras = combine(
        managedConfig.values,
        audit.count,
        permissions,
        status,
    ) { managed, count, perms, last -> Extras(managed, count, perms, last) }

    val state: StateFlow<PrivacyUiState> = combine(prefs, extras) { p, e ->
        PrivacyUiState(
            retentionDays = p.first,
            requestTextMode = p.second,
            keepContactNames = p.third,
            managed = e.managed,
            auditCount = e.count,
            contactsGranted = e.permissions.first,
            microphoneGranted = e.permissions.second,
            status = e.status,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PrivacyUiState())

    init {
        // Retention is enforced whenever the app starts, not only when something is recorded.
        viewModelScope.launch { runCatching { audit.pruneExpired() } }
    }

    fun refreshPermissions() {
        permissions.value = readPermissions()
    }

    fun setRetention(days: Int) = viewModelScope.launch {
        settings.setAuditRetentionDays(days)
        runCatching { audit.pruneExpired() }
    }

    fun setRequestTextMode(mode: RequestTextMode) =
        viewModelScope.launch { settings.setAuditRequestTextMode(mode) }

    fun setKeepContactNames(keep: Boolean) =
        viewModelScope.launch { settings.setAuditKeepContactNames(keep) }

    fun export(target: Uri) = viewModelScope.launch {
        status.value = if (data.export(target)) PrivacyStatus.Exported else PrivacyStatus.ExportFailed
    }

    fun deleteAll() = viewModelScope.launch {
        data.deleteAll()
        status.value = PrivacyStatus.Deleted
    }

    private fun readPermissions(): Pair<Boolean, Boolean> = Pair(
        granted(Manifest.permission.READ_CONTACTS),
        granted(Manifest.permission.RECORD_AUDIO),
    )

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private data class Extras(
        val managed: ManagedConfigValues,
        val count: Int,
        val permissions: Pair<Boolean, Boolean>,
        val status: PrivacyStatus,
    )
}
