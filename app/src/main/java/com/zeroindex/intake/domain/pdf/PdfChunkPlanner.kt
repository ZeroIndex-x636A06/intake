package com.zeroindex.intake.domain.pdf

/** A run of pages, zero-based and inclusive, sent as one request. */
data class PdfChunk(val from: Int, val to: Int, val estimatedBytes: Long) {
    val pages: Int get() = to - from + 1

    /** "pages 3-4" or "page 3", numbered from 1 for people. */
    val label: String get() = if (from == to) "page ${from + 1}" else "pages ${from + 1}-${to + 1}"
}

/**
 * Splits a long PDF into bounded requests (B4). One request holding a whole nutrition-and-allergen table is too
 * big to send, too slow to wait on, and asks the model for more output than it will give: a page of such a table
 * is already 40 or 50 rows. Chunks are a few pages and a size budget, so each request stays small and a failure
 * costs one chunk, not the import.
 */
object PdfChunkPlanner {
    /** A PDF larger than this is refused with a clear message before anything is sent. */
    const val MAX_PDF_BYTES = 15 * 1024 * 1024

    /** More pages than this is a book, not a menu. */
    const val MAX_PAGES = 80

    const val MAX_PAGES_PER_CHUNK = 2

    /** The size budget of one request's PDF, estimated; one oversized page still gets a chunk of its own. */
    const val MAX_CHUNK_BYTES = 2_000_000L

    /**
     * Greedy plan over [pageBytes] (the estimated size of each page): keep adding pages while the chunk stays
     * within [maxPages] and [maxBytes]. Every page lands in exactly one chunk, in order.
     */
    fun plan(
        pageBytes: List<Long>,
        maxPages: Int = MAX_PAGES_PER_CHUNK,
        maxBytes: Long = MAX_CHUNK_BYTES,
    ): List<PdfChunk> {
        require(maxPages >= 1) { "a chunk needs at least one page" }
        val chunks = ArrayList<PdfChunk>()
        var from = 0
        var bytes = 0L
        pageBytes.forEachIndexed { i, size ->
            val pagesSoFar = i - from
            if (pagesSoFar > 0 && (pagesSoFar >= maxPages || bytes + size > maxBytes)) {
                chunks += PdfChunk(from, i - 1, bytes)
                from = i
                bytes = 0
            }
            bytes += size
        }
        if (pageBytes.isNotEmpty()) chunks += PdfChunk(from, pageBytes.size - 1, bytes)
        return chunks
    }

    /** The message for a download or file over [MAX_PDF_BYTES]. */
    fun tooBigMessage(bytes: Long): String =
        "That PDF is ${"%.1f".format(bytes / 1024.0 / 1024.0)} MB, and the limit is ${MAX_PDF_BYTES / 1024 / 1024} MB. " +
            "Use a smaller PDF, or paste the menu text instead."
}
