package com.zeroindex.intake.data.backup.remote

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.domain.backup.BackupNames
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertThrows
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Runs each transport against real servers. Off unless INTAKE_REMOTE_IT=1,
 * because it needs a WebDAV server on 127.0.0.1:8099/dav (user caleb), sshd
 * on 127.0.0.1:2222 and smbd on 127.0.0.1:445 with share "backup" (user bk).
 * docs/BACKUP.md has the exact setup used.
 */
class RemoteStoreIntegrationTest {

    private fun enabled() = assumeTrue("set INTAKE_REMOTE_IT=1 to run", System.getenv("INTAKE_REMOTE_IT") == "1")

    private suspend fun exercise(store: RemoteStore) {
        assertThat(store.test()).contains("writable")
        val a = BackupNames.forTime(1_000_000_000_000L)
        val b = BackupNames.forTime(1_000_086_400_000L)
        store.put(a, byteArrayOf(1, 2, 3))
        store.put(b, ByteArray(256 * 1024) { it.toByte() })
        store.put(b, byteArrayOf(9)) // overwrite in place
        val names = store.list()
        assertThat(names).containsAtLeast(a, b)
        assertThat(names.none { it.endsWith(".partial") || it == ".intake-write-test" }).isTrue()
        store.remove(a)
        store.remove(b)
        store.remove(b) // removing a missing file is not an error
        assertThat(store.list()).containsNoneOf(a, b)
    }

    @Test
    fun webdav() = runBlocking {
        enabled()
        exercise(WebDavStore(OkHttpClient(), "http://127.0.0.1:8099/dav", "/intake/it-webdav", "caleb", "dav-test-pw"))
        val wrong = WebDavStore(OkHttpClient(), "http://127.0.0.1:8099/dav", "/intake", "caleb", "nope")
        val e = assertThrows(RemoteStoreException::class.java) { runBlocking { wrong.test() } }
        assertThat(e.message).contains("refused")
        assertThat(e.message).doesNotContain("nope")
    }

    @Test
    fun sftpPinsTheHostKeyOnFirstUse() = runBlocking {
        enabled()
        fun store(pin: String?) = SftpStore("127.0.0.1", 2222, "/home/bk/intake/it-sftp", "bk", "sftp-test-pw", null, pin)
        val first = assertThrows(UnpinnedHostKeyException::class.java) { runBlocking { store(null).test() } }
        assertThat(first.fingerprint).startsWith("SHA256:")
        exercise(store(first.fingerprint))
        assertThrows(ChangedHostKeyException::class.java) {
            runBlocking { store("SHA256:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA").test() }
        }
        val wrongPassword = SftpStore("127.0.0.1", 2222, "/home/bk/x", "bk", "nope", null, first.fingerprint)
        val e = assertThrows(RemoteStoreException::class.java) { runBlocking { wrongPassword.test() } }
        assertThat(e.message).doesNotContain("nope")
    }

    @Test
    fun smb() = runBlocking {
        enabled()
        exercise(SmbStore("127.0.0.1", "backup", "/intake/it-smb", "bk", "sftp-test-pw", ""))
        val e = assertThrows(RemoteStoreException::class.java) {
            runBlocking { SmbStore("127.0.0.1", "nosuch", "/", "bk", "sftp-test-pw", "").test() }
        }
        assertThat(e.message).contains("no share")
    }
}
