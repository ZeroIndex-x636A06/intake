package com.zeroindex.intake.data.backup.remote

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.Session
import com.jcraft.jsch.SftpException
import com.jcraft.jsch.UserInfo
import com.zeroindex.intake.domain.backup.RemotePaths
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * SFTP through JSch (the maintained mwiede fork). Host keys are pinned on
 * first use: there is no known_hosts file on a phone, so the first test shows
 * the server's SHA-256 fingerprint - the same string `ssh-keygen -lf` prints -
 * and only a fingerprint the user confirmed is ever accepted afterwards.
 */
class SftpStore(
    private val host: String,
    private val port: Int,
    private val folder: String,
    private val username: String,
    private val password: String?,
    private val privateKey: String?,
    private val pinnedFingerprint: String?,
) : RemoteStore {

    private class PinningRepository(private val pinned: String?) : HostKeyRepository {
        var seen: Pair<String, String>? = null

        override fun check(host: String?, key: ByteArray): Int {
            val fp = fingerprint(key)
            seen = fp to keyType(key)
            return when {
                pinned == null -> HostKeyRepository.NOT_INCLUDED
                pinned == fp -> HostKeyRepository.OK
                else -> HostKeyRepository.CHANGED
            }
        }

        override fun add(hostkey: HostKey?, ui: UserInfo?) = Unit
        override fun remove(host: String?, type: String?) = Unit
        override fun remove(host: String?, type: String?, key: ByteArray?) = Unit
        override fun getKnownHostsRepositoryID(): String = "intake-pinned"
        override fun getHostKey(): Array<HostKey> = emptyArray()
        override fun getHostKey(host: String?, type: String?): Array<HostKey> = emptyArray()
    }

    private suspend fun <T> withSftp(block: (ChannelSftp) -> T): T = withContext(Dispatchers.IO) {
        val jsch = JSch()
        val repo = PinningRepository(pinnedFingerprint)
        jsch.setHostKeyRepository(repo)
        privateKey?.let { jsch.addIdentity("intake-backup", it.toByteArray(), null, null) }
        var session: Session? = null
        try {
            session = jsch.getSession(username, host, port).apply {
                password?.let(::setPassword)
                setConfig("StrictHostKeyChecking", "yes")
                setConfig("PreferredAuthentications", if (privateKey != null) "publickey,password" else "password,keyboard-interactive")
                timeout = 30_000
                connect(15_000)
            }
            val channel = (session.openChannel("sftp") as ChannelSftp).apply { connect(15_000) }
            try {
                block(channel)
            } finally {
                channel.disconnect()
            }
        } catch (e: JSchException) {
            val seen = repo.seen
            when {
                seen != null && pinnedFingerprint == null -> throw UnpinnedHostKeyException(seen.first, seen.second)
                seen != null && pinnedFingerprint != seen.first -> throw ChangedHostKeyException(seen.first)
                e.message?.contains("Auth", ignoreCase = true) == true ->
                    throw RemoteStoreException("$host refused the username, password or key.", e)
                else -> throw RemoteStoreException("Could not reach $host on port $port.", e)
            }
        } catch (e: SftpException) {
            throw RemoteStoreException(
                when (e.id) {
                    ChannelSftp.SSH_FX_PERMISSION_DENIED -> "$host does not allow writing to that folder."
                    ChannelSftp.SSH_FX_NO_SUCH_FILE -> "$host has no such folder and would not create it."
                    else -> "The SFTP server on $host returned an error (code ${e.id})."
                },
                e,
            )
        } finally {
            session?.disconnect()
        }
    }

    private fun ChannelSftp.ensureFolder(): String {
        var path = ""
        for (segment in RemotePaths.segments(folder)) {
            path += "/$segment"
            val exists = try { stat(path).isDir } catch (e: SftpException) {
                if (e.id != ChannelSftp.SSH_FX_NO_SUCH_FILE) throw e
                false
            }
            if (!exists) mkdir(path)
        }
        return path.ifEmpty { "." }
    }

    override suspend fun test(): String = withSftp { sftp ->
        val dir = sftp.ensureFolder()
        val probe = "$dir/.intake-write-test"
        sftp.put("ok".byteInputStream(), probe)
        sftp.rm(probe)
        "Connected to $host. The folder exists and is writable."
    }

    override suspend fun put(name: String, bytes: ByteArray) = withSftp { sftp ->
        val dir = sftp.ensureFolder()
        val partial = "$dir/$name.partial"
        val final = "$dir/$name"
        sftp.put(bytes.inputStream(), partial)
        // SFTP v3 rename refuses to overwrite, so clear the target first.
        runCatching { sftp.rm(final) }
        sftp.rename(partial, final)
    }

    override suspend fun list(): List<String> = withSftp { sftp ->
        val dir = sftp.ensureFolder()
        @Suppress("UNCHECKED_CAST")
        (sftp.ls(dir) as java.util.Vector<ChannelSftp.LsEntry>)
            .filter { !it.attrs.isDir }
            .map { it.filename }
    }

    override suspend fun remove(name: String) = withSftp { sftp ->
        val dir = sftp.ensureFolder()
        try { sftp.rm("$dir/$name") } catch (e: SftpException) {
            if (e.id != ChannelSftp.SSH_FX_NO_SUCH_FILE) throw e
        }
    }

    companion object {
        /** OpenSSH's format: "SHA256:" and unpadded base64 of the key blob's digest. */
        fun fingerprint(key: ByteArray): String =
            "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(key))

        /** The algorithm name at the start of an SSH public key blob, e.g. "ssh-ed25519". */
        fun keyType(key: ByteArray): String = runCatching {
            val buf = ByteBuffer.wrap(key)
            val len = buf.int
            require(len in 1..64)
            String(key, 4, len, Charsets.US_ASCII)
        }.getOrDefault("unknown")
    }
}
