package com.zeroindex.intake.domain.plan

import com.zeroindex.intake.domain.model.StoreCategory
import com.zeroindex.intake.domain.units.WeightUnit

/** A generated list grouped for display: store sections in walking order, staples apart. */
data class StandaloneList(
    val sections: List<Pair<StoreCategory, List<GeneratedShoppingLine>>>,
    val pantry: List<GeneratedShoppingLine>,
) {
    val count: Int get() = sections.sumOf { it.second.size } + pantry.size
    val isEmpty: Boolean get() = count == 0
}

/**
 * A shopping list that belongs to no meal plan (feedback round 4, item 11): built from recipes and meal templates the
 * person picks, each cooked a number of times. It uses the same line reader and unit adding as the plan's list
 * ([ShoppingListBuilder.generateFromLines]), so the two agree. Pure; nothing is stored, and no model is involved.
 */
object StandaloneShopping {

    /** One recipe's ingredient lines, each as a contribution scaled by [batches] (cook it twice, buy twice). */
    fun fromRecipe(
        recipeName: String,
        lines: List<String>,
        groups: List<String?> = emptyList(),
        batches: Int = 1,
    ): List<ShoppingContribution> {
        val scale = batches.coerceIn(1, MAX_BATCHES).toDouble()
        return lines.mapIndexedNotNull { i, line ->
            line.trim().takeIf { it.isNotEmpty() }?.let { ShoppingContribution(it, recipeName, groups.getOrNull(i), scale) }
        }
    }

    /** A meal template's foods, each "150 g Rice" so the amount is added up in grams like a recipe line. */
    fun fromTemplate(templateName: String, items: List<Pair<String, Double>>, batches: Int = 1): List<ShoppingContribution> {
        val scale = batches.coerceIn(1, MAX_BATCHES).toDouble()
        return items.filter { (name, grams) -> name.isNotBlank() && grams.isFinite() && grams > 0.0 }
            .map { (name, grams) -> ShoppingContribution("${formatGrams(grams)} g ${name.trim()}", templateName, null, scale) }
    }

    fun build(contributions: List<ShoppingContribution>): StandaloneList {
        val lines = ShoppingListBuilder.generateFromLines(contributions)
        val (pantry, rest) = lines.partition { it.isPantryStaple }
        val sections = StoreCategory.entries.mapNotNull { category ->
            val inCategory = rest.filter { it.category == category }.sortedBy { it.name.lowercase() }
            if (inCategory.isEmpty()) null else category to inCategory
        }
        return StandaloneList(sections, pantry.sortedBy { it.name.lowercase() })
    }

    /** Plain text for sharing: unchecked first within each section, staples last, in the person's weight unit. */
    fun shareText(title: String, list: StandaloneList, checked: Set<String>, unit: WeightUnit): String = buildString {
        appendLine(title)
        fun line(l: GeneratedShoppingLine) {
            val q = ShoppingListBuilder.displayQuantity(l.quantity, unit)
            appendLine("${if (l.key in checked) "[x]" else "[ ]"} ${l.name}${if (q.isNotBlank()) " - $q" else ""}")
        }
        list.sections.forEach { (category, items) ->
            appendLine()
            appendLine(ShoppingListBuilder.sectionTitle(category))
            items.sortedBy { it.key in checked }.forEach(::line)
        }
        if (list.pantry.isNotEmpty()) {
            appendLine()
            appendLine("Pantry staples")
            list.pantry.sortedBy { it.key in checked }.forEach(::line)
        }
    }.trimEnd()

    const val MAX_BATCHES = 9

    private fun formatGrams(g: Double): String =
        if (g == Math.floor(g)) g.toLong().toString() else String.format(java.util.Locale.US, "%.1f", g)
}
