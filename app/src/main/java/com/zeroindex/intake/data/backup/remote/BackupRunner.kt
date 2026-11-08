package com.zeroindex.intake.data.backup.remote

import com.zeroindex.intake.data.backup.BackupService
import com.zeroindex.intake.data.db.dao.HealthSummaryDao
import com.zeroindex.intake.data.db.entity.BackupRunEntity
import com.zeroindex.intake.data.db.entity.BackupStatus
import com.zeroindex.intake.data.settings.SettingsRepository
import com.zeroindex.intake.domain.backup.BackupDestination
import com.zeroindex.intake.domain.backup.BackupNames
import com.zeroindex.intake.domain.backup.BackupProtocol
import com.zeroindex.intake.domain.backup.WebDavUrlPolicy
import com.zeroindex.intake.domain.time.Clock
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient

sealed interface ConnectionTest {
    data class Ok(val message: String) : ConnectionTest
    /** SFTP's first contact: show [fingerprint] and pin it only if the user agrees. */
    data class ConfirmHostKey(val fingerprint: String, val keyType: String) : ConnectionTest
    data class HostKeyChanged(val fingerprint: String) : ConnectionTest
    data class Failed(val message: String) : ConnectionTest
}

sealed interface BackupOutcome {
    data class Done(val name: String, val bytes: Long, val pruned: Int) : BackupOutcome
    data class Skipped(val reason: String) : BackupOutcome
    /** [transient] failures (the server was unreachable) are worth retrying tonight; the rest are not. */
    data class Failed(val message: String, val transient: Boolean) : BackupOutcome
}

/**
 * Amendment §2's headless backup: export everything, encrypt it with the
 * backup passphrase, upload it, prune to the newest [BackupNames.KEEP], and
 * record the attempt. Used by the nightly worker and by "Back up now".
 *
 * A backup never goes to a server unencrypted: without a passphrase the run
 * stops before exporting anything.
 */
@Singleton
class BackupRunner @Inject constructor(
    private val backupService: BackupService,
    private val credentials: BackupCredentialStore,
    private val settings: SettingsRepository,
    private val dao: HealthSummaryDao,
    http: OkHttpClient,
    private val clock: Clock,
) {
    /** A whole-database upload can take minutes on a slow uplink. */
    private val uploadHttp = http.newBuilder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.MINUTES)
        .build()

    private val mutex = Mutex()

    fun problemWith(dest: BackupDestination): String? {
        if (!dest.isComplete) return "Fill in the server, username" + if (dest.protocol == BackupProtocol.SMB) " and share." else "."
        if (dest.protocol == BackupProtocol.WEBDAV) {
            (WebDavUrlPolicy.check(dest.host) as? WebDavUrlPolicy.Verdict.Rejected)?.let { return it.reason }
        }
        val hasSecret = credentials.password(dest.protocol) != null ||
            (dest.protocol == BackupProtocol.SFTP && credentials.sftpPrivateKey() != null)
        if (!hasSecret) return "Enter the password" + if (dest.protocol == BackupProtocol.SFTP) " or a private key." else "."
        return null
    }

    internal fun storeFor(dest: BackupDestination): RemoteStore = when (dest.protocol) {
        BackupProtocol.WEBDAV -> WebDavStore(uploadHttp, dest.host, dest.path, dest.username, credentials.password(dest.protocol).orEmpty())
        BackupProtocol.SFTP -> SftpStore(
            host = dest.host.trim(),
            port = dest.port ?: 22,
            folder = dest.path,
            username = dest.username,
            password = credentials.password(dest.protocol),
            privateKey = credentials.sftpPrivateKey(),
            pinnedFingerprint = dest.hostKeySha256,
        )
        BackupProtocol.SMB -> SmbStore(dest.host.trim(), dest.share.trim(), dest.path, dest.username, credentials.password(dest.protocol).orEmpty(), dest.domain)
    }

    suspend fun test(dest: BackupDestination): ConnectionTest {
        problemWith(dest)?.let { return ConnectionTest.Failed(it) }
        return try {
            ConnectionTest.Ok(storeFor(dest).test())
        } catch (e: UnpinnedHostKeyException) {
            ConnectionTest.ConfirmHostKey(e.fingerprint, e.keyType)
        } catch (e: ChangedHostKeyException) {
            ConnectionTest.HostKeyChanged(e.fingerprint)
        } catch (e: RemoteStoreException) {
            ConnectionTest.Failed(e.message ?: "The server returned an error.")
        } catch (e: IllegalArgumentException) {
            ConnectionTest.Failed(e.message ?: "Check the server settings.")
        }
    }

    suspend fun run(): BackupOutcome = mutex.withLock {
        val dest = settings.settings.first().backupDestination
            ?: return BackupOutcome.Skipped("No backup server is set up.")
        problemWith(dest)?.let { return BackupOutcome.Skipped(it) }
        val passphrase = credentials.passphrase()
            ?: return BackupOutcome.Skipped("Set a backup passphrase first. Backups are never sent unencrypted.")

        val started = clock.nowMillis()
        val run = BackupRunEntity(
            startedAt = started,
            destination = dest.describe(),
            status = BackupStatus.RUNNING,
            createdAt = started,
            updatedAt = started,
        )
        dao.upsertBackupRun(run)

        val outcome = try {
            val bytes = try { backupService.exportBytes(passphrase) } finally { passphrase.fill('\u0000') }
            val store = storeFor(dest)
            val name = BackupNames.forTime(started)
            store.put(name, bytes)
            // Pruning is housekeeping: a failure here never fails the backup that just landed.
            val pruned = runCatching {
                BackupNames.toPrune(store.list()).onEach { store.remove(it) }.size
            }.getOrDefault(0)
            BackupOutcome.Done(name, bytes.size.toLong(), pruned)
        } catch (e: UnpinnedHostKeyException) {
            BackupOutcome.Failed("Confirm the server's identity with Test connection first.", transient = false)
        } catch (e: ChangedHostKeyException) {
            BackupOutcome.Failed(e.message.orEmpty(), transient = false)
        } catch (e: RemoteStoreException) {
            BackupOutcome.Failed(e.message.orEmpty(), transient = e.message?.startsWith("Could not reach") == true)
        } catch (e: Exception) {
            // Class name only: an unexpected exception's message could carry anything.
            BackupOutcome.Failed("Backup failed (${e.javaClass.simpleName}). Your local data is unchanged.", transient = false)
        }

        val finished = clock.nowMillis()
        dao.upsertBackupRun(
            when (outcome) {
                is BackupOutcome.Done -> run.copy(finishedAt = finished, bytes = outcome.bytes, status = BackupStatus.OK, updatedAt = finished)
                is BackupOutcome.Failed -> run.copy(finishedAt = finished, status = BackupStatus.FAILED, error = outcome.message, updatedAt = finished)
                is BackupOutcome.Skipped -> run
            },
        )
        if (outcome is BackupOutcome.Done) settings.setLastExportAt(finished)
        outcome
    }
}
