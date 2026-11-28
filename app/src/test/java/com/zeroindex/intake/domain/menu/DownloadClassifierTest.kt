package com.zeroindex.intake.domain.menu

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DownloadClassifierTest {
    private val url = "https://www.mcdonalds.com/content/dam/sites/ie/nfl/pdf/nutrition/menu.pdf"

    /** The real Akamai refusal an app received for the McDonald's Ireland PDF: 494 bytes of HTML, no secrets. */
    private val accessDenied: ByteArray =
        javaClass.classLoader!!.getResourceAsStream("menu/akamai-access-denied.html")!!.readBytes()

    private fun pdfBytes() = "%PDF-1.7\n1 0 obj\n<< /Type /Catalog >>\nendobj\n".toByteArray(Charsets.ISO_8859_1)

    @Test
    fun `a 403 with an Access Denied web page is a refusal, never a pdf`() {
        assertThat(String(accessDenied)).contains("Access Denied")
        assertThat(DownloadClassifier.classify(403, "text/html", url, accessDenied)).isEqualTo(DownloadVerdict.Refused(403))
    }

    @Test
    fun `401 and 429 are refusals too, whatever the body is`() {
        assertThat(DownloadClassifier.classify(401, null, url, ByteArray(0))).isEqualTo(DownloadVerdict.Refused(401))
        assertThat(DownloadClassifier.classify(429, "application/pdf", url, pdfBytes())).isEqualTo(DownloadVerdict.Refused(429))
    }

    @Test
    fun `a 200 with html at a pdf address is not a pdf`() {
        assertThat(DownloadClassifier.classify(200, "text/html; charset=utf-8", url, accessDenied)).isEqualTo(DownloadVerdict.NotPdf)
    }

    @Test
    fun `a 200 with html at an ordinary address is a page`() {
        val html = "<html><body>menu</body></html>".toByteArray()
        assertThat(DownloadClassifier.classify(200, "text/html", "https://example.com/menu", html)).isEqualTo(DownloadVerdict.Page)
    }

    @Test
    fun `a 200 with a pdf is a pdf`() {
        assertThat(DownloadClassifier.classify(200, "application/pdf", url, pdfBytes())).isEqualTo(DownloadVerdict.Pdf)
    }

    @Test
    fun `a pdf served with the wrong content type is still a pdf`() {
        assertThat(DownloadClassifier.classify(200, "application/octet-stream", url, pdfBytes())).isEqualTo(DownloadVerdict.Pdf)
        assertThat(DownloadClassifier.classify(200, "text/html", "https://example.com/dl?id=3", pdfBytes())).isEqualTo(DownloadVerdict.Pdf)
    }

    @Test
    fun `the marker may follow a little junk but only inside the first kilobyte`() {
        val near = "\r\n".toByteArray() + pdfBytes()
        assertThat(DownloadClassifier.hasPdfMarker(near)).isTrue()
        val far = ByteArray(2000) { ' '.code.toByte() } + pdfBytes()
        assertThat(DownloadClassifier.hasPdfMarker(far)).isFalse()
    }

    @Test
    fun `other failures are left to the older fallback`() {
        assertThat(DownloadClassifier.classify(404, "text/html", url, accessDenied)).isEqualTo(DownloadVerdict.Failed(404))
    }

    @Test
    fun `the refusal message names the site and the status and offers the file picker`() {
        val m = DownloadClassifier.refusedMessage(DownloadClassifier.hostOf(url), 403)
        assertThat(m).startsWith("mcdonalds.com refused the download (HTTP 403).")
        assertThat(m).contains("Open the link in your browser, save the PDF")
        assertThat(DownloadClassifier.offersFilePicker(m)).isTrue()
        assertThat(DownloadClassifier.offersFilePicker("No menu items were found in that PDF.")).isFalse()
    }
}
