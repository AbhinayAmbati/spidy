package com.example.agent.core.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.agent.domain.model.ProviderType

/**
 * Encrypted, device-bound storage for API credentials.
 *
 * Guarantees:
 *  - Keys are written to [EncryptedSharedPreferences] (AES-256, key held in the
 *    Android Keystore). Never plain SharedPreferences.
 *  - [getApiKey] is the ONLY read path and returns the raw secret; it is called
 *    solely by provider adapters at request time.
 *  - [maskedApiKey] is what the UI is allowed to show — never the full value.
 *  - Nothing here is ever logged.
 */
class SecureCredentialStore(context: Context) {

    private val prefs = run {
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

    fun getApiKey(type: ProviderType): String? =
        prefs.getString(keyFor(type), null)?.takeIf { it.isNotBlank() }

    fun hasApiKey(type: ProviderType): Boolean = getApiKey(type) != null

    fun saveApiKey(type: ProviderType, apiKey: String) {
        prefs.edit().putString(keyFor(type), apiKey.trim()).apply()
    }

    fun deleteApiKey(type: ProviderType) {
        prefs.edit().remove(keyFor(type)).apply()
    }

    /** Safe for display: shows only the last 4 characters. */
    fun maskedApiKey(type: ProviderType): String? {
        val key = getApiKey(type) ?: return null
        val tail = key.takeLast(4)
        return "•••• •••• •••• $tail"
    }

    private fun keyFor(type: ProviderType) = "api_key_${type.name.lowercase()}"

    private companion object {
        const val FILE_NAME = "spidey_credentials"
    }
}
