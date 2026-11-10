package com.zeroindex.intake.domain.nutrition

import kotlin.math.floor

/**
 * The four optional nutrients a food may not list. Every food stores `0.0` for a nutrient its source did not give,
 * so [Unlisted] records which ones those were; "not listed" is then told apart from a real zero everywhere (the
 * Nutrition Facts label, the dashboard). Stored on the food and copied onto each log entry as a comma-joined list.
 */
enum class NutrientKey(val code: String, val label: String) {
    FIBER("fiber", "fibre"),
    SUGAR("sugar", "sugar"),
    SODIUM("sodium", "sodium"),
    SATURATED_FAT("satfat", "saturated fat"),
}

object Unlisted {
    fun encode(keys: Set<NutrientKey>): String = keys.sortedBy { it.ordinal }.joinToString(",") { it.code }

    fun decode(text: String?): Set<NutrientKey> {
        if (text.isNullOrBlank()) return emptySet()
        val codes = text.split(',').map { it.trim() }
        return NutrientKey.entries.filter { it.code in codes }.toSet()
    }

    /**
     * For a row that carries only what a person typed (quick add, a recipe or an eat-out estimate): an optional
     * nutrient left at zero was never entered, so it is not listed.
     */
    fun forTypedMacros(m: Macros): Set<NutrientKey> = buildSet {
        if (m.fiber == 0.0) add(NutrientKey.FIBER)
        if (m.sugar == 0.0) add(NutrientKey.SUGAR)
        if (m.sodium == 0.0) add(NutrientKey.SODIUM)
        if (m.saturatedFat == 0.0) add(NutrientKey.SATURATED_FAT)
    }
}

/**
 * Food values per 100 g (or 100 ml). A null optional nutrient is "not listed". [sodiumMg] is milligrams.
 */
data class NutrientsPer100(
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val fiber: Double?,
    val sugar: Double?,
    val sodiumMg: Double?,
    val saturatedFat: Double?,
) {
    companion object {
        /** Builds from a food's stored columns and its [unlisted] set: a listed-as-missing value becomes null. */
        fun of(
            kcal: Double, protein: Double, carbs: Double, fat: Double,
            fiber: Double, sugar: Double, sodiumMg: Double, saturatedFat: Double,
            unlisted: Set<NutrientKey>,
        ) = NutrientsPer100(
            kcal, protein, carbs, fat,
            fiber = fiber.takeUnless { NutrientKey.FIBER in unlisted },
            sugar = sugar.takeUnless { NutrientKey.SUGAR in unlisted },
            sodiumMg = sodiumMg.takeUnless { NutrientKey.SODIUM in unlisted },
            saturatedFat = saturatedFat.takeUnless { NutrientKey.SATURATED_FAT in unlisted },
        )
    }
}

/** US FDA Daily Values (2020 label rules, a 2,000 kcal diet). */
object DailyValues {
    const val REFERENCE_KCAL = 2000
    const val TOTAL_FAT_G = 78.0
    const val SATURATED_FAT_G = 20.0
    const val CHOLESTEROL_MG = 300.0
    const val SODIUM_MG = 2300.0
    const val TOTAL_CARBS_G = 275.0
    const val FIBER_G = 28.0
    const val ADDED_SUGARS_G = 50.0

    /** Salt is 2.5 times the sodium by weight. */
    const val SALT_PER_SODIUM = 2.5

    /** A whole-number % Daily Value, half up (so 3.5 g of saturated fat is 18%). */
    fun percent(amount: Double, dailyValue: Double): Int = floor(amount / dailyValue * 100.0 + 0.5).toInt()

    fun saltGrams(sodiumMg: Double): Double = sodiumMg * SALT_PER_SODIUM / 1000.0
}

enum class LabelMode(val label: String) { PER_PORTION("Per portion"), PER_100("Per 100 g") }

enum class LabelNutrient { TOTAL_FAT, SATURATED_FAT, TRANS_FAT, CHOLESTEROL, SODIUM, SALT, TOTAL_CARBS, FIBER, TOTAL_SUGARS, ADDED_SUGARS, PROTEIN }

/** One line of the label. [amount] is null for "not listed"; [percentDv] is null when there is no amount or no DV. */
data class LabelRow(
    val nutrient: LabelNutrient,
    val name: String,
    val amount: Double?,
    val unit: String,
    val percentDv: Int?,
    val indent: Int,
    val bold: Boolean,
) {
    val listed: Boolean get() = amount != null
}

/** The label drawn by `NutritionFactsLabel`: a view of the stored values, never an image. */
data class NutritionLabel(
    /** Calories for the column shown, unrounded; use [LabelFormat.calories] to show it. */
    val calories: Double,
    val rows: List<LabelRow>,
    val mode: LabelMode,
    /** "1 bar (60 g)" or "100 g" or "355 ml". */
    val servingText: String,
) {
    companion object {
        const val FOOTNOTE = "* The % Daily Value tells you how much a nutrient in a serving contributes to a daily diet. " +
            "2,000 calories a day is used for general nutrition advice."
        const val NOT_LISTED = "not listed"
        const val MICRONUTRIENTS = "Vitamins and minerals: not listed."

        /**
         * @param grams the portion in grams (or millilitres for a drink, taken as 1 g per ml).
         * @param saltInsteadOfSodium show Salt (g) in place of Sodium (mg), the Settings choice.
         */
        fun build(
            per100: NutrientsPer100,
            grams: Double,
            mode: LabelMode,
            servingText: String,
            saltInsteadOfSodium: Boolean = false,
        ): NutritionLabel {
            val f = (if (mode == LabelMode.PER_100) 100.0 else grams) / 100.0
            fun scaled(v: Double?) = v?.let { it * f }
            val fat = per100.fat * f
            val sat = scaled(per100.saturatedFat)
            val sodium = scaled(per100.sodiumMg)
            val carbs = per100.carbs * f
            val fiber = scaled(per100.fiber)
            val sugar = scaled(per100.sugar)
            val protein = per100.protein * f
            fun pct(v: Double?, dv: Double) = v?.let { DailyValues.percent(it, dv) }
            val sodiumRow = if (saltInsteadOfSodium) {
                LabelRow(LabelNutrient.SALT, "Salt", sodium?.let(DailyValues::saltGrams), "g", pct(sodium, DailyValues.SODIUM_MG), 0, true)
            } else {
                LabelRow(LabelNutrient.SODIUM, "Sodium", sodium, "mg", pct(sodium, DailyValues.SODIUM_MG), 0, true)
            }
            val rows = listOf(
                LabelRow(LabelNutrient.TOTAL_FAT, "Total Fat", fat, "g", pct(fat, DailyValues.TOTAL_FAT_G), 0, true),
                LabelRow(LabelNutrient.SATURATED_FAT, "Saturated Fat", sat, "g", pct(sat, DailyValues.SATURATED_FAT_G), 1, false),
                // Intake does not store trans fat, cholesterol or added sugars, so they read "not listed".
                LabelRow(LabelNutrient.TRANS_FAT, "Trans Fat", null, "g", null, 1, false),
                LabelRow(LabelNutrient.CHOLESTEROL, "Cholesterol", null, "mg", null, 0, true),
                sodiumRow,
                LabelRow(LabelNutrient.TOTAL_CARBS, "Total Carbohydrate", carbs, "g", pct(carbs, DailyValues.TOTAL_CARBS_G), 0, true),
                LabelRow(LabelNutrient.FIBER, "Dietary Fiber", fiber, "g", pct(fiber, DailyValues.FIBER_G), 1, false),
                LabelRow(LabelNutrient.TOTAL_SUGARS, "Total Sugars", sugar, "g", null, 1, false),
                LabelRow(LabelNutrient.ADDED_SUGARS, "Added Sugars", null, "g", null, 2, false),
                LabelRow(LabelNutrient.PROTEIN, "Protein", protein, "g", null, 0, true),
            )
            return NutritionLabel(per100.kcal * f, rows, mode, servingText)
        }
    }
}

/** How the label writes its numbers: the FDA's rounding for calories, grams and milligrams. */
object LabelFormat {
    /** Up to 50 kcal to the nearest 5, above that to the nearest 10. */
    fun calories(kcal: Double): Int = (if (kcal <= 50.0) roundTo(kcal, 5.0) else roundTo(kcal, 10.0)).toInt()

    /** Under 0.5 g is "0"; under 5 g to the nearest half gram; otherwise whole grams. */
    fun grams(g: Double): String = when {
        g < 0.5 -> "0"
        g < 5.0 -> roundTo(g, 0.5).let { if (it % 1.0 == 0.0) it.toInt().toString() else "%.1f".format(java.util.Locale.US, it) }
        else -> Math.round(g).toString()
    }

    /** Under 5 mg is "0"; up to 140 mg to the nearest 5; above that to the nearest 10. */
    fun milligrams(mg: Double): String = when {
        mg < 5.0 -> "0"
        mg <= 140.0 -> roundTo(mg, 5.0).toInt().toString()
        else -> roundTo(mg, 10.0).toInt().toString()
    }

    fun amount(row: LabelRow): String? {
        val a = row.amount ?: return null
        return (if (row.unit == "mg") milligrams(a) else grams(a)) + " " + row.unit
    }

    private fun roundTo(v: Double, step: Double): Double = Math.round(v / step) * step
}
