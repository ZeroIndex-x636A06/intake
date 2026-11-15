package com.zeroindex.intake.domain.allergen

/**
 * Splits an ingredient panel into what the food is made of and what the label only says it MAY contain
 * ("may contain milk", "traces of nuts", "made in a facility that also processes soy").
 *
 * A trace statement is a possible cross-contact, not an ingredient, so the engine reads it as MAY_CONTAIN, the same tier
 * Open Food Facts "traces" tags get. It is still a warning (an amber review before logging). Nothing here can clear
 * anything: the trace text is scanned like the rest, only its ceiling is the may-contain tier, and a CONTAINS hit for
 * the same word in the real ingredient part still wins (the engine keeps the stronger hit per allergen word).
 */
object TraceText {
    private val MARKER = Regex(
        "(?:may|might|can|could) contain|traces? of|" +
            "(?:produced|made|processed|manufactured|packed|packaged) (?:in|on) (?:a |an |the )?(?:shared |same )?(?:facility|factory|plant|equipment|lines?)",
        RegexOption.IGNORE_CASE,
    )
    private val END = Regex("[.;|\\n)]|\\bcontains?\\b", RegexOption.IGNORE_CASE)

    data class Split(val main: String?, val traces: String?)

    fun split(text: String?): Split {
        if (text.isNullOrBlank()) return Split(text, null)
        val main = StringBuilder()
        val traces = StringBuilder()
        var cursor = 0
        while (true) {
            val m = MARKER.find(text, cursor)
            if (m == null) {
                main.append(text, cursor, text.length)
                break
            }
            main.append(text, cursor, m.range.first).append(". ")
            val end = END.find(text, m.range.last + 1)?.range?.first ?: text.length
            traces.append(text, m.range.first, end).append(". ")
            cursor = end
        }
        val tr = traces.toString().trim().takeIf { it.isNotEmpty() }
        return if (tr == null) Split(text, null) else Split(main.toString().trim().ifBlank { null }, tr)
    }
}
