package com.zeroindex.intake.data.backup.remote

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The secret half of device sync: this device's sync token. It also clears what the removed remote backups
 * (WebDAV, SFTP, SMB) used to keep here: server passwords, the SFTP private key and the backup passphrase.
 *
 * Same storage as [com.zeroindex.intake.data.keys.ApiKeyStore]: encrypted preferences, excluded from Android
 * backup, never logged, never exported.
 */
@Singleton
class BackupCredentialStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private companion object {
        const val TAG = "BackupCredentials"
        const val FILE = "intake_backup_secrets"
        const val SYNC_TOKEN = "sync_device_token"
        /** Entry names written by the removed remote backups; removed on launch. */
        val DESTINATION_SECRETS = listOf(
            "backup_passphrase", "secret_webdav", "secret_sftp", "secret_smb", "sftp_private_key",
        )
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
        }.onFailure { Log.w(TAG, "Encrypted preferences unavailable; sync is off", it) }.getOrNull()
    }

    fun syncToken(): String? = prefs?.getString(SYNC_TOKEN, null)?.takeIf { it.isNotEmpty() }
    fun setSyncToken(value: String?) {
        val editor = prefs?.edit() ?: return
        if (value.isNullOrEmpty()) editor.remove(SYNC_TOKEN) else editor.putString(SYNC_TOKEN, value)
        editor.apply()
    }

    /** Removes every credential of the old remote backup destination. Keeps the sync token. */
    fun clearDestinationSecrets() {
        val p = prefs ?: return
        if (DESTINATION_SECRETS.none { p.contains(it) }) return
        val editor = p.edit()
        DESTINATION_SECRETS.forEach { editor.remove(it) }
        editor.apply()
    }
}
