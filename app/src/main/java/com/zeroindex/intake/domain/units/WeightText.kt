package com.zeroindex.intake.domain.units

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Training text is written by the pure engines in kilograms ("Start at about 60 kg", "+2.5 kg"), because that is what
 * is stored. Whatever shows it to the person passes it through here, so every weight reads in their unit and no screen
 * has to know which sentences carry a weight.
 */
object WeightText {
    private val KG = Regex("""(?<![\p{L}\d.,])(\d+(?:\.\d+)?)\s?kg(?![\p{L}\d])""")

    /** [text] with every "N kg" rewritten in [unit]. Kilograms are left exactly as written. */
    fun inUnit(text: String, unit: WeightUnit): String {
        if (unit == WeightUnit.KILOGRAMS || !text.contains("kg")) return text
        return KG.replace(text) { m -> "${number(m.groupValues[1].toDouble(), unit)} ${unit.label}" }
    }

    /** One weight with its unit: "130 lb", "59 kg", "2.5 lb". */
    fun format(kg: Double, unit: WeightUnit): String = "${number(kg, unit)} ${unit.label}"

    /**
     * The number alone. Pounds snap to the nearest half pound, since anything finer is conversion noise (59 kg is
     * 130 lb, not 130.07); kilograms keep one decimal. A whole number drops its ".0".
     */
    fun number(kg: Double, unit: WeightUnit): String {
        val rounded = snapped(kg, unit)
        return if (abs(rounded - rounded.roundToLong()) < 1e-9) rounded.roundToLong().toString() else rounded.toString()
    }

    /**
     * An estimated one-rep max: one decimal in the person's unit, never snapped to a loadable half pound. An estimate is
     * a calculated figure, and every place that shows one (Training stats, records, calculators, exercise sheet) uses this
     * one rounding, so the same set reads the same everywhere (QA Q7-10: 186.7 on one line and 186.5 on the next).
     */
    fun estimate(kg: Double, unit: WeightUnit): String {
        val v = Math.round(unit.fromKg(kg) * 10) / 10.0
        return if (abs(v - v.roundToLong()) < 1e-9) v.roundToLong().toString() else v.toString()
    }

    /** [estimate] with the unit label: "186.7 lb". */
    fun formatEstimate(kg: Double, unit: WeightUnit): String = "${estimate(kg, unit)} ${unit.label}"

    /**
     * A weight in a set row: always one decimal place ("50.0", "57.5"), so every row of a session reads the same way
     * whether the number was typed, prescribed or copied from last time. The value is snapped first, so a weight that
     * is 49.9999 after conversion still reads "50.0".
     */
    fun setRow(kg: Double, unit: WeightUnit): String = String.format(Locale.ROOT, "%.1f", snapped(kg, unit))

    private fun snapped(kg: Double, unit: WeightUnit): Double {
        val value = unit.fromKg(kg)
        return when (unit) {
            WeightUnit.POUNDS -> (value * 2).roundToLong() / 2.0
            WeightUnit.KILOGRAMS -> (value * 10).roundToLong() / 10.0
        }
    }
}
