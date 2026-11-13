package com.zeroindex.intake.domain.menu

/**
 * Pairs the items of an allergen chart with the items of a nutrition menu for the same restaurant. Pure.
 *
 * Names are compared after dropping case, punctuation, trademark signs and size words ("Small", "Medium", "Large",
 * "Regular") and quantities ("40g", "25ml"). The size is kept apart, so "Fries (Large)" in one file meets
 * "Large Fries" in the other, and a chart row with no size stands for every size of that item.
 */
object MenuItemMatcher {
    private val SIZE_WORD = Regex("""\b(small|medium|large|regular|reg|extra large|xl)\b""")
    private val QUANTITY = Regex("""\b\d+(\.\d+)?\s?(g|ml|oz|kg|cl|l)\b""")
    private val MARKS = Regex("""[®™©*†§¥]""")

    private fun cleaned(name: String): String = name.lowercase()
        .replace(MARKS, " ")
        .replace("&", " and ")
        .replace(Regex("""[^a-z0-9\s.]"""), " ")
        .replace(Regex("""\s+"""), " ").trim()

    /** The comparison key: the name without size words and quantities. */
    fun normalise(name: String): String = cleaned(name)
        .replace(QUANTITY, " ").replace(SIZE_WORD, " ")
        .replace(Regex("""\s+"""), " ").trim()

    /** The size word or quantity in [name], lower case ("reg" counts as "regular"), or null. */
    fun sizeOf(name: String): String? {
        val c = cleaned(name)
        val q = QUANTITY.find(c)?.value?.replace(" ", "")
        if (q != null) return q
        val w = SIZE_WORD.find(c)?.value ?: return null
        return if (w == "reg") "regular" else w
    }

    /** One pairing: both indexes for a match, one for an item found in only one file. */
    data class Pair(val chart: Int?, val nutrition: Int?)

    /** Pairs every nutrition item with at most one chart row (a size-less chart row may take several sizes). */
    fun pair(chartNames: List<String>, nutritionNames: List<String>): List<Pair> {
        val chartKeys = chartNames.map(::normalise)
        val chartSizes = chartNames.map(::sizeOf)
        val used = HashSet<Int>()
        val out = ArrayList<Pair>()
        for ((n, nName) in nutritionNames.withIndex()) {
            val key = normalise(nName)
            val size = sizeOf(nName)
            val candidates = chartKeys.indices.filter { chartKeys[it] == key && key.isNotEmpty() }
            val pick = candidates.firstOrNull { chartSizes[it] == size && it !in used }
                ?: candidates.firstOrNull { chartSizes[it] == null }
                // No size on this side: take the one chart row of that name, if there is only one.
                ?: candidates.singleOrNull { size == null && it !in used }
            if (pick != null) {
                used += pick
                out += Pair(pick, n)
            } else {
                out += Pair(null, n)
            }
        }
        for (c in chartNames.indices) if (c !in used) out += Pair(c, null)
        return out
    }

    /** Allergens of two files for one item: the union, a tick beating an asterisk. Only ever raises. */
    fun union(a: StatedAllergens, b: StatedAllergens): StatedAllergens {
        val contains = (a.contains + b.contains).distinctBy { it.lowercase() }
        val may = (a.mayContain + b.mayContain).distinctBy { it.lowercase() }
            .filter { m -> contains.none { it.equals(m, ignoreCase = true) } }
        return StatedAllergens(contains, may)
    }
}

/** When to ask for the nutrition menu after an allergen-only import. Pure, so the card's rules are tested. */
object NutritionPrompt {
    /** Items with no calories at all, from a chart: there is something to add. */
    fun isAllergenOnly(kcals: List<Double?>): Boolean = kcals.isNotEmpty() && kcals.all { it == null }

    /** The follow-up card appears right after such an import unless the person skipped it for this source. */
    fun shouldPrompt(kcals: List<Double?>, skipped: Boolean): Boolean = isAllergenOnly(kcals) && !skipped

    /** The small "Add nutrition menu" action on a source row is always there for an allergen-only source. */
    fun showsRowAction(kcals: List<Double?>): Boolean = isAllergenOnly(kcals)

    fun summary(matched: Int, total: Int): String = "Matched $matched of $total items."
}
