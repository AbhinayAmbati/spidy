package com.example.agent.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.agent.core.audit.AuditPolicy
import com.example.agent.core.audit.RequestTextMode
import com.example.agent.core.security.SecureCredentialStore
import com.example.agent.domain.model.AppearanceMode
import com.example.agent.domain.model.ProviderConfig
import com.example.agent.domain.model.ProviderType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "spidey_settings")

/**
 * Non-secret user settings. Secrets live only in [SecureCredentialStore]; this
 * class stores *which* provider/model is selected and never a key.
 */
class SettingsRepository(
    private val context: Context,
    private val credentials: SecureCredentialStore,
) {

    private object Keys {
        val PROVIDER = stringPreferencesKey("provider")
        val MODEL = stringPreferencesKey("model")
        val BASE_URL = stringPreferencesKey("base_url")
        val APPEARANCE = stringPreferencesKey("appearance")
        val MEMORY_ENABLED = booleanPreferencesKey("memory_enabled")
        val TOUCH_TO_STOP = booleanPreferencesKey("touch_to_stop")
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val VOICE_GLOW = booleanPreferencesKey("voice_glow")
        val VOICE_FACE = booleanPreferencesKey("voice_face")
        val AUDIT_RETENTION_DAYS = intPreferencesKey("audit_retention_days")
        val AUDIT_KEEP_CONTACT_NAMES = booleanPreferencesKey("audit_keep_contact_names")
        val AUDIT_REQUEST_TEXT = stringPreferencesKey("audit_request_text")
    }

    /**
     * Combines settings with the credential store's version counter. The API key lives in
     * EncryptedSharedPreferences, NOT DataStore, so without that second source this flow
     * would never re-emit after a key is saved and `hasApiKey` would stay stuck on false.
     */
    val providerConfig: Flow<ProviderConfig> = combine(
        context.dataStore.data,
        credentials.version,
    ) { prefs, _ ->
        val type = ProviderType.fromKey(prefs[Keys.PROVIDER])
        ProviderConfig(
            type = type,
            model = prefs[Keys.MODEL] ?: type.defaultModel,
            baseUrl = prefs[Keys.BASE_URL] ?: type.defaultBaseUrl,
            hasApiKey = credentials.hasApiKey(type),
        )
    }

    /** Null when encrypted credential storage is healthy; otherwise why it is not. */
    val credentialStorageError: String? get() = credentials.unavailableReason

    suspend fun currentProviderConfig(): ProviderConfig = providerConfig.first()

    suspend fun saveProvider(type: ProviderType, model: String, baseUrl: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.PROVIDER] = type.name
            prefs[Keys.MODEL] = model
            prefs[Keys.BASE_URL] = baseUrl
        }
    }

    val appearance: Flow<AppearanceMode> = context.dataStore.data.map {
        AppearanceMode.fromKey(it[Keys.APPEARANCE])
    }

    suspend fun setAppearance(mode: AppearanceMode) {
        context.dataStore.edit { it[Keys.APPEARANCE] = mode.name }
    }

    val memoryEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.MEMORY_ENABLED] ?: true }

    suspend fun setMemoryEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.MEMORY_ENABLED] = enabled }
    }

    /** Whether touching the screen stops a running task. */
    val touchToStop: Flow<Boolean> = context.dataStore.data.map { it[Keys.TOUCH_TO_STOP] ?: true }

    suspend fun setTouchToStop(enabled: Boolean) {
        context.dataStore.edit { it[Keys.TOUCH_TO_STOP] = enabled }
    }

    /** The blue glow around the screen edges while listening. */
    val voiceGlow: Flow<Boolean> = context.dataStore.data.map { it[Keys.VOICE_GLOW] ?: true }

    suspend fun setVoiceGlow(enabled: Boolean) {
        context.dataStore.edit { it[Keys.VOICE_GLOW] = enabled }
    }

    /** The small ASCII character on the status overlay. */
    val voiceFace: Flow<Boolean> = context.dataStore.data.map { it[Keys.VOICE_FACE] ?: true }

    suspend fun setVoiceFace(enabled: Boolean) {
        context.dataStore.edit { it[Keys.VOICE_FACE] = enabled }
    }

    val onboarded: Flow<Boolean> = context.dataStore.data.map { it[Keys.ONBOARDED] ?: false }

    suspend fun setOnboarded(value: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDED] = value }
    }

    // ---- audit and privacy ----

    /** Days audit records are kept; 0 turns the audit log off. An administrator may override it. */
    val auditRetentionDays: Flow<Int> = context.dataStore.data.map {
        it[Keys.AUDIT_RETENTION_DAYS] ?: AuditPolicy.DEFAULT_RETENTION_DAYS
    }

    suspend fun setAuditRetentionDays(days: Int) {
        context.dataStore.edit { it[Keys.AUDIT_RETENTION_DAYS] = days.coerceAtLeast(0) }
    }

    /** Off by default: contacts are stored as a placeholder. */
    val auditKeepContactNames: Flow<Boolean> = context.dataStore.data.map {
        it[Keys.AUDIT_KEEP_CONTACT_NAMES] ?: false
    }

    suspend fun setAuditKeepContactNames(keep: Boolean) {
        context.dataStore.edit { it[Keys.AUDIT_KEEP_CONTACT_NAMES] = keep }
    }

    val auditRequestTextMode: Flow<RequestTextMode> = context.dataStore.data.map {
        RequestTextMode.fromKey(it[Keys.AUDIT_REQUEST_TEXT])
    }

    suspend fun setAuditRequestTextMode(mode: RequestTextMode) {
        context.dataStore.edit { it[Keys.AUDIT_REQUEST_TEXT] = mode.key }
    }

    // ---- credentials (delegated so callers have one settings surface) ----

    fun hasApiKey(type: ProviderType) = credentials.hasApiKey(type)
    fun maskedApiKey(type: ProviderType) = credentials.maskedApiKey(type)
    fun saveApiKey(type: ProviderType, key: String) = credentials.saveApiKey(type, key)
    fun deleteApiKey(type: ProviderType) = credentials.deleteApiKey(type)
}
