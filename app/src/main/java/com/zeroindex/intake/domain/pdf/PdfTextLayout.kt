package com.zeroindex.intake.domain.pdf

/** A piece of text a PDF reader reported, with where it sits on the page (any consistent unit, y growing downward). */
data class TextFragment(val text: String, val left: Float, val top: Float, val right: Float, val bottom: Float)

/**
 * Puts the text fragments a phone's PDF reader returns back into reading lines.
 *
 * A reader may hand back one fragment per word, per cell or per line, in any order. Fragments whose vertical middles
 * are close (within half the typical text height) belong to one line; inside a line they run left to right and are
 * joined with a space. Text that has no position (a reader that gives none) is kept as it came, one line per
 * fragment, so the table parser still gets whole lines.
 */
object PdfTextLayout {

    fun lines(fragments: List<TextFragment>): List<String> {
        val clean = fragments.filter { it.text.isNotBlank() }
        if (clean.isEmpty()) return emptyList()
        val positioned = clean.filter { it.bottom > it.top }
        // No usable positions: trust the reader's own order and line breaks.
        if (positioned.isEmpty() || positioned.size * 2 < clean.size) return clean.flatMap { it.text.lines() }.map { it.trim() }.filter { it.isNotEmpty() }

        val heights = positioned.map { it.bottom - it.top }.sorted()
        val tolerance = heights[heights.size / 2] * 0.5f
        val sorted = positioned.sortedBy { (it.top + it.bottom) / 2 }
        val lines = ArrayList<MutableList<TextFragment>>()
        var lineMiddle = 0f
        for (f in sorted) {
            val mid = (f.top + f.bottom) / 2
            if (lines.isNotEmpty() && kotlin.math.abs(mid - lineMiddle) <= tolerance) {
                lines.last() += f
                // Follow the line as it fills, so a slightly slanted row still joins up.
                lineMiddle = lines.last().sumOf { ((it.top + it.bottom) / 2).toDouble() }.toFloat() / lines.last().size
            } else {
                lines += mutableListOf(f)
                lineMiddle = mid
            }
        }
        return lines.map { line -> line.sortedBy { it.left }.joinToString(" ") { it.text.trim() } }
            .map { it.replace(Regex("\\s+"), " ").trim() }
            .filter { it.isNotEmpty() }
    }
}
