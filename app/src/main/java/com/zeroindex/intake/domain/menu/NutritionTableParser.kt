package com.zeroindex.intake.domain.menu

/** One menu item read from a nutrition table. Amounts are per serving, in grams unless the name says otherwise. */
data class MenuTableRow(
    val name: String,
    /** The section heading above the row ("SLOW ROASTED BEEF"), as the document wrote it, or null. */
    val category: String?,
    val stated: StatedAllergens,
    val servingWeightG: Double?,
    /** "Small", "8oz": what the serving column said when it held a size instead of a weight. */
    val servingLabel: String?,
    val kcal: Double,
    val fatG: Double,
    val saturatedFatG: Double,
    val sodiumMg: Double,
    val carbsG: Double,
    val fiberG: Double,
    val sugarsG: Double,
    val proteinG: Double,
    /** True when a "†" or footnote star says the item shares a fryer or a facility, which the chart cannot name. */
    val sharedFacility: Boolean = false,
)

/**
 * What a parse found. [skipped] counts the lines that looked like table rows and were not used: their numbers did
 * not fit the columns, or did not add up. Nothing is guessed to fill a gap, so a skipped row is left out and counted.
 */
data class MenuTableParse(
    val rows: List<MenuTableRow>,
    val skipped: Int,
    /** The first few skipped lines, for the review sheet and for tests. */
    val skippedLines: List<String>,
    /** True when the column order was read from heading words; false when the common order was assumed. */
    val columnsFromHeading: Boolean,
) {
    val foundTable: Boolean get() = rows.isNotEmpty()
}

/**
 * Reads a restaurant nutrition-and-allergen table from the text of a PDF, with no model.
 *
 * What it expects: one item per line, a name, optionally "Contains: Milk, Egg; May contain: Sesame", then a run of
 * numbers (serving weight g, calories, calories from fat, fat, saturated fat, trans fat, cholesterol mg, sodium mg,
 * carbohydrate, fibre, sugars, protein). A name line followed by a numbers-only line is joined, because a phone's
 * extractor may split a row. A section heading is a line in capitals with no numbers.
 *
 * Columns come from the heading words when a single line holds enough of them in order; otherwise the common order
 * above is used. A run of one fewer number than there are columns, headed by a size word ("Small", "8oz") where the
 * weight should be, is a drink-style row without a weight.
 *
 * A row is used only when its numbers fit: saturated fat cannot exceed fat, fibre and sugars cannot exceed
 * carbohydrate, and calories have to agree with fat, carbohydrate and protein to within a loose margin. Anything
 * else is skipped and counted.
 */
object NutritionTableParser {

    private enum class Col { SERVING, KCAL, KCAL_FAT, FAT, SAT, TRANS, CHOL, SODIUM, CARB, FIBER, SUGARS, PROTEIN }

    private val DEFAULT_ORDER = listOf(
        Col.SERVING, Col.KCAL, Col.KCAL_FAT, Col.FAT, Col.SAT, Col.TRANS, Col.CHOL, Col.SODIUM, Col.CARB, Col.FIBER, Col.SUGARS, Col.PROTEIN,
    )

    private val NUMBER = Regex("^<?\\d+(?:[.,]\\d+)?\\+?$")
    private val MARKER = Regex("(?i)\\bcontains\\b\\s*:?|\\bmay\\s+contain\\b\\s*:?|†")
    private val SIZE_WORD = Regex("(?i)^(small|sm|med|medium|large|lg|xl|xxl|regular|kids?|\\d+(?:\\.\\d+)?\\s?(?:fl\\.?\\s?)?(?:oz|ml|l|g))$")
    private val SIZE_NAMES = mapOf("sm" to "Small", "med" to "Medium", "lg" to "Large")
    private val PAGE_LINE = Regex("(?i)\\bpage\\s+\\d+\\s+of\\s+\\d+\\b")

    /** Heading words, in the order they are looked for: the longer or more specific wording first. */
    private val HEADER_WORDS = listOf(
        Col.KCAL_FAT to Regex("calories\\s+from\\s+fat|cal\\.?\\s+from\\s+fat|fat\\s+calories"),
        Col.SAT to Regex("saturated\\s+fat|sat\\.?\\s+fat"),
        Col.TRANS to Regex("trans\\s+fat"),
        Col.FAT to Regex("fat\\s*[-–]\\s*total|total\\s+fat"),
        Col.SERVING to Regex("serving\\s+(?:weight|size)"),
        Col.KCAL to Regex("calories|energy|kcal"),
        Col.CHOL to Regex("cholesterol"),
        Col.SODIUM to Regex("sodium"),
        Col.CARB to Regex("total\\s+carbohydrates?|carbohydrates?|carbs"),
        Col.FIBER to Regex("dietary\\s+fib(?:er|re)|fib(?:er|re)"),
        Col.SUGARS to Regex("sugars?"),
        Col.PROTEIN to Regex("protein"),
    )

    fun parse(pages: List<String>): MenuTableParse = parseLines(pages.flatMap { it.lines() })

    fun parse(text: String): MenuTableParse = parse(listOf(text))

    fun parseLines(lines: List<String>): MenuTableParse {
        val rows = ArrayList<MenuTableRow>()
        val skippedLines = ArrayList<String>()
        var skipped = 0
        var order = DEFAULT_ORDER
        var fromHeading = false
        var category: String? = null
        // The last text lines that were not rows: a name (and its allergen line) waiting for a numbers-only line.
        val pending = ArrayList<String>()
        val facilityNote = lines.any { Regex("(?i)facility that processes\\s+peanuts").containsMatchIn(it) }

        fun skip(line: String) {
            skipped++
            if (skippedLines.size < 25) skippedLines += line.trim().take(160)
        }

        for (raw in lines) {
            val line = raw.replace(' ', ' ').trim()
            if (line.isEmpty()) continue

            val header = headerOrder(line)
            if (header != null) {
                order = header
                fromHeading = true
                pending.clear()
                continue
            }

            val tokens = line.split(Regex("\\s+"))
            var runStart = tokens.size
            while (runStart > 0 && NUMBER.matches(tokens[runStart - 1])) runStart--
            val runLength = tokens.size - runStart

            if (PAGE_LINE.containsMatchIn(line) && runLength < 6) {
                pending.clear()
                continue
            }

            if (runLength < 6) {
                if (isHeading(line, runLength)) {
                    category = line.trim()
                    pending.clear()
                } else if (line.length <= 120) {
                    pending += line
                    while (pending.size > 2) pending.removeAt(0)
                }
                continue
            }

            // A candidate row. Its numbers must fit the columns; the name may be on the line before.
            val prefix = tokens.subList(0, runStart).joinToString(" ")
            val numbers = tokens.subList(runStart, tokens.size).map { it.removePrefix("<").removeSuffix("+").replace(',', '.').toDouble() }
            val text = if (prefix.isNotBlank()) {
                pending.clear()
                prefix
            } else {
                val joined = joinPending(pending)
                pending.clear()
                joined
            }
            if (text.isBlank()) {
                skip(line)
                continue
            }
            val row = buildRow(text, numbers, order, category, facilityNote)
            if (row == null) skip(line) else rows += row
        }
        return MenuTableParse(rows, skipped, skippedLines, fromHeading)
    }

    private fun joinPending(pending: List<String>): String = when {
        pending.isEmpty() -> ""
        pending.size == 2 && MARKER.find(pending[1])?.range?.first == 0 -> pending[0] + " " + pending[1]
        else -> pending.last()
    }

    /** All capitals, no numbers, short: a section band like "SLOW ROASTED BEEF". */
    private fun isHeading(line: String, runLength: Int): Boolean {
        if (runLength != 0 || line.length > 60) return false
        val letters = line.filter(Char::isLetter)
        return letters.length >= 3 && letters.all(Char::isUpperCase) && line.none(Char::isDigit)
    }

    /**
     * The column order from one line that names at least eight columns. A heading split over many lines, or rotated
     * so that an extractor shuffles its words, is not trusted: the common order is used instead.
     */
    private fun headerOrder(line: String): List<Col>? {
        if (line.length < 30) return null
        var text = line.lowercase()
        val found = ArrayList<Pair<Int, Col>>()
        for ((col, regex) in HEADER_WORDS) {
            for (m in regex.findAll(text).toList()) {
                found += m.range.first to col
                text = text.replaceRange(m.range, " ".repeat(m.range.last - m.range.first + 1))
            }
        }
        val cols = found.sortedBy { it.first }.map { it.second }
        if (cols.size < 8 || cols.toSet().size != cols.size || Col.KCAL !in cols) return null
        return cols
    }

    private fun buildRow(text: String, numbers: List<Double>, order: List<Col>, category: String?, facilityNote: Boolean): MenuTableRow? {
        // Two fits only: every column, or every column but the serving weight (a size word stands in for it).
        val withWeight = numbers.size == order.size
        val withoutWeight = order.firstOrNull() == Col.SERVING && numbers.size == order.size - 1
        if (!withWeight && !withoutWeight) return null
        val cols = if (withWeight) order else order.drop(1)
        val value = cols.zip(numbers).toMap()
        fun v(c: Col) = value[c]
        val kcal = v(Col.KCAL) ?: return null
        val fat = v(Col.FAT) ?: return null
        val carb = v(Col.CARB) ?: return null
        val protein = v(Col.PROTEIN) ?: return null
        val sat = v(Col.SAT) ?: 0.0
        val fiber = v(Col.FIBER) ?: 0.0
        val sugars = v(Col.SUGARS) ?: 0.0
        val sodium = v(Col.SODIUM) ?: 0.0
        if (!fits(kcal, v(Col.KCAL_FAT), fat, sat, v(Col.TRANS), carb, fiber, sugars, protein, sodium)) return null

        // The words before the numbers: a name, then optional allergen text, and for a weight-less row a size word.
        var words = text.trim()
        var servingLabel: String? = null
        if (withoutWeight) {
            val last = words.split(Regex("\\s+")).last()
            if (SIZE_WORD.matches(last)) {
                servingLabel = SIZE_NAMES[last.lowercase()] ?: last
                words = words.removeSuffix(last).trim()
            }
        }
        val marker = MARKER.find(words)
        var namePart = if (marker != null) words.substring(0, marker.range.first) else words
        var allergenPart = if (marker != null) words.substring(marker.range.first) else ""

        // A size word may also sit after the allergen text ("Contains: Milk  8oz").
        if (withoutWeight && servingLabel == null && allergenPart.isNotEmpty()) {
            val last = allergenPart.trim().split(Regex("\\s+")).last()
            if (SIZE_WORD.matches(last)) {
                servingLabel = SIZE_NAMES[last.lowercase()] ?: last
                allergenPart = allergenPart.trim().removeSuffix(last)
            }
        }

        val facility = Regex("^\\*(?!\\*)").containsMatchIn(namePart.trim())
        var leading = namePart.trim().trimStart('*', '\u2022', '†', ' ')
        // Only a lone "u" glyph (a footnote symbol) is dropped, never the first letter of a word.
        if (leading.startsWith("u ")) leading = leading.removePrefix("u ")
        namePart = leading
        val name = cleanName(namePart)
        if (name.isBlank() || name.none(Char::isLetter)) return null

        var stated = StatedAllergens.parse(allergenPart)
        if (facility && facilityNote) {
            stated = StatedAllergens(
                stated.contains,
                (stated.mayContain + listOf("Peanuts", "Tree nuts")).distinctBy { it.lowercase() },
            )
        }
        val shared = facility || allergenPart.contains('†')

        val display = if (servingLabel != null) "$name ($servingLabel)" else name
        return MenuTableRow(
            name = display,
            category = category?.let(::titleCase),
            stated = stated,
            servingWeightG = if (withWeight) v(Col.SERVING)?.takeIf { it > 0 } else null,
            servingLabel = servingLabel,
            kcal = kcal,
            fatG = fat,
            saturatedFatG = sat,
            sodiumMg = sodium,
            carbsG = carb,
            fiberG = fiber,
            sugarsG = sugars,
            proteinG = protein,
            sharedFacility = shared,
        )
    }

    /** Do these numbers describe one real serving? Rounding on a label allows a little give. */
    private fun fits(
        kcal: Double, kcalFat: Double?, fat: Double, sat: Double, trans: Double?, carb: Double, fiber: Double,
        sugars: Double, protein: Double, sodium: Double,
    ): Boolean {
        if (kcal > 5000 || sodium > 20000) return false
        if (sat > fat + 1 || (trans ?: 0.0) > fat + 1) return false
        if (fiber > carb + 1 || sugars > carb + 1) return false
        if (kcalFat != null && kcalFat > kcal + 10) return false
        val atwater = 4 * protein + 4 * carb + 9 * fat
        return kotlin.math.abs(kcal - atwater) <= maxOf(40.0, 0.25 * kcal)
    }

    private fun cleanName(raw: String): String = raw
        .replace(Regex("[®™°†]"), "")
        .replace(Regex("\\s+u$"), "")
        .replace(Regex("\\s+"), " ")
        .trim(' ', '-', '–', '*')

    private fun titleCase(heading: String): String = heading.lowercase().split(' ').joinToString(" ") { w ->
        w.replaceFirstChar(Char::titlecase)
    }
}
