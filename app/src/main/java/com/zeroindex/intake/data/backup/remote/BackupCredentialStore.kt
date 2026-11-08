package com.zeroindex.intake.data.backup.remote

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.zeroindex.intake.domain.backup.BackupProtocol
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The secret half of backups and sync: the server password (or SFTP private
 * key) per protocol, the passphrase backups are encrypted with, and this
 * device's sync token.
 *
 * The passphrase has to live on the phone for a backup to run at 2 AM with no
 * one there to type it. What it protects is the copy on the server: someone
 * who gets the NAS or the cloud folder gets ciphertext. Someone who gets this
 * phone unlocked already has the data in the app itself.
 *
 * Same storage as [com.zeroindex.intake.data.keys.ApiKeyStore]: encrypted
 * preferences, excluded from Android backup, never logged, never exported.
 */
@Singleton
class BackupCredentialStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private companion object {
        const val TAG = "BackupCredentials"
        const val FILE = "intake_backup_secrets"
        const val PASSPHRASE = "backup_passphrase"
        const val SYNC_TOKEN = "sync_device_token"
        fun secretKey(protocol: BackupProtocol) = "secret_${protocol.name.lowercase()}"
        fun privateKeyKey() = "sftp_private_key"
    }

    private val prefs: SharedPreferences? by lazy {
        runCatching {
            EncryptedSharedPreferences.create(
                context,
                FILE,
                MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }.onFailure { Log.w(TAG, "Encrypted preferences unavailable; backups to a server are off", it) }.getOrNull()
    }

    private val _state = MutableStateFlow(read())
    val state: StateFlow<BackupSecretsState> = _state.asStateFlow()

    fun password(protocol: BackupProtocol): String? = prefs?.getString(secretKey(protocol), null)?.takeIf { it.isNotEmpty() }
    fun sftpPrivateKey(): String? = prefs?.getString(privateKeyKey(), null)?.takeIf { it.isNotBlank() }
    fun passphrase(): CharArray? = prefs?.getString(PASSPHRASE, null)?.takeIf { it.isNotEmpty() }?.toCharArray()

    fun syncToken(): String? = prefs?.getString(SYNC_TOKEN, null)?.takeIf { it.isNotEmpty() }
    fun setSyncToken(value: String?) = put(SYNC_TOKEN, value)

    fun setPassword(protocol: BackupProtocol, value: String?) = put(secretKey(protocol), value)
    fun setSftpPrivateKey(value: String?) = put(privateKeyKey(), value?.trim())
    fun setPassphrase(value: String?) = put(PASSPHRASE, value)

    private fun put(name: String, value: String?) {
        val editor = prefs?.edit() ?: return
        if (value.isNullOrEmpty()) editor.remove(name) else editor.putString(name, value)
        editor.apply()
        _state.value = read()
    }

    private fun read() = BackupSecretsState(
        passwords = BackupProtocol.entries.filter { password(it) != null }.toSet(),
        hasPrivateKey = sftpPrivateKey() != null,
        hasPassphrase = passphrase()?.also { it.fill('\u0000') } != null,
        available = prefs != null,
    )
}

/** Which secrets exist, never their values. */
data class BackupSecretsState(
    val passwords: Set<BackupProtocol> = emptySet(),
    val hasPrivateKey: Boolean = false,
    val hasPassphrase: Boolean = false,
    val available: Boolean = true,
)
