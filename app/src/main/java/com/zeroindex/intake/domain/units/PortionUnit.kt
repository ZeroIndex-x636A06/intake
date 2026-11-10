package com.zeroindex.intake.domain.units

import java.util.Locale
import kotlin.math.roundToInt

private const val PORTION_GRAMS_PER_OUNCE = 28.349523125

/**
 * The unit a food portion is shown and typed in. Portions are stored in grams, always (SPEC §3.5); this only
 * converts at the edge, so no ounce value is ever written to the database.
 *
 * Settings has one weight unit (lb or kg), and a food portion follows it: pounds people think in ounces,
 * kilogram people in grams.
 */
enum class PortionUnit(val label: String, private val gramsPerUnit: Double) {
    GRAMS("g", 1.0),
    OUNCES("oz", PORTION_GRAMS_PER_OUNCE),
    ;

    fun fromGrams(grams: Double): Double = grams / gramsPerUnit

    fun toGrams(value: Double): Double = value * gramsPerUnit

    /** "170 g" or "6.0 oz": whole grams, ounces to a tenth. */
    fun format(grams: Double): String = "${number(grams)} $label"

    /** Just the number, as it goes in an edit field. */
    fun number(grams: Double): String = when (this) {
        GRAMS -> grams.roundToInt().toString()
        OUNCES -> String.format(Locale.US, "%.1f", fromGrams(grams))
    }

    /** "150-190 g" for a range, collapsing to one value when the ends round alike. */
    fun formatRange(lowGrams: Double, highGrams: Double): String {
        val lo = number(lowGrams)
        val hi = number(highGrams)
        return if (lo == hi) "$lo $label" else "$lo–$hi $label"
    }

    /** Reads what the person typed (a comma decimal is accepted) as grams, or null when it is not a number. */
    fun parseToGrams(text: String): Double? =
        text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }?.let(::toGrams)

    companion object {
        const val GRAMS_PER_OUNCE = PORTION_GRAMS_PER_OUNCE

        fun forWeightUnit(unit: WeightUnit): PortionUnit = when (unit) {
            WeightUnit.POUNDS -> OUNCES
            WeightUnit.KILOGRAMS -> GRAMS
        }
    }
}
