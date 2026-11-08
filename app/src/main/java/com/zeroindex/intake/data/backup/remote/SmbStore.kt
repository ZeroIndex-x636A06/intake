package com.zeroindex.intake.data.backup.remote

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.mssmb2.SMBApiException
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import com.zeroindex.intake.domain.backup.RemotePaths
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * SMB 2/3 through smbj, for a Windows share or a NAS's SMB service. Message
 * signing is required, so a tampering middlebox cannot swap the file; the
 * file itself is already encrypted before it leaves the phone.
 */
class SmbStore(
    private val host: String,
    private val shareName: String,
    private val folder: String,
    private val username: String,
    private val password: String,
    private val domain: String,
) : RemoteStore {

    private val config = SmbConfig.builder()
        .withTimeout(30, TimeUnit.SECONDS)
        .withSoTimeout(30, TimeUnit.SECONDS)
        .withSigningRequired(true)
        .build()

    /** smbj wants backslashes and no leading separator. */
    private val dir: String = RemotePaths.segments(folder).joinToString("\\")

    private fun child(name: String) = if (dir.isEmpty()) name else "$dir\\$name"

    private suspend fun <T> withShare(block: (DiskShare) -> T): T = withContext(Dispatchers.IO) {
        try {
            SMBClient(config).use { client ->
                client.connect(host).use { connection ->
                    val session = connection.authenticate(AuthenticationContext(username, password.toCharArray(), domain.ifBlank { null }))
                    (session.connectShare(shareName) as? DiskShare ?: throw RemoteStoreException("\\\\$host\\$shareName is not a file share."))
                        .use(block)
                }
            }
        } catch (e: SMBApiException) {
            val status = e.status.name
            throw RemoteStoreException(
                when {
                    status.contains("LOGON_FAILURE") || status.contains("ACCESS_DENIED") && status.contains("LOGON") ->
                        "$host refused the username or password."
                    status.contains("ACCESS_DENIED") -> "$host does not allow writing to that folder."
                    status.contains("BAD_NETWORK_NAME") -> "$host has no share called \"$shareName\"."
                    status.contains("DISK_FULL") -> "$host is out of space."
                    else -> "The SMB server on $host returned $status."
                },
                e,
            )
        } catch (e: IOException) {
            throw RemoteStoreException("Could not reach $host over SMB.", e)
        }
    }

    private fun DiskShare.ensureFolder() {
        var path = ""
        for (segment in RemotePaths.segments(folder)) {
            path = if (path.isEmpty()) segment else "$path\\$segment"
            if (!folderExists(path)) mkdir(path)
        }
    }

    private fun DiskShare.write(path: String, bytes: ByteArray) {
        openFile(
            path,
            setOf(AccessMask.GENERIC_WRITE, AccessMask.DELETE),
            setOf(FileAttributes.FILE_ATTRIBUTE_NORMAL),
            SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OVERWRITE_IF,
            null,
        ).use { file -> file.outputStream.use { it.write(bytes) } }
    }

    override suspend fun test(): String = withShare { share ->
        share.ensureFolder()
        val probe = child(".intake-write-test")
        share.write(probe, "ok".toByteArray())
        share.rm(probe)
        "Connected to \\\\$host\\$shareName. The folder exists and is writable."
    }

    override suspend fun put(name: String, bytes: ByteArray) = withShare { share ->
        share.ensureFolder()
        val partial = child("$name.partial")
        share.write(partial, bytes)
        share.openFile(
            partial,
            setOf(AccessMask.DELETE, AccessMask.GENERIC_WRITE),
            null,
            SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OPEN,
            null,
        ).use { it.rename(child(name), true) }
    }

    override suspend fun list(): List<String> = withShare { share ->
        if (dir.isNotEmpty() && !share.folderExists(dir)) return@withShare emptyList()
        share.list(dir)
            .filter { it.fileName != "." && it.fileName != ".." }
            .filter { it.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value == 0L }
            .map { it.fileName }
    }

    override suspend fun remove(name: String) = withShare { share ->
        if (share.fileExists(child(name))) share.rm(child(name))
    }
}
