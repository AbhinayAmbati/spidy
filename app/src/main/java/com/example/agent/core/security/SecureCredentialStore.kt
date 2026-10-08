package com.example.agent.core.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.agent.domain.model.ProviderType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Encrypted, device-bound storage for API credentials.
 *
 *  - Keys go to [EncryptedSharedPreferences] (AES-256, key in the Android Keystore).
 *  - [getApiKey] is the only read path that returns the raw secret.
 *  - [maskedApiKey] is what the UI may show — never the full value.
 *  - Nothing here is ever logged.
 */
class SecureCredentialStore(context: Context) {

    /**
     * Creating this touches the Android Keystore, which can fail on a device with a
     * broken or unavailable keystore. Throwing from the constructor would take the whole
     * app down at injection time, so the failure is captured and surfaced to the UI
     * instead of being hidden.
     */
    private val creation: Result<SharedPreferences> = runCatching {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private val prefs: SharedPreferences? get() = creation.getOrNull()

    /** Human-readable reason credential storage is unusable, or null when healthy. */
    val unavailableReason: String?
        get() = creation.exceptionOrNull()?.let {
            "${it::class.simpleName}: ${it.message ?: "no detail"}"
        }

    private val _version = MutableStateFlow(0)

    /**
     * Bumps on every credential change. Derived state (such as a "key saved" indicator)
     * lives in flows backed by *other* storage, so without this it would never refresh.
     */
    val version: StateFlow<Int> = _version.asStateFlow()

    fun getApiKey(type: ProviderType): String? =
        prefs?.getString(keyFor(type), null)?.takeIf { it.isNotBlank() }

    fun hasApiKey(type: ProviderType): Boolean = getApiKey(type) != null

    /**
     * Writes the key synchronously so a failure is reported rather than swallowed.
     * @throws IllegalStateException if storage is unavailable or the write fails.
     * @throws IllegalArgumentException if the key is blank.
     */
    fun saveApiKey(type: ProviderType, apiKey: String) {
        val store = prefs ?: throw IllegalStateException(
            "Encrypted storage is unavailable (${unavailableReason ?: "unknown reason"})"
        )
        val trimmed = apiKey.trim()
        require(trimmed.isNotEmpty()) { "The API key is empty" }
        // commit() rather than apply(): we want to know the write actually landed.
        if (!store.edit().putString(keyFor(type), trimmed).commit()) {
            throw IllegalStateException("The API key could not be written to encrypted storage")
        }
        _version.value++
    }

    fun deleteApiKey(type: ProviderType) {
        prefs?.edit()?.remove(keyFor(type))?.commit()
        _version.value++
    }

    /** Safe for display: shows only the last 4 characters. */
    fun maskedApiKey(type: ProviderType): String? {
        val key = getApiKey(type) ?: return null
        return "•••• •••• •••• ${key.takeLast(4)}"
    }

    private fun keyFor(type: ProviderType) = "api_key_${type.name.lowercase()}"

    private companion object {
        const val FILE_NAME = "spidey_credentials"
    }
}
