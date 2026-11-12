package com.zeroindex.intake.domain.plan

import com.zeroindex.intake.data.db.entity.ShoppingListItemEntity
import com.zeroindex.intake.domain.model.StoreCategory
import com.zeroindex.intake.domain.units.WeightUnit
import java.util.Locale

/** An ingredient as the shopping list sees it. [grams] is null when the recipe gives no amount. */
data class ShoppingIngredient(
    val name: String,
    val grams: Double?,
    /** The recipe's group header for this line ("For the Sauce"), when the recipe has groups. */
    val group: String? = null,
)

/** A generated shopping line before it becomes a row. [key] is the normalised name, the basis of its id. */
data class GeneratedShoppingLine(
    val key: String,
    val name: String,
    /** Grams as text ("450 g"), or empty. Display converts to the user's weight unit. */
    val quantity: String,
    val category: StoreCategory,
    val isPantryStaple: Boolean,
    /** Which recipe groups use it: "For the Sauce, For the Chicken". Null when none are grouped. */
    val note: String? = null,
    /** The recipes that use it, for "where is this used". */
    val sources: List<String> = emptyList(),
)

data class ShoppingSections(
    /** Store sections in the order a store is walked, empty ones omitted. */
    val sections: List<Pair<StoreCategory, List<ShoppingListItemEntity>>>,
    val pantry: List<ShoppingListItemEntity>,
) {
    val count: Int get() = sections.sumOf { it.second.size } + pantry.size
}

object ShoppingListBuilder {

    fun normalise(name: String): String =
        name.trim().lowercase().replace(Regex("\\s+"), " ")

    /**
     * SPEC §9: generated from the week's recipes. The same ingredient in two
     * recipes becomes one line with the grams added up; if any occurrence has no
     * amount the line keeps what is known and nothing more is claimed.
     */
    fun generate(ingredients: List<ShoppingIngredient>): List<GeneratedShoppingLine> =
        ingredients
            .filter { it.name.isNotBlank() }
            .groupBy { normalise(it.name) }
            .map { (key, group) ->
                val grams = group.mapNotNull { it.grams }.filter { it > 0 }.sum()
                val name = group.first().name.trim()
                val staple = isPantryStaple(key)
                GeneratedShoppingLine(
                    key = key,
                    name = name,
                    quantity = if (grams > 0) "${formatNumber(grams)} g" else "",
                    category = categoryOf(key),
                    isPantryStaple = staple,
                    note = group.mapNotNull { it.group?.takeIf { g -> g.isNotBlank() } }.distinct()
                        .joinToString(", ").takeIf { it.isNotEmpty() },
                )
            }
            .sortedBy { it.key }

    /**
     * The same, from whole ingredient lines ("2 medium zucchini", "1 tbsp olive oil"): amounts in different units are
     * added up by [ShoppingQuantities], scaled per recipe, and each line's source recipes are kept.
     */
    fun generateFromLines(lines: List<ShoppingContribution>): List<GeneratedShoppingLine> =
        ShoppingQuantities.aggregate(lines).map { t ->
            GeneratedShoppingLine(
                key = t.key,
                name = t.name,
                quantity = t.quantity,
                category = categoryOf(t.key),
                isPantryStaple = isPantryStaple(t.key),
                note = listOfNotNull(t.groups.joinToString(", ").takeIf { it.isNotEmpty() }, t.sources.takeIf { it.isNotEmpty() }?.let { "from " + it.joinToString(", ") })
                    .joinToString(" · ").takeIf { it.isNotEmpty() },
                sources = t.sources,
            )
        }

    private val MEAT = setOf(
        "beef", "chicken", "pork", "turkey", "salmon", "tuna", "steak", "ribeye", "chuck", "bacon", "sausage",
        "ham", "shrimp", "fish", "lamb", "cod", "thighs", "breast", "meatballs", "brisket", "franks",
    )
    private val PRODUCE = setOf(
        "lettuce", "romaine", "tomato", "tomatoes", "onion", "onions", "garlic", "pepper", "peppers", "avocado",
        "avocados", "lime", "limes", "lemon", "lemons", "cilantro", "parsley", "carrot", "carrots", "broccoli",
        "cucumber", "cabbage", "potato", "potatoes", "banana", "bananas", "apple", "apples", "grapes", "celery",
        "zucchini", "spinach", "mushrooms", "ginger", "greens", "basil", "squash",
    )
    private val FROZEN = setOf("frozen")
    private val PANTRY = setOf(
        "rice", "pasta", "spaghetti", "penne", "macaroni", "oil", "vinegar", "salt", "sugar", "flour", "cornstarch",
        "seasoning", "paprika", "cumin", "oregano", "powder", "sauce", "broth", "beans", "mayo", "ketchup",
        "mustard", "soy", "sriracha", "breadcrumbs", "crackers", "oats", "yeast", "paste", "canned", "almonds",
        "cashews", "bread", "tortillas", "buns", "rolls",
    )
    private val STAPLES = setOf(
        "salt", "pepper", "oil", "vinegar", "sugar", "flour", "cornstarch", "paprika", "cumin", "oregano",
        "seasoning", "powder", "yeast", "ketchup", "mustard", "sriracha", "mayo", "rice", "pasta",
    )

    private fun tokens(key: String): Set<String> = key.split(Regex("[^a-z]+")).filter { it.isNotEmpty() }.toSet()

    fun categoryOf(key: String): StoreCategory {
        val t = tokens(key)
        return when {
            t.any { it in FROZEN } -> StoreCategory.FROZEN
            t.any { it in MEAT } -> StoreCategory.MEAT
            t.any { it in PRODUCE } && t.none { it in setOf("powder", "sauce", "paste", "canned") } -> StoreCategory.PRODUCE
            t.any { it in PANTRY } -> StoreCategory.PANTRY
            else -> StoreCategory.OTHER
        }
    }

    fun isPantryStaple(key: String): Boolean {
        val t = tokens(key)
        // "black pepper" is a staple; "bell pepper" is produce.
        if ("bell" in t || "peppers" in t) return false
        return t.any { it in STAPLES }
    }

    /** Groups stored rows for display: staples apart, the rest by store section. */
    fun group(items: List<ShoppingListItemEntity>): ShoppingSections {
        val live = items.filter { it.deletedAt == null }
        val (pantry, rest) = live.partition { it.isPantryStaple }
        val sections = StoreCategory.entries.mapNotNull { category ->
            val inCategory = rest.filter { it.category == category }.sortedBy { it.name.lowercase() }
            if (inCategory.isEmpty()) null else category to inCategory
        }
        return ShoppingSections(sections, pantry.sortedBy { it.name.lowercase() })
    }

    fun sectionTitle(category: StoreCategory): String = when (category) {
        StoreCategory.PRODUCE -> "Produce"
        StoreCategory.MEAT -> "Meat & fish"
        StoreCategory.PANTRY -> "Pantry & grains"
        StoreCategory.FROZEN -> "Frozen"
        StoreCategory.OTHER -> "Other"
    }

    /** Stored quantities are metric ("450 g", "250 ml", "2 medium + 200 g"); show them in the units from settings. Free text passes through. */
    fun displayQuantity(quantity: String, unit: WeightUnit): String = ShoppingQuantities.display(quantity, unit)

    /** Plain text for ACTION_SEND: unchecked items first within each section, pantry last. */
    fun shareText(title: String, sections: ShoppingSections, unit: WeightUnit): String = buildString {
        appendLine(title)
        fun line(i: ShoppingListItemEntity) {
            val q = displayQuantity(i.quantity, unit)
            appendLine("${if (i.isChecked) "[x]" else "[ ]"} ${i.name}${if (q.isNotBlank()) " - $q" else ""}${i.note?.let { " ($it)" }.orEmpty()}")
        }
        sections.sections.forEach { (category, items) ->
            appendLine()
            appendLine(sectionTitle(category))
            items.sortedBy { it.isChecked }.forEach(::line)
        }
        if (sections.pantry.isNotEmpty()) {
            appendLine()
            appendLine("Pantry staples")
            sections.pantry.sortedBy { it.isChecked }.forEach(::line)
        }
    }.trimEnd()

    private fun formatNumber(value: Double, decimals: Int = 0): String =
        String.format(Locale.US, "%.${decimals}f", value).let {
            if (decimals > 0) it.trimEnd('0').trimEnd('.') else it
        }
}
