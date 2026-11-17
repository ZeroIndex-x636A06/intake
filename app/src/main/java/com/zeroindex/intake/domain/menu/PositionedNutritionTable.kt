package com.zeroindex.intake.domain.menu

import com.zeroindex.intake.domain.pdf.TextFragment
import kotlin.math.abs

/**
 * Reads a wide nutrition table by where its text sits on the page, with no model.
 *
 * [NutritionTableParser] reads lines of text. That fails on the McDonald's USA nutrition PDF (and on any chart laid out
 * like it), for three reasons a line-based reader cannot see through:
 *  - the column headings are rotated, so no line of text names the columns;
 *  - every "% Daily Value" cell sits between the gram columns, so a row has 21 numbers, not the 12 it expects;
 *  - an item name wraps over two to seven lines that are centred on the numbers, so the words of one name come before
 *    and after the line that holds its numbers, mixed with the serving size ("9.7 oz ... (274 g)").
 *
 * This reader works from the fragments' boxes instead. Number cells are found as long runs of numbers on one line; the
 * columns are the x positions those runs share; each column is named from the heading words that sit above it (rotated
 * or not); the name words left of the numbers are given to the nearest row; a line of text that no row is near, and
 * that sits just above a row, is a section heading. A row is kept only when its numbers fit one real serving, exactly
 * as in [NutritionTableParser]; anything else is counted as skipped. Nothing is guessed.
 *
 * Such a table has no allergen information. Every row is returned with [MenuTableRow.allergensStated] false, so it can
 * only ever be shown as "check the label".
 */
object PositionedNutritionTable {

    private enum class Col { KCAL, KCAL_FAT, FAT, SAT, TRANS, CHOL, SODIUM, CARB, FIBER, SUGARS, PROTEIN, IGNORE }

    private val NUMBER = Regex("^<?\\d+(?:\\.\\d+)?\\+?$")
    private val PLACEHOLDER = Regex("^(?:-|–|—|n/?a|na|\\*)$", RegexOption.IGNORE_CASE)
    private val WEIGHT = Regex("\\(\\s*(\\d+(?:\\.\\d+)?)\\s*(g|ml)\\s*\\)", RegexOption.IGNORE_CASE)
    private val OUNCES = Regex("\\b(\\d+(?:\\.\\d+)?)\\s*(fl\\.?\\s*oz|oz|pkg|pkgs|packet)\\b", RegexOption.IGNORE_CASE)

    private const val MIN_RUN = 10
    private val DV_LAYOUT = listOf(
        Col.KCAL, Col.KCAL_FAT, Col.FAT, Col.IGNORE, Col.SAT, Col.IGNORE, Col.TRANS, Col.CHOL, Col.IGNORE, Col.SODIUM,
        Col.IGNORE, Col.CARB, Col.IGNORE, Col.FIBER, Col.IGNORE, Col.SUGARS, Col.PROTEIN, Col.IGNORE, Col.IGNORE, Col.IGNORE, Col.IGNORE,
    )

    private class Word(val text: String, val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val midX get() = (left + right) / 2
        val midY get() = (top + bottom) / 2
        val height get() = bottom - top
        val numeric get() = NUMBER.matches(text) || PLACEHOLDER.matches(text)
    }

    private class Line(val words: List<Word>) {
        val midY = words.sumOf { it.midY.toDouble() }.toFloat() / words.size
        val top = words.minOf { it.top }
        val bottom = words.maxOf { it.bottom }
        val text: String get() = words.sortedBy { it.left }.joinToString(" ") { it.text }
    }

    private class DataRow(val cells: Map<Int, Word>, val midY: Float, val top: Float)

    fun parse(pages: List<List<TextFragment>>): MenuTableParse {
        val rows = ArrayList<MenuTableRow>()
        val skippedLines = ArrayList<String>()
        var skipped = 0
        var lastColumns: List<Col>? = null
        var fromHeading = false
        var category: String? = null
        for (page in pages) {
            val r = parsePage(page, lastColumns, category)
            category = r.rows.lastOrNull()?.category ?: category
            rows += r.rows
            skipped += r.skipped
            for (s in r.skippedLines) if (skippedLines.size < 25) skippedLines += s
            if (r.columns != null) lastColumns = r.columns
            if (r.fromHeading) fromHeading = true
        }
        return MenuTableParse(rows, skipped, skippedLines, fromHeading)
    }

    private class PageResult(
        val rows: List<MenuTableRow>,
        val skipped: Int,
        val skippedLines: List<String>,
        val columns: List<Col>?,
        val fromHeading: Boolean,
    )

    private fun empty() = PageResult(emptyList(), 0, emptyList(), null, false)

    private fun median(values: List<Float>): Float = values.sorted()[values.size / 2]

    private fun parsePage(fragments: List<TextFragment>, previous: List<Col>?, carriedCategory: String?): PageResult {
        val words = expand(fragments)
        if (words.size < MIN_RUN) return empty()
        val medianHeight = median(words.map { it.height })
        // Rotated heading words are tall: they are kept apart so they never join a line of ordinary text.
        val flat = words.filter { it.height <= medianHeight * 2.5f }
        val lines = groupLines(flat, medianHeight * 0.5f)

        // Number runs: the longest unbroken run of number-like words on a line.
        val runs = lines.mapNotNull { line ->
            val sorted = line.words.sortedBy { it.left }
            var best = emptyList<Word>()
            var current = ArrayList<Word>()
            for (w in sorted) {
                if (w.numeric) {
                    current += w
                } else {
                    if (current.size > best.size) best = current
                    current = ArrayList()
                }
            }
            if (current.size > best.size) best = current
            if (best.size >= MIN_RUN) line to best else null
        }
        if (runs.isEmpty()) return empty()
        val modal = runs.groupingBy { it.second.size }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenByDescending { it.key }).first().key
        if (modal < MIN_RUN) return empty()
        val full = runs.filter { it.second.size >= modal }.map { (line, run) -> line to run.takeLast(modal) }
        // Column centres: the median x of each position over the full-width rows.
        val centres = (0 until modal).map { i -> median(full.map { it.second[i].midX }) }
        val pitch = (centres.last() - centres.first()) / (modal - 1).coerceAtLeast(1)
        if (pitch <= 0f) return empty()

        val dataWords = HashSet<Word>()
        val data = ArrayList<DataRow>()
        for ((line, run) in runs) {
            val cells = HashMap<Int, Word>()
            val candidates = if (run.size > modal) run.takeLast(modal) else run
            var ok = true
            for (w in candidates) {
                val idx = centres.indices.minByOrNull { abs(centres[it] - w.midX) }!!
                if (abs(centres[idx] - w.midX) > pitch * 0.45f || cells.containsKey(idx)) { ok = false; break }
                cells[idx] = w
            }
            if (!ok) continue
            dataWords += cells.values
            data += DataRow(cells, line.midY, line.top)
        }
        if (data.isEmpty()) return empty()
        data.sortBy { it.midY }

        val firstTop = data.first().top
        val heading = columnsFromHeading(words, centres, pitch, firstTop)
        val columns = heading ?: previous?.takeIf { it.size == modal } ?: DV_LAYOUT.takeIf { modal == DV_LAYOUT.size }
        val fromHeading = heading != null
        if (columns == null) return PageResult(emptyList(), data.size, emptyList(), null, false)

        // Name words: ordinary words left of the first number column, grouped into lines, then into blocks.
        val nameLimit = centres.first() - pitch * 0.3f
        val nameWords = flat.filter { it.midX < nameLimit && it !in dataWords }
        val nameLines = groupLines(nameWords, medianHeight * 0.5f).sortedBy { it.midY }
        val blocks = ArrayList<MutableList<Line>>()
        for (l in nameLines) {
            if (blocks.isNotEmpty() && l.midY - blocks.last().last().midY <= medianHeight * 1.7f) blocks.last() += l else blocks += mutableListOf(l)
        }
        fun nearRow(block: List<Line>): Boolean {
            val top = block.minOf { it.top }
            val bottom = block.maxOf { it.bottom }
            return data.any { d -> d.midY >= top - medianHeight && d.midY <= bottom + medianHeight }
        }
        val linesByRow = HashMap<DataRow, MutableList<Line>>()
        val headings = ArrayList<Pair<Float, String>>() // bottom of heading, text
        for ((bi, block) in blocks.withIndex()) {
            if (nearRow(block)) {
                for (l in block) {
                    val d = data.minByOrNull { abs(it.midY - l.midY) }!!
                    linesByRow.getOrPut(d) { ArrayList() } += l
                }
            } else if (block.size == 1) {
                val next = blocks.getOrNull(bi + 1)
                val text = block[0].text
                val letters = text.count(Char::isLetter)
                if (next != null && nearRow(next) && next.first().top - block[0].bottom <= medianHeight * 3.5f &&
                    letters >= 3 && text.length <= 60 && !text.endsWith(".")
                ) {
                    headings += block[0].bottom to text
                }
            }
        }

        val rows = ArrayList<MenuTableRow>()
        val skippedLines = ArrayList<String>()
        var skipped = 0
        for (d in data) {
            val own = (linesByRow[d] ?: emptyList<Line>()).sortedBy { it.midY }
            val rowTop = own.firstOrNull()?.top ?: d.top
            val category = headings.lastOrNull { it.first <= rowTop + 1f }?.second ?: carriedCategory
            val nameText = own.joinToString(" ") { it.text }.replace(Regex("\\s+"), " ")
            val numbers = columns.indices.map { i ->
                val w = d.cells[i]
                when {
                    w == null -> null
                    PLACEHOLDER.matches(w.text) -> 0.0
                    else -> w.text.removePrefix("<").removeSuffix("+").toDouble()
                }
            }
            val row = buildRow(nameText, columns, numbers, category)
            if (row == null) {
                skipped++
                if (skippedLines.size < 25) skippedLines += (nameText + " " + d.cells.entries.sortedBy { it.key }.joinToString(" ") { it.value.text }).take(160)
            } else {
                rows += row
            }
        }
        return PageResult(rows, skipped, skippedLines, columns, fromHeading)
    }

    private fun buildRow(nameText: String, columns: List<Col>, numbers: List<Double?>, category: String?): MenuTableRow? {
        fun v(c: Col): Double? = columns.indexOf(c).takeIf { it >= 0 }?.let { numbers[it] }
        val kcal = v(Col.KCAL) ?: return null
        val fat = v(Col.FAT) ?: return null
        val carb = v(Col.CARB) ?: return null
        val protein = v(Col.PROTEIN) ?: return null
        val sat = v(Col.SAT) ?: 0.0
        val fiber = v(Col.FIBER) ?: 0.0
        val sugars = v(Col.SUGARS) ?: 0.0
        val sodium = v(Col.SODIUM) ?: 0.0
        if (!NutritionTableParser.fits(kcal, v(Col.KCAL_FAT), fat, sat, v(Col.TRANS), carb, fiber, sugars, protein, sodium)) return null

        var text = nameText
        var weightG: Double? = null
        WEIGHT.find(text)?.let { m ->
            if (m.groupValues[2].equals("g", ignoreCase = true)) weightG = m.groupValues[1].toDouble()
            text = text.removeRange(m.range)
        }
        var label: String? = null
        OUNCES.find(text)?.let { m ->
            label = m.value.replace(Regex("\\s+"), " ")
            text = text.removeRange(m.range)
        }
        val facility = text.contains('†')
        text = text.replace('§', ' ')
        val name = NutritionTableParser.cleanName(text)
        if (name.isBlank() || name.none(Char::isLetter)) return null
        val display = if (weightG == null && label != null) "$name ($label)" else name
        return MenuTableRow(
            name = display,
            category = category?.let(NutritionTableParser::titleCase),
            stated = StatedAllergens.NONE,
            servingWeightG = weightG?.takeIf { it > 0 },
            servingLabel = if (weightG == null) label else null,
            kcal = kcal,
            fatG = fat,
            saturatedFatG = sat,
            sodiumMg = sodium,
            carbsG = carb,
            fiberG = fiber,
            sugarsG = sugars,
            proteinG = protein,
            sharedFacility = facility,
            allergensStated = false,
        )
    }

    /**
     * Names each number column from the heading words above it. Words are matched to a column by x; a column's words are
     * read as a set, so a rotated heading (whose words a reader may return in any order) works the same as a flat one.
     * Returns null unless the columns an item needs (calories, fat, saturated fat, carbohydrate, protein) are all named.
     */
    private fun columnsFromHeading(words: List<Word>, centres: List<Float>, pitch: Float, firstTop: Float): List<Col>? {
        val bags = List(centres.size) { HashSet<String>() }
        for (w in words) {
            if (w.top >= firstTop || firstTop - w.top > 900f) continue
            val idx = centres.indices.minByOrNull { abs(centres[it] - w.midX) } ?: continue
            if (abs(centres[idx] - w.midX) > pitch * 0.4f) continue
            val t = w.text.lowercase().trim('(', ')', '*', ',', '.', ':')
            if (t.isNotEmpty()) bags[idx] += t
        }
        val cols = bags.map(::classify)
        if (cols.any { it == null }) return null
        val named = cols.filterNotNull()
        val needed = listOf(Col.KCAL, Col.FAT, Col.SAT, Col.CARB, Col.PROTEIN)
        if (needed.any { named.count { c -> c == it } != 1 }) return null
        return named
    }

    private fun classify(bag: Set<String>): Col? {
        fun has(vararg w: String) = w.any { it in bag }
        return when {
            has("calories", "energy", "kcal", "cal") && has("from") -> Col.KCAL_FAT
            has("calories", "energy", "kcal") -> Col.KCAL
            has("saturated", "sat") -> Col.SAT
            has("trans") -> Col.TRANS
            has("cholesterol") -> Col.CHOL
            has("sodium") -> Col.SODIUM
            has("carbohydrates", "carbohydrate", "carbs") -> Col.CARB
            has("fiber", "fibre", "dietary") -> Col.FIBER
            has("sugars", "sugar") -> Col.SUGARS
            has("protein") -> Col.PROTEIN
            has("vitamin", "calcium", "iron") -> Col.IGNORE
            has("fat", "total") -> Col.FAT
            has("daily", "value", "value**", "%", "dv") -> Col.IGNORE
            else -> null
        }
    }

    /** One word per whitespace-separated piece, its box cut in proportion to its characters. */
    private fun expand(fragments: List<TextFragment>): List<Word> {
        val out = ArrayList<Word>()
        for (f in fragments) {
            if (f.text.isBlank() || f.bottom <= f.top || f.right <= f.left) continue
            val parts = f.text.trim().split(Regex("\\s+"))
            if (parts.size == 1) {
                out += Word(parts[0], f.left, f.top, f.right, f.bottom)
                continue
            }
            val total = parts.sumOf { it.length } + (parts.size - 1)
            val width = f.right - f.left
            var at = 0
            for (p in parts) {
                val l = f.left + width * at / total
                val r = f.left + width * (at + p.length) / total
                out += Word(p, l, f.top, r, f.bottom)
                at += p.length + 1
            }
        }
        return out
    }

    private fun groupLines(words: List<Word>, tolerance: Float): List<Line> {
        val sorted = words.sortedBy { it.midY }
        val groups = ArrayList<MutableList<Word>>()
        var mid = 0f
        for (w in sorted) {
            if (groups.isNotEmpty() && abs(w.midY - mid) <= tolerance) {
                groups.last() += w
                mid = groups.last().sumOf { it.midY.toDouble() }.toFloat() / groups.last().size
            } else {
                groups += mutableListOf(w)
                mid = w.midY
            }
        }
        return groups.map { Line(it) }
    }
}
