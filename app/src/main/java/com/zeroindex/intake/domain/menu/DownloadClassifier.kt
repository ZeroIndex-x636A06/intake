package com.zeroindex.intake.domain.menu

/** What a menu download turned out to be, judged from the status line, the headers and the first bytes. */
sealed interface DownloadVerdict {
    /** A real PDF: it starts with the `%PDF-` marker, whatever the server called it. */
    data object Pdf : DownloadVerdict

    /** An ordinary web page or text, to be read as before. */
    data object Page : DownloadVerdict

    /** The site said no (401, 403 or 429). Nothing was read. */
    data class Refused(val status: Int) : DownloadVerdict

    /** The address ends in .pdf or the server said PDF, but the bytes are not one: typically an error or consent page. */
    data object NotPdf : DownloadVerdict

    /** Some other failure (404, 500, ...). The caller falls back to its older behaviour. */
    data class Failed(val status: Int) : DownloadVerdict
}

/**
 * Checks a download before anything parses it. Pure, so it is unit tested.
 *
 * A site's bot protection often answers an app with a 403 and an HTML "Access Denied" page. That must be reported as
 * a refusal, not fed to the PDF reader. The client never imitates a browser to get round such a refusal.
 */
object DownloadClassifier {
    /** How much of the start of the body is looked at for the PDF marker (the PDF spec allows a little junk first). */
    const val HEAD_BYTES = 1024

    /** Statuses that mean "this site will not hand the file to an app". */
    fun isRefusalStatus(status: Int): Boolean = status == 401 || status == 403 || status == 429

    fun classify(status: Int, contentType: String?, url: String, head: ByteArray): DownloadVerdict {
        if (isRefusalStatus(status)) return DownloadVerdict.Refused(status)
        if (status !in 200..299) return DownloadVerdict.Failed(status)
        if (hasPdfMarker(head)) return DownloadVerdict.Pdf
        val type = contentType.orEmpty().lowercase()
        val addressSaysPdf = url.substringBefore('?').substringBefore('#').endsWith(".pdf", ignoreCase = true)
        return if ("pdf" in type || addressSaysPdf) DownloadVerdict.NotPdf else DownloadVerdict.Page
    }

    fun hasPdfMarker(head: ByteArray): Boolean {
        val limit = minOf(head.size, HEAD_BYTES) - 4
        for (i in 0..limit) {
            if (head[i] == '%'.code.toByte() && head[i + 1] == 'P'.code.toByte() && head[i + 2] == 'D'.code.toByte() &&
                head[i + 3] == 'F'.code.toByte() && head[i + 4] == '-'.code.toByte()
            ) {
                return true
            }
        }
        return false
    }

    /** Appears in every message that has the "choose it yourself" way out, so the screen can offer the file picker. */
    const val PICKER_HINT = "Choose a PDF"

    fun refusedMessage(host: String, status: Int): String =
        "$host refused the download (HTTP $status). Open the link in your browser, save the PDF, " +
            "then choose it with '$PICKER_HINT'."

    fun notPdfMessage(host: String): String =
        "$host sent a web page instead of the PDF, which usually means it blocks apps. Open the link in your browser, " +
            "save the PDF, then choose it with '$PICKER_HINT'."

    fun offersFilePicker(message: String?): Boolean = message != null && message.contains("'$PICKER_HINT'")

    fun hostOf(url: String): String =
        url.substringAfter("://", url).substringBefore('/').substringBefore('?').substringBefore('#')
            .substringBefore(':').removePrefix("www.").ifBlank { "The site" }
}
