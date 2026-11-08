package com.zeroindex.intake.data.backup.remote

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WebDavStoreTest {

    private val multistatus = """
        <?xml version="1.0"?>
        <d:multistatus xmlns:d="DAV:">
          <d:response><d:href>/dav/intake/backups/</d:href></d:response>
          <d:response><d:href>/dav/intake/backups/intake-backup-20261107-020000.intake</d:href></d:response>
          <d:response><d:href>https://nas.example.net/dav/intake/backups/notes%20old.txt</d:href></d:response>
          <d:response><d:href>/dav/intake/backups/archive/</d:href></d:response>
        </d:multistatus>
    """.trimIndent()

    @Test
    fun `lists files and skips collections`() {
        val names = WebDavStore.fileNames(WebDavStore.hrefs(multistatus))
        assertThat(names).containsExactly("intake-backup-20261107-020000.intake", "notes old.txt").inOrder()
    }

    @Test
    fun `reads hrefs whatever the namespace prefix`() {
        val xml = "<D:multistatus><D:response><D:href>/a/b.intake</D:href></D:response>" +
            "<response><href>/a/c.intake</href></response></D:multistatus>"
        assertThat(WebDavStore.hrefs(xml)).containsExactly("/a/b.intake", "/a/c.intake")
    }

    @Test
    fun `a plus in a file name is not read as a space`() {
        assertThat(WebDavStore.fileNames(listOf("/a/x+y.intake"))).containsExactly("x+y.intake")
    }
}
