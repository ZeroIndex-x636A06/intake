package com.zeroindex.intake.domain.drinks

import com.zeroindex.intake.domain.nutrition.Macros

/**
 * Plain brewed tea or coffee, the base of most custom drinks. A tea or coffee that came from a user-edited Open Food
 * Facts entry or a search can carry numbers that cannot be a plain brew (half a gram of protein per 100 ml, a few
 * calories that no ingredient explains), and then a custom drink built on it is wrong before any sugar is added.
 *
 * This is deterministic and local: when the name says "plain" (no milk, cream, syrup, sweetener, flavour or mix), the
 * carbohydrate is next to nothing and the drink is in the category tea or coffee, the base is checked against what a
 * brew can physically hold. Inside the bounds it is kept as it is. Outside them, the USDA SR Legacy values for the brewed
 * drink replace it, and the screen says so. Nothing here touches allergens.
 *
 * USDA SR Legacy per 100 g, used as the generic values: "Beverages, tea, black, brewed, prepared with tap water" (1 kcal,
 * 0 g protein, 0.3 g carbohydrate, 0 g sugars, 3 mg sodium) and "Beverages, coffee, brewed, prepared with tap water"
 * (1 kcal, 0.12 g protein, 0 g carbohydrate, 2 mg sodium). A millilitre is counted as a gram.
 */
object PlainBrew {

    enum class Kind { TEA, COFFEE }

    /** Words that make a tea or coffee something other than a plain brew. */
    private val notPlain = setOf(
        "milk", "latte", "cappuccino", "mocha", "macchiato", "cream", "creamer", "creamy", "sweet", "sweetened", "sugar", "sugary", "honey",
        "syrup", "lemon", "peach", "raspberry", "bubble", "boba", "chai", "frappuccino", "frappe", "flavored", "flavoured", "vanilla",
        "caramel", "hazelnut", "pumpkin", "mix", "powder", "powdered", "instant", "concentrate", "smoothie", "shake", "whip", "whipped",
        "cocoa", "chocolate", "oat", "soy", "almond", "coconut", "matcha", "kombucha", "bottled",
    )

    /** What a plain brew can hold per 100 ml: more than this is not a plain brew's numbers (USDA espresso, the highest, is 9 kcal). */
    const val MAX_PROTEIN = 0.35
    const val MAX_FAT = 0.3
    const val MAX_KCAL = 10.0

    /** Above this much carbohydrate per 100 ml the drink is treated as sweetened and left alone. */
    const val MAX_CARBS_FOR_PLAIN = 0.5

    fun kindOf(category: DrinkCategory?): Kind? = when (category) {
        DrinkCategory.TEA -> Kind.TEA
        DrinkCategory.COFFEE -> Kind.COFFEE
        else -> null
    }

    /** True when the name and the stored allergen text describe an unflavoured, unsweetened, milk-free brew. */
    fun looksPlain(name: String, allergenTags: String): Boolean {
        val tokens = name.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty() || tokens.any { it in notPlain }) return false
        return !allergenTags.lowercase().contains("milk")
    }

    /** The generic brew for [kind]: USDA SR Legacy, rounded. */
    fun generic(kind: Kind): Macros = when (kind) {
        Kind.TEA -> Macros(kcal = 1.0, protein = 0.0, carbs = 0.3, fat = 0.0, fiber = 0.0, sugar = 0.0, sodium = 3.0, saturatedFat = 0.0)
        Kind.COFFEE -> Macros(kcal = 1.0, protein = 0.12, carbs = 0.0, fat = 0.0, fiber = 0.0, sugar = 0.0, sodium = 2.0, saturatedFat = 0.0)
    }

    /**
     * What the check decided. [per100] is the base's nutrition to use; [unlisted] is the comma-joined list of nutrients that
     * are still unknown; [replaced] is true when the generic brew stood in for out-of-range numbers.
     */
    data class Checked(val per100: Macros, val unlisted: String, val replaced: Boolean)

    fun check(category: DrinkCategory?, name: String, allergenTags: String, per100: Macros, unlisted: String): Checked {
        val kind = kindOf(category)
        val keep = Checked(per100, unlisted, replaced = false)
        if (kind == null || !looksPlain(name, allergenTags) || per100.carbs > MAX_CARBS_FOR_PLAIN) return keep
        val outOfRange = per100.protein > MAX_PROTEIN || per100.fat > MAX_FAT || per100.kcal > MAX_KCAL
        if (outOfRange) return Checked(generic(kind), unlisted = "", replaced = true)
        // In range: a plain brew has no sugar, fibre or saturated fat to speak of, so those are zero rather than unknown.
        val known = setOf("sugar", "fiber", "satfat")
        val codes = unlisted.split(',').map { it.trim() }.filter { it.isNotEmpty() && it !in known }
        return Checked(per100.copy(sugar = 0.0, fiber = 0.0, saturatedFat = 0.0), codes.joinToString(","), replaced = false)
    }
}
