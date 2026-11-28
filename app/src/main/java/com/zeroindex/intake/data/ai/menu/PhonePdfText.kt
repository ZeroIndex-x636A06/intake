package com.zeroindex.intake.data.ai.menu

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import com.zeroindex.intake.domain.pdf.PdfTextLayout
import com.zeroindex.intake.domain.pdf.TextFragment
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Reads the text of a PDF on this phone, with no network and no model.
 *
 * It uses the platform's own PDF reader: `PdfRenderer.Page.getTextContents()`, which exists from Android 15 (API 35).
 * On an older phone it says so and reads nothing, and the import falls back to the AI path as before. The text comes
 * back as positioned fragments; [PdfTextLayout] puts them into lines, whatever order the reader returned them in.
 *
 * Not seen working on a device yet: written against the platform API, checked by compiling and by the parser's tests
 * on text extracted elsewhere from the same kind of file.
 */
@Singleton
class PhonePdfText @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    sealed interface Result {
        /** One entry per page, each page as reading lines joined with newlines. */
        data class Pages(val pages: List<String>, val fragments: List<List<TextFragment>> = emptyList()) : Result

        /** This phone cannot read PDF text (older than Android 15). */
        data class Unsupported(val reason: String) : Result

        /** The reader refused or failed on this file (password, damaged). */
        data class Failed(val reason: String) : Result
    }

    val isSupported: Boolean get() = Build.VERSION.SDK_INT >= MIN_SDK

    suspend fun read(bytes: ByteArray): Result {
        if (!isSupported) return Result.Unsupported("Reading a PDF on the phone needs Android 15 or later.")
        return withContext(Dispatchers.IO) {
            val file = File.createTempFile("menu-", ".pdf", context.cacheDir)
            try {
                file.writeBytes(bytes)
                readPages(file)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: SecurityException) {
                Result.Failed("This PDF is password protected.")
            } catch (e: Exception) {
                Result.Failed("The phone's PDF reader could not open this file.")
            } finally {
                file.delete()
            }
        }
    }

    // The caller checked isSupported; the lint check cannot see through it.
    @android.annotation.SuppressLint("NewApi")
    private suspend fun readPages(file: File): Result {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { renderer ->
                val count = minOf(renderer.pageCount, MAX_PAGES)
                val pages = ArrayList<String>(count)
                val positioned = ArrayList<List<TextFragment>>(count)
                for (i in 0 until count) {
                    coroutineContext.ensureActive()
                    renderer.openPage(i).use { page ->
                        val fragments = page.textContents.map { c ->
                            val b = c.bounds
                            if (b.isEmpty()) {
                                TextFragment(c.text, 0f, 0f, 0f, 0f)
                            } else {
                                TextFragment(
                                    c.text,
                                    b.minOf { it.left }, b.minOf { it.top }, b.maxOf { it.right }, b.maxOf { it.bottom },
                                )
                            }
                        }
                        pages += PdfTextLayout.lines(fragments).joinToString("\n")
                        positioned += fragments
                    }
                }
                return Result.Pages(pages, positioned)
            }
        }
    }

    companion object {
        const val MIN_SDK = 35
        const val MAX_PAGES = 100
    }
}
