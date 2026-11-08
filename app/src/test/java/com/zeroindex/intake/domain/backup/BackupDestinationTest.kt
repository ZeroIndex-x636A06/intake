package com.zeroindex.intake.domain.backup

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupDestinationTest {

    @Test
    fun `paths normalise slashes and refuse parent references`() {
        assertThat(RemotePaths.normalize("intake//backups/")).isEqualTo("/intake/backups")
        assertThat(RemotePaths.normalize("\\nas\\intake")).isEqualTo("/nas/intake")
        assertThat(RemotePaths.normalize("/")).isEqualTo("")
        assertThat(RemotePaths.join("/a/b/", "f.intake")).isEqualTo("/a/b/f.intake")
        assertThrows(IllegalArgumentException::class.java) { RemotePaths.normalize("/a/../etc") }
    }

    @Test
    fun `backup names sort by time and pruning keeps the newest`() {
        val names = (1..5).map { BackupNames.forTime(1_700_000_000_000L + it * 86_400_000L) }
        assertThat(names.sorted()).isEqualTo(names)
        val prune = BackupNames.toPrune(names + "notes.txt" + "intake-backup-latest.intake", keep = 3)
        assertThat(prune).containsExactly(names[0], names[1]).inOrder()
    }

    @Test
    fun `names are utc so they do not depend on the phone's zone`() {
        assertThat(BackupNames.forTime(0)).isEqualTo("intake-backup-19700101-000000.intake")
        assertThat(BackupNames.isOurs("intake-backup-19700101-000000.intake")).isTrue()
        assertThat(BackupNames.isOurs("intake-backup-1970.intake")).isFalse()
    }

    @Test
    fun `webdav requires https except for plainly local hosts`() {
        assertThat(WebDavUrlPolicy.check("https://nas.example.net/dav")).isEqualTo(WebDavUrlPolicy.Verdict.Ok)
        assertThat(WebDavUrlPolicy.check("http://192.168.1.20:5005/dav")).isEqualTo(WebDavUrlPolicy.Verdict.LocalPlainHttp)
        assertThat(WebDavUrlPolicy.check("http://nas.local/dav")).isEqualTo(WebDavUrlPolicy.Verdict.LocalPlainHttp)
        assertThat(WebDavUrlPolicy.check("http://[fd00::5]/dav")).isEqualTo(WebDavUrlPolicy.Verdict.LocalPlainHttp)
        assertThat(WebDavUrlPolicy.check("http://nas.example.net/dav")).isInstanceOf(WebDavUrlPolicy.Verdict.Rejected::class.java)
        assertThat(WebDavUrlPolicy.check("http://8.8.8.8/")).isInstanceOf(WebDavUrlPolicy.Verdict.Rejected::class.java)
        assertThat(WebDavUrlPolicy.check("ftp://nas.local/")).isInstanceOf(WebDavUrlPolicy.Verdict.Rejected::class.java)
        assertThat(WebDavUrlPolicy.check("https://me:pw@nas.example.net/")).isInstanceOf(WebDavUrlPolicy.Verdict.Rejected::class.java)
    }

    @Test
    fun `description never includes credentials`() {
        val d = BackupDestination(BackupProtocol.WEBDAV, host = "https://nas.example.net/dav", username = "caleb")
        assertThat(d.describe()).isEqualTo("WebDAV · nas.example.net/intake/backups")
        assertThat(d.isComplete).isTrue()
        assertThat(BackupDestination(BackupProtocol.SMB, host = "nas", username = "u").isComplete).isFalse()
    }
}
