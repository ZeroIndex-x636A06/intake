package com.zeroindex.intake.domain.recipe

import com.zeroindex.intake.domain.script.ScriptText

/** ISO-8601 durations as schema.org writes them: PT1H30M, P0DT45M, PT90S. */
object IsoDuration {
    private val ISO = Regex(
        """^P(?:(\d+(?:\.\d+)?)W)?(?:(\d+(?:\.\d+)?)D)?(?:T(?:(\d+(?:\.\d+)?)H)?(?:(\d+(?:\.\d+)?)M)?(?:(\d+(?:\.\d+)?)S)?)?$""",
        RegexOption.IGNORE_CASE,
    )

    /** Whole minutes (rounded up from seconds), or null when [text] is not a duration. Zero is null. */
    fun minutes(text: String?): Int? {
        val t = text?.trim().orEmpty()
        if (t.isEmpty()) return null
        val m = ISO.matchEntire(t)
        val minutes: Double = if (m != null && t.length > 1 && t.any { it.isDigit() }) {
            fun g(i: Int) = m.groupValues[i].toDoubleOrNull() ?: 0.0
            g(1) * 7 * 24 * 60 + g(2) * 24 * 60 + g(3) * 60 + g(4) + g(5) / 60.0
        } else {
            // Sites sometimes write "45 min" or a bare number of minutes.
            val seconds = ScriptText.restSeconds(t)
            when {
                seconds != null -> seconds / 60.0
                Regex("""\d{1,4}""").matches(t) -> t.toDouble()
                else -> return null
            }
        }
        if (minutes <= 0.0 || minutes > 60 * 24 * 14) return null
        return Math.ceil(minutes - 1e-9).toInt()
    }
}

/** One ingredient line as written, split into amount, unit and name. */
data class ParsedIngredient(
    /** The line as given (trimmed). */
    val original: String,
    /** The amount and unit as written, e.g. "1 1/2 cups"; empty if the line has none. */
    val quantityText: String,
    /** Upper value of a range ("1-2" gives 2), null if there is no amount. */
    val quantity: Double?,
    /** The unit word as written, if any. */
    val unit: String?,
    val name: String,
    /** Grams, only when the unit is a weight (g, kg, oz, lb). Volumes are never guessed. */
    val grams: Double?,
)

/**
 * Splits "1 ½ cups milk", "1-2 tbsp olive oil", "2 (14 oz) cans tomatoes" or "salt to taste"
 * into quantity, unit and name. Pure and total: any input gives a result, and a line with no
 * leading amount is all name. Builds on the script format's quantity reader.
 */
object IngredientLineParser {
    private val FRACTIONS = mapOf(
        '½' to "1/2", '⅓' to "1/3", '⅔' to "2/3", '¼' to "1/4", '¾' to "3/4",
        '⅕' to "1/5", '⅖' to "2/5", '⅗' to "3/5", '⅘' to "4/5", '⅙' to "1/6", '⅚' to "5/6",
        '⅛' to "1/8", '⅜' to "3/8", '⅝' to "5/8", '⅞' to "7/8",
    )

    /** Rewrites unicode fractions as ascii ("1½" and "1 ½" both give "1 1/2") and tidies spaces. */
    fun normalise(text: String): String {
        val sb = StringBuilder()
        text.forEach { c ->
            val f = FRACTIONS[c]
            if (f == null) {
                sb.append(c)
            } else {
                // Keep a whole number in front separate: "1½" -> "1 1/2".
                if (sb.isNotEmpty() && sb.last().isDigit()) sb.append(' ')
                sb.append(f)
            }
        }
        return sb.toString().replace(' ', ' ').replace(Regex("""\s+"""), " ").trim()
    }

    fun parse(line: String): ParsedIngredient {
        val original = line.trim()
        val text = normalise(original)
        val q = runCatching { ScriptText.quantity(text) }.getOrNull()
        if (q == null || q.name.isBlank()) {
            return ParsedIngredient(original, "", null, null, text, null)
        }
        val grams = q.unit?.let { u -> ScriptText.gramsPerUnit(u)?.let { ScriptText.round1(q.value * it) } }
        return ParsedIngredient(original, q.text, q.value, q.unit, q.name, grams)
    }
}

/** Plain-text helpers for page content. */
internal object HtmlText {
    private val ENTITY = Regex("""&(#x[0-9a-fA-F]{1,6}|#\d{1,7}|[a-zA-Z]{2,8});""")
    private val NAMED = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
        "ndash" to "-", "mdash" to "-", "hellip" to "...", "deg" to "°", "frac12" to "½",
        "frac14" to "¼", "frac34" to "¾", "rsquo" to "'", "lsquo" to "'", "rdquo" to "\"", "ldquo" to "\"",
        "times" to "x", "eacute" to "é", "egrave" to "è",
    )

    fun decode(text: String): String = ENTITY.replace(text) { m ->
        val body = m.groupValues[1]
        when {
            body.startsWith("#x") || body.startsWith("#X") -> codePoint(body.substring(2).toIntOrNull(16))
            body.startsWith("#") -> codePoint(body.substring(1).toIntOrNull())
            else -> NAMED[body] ?: m.value
        } ?: m.value
    }

    private fun codePoint(cp: Int?): String? =
        cp?.takeIf { it in 1..0x10FFFF && it !in 0xD800..0xDFFF }?.let { String(Character.toChars(it)) }

    /** Tags removed (breaks and block ends become spaces or newlines), entities decoded, spaces collapsed. */
    fun plain(html: String, keepLines: Boolean = false): String {
        var t = html.replace(Regex("""<\s*br\s*/?\s*>""", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("""</\s*(p|div|li|h[1-6]|tr)\s*>""", RegexOption.IGNORE_CASE), "\n")
        t = t.replace(Regex("""<[^>]*>"""), " ")
        t = decode(t).replace(' ', ' ')
        return if (keepLines) {
            t.lines().map { it.replace(Regex("""[ \t]+"""), " ").trim() }.filter { it.isNotEmpty() }.joinToString("\n")
        } else {
            t.replace(Regex("""\s+"""), " ").trim()
        }
    }
}
