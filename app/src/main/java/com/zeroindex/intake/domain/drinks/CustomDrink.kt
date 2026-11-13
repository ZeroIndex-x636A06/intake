package com.zeroindex.intake.domain.drinks

import com.zeroindex.intake.domain.nutrition.Macros
import kotlin.math.roundToInt
import kotlinx.serialization.Serializable

/**
 * Custom versions of drinks ("Black coffee + 3 tsp sugar"). All the maths is here, pure Kotlin, so it is tested without a
 * phone: what the add-ins add, the volume the drink ends up with, the per-100 ml numbers a saved "My drink" stores, the
 * caffeine and strength after dilution, and the allergen text (the union of the base drink and every add-in).
 *
 * A millilitre of drink or liquid add-in is counted as a gram, as everywhere in the app.
 */

/** How an add-in amount is measured. [label] is what the person sees and what the drink's name uses. */
enum class AddInUnit(val label: String) {
    TSP("tsp"), TBSP("tbsp"), ML("ml"), PUMP("pump"), PACKET("packet"), GRAM("g");

    companion object {
        fun fromStored(name: String): AddInUnit = entries.firstOrNull { it.name == name } ?: GRAM
    }
}

/**
 * One thing added to a drink, frozen as it was: the nutrition per 100 g (or 100 ml) and the amount in grams, so a saved
 * drink does not change if a built-in value is later corrected. [volumeMl] is how much it adds to the glass: a splash of
 * milk adds volume, a spoon of sugar dissolves and adds none. [allergenTags] is the stored allergen text (`en:milk | milk`).
 */
@Serializable
data class AddIn(
    val key: String,
    val name: String,
    val amount: Double,
    val unit: String,
    val grams: Double,
    val volumeMl: Double,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val fiber: Double = 0.0,
    val sugar: Double = 0.0,
    /** Milligrams. */
    val sodium: Double = 0.0,
    val saturatedFat: Double = 0.0,
    val allergenTags: String = "",
    /** Comma-joined codes of nutrients the source did not list. */
    val unlisted: String = "",
) {
    /** What this add-in contributes, in absolute amounts. */
    fun nutrition(): Macros = Macros(kcal, protein, carbs, fat, fiber, sugar, sodium, saturatedFat) * (grams / 100.0)

    /** "3 tsp sugar", "60 ml whole milk", "1 pump flavoured syrup", or "30 g banana" for a food from search. */
    fun label(): String = "${formatAmount(amount)} ${AddInUnit.fromStored(unit).label} ${name.lowercase()}"
}

/** A built-in add-in: its nutrition per 100 g (or 100 ml) and the units it comes in, each with its size in grams. */
data class AddInOption(
    val key: String,
    val name: String,
    val group: String,
    val per100: Macros,
    /** Unit to grams (or millilitres) per one unit. The first entry is the default unit. */
    val units: Map<AddInUnit, Double>,
    val defaultAmount: Double,
    /** True for a liquid, which adds to the volume of the drink. */
    val liquid: Boolean,
    val allergenTags: String = "",
    val unlisted: String = "",
) {
    val defaultUnit: AddInUnit get() = units.keys.first()

    fun toAddIn(amount: Double, unit: AddInUnit = defaultUnit): AddIn {
        val grams = amount * (units[unit] ?: 1.0)
        return AddIn(
            key = key, name = name, amount = amount, unit = unit.name, grams = grams, volumeMl = if (liquid) grams else 0.0,
            kcal = per100.kcal, protein = per100.protein, carbs = per100.carbs, fat = per100.fat, fiber = per100.fiber,
            sugar = per100.sugar, sodium = per100.sodium, saturatedFat = per100.saturatedFat,
            allergenTags = allergenTags, unlisted = unlisted,
        )
    }
}

/**
 * The small built-in list. Nutrition per 100 g (100 ml for liquids) is the USDA FoodData Central (SR Legacy) value for
 * the generic food, rounded; a plant milk or a syrup, which USDA lists by brand, is a typical figure. Spoon sizes: a
 * teaspoon of sugar is 4 g (the label teaspoon), of honey 7 g; a tablespoon of a liquid is 15 ml; one syrup pump is
 * 10 ml. Dairy add-ins carry the `en:milk` tag, so the allergen engine raises dairy without any help.
 */
object AddInCatalog {
    private const val MILK = "en:milk | milk"

    private fun liquidUnits(): Map<AddInUnit, Double> = mapOf(AddInUnit.TBSP to 15.0, AddInUnit.ML to 1.0)

    val ALL: List<AddInOption> = listOf(
        AddInOption("sugar", "Sugar", "Sweeteners", Macros(387.0, 0.0, 99.98, 0.0, 0.0, 99.8, 1.0, 0.0), mapOf(AddInUnit.TSP to 4.0, AddInUnit.GRAM to 1.0), 1.0, liquid = false),
        AddInOption("honey", "Honey", "Sweeteners", Macros(304.0, 0.3, 82.4, 0.0, 0.2, 82.1, 4.0, 0.0), mapOf(AddInUnit.TSP to 7.0, AddInUnit.GRAM to 1.0), 1.0, liquid = false),
        AddInOption("syrup", "Flavoured syrup", "Sweeteners", Macros(260.0, 0.0, 65.0, 0.0, 0.0, 65.0, 10.0, 0.0), mapOf(AddInUnit.PUMP to 10.0, AddInUnit.ML to 1.0), 1.0, liquid = true),
        AddInOption("sweetener", "Sweetener", "Sweeteners", Macros(), mapOf(AddInUnit.PACKET to 1.0), 1.0, liquid = false),
        AddInOption("milk-whole", "Whole milk", "Milk and cream", Macros(61.0, 3.15, 4.8, 3.25, 0.0, 5.05, 43.0, 1.86), liquidUnits(), 2.0, liquid = true, allergenTags = MILK),
        AddInOption("milk-2", "2% milk", "Milk and cream", Macros(50.0, 3.3, 4.8, 1.9, 0.0, 5.1, 47.0, 1.2), liquidUnits(), 2.0, liquid = true, allergenTags = MILK),
        AddInOption("milk-skim", "Skim milk", "Milk and cream", Macros(34.0, 3.4, 5.0, 0.1, 0.0, 5.1, 42.0, 0.1), liquidUnits(), 2.0, liquid = true, allergenTags = MILK),
        AddInOption("cream", "Heavy cream", "Milk and cream", Macros(340.0, 2.8, 2.8, 36.1, 0.0, 2.9, 27.0, 23.0), liquidUnits(), 1.0, liquid = true, allergenTags = "en:milk | cream"),
        AddInOption("half-half", "Half-and-half", "Milk and cream", Macros(131.0, 3.1, 4.3, 11.5, 0.0, 4.0, 40.0, 7.2), liquidUnits(), 1.0, liquid = true, allergenTags = "en:milk | half and half"),
        AddInOption("creamer", "Non-dairy creamer", "Milk and cream", Macros(140.0, 0.6, 11.0, 10.0, 0.0, 10.0, 80.0, 1.0), liquidUnits(), 1.0, liquid = true),
        AddInOption("oat", "Oat milk", "Plant milks", Macros(48.0, 1.0, 8.0, 1.5, 0.8, 4.0, 40.0, 0.2), liquidUnits(), 2.0, liquid = true, allergenTags = "oat milk"),
        AddInOption("almond", "Almond milk", "Plant milks", Macros(15.0, 0.6, 0.6, 1.2, 0.2, 0.0, 70.0, 0.1), liquidUnits(), 2.0, liquid = true, allergenTags = "almond milk"),
        AddInOption("soy", "Soy milk", "Plant milks", Macros(33.0, 2.9, 1.7, 1.8, 0.4, 0.4, 51.0, 0.3), liquidUnits(), 2.0, liquid = true, allergenTags = "en:soybeans | soy milk"),
    )

    fun byKey(key: String): AddInOption? = ALL.firstOrNull { it.key == key }
}

/** Per-100 ml numbers of a drink, as a food row stores them. */
data class DrinkBase(
    val name: String,
    /** Nutrition per 100 ml (a millilitre counted as a gram); sodium in mg. */
    val per100: Macros,
    val caffeineMgPer100ml: Double = 0.0,
    val abvPercent: Double = 0.0,
    val allergenTags: String = "",
    val unlisted: String = "",
)

/** What a base drink and its add-ins come to. */
data class CustomDrinkResult(
    /** The glass: the base volume plus every liquid add-in. */
    val totalVolumeMl: Double,
    /** Absolute nutrition of the whole glass. */
    val total: Macros,
    /** The same, per 100 ml of the finished drink: what a saved "My drink" stores. */
    val per100ml: Macros,
    val caffeineMg: Double,
    val caffeineMgPer100ml: Double,
    val alcoholMl: Double,
    val abvPercent: Double,
)

object CustomDrink {

    /** Base drink at [baseVolumeMl] plus [addIns]. */
    fun compute(base: DrinkBase, baseVolumeMl: Double, addIns: List<AddIn>): CustomDrinkResult {
        require(baseVolumeMl > 0) { "the base volume must be positive" }
        val total = addIns.fold(base.per100 * (baseVolumeMl / 100.0)) { acc, a -> acc + a.nutrition() }
        val volume = baseVolumeMl + addIns.sumOf { it.volumeMl }
        val caffeine = base.caffeineMgPer100ml * baseVolumeMl / 100.0
        val alcohol = base.abvPercent / 100.0 * baseVolumeMl
        return CustomDrinkResult(
            totalVolumeMl = volume,
            total = total,
            per100ml = total * (100.0 / volume),
            caffeineMg = caffeine,
            caffeineMgPer100ml = caffeine * 100.0 / volume,
            alcoholMl = alcohol,
            abvPercent = alcohol * 100.0 / volume,
        )
    }

    /** "Coffee, black + 3 tsp sugar + 30 ml whole milk". */
    fun name(baseName: String, addIns: List<AddIn>): String =
        if (addIns.isEmpty()) baseName else baseName + addIns.joinToString("") { " + ${it.label()}" }

    /**
     * The stored allergen text of the finished drink: every structured tag of the base and of each add-in, and the
     * ingredient text of each plus the add-ins' names. It is a union, so it can only add to what the base drink says; the
     * local engine reads it exactly like a packaged food's, and nothing here removes a warning.
     */
    fun allergenText(base: DrinkBase, addIns: List<AddIn>): String {
        val tags = LinkedHashSet<String>()
        val text = ArrayList<String>()
        fun absorb(stored: String) {
            if (stored.isBlank()) return
            val at = stored.indexOf(" | ")
            val head = if (at >= 0) stored.substring(0, at) else stored
            val tail = if (at >= 0) stored.substring(at + 3) else ""
            val pieces = head.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            if (pieces.isNotEmpty() && pieces.all { TAG.matches(it) }) {
                tags += pieces
                if (tail.isNotBlank()) text += tail.trim()
            } else {
                text += stored.trim()
            }
        }
        absorb(base.allergenTags)
        addIns.forEach { absorb(it.allergenTags); text += it.name.lowercase() }
        val ingredients = text.filter { it.isNotBlank() }.distinct().joinToString(", ")
        return buildString {
            append(tags.joinToString(","))
            if (ingredients.isNotEmpty()) {
                if (isNotEmpty()) append(" | ")
                append(ingredients)
            }
        }
    }

    /** A nutrient is unlisted in the finished drink when the base or any add-in did not list it. */
    fun unlistedUnion(base: DrinkBase, addIns: List<AddIn>): String =
        (listOf(base.unlisted) + addIns.map { it.unlisted }).flatMap { it.split(',') }.map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString(",")

    private val TAG = Regex("^[a-z]{2}:[a-z0-9-]+$")
}

/**
 * What a saved "My drink" remembers, so it can be edited later: the base drink, how much of it, and the add-ins. Kept as
 * JSON in the drink's own food row (`food.customDrink`); the nutrition columns of that row are the computed result.
 */
@Serializable
data class CustomDrinkRecipe(
    val baseFoodId: String,
    val baseName: String,
    val baseVolumeMl: Double,
    val addIns: List<AddIn>,
) {
    fun encode(): String = JSON.encodeToString(serializer(), this)

    companion object {
        private val JSON = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; encodeDefaults = true }

        /** Null for a missing or unreadable value, so a damaged row is an ordinary drink rather than a crash. */
        fun decode(text: String?): CustomDrinkRecipe? =
            text?.takeIf { it.isNotBlank() }?.let { runCatching { JSON.decodeFromString(serializer(), it) }.getOrNull() }
    }
}

internal fun formatAmount(v: Double): String =
    if (v == v.roundToInt().toDouble()) v.roundToInt().toString() else "%.1f".format(java.util.Locale.US, v).removeSuffix(".0")
