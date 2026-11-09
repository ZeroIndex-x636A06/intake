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
        const val KEY_OPENROUTER = "openrouter_api_key"
        const val KEY_USDA = "usda_api_key"
        const val KEY_OFF_USER = "off_username"
        const val KEY_OFF_PASSWORD = "off_password"

        /**
         * Superseded by OpenRouter (amendment §1), which serves Anthropic
         * models among others. Removed on start so no unused secret lingers.
         */
        const val KEY_ANTHROPIC_LEGACY = "anthropic_api_key"
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

    private val _state = MutableStateFlow(dropLegacyKeys().let { read() })
    val state: StateFlow<ApiKeyState> = _state.asStateFlow()

    fun openRouterKey(): String? = prefs?.getString(KEY_OPENROUTER, null)?.takeIf { it.isNotBlank() }
    fun usdaKey(): String? = prefs?.getString(KEY_USDA, null)?.takeIf { it.isNotBlank() }

    /**
     * The user's own Open Food Facts account, used only to contribute a product they
     * chose to send (docs/FOOD-DATA.md). Stored like the keys above; read only by
     * the contribution call, which puts them in a POST body and nowhere else.
     */
    fun offUsername(): String? = prefs?.getString(KEY_OFF_USER, null)?.takeIf { it.isNotBlank() }
    fun offPassword(): String? = prefs?.getString(KEY_OFF_PASSWORD, null)?.takeIf { it.isNotEmpty() }

    fun setOffCredentials(username: String?, password: String?) {
        val editor = prefs?.edit() ?: return
        if (username.isNullOrBlank()) editor.remove(KEY_OFF_USER) else editor.putString(KEY_OFF_USER, username.trim())
        if (password.isNullOrEmpty()) editor.remove(KEY_OFF_PASSWORD) else editor.putString(KEY_OFF_PASSWORD, password)
        editor.apply()
        _state.value = read()
    }

    fun setOpenRouterKey(key: String?) = put(KEY_OPENROUTER, key)
    fun setUsdaKey(key: String?) = put(KEY_USDA, key)

    fun refresh() { _state.value = read() }

    private fun dropLegacyKeys() {
        prefs?.takeIf { it.contains(KEY_ANTHROPIC_LEGACY) }?.edit()?.remove(KEY_ANTHROPIC_LEGACY)?.apply()
    }

    private fun put(name: String, value: String?) {
        val editor = prefs?.edit() ?: return
        if (value.isNullOrBlank()) editor.remove(name) else editor.putString(name, value.trim())
        editor.apply()
        _state.value = read()
    }

    private fun read() = ApiKeyState(
        hasOpenRouterKey = openRouterKey() != null,
        hasUsdaKey = usdaKey() != null,
        hasOffCredentials = offUsername() != null && offPassword() != null,
        offUsername = offUsername(),
        storeAvailable = prefs != null,
    )
}

/** Only whether a key exists is exposed to the UI - never the key or password itself. */
data class ApiKeyState(
    val hasOpenRouterKey: Boolean = false,
    val hasUsdaKey: Boolean = false,
    /** Whether an Open Food Facts username and password are saved. The password is never exposed. */
    val hasOffCredentials: Boolean = false,
    /** The username is not a secret and is shown back to the user; the password never is. */
    val offUsername: String? = null,
    val storeAvailable: Boolean = true,
)
