package com.zeroindex.intake.domain.menu

import com.zeroindex.intake.domain.pdf.TextFragment
import kotlin.math.abs

/** One row of a restaurant allergen chart: an item (or one size of it) and what the chart says about it. */
data class ChartItem(
    val name: String,
    /** "SMALL", "40g" and so on, when the row has one. */
    val size: String?,
    /** The title of the table the row sits in ("Drinks", "Breakfast"). */
    val category: String?,
    val vegetarian: Boolean,
    val vegan: Boolean,
    /** Allergens with a tick, as names the allergen engine understands. Only ever raises a warning. */
    val contains: List<String>,
    /** Allergens with an asterisk ("may contain, presence cannot be excluded"). */
    val mayContain: List<String>,
    /** False when a mark could not be placed under one column with confidence. The item is then flagged, never trusted. */
    val marksPlaced: Boolean = true,
) {
    val stated: StatedAllergens get() = StatedAllergens(contains, mayContain)
}

data class AllergenChartParse(
    val items: List<ChartItem>,
    /** Ticks and asterisks seen in all tables. A "chart" with none means the reader could not see the tick glyphs. */
    val marks: Int,
    /** Tables whose columns could not be matched to the allergen headings; their rows were left out. */
    val skippedTables: Int,
) {
    /** True when this really is an allergen chart that was read: items, and at least one mark to prove ticks are visible. */
    val foundChart: Boolean get() = items.isNotEmpty() && marks > 0
}

/** The 19 allergen columns of the Irish and UK "allergen information guide" layout, left to right after Vegetarian. */
object AllergenChartColumns {
    private data class Column(val heading: String, val tags: List<String>)

    private val columns = listOf(
        Column("wheat", listOf("Wheat", "Gluten")),
        Column("rye", listOf("Rye", "Gluten")),
        Column("barley", listOf("Barley", "Gluten")),
        Column("oats", listOf("Oats", "Gluten")),
        Column("spelt", listOf("Spelt", "Gluten")),
        Column("kamut", listOf("Kamut", "Gluten")),
        Column("soya", listOf("Soy")),
        Column("nuts", listOf("Tree nuts")),
        Column("peanuts", listOf("Peanuts")),
        Column("sesame", listOf("Sesame")),
        Column("milk", listOf("Milk")),
        Column("eggs", listOf("Egg")),
        Column("fish", listOf("Fish")),
        Column("crustaceans", listOf("Shellfish")),
        Column("celery", listOf("Celery")),
        Column("sulphur", listOf("Sulphites")),
        Column("mustard", listOf("Mustard")),
        Column("molluscs", listOf("Molluscs")),
        Column("lupin", listOf("Lupin")),
    )

    val count: Int get() = columns.size

    /** The allergen names handed to the engine for the column at [index]. */
    fun tags(index: Int): List<String> = columns[index].tags

    /** The column a heading word belongs to ("Sulphur" or "Sulphur Dioxide & Sulphites" is the sulphites column). */
    fun indexOfHeading(text: String): Int? {
        val t = text.trim().lowercase()
        if (t.isEmpty()) return null
        return columns.indexOfFirst { t == it.heading || (it.heading == "sulphur" && t.startsWith("sulphur")) }
            .takeIf { it >= 0 }
    }
}

/**
 * Reads a restaurant's allergen chart from positioned page text: tables whose rows are items and whose columns are the
 * allergens, with a tick (a symbol-font glyph) for "contains" and an asterisk for "may contain".
 *
 * Nothing here can clear an allergen. A blank cell only means the chart does not mark that allergen, so every item
 * stays "not stated" for it; the caller must never show a chart item as free of an allergen. Marks are placed by their
 * position under the column headings, which are matched to the 19 known allergens by position in a regular grid
 * (Wheat to Lupin), not by guessing.
 */
object AllergenChartParser {
    private val TICK_RANGE = ''..''
    private val TICK_CHARS = setOf('✓', '✔', '☑', '√', '✅')
    private val STAR_CHARS = setOf('*', '∗', '＊', '✱')
    private val SIZE_LIKE = Regex("""^(\d+(\.\d+)?\s?(g|ml|oz|kg|l|cl)|small|medium|large|regular|single|double|still|reg|sml|med|lge|lg)$""", RegexOption.IGNORE_CASE)

    private class Table(
        val vegX: Float,
        val bandTop: Float,
        val bandBottom: Float,
        val firstCol: Float,
        val pitch: Float,
        val colWidth: Float,
    )

    fun parse(pages: List<List<TextFragment>>): AllergenChartParse {
        val items = ArrayList<ChartItem>()
        var marks = 0
        var skipped = 0
        for (page in pages) {
            val fragments = page.filter { it.text.isNotBlank() && it.right > it.left }
            val tables = findTables(fragments)
            skipped += tables.skipped
            // Tables that share a column of the page end where the next one's heading band begins.
            for (t in tables.found) {
                val next = tables.found.filter { abs(it.vegX - t.vegX) < 20f && it.bandTop > t.bandBottom }.minByOrNull { it.bandTop }
                val bottom = next?.bandTop ?: Float.MAX_VALUE
                val read = readTable(t, bottom, fragments)
                items += read.first
                marks += read.second
            }
        }
        return AllergenChartParse(items, marks, skipped)
    }

    private class Found(val found: List<Table>, val skipped: Int)

    /** A table's anchor is its rotated "Vegetarian" heading; the allergen headings follow it in the same band. */
    private fun findTables(fragments: List<TextFragment>): Found {
        val found = ArrayList<Table>()
        var skipped = 0
        val anchors = fragments.filter { it.text.trim().equals("Vegetarian", ignoreCase = true) && (it.bottom - it.top) > (it.right - it.left) * 1.5f }
        for (a in anchors) {
            val top = a.top - 6f
            val bottom = a.bottom + 6f
            val band = fragments.filter { it.top >= top && it.bottom <= bottom && it.left > a.left - 1f && it.left < a.left + 320f }
            val byIndex = HashMap<Int, TextFragment>()
            for (f in band) {
                if ((f.bottom - f.top) <= (f.right - f.left)) continue // headings are rotated: taller than wide
                val i = AllergenChartColumns.indexOfHeading(f.text) ?: continue
                byIndex.putIfAbsent(i, f)
            }
            val wheat = byIndex[0]
            val lupin = byIndex[AllergenChartColumns.count - 1]
            if (wheat == null || lupin == null) { skipped++; continue }
            val pitch = (lupin.left - wheat.left) / (AllergenChartColumns.count - 1)
            val colWidth = wheat.right - wheat.left
            // Every other heading found must sit where the grid says it should, or the grid is not what we think it is.
            val consistent = byIndex.all { (i, f) -> abs(f.left - (wheat.left + pitch * i)) <= pitch * 0.4f }
            if (pitch < 4f || !consistent) { skipped++; continue }
            found += Table(a.left, a.top, a.bottom, wheat.left, pitch, colWidth)
        }
        return Found(found, skipped)
    }

    private fun isTick(c: Char) = c in TICK_RANGE || c in TICK_CHARS
    private fun isStar(c: Char) = c in STAR_CHARS

    private class Row(val mid: Float, val parts: MutableList<TextFragment> = ArrayList())

    private fun readTable(t: Table, bottom: Float, all: List<TextFragment>): Pair<List<ChartItem>, Int> {
        val nameLeft = t.vegX - 170f
        val right = t.firstCol + t.pitch * (AllergenChartColumns.count - 1) + t.colWidth + t.pitch
        val inHeader = all.filter { it.top >= t.bandTop - 18f && it.bottom <= t.bandBottom + 6f && it.left >= nameLeft && it.right < t.vegX - 5f }
        val body = all.filter { it.top >= t.bandBottom - 0.5f && it.bottom <= bottom + 0.5f && it.left >= nameLeft && it.left < right }
        val category = titleOf(inHeader)

        // Rows are the clusters of fragments sharing a vertical middle.
        val heights = body.map { it.bottom - it.top }.sorted()
        if (heights.isEmpty()) return emptyList<ChartItem>() to 0
        val tolerance = heights[heights.size / 2] * 0.4f
        val rows = ArrayList<Row>()
        for (f in body.sortedBy { (it.top + it.bottom) / 2 }) {
            val mid = (f.top + f.bottom) / 2
            val last = rows.lastOrNull()
            if (last != null && abs(mid - last.mid) <= tolerance) last.parts += f else rows += Row(mid).also { it.parts += f }
        }

        val nameHeight = body.filter { it.right < t.vegX - 40f }.maxOfOrNull { it.bottom - it.top }
        val items = ArrayList<ChartItem>()
        var marks = 0
        var currentName: String? = null
        for (row in rows) {
            val nameParts = ArrayList<TextFragment>()
            var size: String? = null
            var vegetarian = false
            var vegan = false
            val contains = LinkedHashSet<String>()
            val may = LinkedHashSet<String>()
            var placed = true
            var hasMarks = false
            for (f in row.parts.sortedBy { it.left }) {
                val text = f.text.trim()
                val centre = (f.left + f.right) / 2
                val compact = text.filter { !it.isWhitespace() }
                when {
                    // The Vegetarian column.
                    centre >= t.vegX - 1f && centre <= t.vegX + t.pitch && (text == "V" || text == "VE") -> {
                        if (text == "VE") vegan = true else vegetarian = true
                    }
                    // A mark in the allergen grid.
                    centre >= t.firstCol - t.pitch * 0.3f && compact.isNotEmpty() && compact.all { isTick(it) || isStar(it) } -> {
                        val n = compact.length
                        for ((k, ch) in compact.withIndex()) {
                            val x = if (n == 1) centre else f.left + (k + 0.5f) * (f.right - f.left) / n
                            val idx = Math.round((x - t.colWidth / 2 - t.firstCol) / t.pitch)
                            val offGrid = abs(x - (t.firstCol + t.colWidth / 2 + t.pitch * idx)) > t.pitch * 0.4f
                            if (idx !in 0 until AllergenChartColumns.count || offGrid) { placed = false; continue }
                            marks++
                            hasMarks = true
                            val target = if (isTick(ch)) contains else may
                            target += AllergenChartColumns.tags(idx)
                        }
                    }
                    isSize(f, t, nameHeight) -> size = (size?.let { "$it " } ?: "") + text
                    f.right < t.vegX - 2f -> nameParts += f
                    // Anything else in the grid area (footnote signs and similar) is ignored.
                }
            }
            var rowName = cleanName(nameParts.sortedBy { it.left }.joinToString(" ") { it.text.trim() })
            // A table of one product (Fries) lists only sizes: the title is the product.
            if (rowName.isNotEmpty() && size == null && category != null && SIZE_LIKE.matches(rowName)) {
                size = rowName
                rowName = category
            }
            if (rowName.isNotEmpty()) currentName = rowName
            val name = currentName
            val hasData = size != null || vegetarian || vegan || hasMarks
            if (name == null || (rowName.isEmpty() && !hasData)) continue
            // A tick wins over an asterisk for the same allergen name (Gluten appears under several cereals).
            may.removeAll(contains)
            items += ChartItem(
                name = name,
                size = size?.let(::tidySize),
                category = category,
                vegetarian = vegetarian,
                vegan = vegan,
                contains = contains.toList(),
                mayContain = may.toList(),
                marksPlaced = placed,
            )
        }
        return items to marks
    }

    private fun isSize(f: TextFragment, t: Table, nameHeight: Float?): Boolean {
        if (f.left < t.vegX - 34f || f.right > t.vegX + 1f) return false
        val small = nameHeight != null && (f.bottom - f.top) < nameHeight * 0.85f
        return small || SIZE_LIKE.matches(f.text.trim())
    }

    /** "SMALL" becomes "Small"; "40g" stays as it is. */
    private fun tidySize(s: String): String =
        if (s.any { it.isDigit() }) s else s.lowercase().replaceFirstChar(Char::titlecase)

    private fun cleanName(raw: String): String =
        raw.replace(Regex("\\s+"), " ").replace(" ®", "®").trim().trimEnd('§', '¥', '†', '*', ' ').trim()

    /** The table title: the tallest text in the heading band, in reading order ("Condiments & Sauces"). */
    private fun titleOf(band: List<TextFragment>): String? {
        if (band.isEmpty()) return null
        val tallest = band.maxOf { it.bottom - it.top }
        return band.filter { (it.bottom - it.top) >= tallest * 0.85f }
            .sortedWith(compareBy({ Math.round(it.top / 3f) }, { it.left }))
            .joinToString(" ") { it.text.trim() }
            .replace(Regex("\\s+"), " ").trim().ifEmpty { null }
    }
}
