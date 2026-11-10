package com.zeroindex.intake.data.ai.menu

/**
 * Joins the items read from each chunk of a long PDF into one menu (B4). A row can be read twice when it sits
 * on a page boundary, and a model sometimes repeats a header row as an item, so the merge drops exact repeats
 * and keeps the fuller copy. Two items that share a name but differ in calories are kept as two (a "Small" and
 * a "Large" that lost their suffix): dropping one would silently lose data, and the review screen lets the
 * person reject either.
 *
 * Allergen declarations are only ever added together, never removed: this is the PDF's "Contains:" list, and
 * the local allergen engine still judges every item afterwards.
 */
object MenuMerge {

    fun merge(chunks: List<List<ParsedItem>>): List<ParsedItem> {
        val kept = ArrayList<ParsedItem>()
        val index = HashMap<Pair<String, Long?>, Int>()
        for (item in chunks.flatten()) {
            val name = normalise(item.name)
            if (name.isEmpty()) continue
            val key = name to item.kcal?.let { Math.round(it) }
            val at = index[key]
            if (at == null) {
                index[key] = kept.size
                kept += item
            } else {
                kept[at] = combine(kept[at], item)
            }
        }
        return kept
    }

    /** Lower case, one space between words, without trademark marks and the footnote symbols a menu adds. */
    fun normalise(name: String): String =
        name.lowercase()
            .replace(Regex("[®™©*†‡•]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun combine(a: ParsedItem, b: ParsedItem): ParsedItem {
        val (better, other) = if (score(b) > score(a)) b to a else a to b
        val allergens = (better.containsAllergens + other.containsAllergens)
            .distinctBy { it.trim().lowercase() }
            .filter { it.isNotBlank() }
        return better.copy(
            description = better.description.ifBlank { other.description },
            ingredients = listOf(better.ingredients, other.ingredients).maxByOrNull { it.length }.orEmpty(),
            kcal = better.kcal ?: other.kcal,
            protein = better.protein ?: other.protein,
            carbs = better.carbs ?: other.carbs,
            fat = better.fat ?: other.fat,
            priceCents = better.priceCents ?: other.priceCents,
            containsAllergens = allergens,
            allergenNotes = listOf(better.allergenNotes, other.allergenNotes).maxByOrNull { it.length }.orEmpty(),
        )
    }

    private fun score(i: ParsedItem): Int =
        (if (i.macrosPublished) 10 else 0) +
            listOf(i.kcal, i.protein, i.carbs, i.fat, i.priceCents).count { it != null } +
            (if (i.ingredients.isNotBlank()) 1 else 0)
}
