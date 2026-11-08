package com.zeroindex.intake.domain.nutrition

import kotlin.math.max
import kotlin.math.roundToInt

/** Absolute macros for some amount of food: grams for macros, kcal for energy. */
data class Macros(
    val kcal: Double = 0.0,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fat: Double = 0.0,
    val fiber: Double = 0.0,
    val sugar: Double = 0.0,
    val sodium: Double = 0.0,
    val saturatedFat: Double = 0.0,
) {
    operator fun plus(other: Macros) = Macros(
        kcal = kcal + other.kcal,
        protein = protein + other.protein,
        carbs = carbs + other.carbs,
        fat = fat + other.fat,
        fiber = fiber + other.fiber,
        sugar = sugar + other.sugar,
        sodium = sodium + other.sodium,
        saturatedFat = saturatedFat + other.saturatedFat,
    )

    operator fun times(factor: Double) = Macros(
        kcal = kcal * factor,
        protein = protein * factor,
        carbs = carbs * factor,
        fat = fat * factor,
        fiber = fiber * factor,
        sugar = sugar * factor,
        sodium = sodium * factor,
        saturatedFat = saturatedFat * factor,
    )

    companion object {
        val ZERO = Macros()

        /** Scales per-100g values to [grams]. */
        fun fromPer100g(
            grams: Double,
            kcal: Double,
            protein: Double,
            carbs: Double,
            fat: Double,
            fiber: Double = 0.0,
            sugar: Double = 0.0,
            sodium: Double = 0.0,
            saturatedFat: Double = 0.0,
        ): Macros {
            val f = grams / 100.0
            return Macros(
                kcal = kcal * f,
                protein = protein * f,
                carbs = carbs * f,
                fat = fat * f,
                fiber = fiber * f,
                sugar = sugar * f,
                sodium = sodium * f,
                saturatedFat = saturatedFat * f,
            )
        }
    }
}

data class MacroTarget(
    val kcal: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
)

/**
 * What the Today screen needs to draw the ring and the remaining line.
 *
 * SPEC §5 wants the actionable framing: not just calories left, but protein
 * left alongside it, because "120 g of protein in 400 kcal" is the decision.
 */
data class DayProgress(
    val consumed: Macros,
    val target: MacroTarget?,
) {
    val kcalRemaining: Int? = target?.let { (it.kcal - consumed.kcal).roundToInt() }
    val proteinRemaining: Int? = target?.let { (it.proteinG - consumed.protein).roundToInt() }
    val carbsRemaining: Int? = target?.let { (it.carbsG - consumed.carbs).roundToInt() }
    val fatRemaining: Int? = target?.let { (it.fatG - consumed.fat).roundToInt() }

    /** 0f..1f for the ring; over-target clamps so the arc never wraps twice. */
    val kcalFraction: Float = fraction(consumed.kcal, target?.kcal?.toDouble())
    val proteinFraction: Float = fraction(consumed.protein, target?.proteinG?.toDouble())
    val carbsFraction: Float = fraction(consumed.carbs, target?.carbsG?.toDouble())
    val fatFraction: Float = fraction(consumed.fat, target?.fatG?.toDouble())

    val isOverKcal: Boolean = target != null && consumed.kcal > target.kcal

    private fun fraction(value: Double, target: Double?): Float {
        if (target == null || target <= 0.0) return 0f
        return (value / target).coerceIn(0.0, 1.0).toFloat()
    }
}

/**
 * SPEC §8: protein 1.6-2.2 g/kg of goal bodyweight defaulting to 2.0, fat at a
 * minimum of 0.6 g/kg, carbs fill the remainder.
 */
object MacroSplit {

    const val PROTEIN_G_PER_KG_MIN = 1.6
    const val PROTEIN_G_PER_KG_MAX = 2.2
    const val PROTEIN_G_PER_KG_DEFAULT = 2.0
    const val FAT_G_PER_KG_MIN = 0.6

    const val KCAL_PER_G_PROTEIN = 4.0
    const val KCAL_PER_G_CARB = 4.0
    const val KCAL_PER_G_FAT = 9.0

    fun forKcal(
        kcal: Int,
        goalWeightKg: Double,
        proteinPerKg: Double = PROTEIN_G_PER_KG_DEFAULT,
        fatPerKg: Double = FAT_G_PER_KG_MIN,
    ): MacroTarget {
        require(kcal > 0) { "kcal must be positive, was $kcal" }
        require(goalWeightKg > 0) { "goalWeightKg must be positive, was $goalWeightKg" }

        val protein = (goalWeightKg * proteinPerKg.coerceIn(PROTEIN_G_PER_KG_MIN, PROTEIN_G_PER_KG_MAX))
            .roundToInt()
        val fat = (goalWeightKg * max(fatPerKg, FAT_G_PER_KG_MIN)).roundToInt()
        val remaining = kcal - protein * KCAL_PER_G_PROTEIN - fat * KCAL_PER_G_FAT
        val carbs = max(0.0, remaining / KCAL_PER_G_CARB).roundToInt()

        return MacroTarget(kcal = kcal, proteinG = protein, carbsG = carbs, fatG = fat)
    }
}
