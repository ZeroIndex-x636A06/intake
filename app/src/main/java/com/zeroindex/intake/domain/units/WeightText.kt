package com.zeroindex.intake.domain.units

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
        val value = unit.fromKg(kg)
        val rounded = when (unit) {
            WeightUnit.POUNDS -> (value * 2).roundToLong() / 2.0
            WeightUnit.KILOGRAMS -> (value * 10).roundToLong() / 10.0
        }
        return if (abs(rounded - rounded.roundToLong()) < 1e-9) rounded.roundToLong().toString() else rounded.toString()
    }
}
