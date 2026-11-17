package com.zeroindex.intake.domain.food

/**
 * Orders the foods on this phone for a typed query so a word that starts with what was typed beats a match in the middle
 * of a word ("almo" finds Almond milk before Salmon). The bundled reference search ranks its own results the same way
 * (`FoodRefSearch.tier`); this is the same idea for My foods and the local results, which are plain substring matches.
 *
 * Tiers, best first:
 *  0. every typed word starts a word of the name and the name begins with the first one ("almo" -> "Almond milk");
 *  1. every typed word starts a word of the name ("milk al" -> "Almond milk");
 *  2. every typed word starts a word of the name or the brand;
 *  3. at least one typed word only matches inside a word ("almo" -> "Salmon").
 * Within a tier the incoming order is kept, so a caller's own order (most logged first, or alphabetical) still applies.
 */
object NameMatchRank {

    private val NOT_LETTER_OR_DIGIT = Regex("[^\\p{L}\\p{N}]+")

    private fun words(text: String): List<String> = text.lowercase().split(NOT_LETTER_OR_DIGIT).filter { it.isNotEmpty() }

    /** The tier of one item, or null when it does not contain every typed word at all. A blank query ranks everything 0. */
    fun tier(name: String, brand: String, query: String): Int? {
        val tokens = words(query).distinct()
        if (tokens.isEmpty()) return 0
        val nameLower = name.lowercase()
        val brandLower = brand.lowercase()
        if (!tokens.all { nameLower.contains(it) || brandLower.contains(it) }) return null
        val nameWords = words(name)
        val brandWords = words(brand)
        return when {
            tokens.all { t -> nameWords.any { it.startsWith(t) } } ->
                if (nameWords.firstOrNull()?.startsWith(tokens.first()) == true) 0 else 1
            tokens.all { t -> nameWords.any { it.startsWith(t) } || brandWords.any { it.startsWith(t) } } -> 2
            else -> 3
        }
    }

    /** True when every typed word starts a word of the name or brand ("almo" and "Almond milk"), not just sits inside one ("Salmon"). */
    fun startsAWord(name: String, brand: String, query: String): Boolean = tier(name, brand, query)?.let { it <= 2 } == true

    /** [items] that contain the query, best tier first and in their own order inside a tier. A blank query keeps everything as it is. */
    fun <T> filterAndRank(items: List<T>, query: String, name: (T) -> String, brand: (T) -> String = { "" }): List<T> {
        if (query.isBlank()) return items
        return items.mapNotNull { item -> tier(name(item), brand(item), query)?.let { it to item } }
            .sortedBy { it.first } // sortedBy is stable
            .map { it.second }
    }

    /** [items] already known to match, only reordered: best tier first, stable inside a tier. */
    fun <T> rank(items: List<T>, query: String, name: (T) -> String, brand: (T) -> String = { "" }): List<T> {
        if (query.isBlank()) return items
        return items.sortedBy { tier(name(it), brand(it), query) ?: 3 }
    }
}
