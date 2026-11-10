package com.zeroindex.intake.data.ai.menu

import com.zeroindex.intake.domain.ai.MenuText
import com.zeroindex.intake.domain.pdf.PdfChunkPlanner
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/** What a menu address turned out to be. */
sealed interface Fetched {
    class Page(val html: String?, val text: String?) : Fetched
    class Pdf(val bytes: ByteArray) : Fetched
    data object Unavailable : Fetched
}

/** Download progress: bytes so far and, when the server said, the total. */
fun interface DownloadProgress {
    fun onBytes(read: Long, total: Long?)
}

/**
 * Fetches a menu address off the main thread, with a size cap and cancellation (B4).
 *
 * The Arby's link is a 300 KB PDF. The old fetch read every response with `body.string()`, which decodes the
 * whole body as text with no limit, so a PDF came back as thousands of characters of binary noise that was
 * then handed to the model as if it were a web page. This reads the body in a bounded stream, recognises a PDF
 * by its content type, its first bytes or its address, refuses anything over [PdfChunkPlanner.MAX_PDF_BYTES]
 * with a clear message before reading the rest, and stops the moment the coroutine is cancelled (the OkHttp
 * call is cancelled with it).
 */
@Singleton
class MenuFetcher @Inject constructor(private val http: OkHttpClient) {

    private companion object {
        const val USER_AGENT = "Intake/1.0 (personal menu import; one request per menu per week)"
        const val MAX_HTML_BYTES = 4 * 1024 * 1024
    }

    /** A patient copy of the shared client: a 15 MB PDF over a slow link needs more than the lookup defaults. */
    private val patient: OkHttpClient by lazy {
        http.newBuilder()
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(120, TimeUnit.SECONDS)
            .build()
    }

    suspend fun fetch(url: String, progress: DownloadProgress = DownloadProgress { _, _ -> }): Fetched =
        withContext(Dispatchers.IO) {
            val request = runCatching {
                Request.Builder().url(url).header("User-Agent", USER_AGENT)
                    .header("Accept", "text/html,application/pdf;q=0.9,*/*;q=0.5").get().build()
            }.getOrElse { throw MenuImportException("That does not look like a web address.") }

            val response = try {
                patient.newCall(request).await()
            } catch (e: IOException) {
                return@withContext Fetched.Unavailable
            }
            response.use { r ->
                if (!r.isSuccessful) return@withContext Fetched.Unavailable
                val type = r.header("Content-Type").orEmpty().lowercase()
                val length = r.body.contentLength().takeIf { it >= 0 }
                val looksPdf = "pdf" in type || url.substringBefore('?').substringBefore('#').endsWith(".pdf", ignoreCase = true)
                val limit = if (looksPdf) PdfChunkPlanner.MAX_PDF_BYTES.toLong() else MAX_HTML_BYTES.toLong()
                if (length != null && length > limit && looksPdf) {
                    throw MenuImportException(PdfChunkPlanner.tooBigMessage(length))
                }
                val bytes = try {
                    readCapped(r.body.byteStream(), limit, length, progress)
                } catch (e: IOException) {
                    currentCoroutineContext().ensureActive()
                    return@withContext Fetched.Unavailable
                }
                if (bytes.size > limit) {
                    if (looksPdf) throw MenuImportException(PdfChunkPlanner.tooBigMessage(bytes.size.toLong()))
                    // A web page past the cap is read as far as it goes: the menu is near the top of a page.
                }
                val isPdf = looksPdf || startsWithPdf(bytes)
                if (isPdf) {
                    if (bytes.size > PdfChunkPlanner.MAX_PDF_BYTES) throw MenuImportException(PdfChunkPlanner.tooBigMessage(bytes.size.toLong()))
                    return@withContext Fetched.Pdf(bytes)
                }
                val body = String(bytes, Charsets.UTF_8)
                val isHtml = "html" in type || body.trimStart().startsWith("<")
                Fetched.Page(
                    html = body.takeIf { isHtml },
                    text = if (isHtml) MenuText.fromHtml(body) else body.take(MenuText.MAX_CHARS),
                )
            }
        }

    /**
     * Reads at most [limit] bytes plus one, so "too big" can be told from "exactly at the limit", checking for
     * cancellation between reads and reporting progress.
     */
    internal suspend fun readCapped(input: InputStream, limit: Long, total: Long?, progress: DownloadProgress): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var read = 0L
        while (read <= limit) {
            currentCoroutineContext().ensureActive()
            val n = input.read(buffer, 0, minOf(buffer.size.toLong(), limit + 1 - read).toInt())
            if (n < 0) break
            out.write(buffer, 0, n)
            read += n
            progress.onBytes(read, total)
        }
        return out.toByteArray()
    }

    private fun startsWithPdf(bytes: ByteArray): Boolean =
        bytes.size >= 5 && bytes[0] == '%'.code.toByte() && bytes[1] == 'P'.code.toByte() &&
            bytes[2] == 'D'.code.toByte() && bytes[3] == 'F'.code.toByte() && bytes[4] == '-'.code.toByte()
}

/** Reads a picked file's stream with the same cap and the same cancellation. */
internal suspend fun readPdfStream(input: InputStream, progress: DownloadProgress = DownloadProgress { _, _ -> }): ByteArray {
    val limit = PdfChunkPlanner.MAX_PDF_BYTES.toLong()
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(16 * 1024)
    var read = 0L
    while (true) {
        currentCoroutineContext().ensureActive()
        val n = input.read(buffer)
        if (n < 0) break
        read += n
        if (read > limit) throw MenuImportException(PdfChunkPlanner.tooBigMessage(read))
        out.write(buffer, 0, n)
        progress.onBytes(read, null)
    }
    return out.toByteArray()
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    enqueue(
        object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!cont.isCancelled) cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) = cont.resume(response)
        },
    )
    cont.invokeOnCancellation { runCatching { cancel() } }
}

/** Rethrows a cancellation so a caller that catches broadly does not swallow Cancel. */
internal inline fun <T> rethrowingCancellation(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    Result.failure(e)
}
