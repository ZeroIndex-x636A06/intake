package com.zeroindex.intake.domain.recipe

import com.zeroindex.intake.domain.allergen.AllergenScanResult

/** "Prep 15 · Cook 18 · Total 33 min", from whichever of the three the recipe has. */
object RecipeTimes {
    fun label(prep: Int?, cook: Int?, total: Int?): String? {
        val p = prep?.takeIf { it > 0 }
        val c = cook?.takeIf { it > 0 }
        val t = total?.takeIf { it > 0 } ?: listOfNotNull(p, c).takeIf { it.isNotEmpty() }?.sum()
        if (p == null && c == null) return t?.let { "$it min" }
        return listOfNotNull(p?.let { "Prep $it" }, c?.let { "Cook $it" }, t?.let { "Total $it min" }).joinToString(" · ")
    }
}

/** The page's fibre, sugar, saturated fat and sodium for the recipe's nutrition block, as label and value pairs. */
object RecipeNutritionLabels {
    fun extras(fiberG: Double?, sugarG: Double?, saturatedFatG: Double?, sodiumMg: Double?): List<Pair<String, String>> =
        listOfNotNull(
            fiberG?.let { "Fibre" to grams(it) },
            sugarG?.let { "Sugar" to grams(it) },
            saturatedFatG?.let { "Saturated fat" to grams(it) },
            sodiumMg?.let { "Sodium" to "${java.text.NumberFormat.getIntegerInstance(java.util.Locale.US).format(Math.round(it))} mg" },
        )

    private fun grams(v: Double): String = if (v < 10 && v % 1.0 != 0.0) "${"%.1f".format(java.util.Locale.US, v)} g" else "${Math.round(v)} g"
}

/**
 * How ingredient groups are stored on a recipe (docs/RECIPE-IMPORT.md): one group label per ingredient.
 * Free-text ingredients live as lines of `recipe.ingredientsText`, and their labels as lines of
 * `recipe.ingredientGroups`, one per line in the same order and blank for "no group". Food-backed
 * ingredients keep their label on their own row.
 */
object IngredientGroupsCodec {
    /** Null when nothing is grouped, so an ungrouped recipe stores nothing. */
    fun encode(labels: List<String?>): String? =
        if (labels.all { it.isNullOrBlank() }) null else labels.joinToString("\n") { it?.replace(Regex("""\s+"""), " ")?.trim().orEmpty() }

    /** One label per line, [lineCount] of them: missing ones are null, so older or shorter data is safe. */
    fun decode(text: String?, lineCount: Int): List<String?> {
        val parts = text?.split('\n').orEmpty()
        return List(lineCount) { parts.getOrNull(it)?.trim()?.takeIf { s -> s.isNotEmpty() } }
    }
}

/** A run of consecutive items sharing a group header. [label] is null for items with no group. */
data class IngredientSection<T>(val label: String?, val items: List<T>)

object IngredientSections {
    /** Consecutive items with the same label form one section, so the order on the page is kept. */
    fun <T> of(items: List<T>, labelOf: (T) -> String?): List<IngredientSection<T>> {
        val out = ArrayList<IngredientSection<T>>()
        for (item in items) {
            val label = labelOf(item)?.takeIf { it.isNotBlank() }
            val last = out.lastOrNull()
            if (last != null && last.label == label) {
                out[out.lastIndex] = last.copy(items = last.items + item)
            } else {
                out += IngredientSection(label, listOf(item))
            }
        }
        return out
    }

    /**
     * Pasted or typed ingredients where a line starting with "## " (or "# ") is a group header for the lines
     * below it: "## For the Sauce". Returns (group, line) pairs in order; blank lines are dropped.
     */
    fun parseWithHeaders(text: String, defaultGroup: String? = null): List<Pair<String?, String>> {
        var group = defaultGroup?.takeIf { it.isNotBlank() }
        val out = ArrayList<Pair<String?, String>>()
        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("#")) {
                group = line.trimStart('#').trim().trimEnd(':').trim().takeIf { it.isNotEmpty() }
                continue
            }
            out += group to line
        }
        return out
    }

    /** The inverse: lines with a "## Group" header wherever the group changes, for an editable text box. */
    fun toTextWithHeaders(lines: List<String>, groups: List<String?>): String {
        val sb = StringBuilder()
        var current: String? = null
        lines.forEachIndexed { i, line ->
            val g = groups.getOrNull(i)?.takeIf { it.isNotBlank() }
            if (g != current) {
                // "##" on its own ends a group: the lines after it belong to none.
                sb.append("##").append(g?.let { " $it" }.orEmpty()).append('\n')
                current = g
            }
            sb.append(line).append('\n')
        }
        return sb.toString().trimEnd()
    }
}

/** A small label for one ingredient line: "contains dairy", "may contain nuts" or "check the label". */
data class LineAllergenTag(val severity: Severity, val text: String) {
    enum class Severity { CHECK_LABEL, MAY_CONTAIN, CONTAINS }
}

object LineAllergens {
    /**
     * The strongest thing the local engine found on one ingredient line, as a tag, or null for a clean
     * line. The scan used normal ingredient rules, so a plant substitute ("vegan butter") arrives as a
     * check-the-label result and a plain "heavy whipping cream" as a contains. This only describes
     * what the engine said; it never clears or lowers anything.
     */
    fun tagOf(result: AllergenScanResult): LineAllergenTag? = when {
        result.contains.isNotEmpty() ->
            LineAllergenTag(LineAllergenTag.Severity.CONTAINS, "contains ${names(result.contains.map { it.allergen })}")
        result.mayContain.isNotEmpty() ->
            LineAllergenTag(LineAllergenTag.Severity.MAY_CONTAIN, "may contain ${names(result.mayContain.map { it.allergen })}")
        result.checkLabel.isNotEmpty() ->
            LineAllergenTag(LineAllergenTag.Severity.CHECK_LABEL, "check the label")
        else -> null
    }

    private fun names(allergens: List<String>) = allergens.map { it.trim().lowercase() }.distinct().joinToString(", ")

    /** Indexes of the lines that carry a tag, for "show me the flagged lines". */
    fun flaggedIndexes(tags: List<LineAllergenTag?>): List<Int> = tags.indices.filter { tags[it] != null }
}
