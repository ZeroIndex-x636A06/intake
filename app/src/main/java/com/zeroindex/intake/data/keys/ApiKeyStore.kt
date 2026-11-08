package com.zeroindex.intake.data.keys

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * SPEC §3.3: API keys are supplied by the user at runtime and stored in
 * EncryptedSharedPreferences. No key is hardcoded, none is in the repo, and the
 * app is fully functional without any key minus the AI feature.
 */
@Singleton
class ApiKeyStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private companion object {
        const val TAG = "ApiKeyStore"
        const val FILE = "intake_api_keys"
        const val KEY_ANTHROPIC = "anthropic_api_key"
        const val KEY_USDA = "usda_api_key"
    }

    /**
     * Null when the keystore is unavailable. That happens on a small number of
     * devices with broken keystore implementations, and it must degrade to "no
     * keys" rather than taking the app down on launch.
     */
    private val prefs: SharedPreferences? by lazy {
        runCatching {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }.onFailure {
            Log.w(TAG, "Encrypted preferences unavailable; running without API keys", it)
        }.getOrNull()
    }

    private val _state = MutableStateFlow(read())
    val state: StateFlow<ApiKeyState> = _state.asStateFlow()

    fun anthropicKey(): String? = prefs?.getString(KEY_ANTHROPIC, null)?.takeIf { it.isNotBlank() }
    fun usdaKey(): String? = prefs?.getString(KEY_USDA, null)?.takeIf { it.isNotBlank() }

    fun setAnthropicKey(key: String?) = put(KEY_ANTHROPIC, key)
    fun setUsdaKey(key: String?) = put(KEY_USDA, key)

    fun refresh() { _state.value = read() }

    private fun put(name: String, value: String?) {
        val editor = prefs?.edit() ?: return
        if (value.isNullOrBlank()) editor.remove(name) else editor.putString(name, value.trim())
        editor.apply()
        _state.value = read()
    }

    private fun read() = ApiKeyState(
        hasAnthropicKey = anthropicKey() != null,
        hasUsdaKey = usdaKey() != null,
        storeAvailable = prefs != null,
    )
}

/** Only whether a key exists is exposed to the UI - never the key itself. */
data class ApiKeyState(
    val hasAnthropicKey: Boolean = false,
    val hasUsdaKey: Boolean = false,
    val storeAvailable: Boolean = true,
)
