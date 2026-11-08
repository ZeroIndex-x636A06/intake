package com.zeroindex.intake.domain.script

import kotlin.math.abs

/** One line of the file with HTML comments blanked out (so columns stay true). */
internal class SrcLine(val no: Int, val text: String) {
    val indent: Int = text.length - text.trimStart().length
    val trimmed: String = text.trim()
    val isBlank: Boolean get() = trimmed.isEmpty()
}

internal data class Label(
    val raw: String,
    /** Lower-case letters and digits only, so "Per 100 g" is "per100g". */
    val key: String,
    val value: String,
    val keyCol: Int,
    val valueCol: Int,
    val bulleted: Boolean,
)

internal data class Bullet(val indent: Int, val content: String, val contentCol: Int)

internal data class Heading(val level: Int, val text: String, val textCol: Int)

internal object ScriptText {
    const val MAX_CHARS = 2_000_000

    private val LABEL = Regex("""^(\s*)([-*+•]\s+)?([A-Za-z][A-Za-z0-9 ]{0,40}?)\s*:\s*(.*)$""")
    private val BULLET = Regex("""^(\s*)([-*+•]|\d{1,3}[.)])\s+(\S.*)$""")
    private val HEADING = Regex("""^\s{0,3}(#{1,6})\s*(.*)$""")

    /** Normalises line endings, blanks HTML comments and splits into lines. Null when too large. */
    fun prepare(raw: String, sink: DiagnosticSink): List<SrcLine>? {
        if (raw.length > MAX_CHARS) {
            sink.report(DiagnosticCode.FILE_TOO_LARGE, 1, 1, "This file is too large to be a script (limit 2,000,000 characters).")
            return null
        }
        val s = raw.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
        val chars = s.toCharArray()
        var i = 0
        while (true) {
            val start = s.indexOf("<!--", i)
            if (start < 0) break
            val end = s.indexOf("-->", start + 4)
            val stop = if (end < 0) s.length else end + 3
            if (end < 0) {
                val before = s.substring(0, start)
                val line = before.count { it == '\n' } + 1
                val col = start - (before.lastIndexOf('\n') + 1) + 1
                sink.report(
                    DiagnosticCode.UNCLOSED_COMMENT, line, col,
                    "This comment starts with <!-- but never ends with -->, so everything after it would be ignored.",
                )
            }
            for (k in start until stop) if (chars[k] != '\n') chars[k] = ' '
            i = stop
        }
        return String(chars).split('\n').mapIndexed { idx, t -> SrcLine(idx + 1, t.trimEnd()) }
    }

    fun key(s: String): String = s.lowercase().filter { it.isLetterOrDigit() }

    fun label(line: SrcLine, allowBullet: Boolean): Label? {
        val m = LABEL.matchEntire(line.text) ?: return null
        val bullet = m.groups[2]
        if (bullet != null && !allowBullet) return null
        val raw = m.groups[3]!!.value.trim()
        val valueGroup = m.groups[4]!!
        val valueCol = if (valueGroup.value.isEmpty()) line.text.length + 1 else valueGroup.range.first + 1
        return Label(raw, key(raw), valueGroup.value.trim(), m.groups[3]!!.range.first + 1, valueCol, bullet != null)
    }

    fun bullet(line: SrcLine): Bullet? {
        val m = BULLET.matchEntire(line.text) ?: return null
        return Bullet(m.groups[1]!!.value.length, m.groups[3]!!.value.trim(), m.groups[3]!!.range.first + 1)
    }

    fun heading(line: SrcLine): Heading? {
        val m = HEADING.matchEntire(line.text) ?: return null
        val text = m.groups[2]!!.value.trim()
        return Heading(m.groups[1]!!.value.length, text, if (text.isEmpty()) line.text.length + 1 else m.groups[2]!!.range.first + 1)
    }

    /** The closest candidate to [word], if one is near enough to be a typo. */
    fun suggest(word: String, candidates: Collection<String>): String? {
        val k = key(word)
        if (k.isEmpty()) return null
        return candidates.map { it to distance(k, key(it)) }
            .filter { (c, d) -> d <= 2 || key(c).startsWith(k) || k.startsWith(key(c)) }
            .minByOrNull { it.second }?.first
    }

    private fun distance(a: String, b: String): Int {
        val prev = IntArray(b.length + 1) { it }
        val cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            }
            System.arraycopy(cur, 0, prev, 0, prev.size)
        }
        return prev[b.length]
    }

    fun fmt(d: Double): String =
        if (d == Math.floor(d) && abs(d) < 1e15) d.toLong().toString() else d.toString()

    fun strictNumber(s: String): Double? =
        if (Regex("""\d+(\.\d+)?""").matches(s)) s.toDoubleOrNull() else null

    /** Joins lines into free text: one string, single blank line between paragraphs. */
    fun freeText(lines: List<String>): String {
        val out = ArrayList<String>()
        lines.forEach { l ->
            if (l.isEmpty()) {
                if (out.isNotEmpty() && out.last().isNotEmpty()) out += ""
            } else {
                out += l
            }
        }
        while (out.isNotEmpty() && out.last().isEmpty()) out.removeAt(out.lastIndex)
        return out.joinToString("\n")
    }

    // ---- quantities -----------------------------------------------------

    private const val NUM = """(?:\d+\s+\d+/\d+|\d+/\d+|\d+(?:\.\d+)?|[½¼¾⅓⅔⅛])"""
    private const val UNITS =
        "kilograms?|kgs?|grams?|gr|g|ounces?|oz|pounds?|lbs?|cups?|tablespoons?|tbsps?|tbs|teaspoons?|tsps?|" +
            "cans?|packets?|cloves?|bunch(?:es)?|pinch(?:es)?|ml|l"
    private val QTY = Regex(
        """^($NUM(?:\s*(?:-|–|—|to)\s*$NUM)?)(?=\s|\(|[A-Za-z]|$)(?:\s*($UNITS)\.?(?![A-Za-z]))?\s*(.*)$""",
        RegexOption.IGNORE_CASE,
    )
    private val ONE_NUM = Regex(NUM)

    data class Quantity(
        /** As written, e.g. "5-6 lb" or "1/2 cup". */
        val text: String,
        /** The upper value of a range. */
        val value: Double,
        val unit: String?,
        val name: String,
    )

    private fun numValue(s: String): Double? {
        val t = s.trim()
        when (t) {
            "½" -> return 0.5; "¼" -> return 0.25; "¾" -> return 0.75
            "⅓" -> return 1.0 / 3; "⅔" -> return 2.0 / 3; "⅛" -> return 0.125
        }
        Regex("""(\d+)\s+(\d+)/(\d+)""").matchEntire(t)?.let {
            val d = it.groupValues[3].toDouble()
            val n = it.groupValues[2].toDouble()
            return if (d == 0.0 || n >= d) null else it.groupValues[1].toDouble() + n / d
        }
        Regex("""(\d+)/(\d+)""").matchEntire(t)?.let {
            val d = it.groupValues[2].toDouble()
            val n = it.groupValues[1].toDouble()
            return if (d == 0.0 || n >= d) null else n / d
        }
        return t.toDoubleOrNull()
    }

    /** Splits "3 lb ground beef 90/10" into quantity, unit and name. Null when there is no leading quantity. */
    fun quantity(text: String): Quantity? {
        val m = QTY.matchEntire(text.trim()) ?: return null
        val qtyText = m.groupValues[1]
        val values = ONE_NUM.findAll(qtyText).map { numValue(it.value) }.toList()
        if (values.isEmpty() || values.any { it == null }) return null
        val upper = values.filterNotNull().max()
        val unit = m.groupValues[2].ifEmpty { null }
        var name = m.groupValues[3].trim()
        if (name.lowercase().startsWith("of ")) name = name.substring(3).trim()
        val shownQty = qtyText.replace(Regex("""\s+"""), " ")
        return Quantity(if (unit != null) "$shownQty $unit" else shownQty, upper, unit, name)
    }

    fun gramsPerUnit(unit: String): Double? = when (unit.lowercase()) {
        "g", "gr", "gram", "grams" -> 1.0
        "kg", "kgs", "kilogram", "kilograms" -> 1000.0
        "oz", "ounce", "ounces" -> 28.349523125
        "lb", "lbs", "pound", "pounds" -> 453.59237
        else -> null
    }

    fun round1(d: Double): Double = Math.round(d * 10.0) / 10.0

    /** A weight such as "370 g" or "13oz", in grams. */
    fun gramsOf(text: String): Double? {
        val m = Regex("""^(\d+(?:\.\d+)?)\s*([A-Za-z]+)\.?$""").matchEntire(text.trim()) ?: return null
        val per = gramsPerUnit(m.groupValues[2]) ?: return null
        return round1(m.groupValues[1].toDouble() * per)
    }

    // ---- durations ------------------------------------------------------

    /** Seconds for 2:30, 150s, 150 sec, 2 min, 2 min 30 s, 90 seconds. Null when not understood. */
    fun restSeconds(text: String): Int? {
        val t = text.trim().lowercase()
        Regex("""(\d{1,3}):(\d{2})""").matchEntire(t)?.let {
            val sec = it.groupValues[2].toInt()
            if (sec >= 60) return null
            return it.groupValues[1].toInt() * 60 + sec
        }
        val part = Regex("""(\d+(?:\.\d+)?)\s*(hours?|hrs?|h|minutes?|mins?|m|seconds?|secs?|s)\b""")
        var rest = t
        var total = 0.0
        var any = false
        while (rest.isNotEmpty()) {
            val m = part.find(rest) ?: return null
            if (rest.substring(0, m.range.first).replace(Regex("""[\s,]|and"""), "").isNotEmpty()) return null
            val v = m.groupValues[1].toDouble()
            total += v * when (m.groupValues[2].first()) {
                'h' -> 3600
                'm' -> 60
                else -> 1
            }
            any = true
            rest = rest.substring(m.range.last + 1)
            if (rest.replace(Regex("""[\s,]|and"""), "").isEmpty()) break
        }
        return if (any && total < Int.MAX_VALUE) Math.round(total).toInt() else null
    }

    fun clock(seconds: Int): String = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}
