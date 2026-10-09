package com.example.agent.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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

    val onboarded: Flow<Boolean> = context.dataStore.data.map { it[Keys.ONBOARDED] ?: false }

    suspend fun setOnboarded(value: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDED] = value }
    }

    // ---- credentials (delegated so callers have one settings surface) ----

    fun hasApiKey(type: ProviderType) = credentials.hasApiKey(type)
    fun maskedApiKey(type: ProviderType) = credentials.maskedApiKey(type)
    fun saveApiKey(type: ProviderType, key: String) = credentials.saveApiKey(type, key)
    fun deleteApiKey(type: ProviderType) = credentials.deleteApiKey(type)
}
