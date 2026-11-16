package com.zeroindex.intake.domain.units

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * The pure engines write explanations in the stored units ("3,050 ml", "87.1 kg") because that is what they hold.
 * Whatever shows an explanation passes it through here so every weight and volume reads in the person's units
 * (QA Q4-5). Text that is already in their unit is left alone.
 */
object UnitText {
    private val ML = Regex("""(?<![\p{L}\d.,])(\d[\d,]*(?:\.\d+)?)\s?ml(?![\p{L}\d])""")

    /** [text] with every "N kg" and "N ml" rewritten in the given units. Metric units leave the text as written. */
    fun inUnits(text: String, weight: WeightUnit, volume: VolumeUnit): String =
        volumeInUnit(WeightText.inUnit(text, weight), volume)

    /** [text] with every "N ml" (grouped thousands allowed) rewritten as "N fl oz" when [unit] is fluid ounces. */
    fun volumeInUnit(text: String, unit: VolumeUnit): String {
        if (unit == VolumeUnit.MILLILITRES || !text.contains("ml")) return text
        return ML.replace(text) { m ->
            val ml = m.groupValues[1].replace(",", "").toDoubleOrNull() ?: return@replace m.value
            unit.format(ml)
        }
    }
}

/**
 * Distances for people on inches and miles. [LengthUnit] is the one length setting, so inches means miles for a
 * walk or a drive and centimetres means kilometres (QA Q4-19).
 */
object DistanceText {
    private const val KM_PER_MILE = 1.609344

    /** "6 miles" or "10 km" for a distance given in kilometres, to [decimals] places (a whole ".0" is dropped). */
    fun format(km: Double, unit: LengthUnit, decimals: Int = 0): String {
        val value = if (unit == LengthUnit.INCHES) km / KM_PER_MILE else km
        val label = when (unit) {
            LengthUnit.CENTIMETRES -> "km"
            LengthUnit.INCHES -> if (abs(value - 1.0) < 0.05) "mile" else "miles"
        }
        val text = String.format(Locale.US, "%.${decimals}f", value)
        return "${if (decimals > 0 && text.endsWith(".0")) text.dropLast(2) else text} $label"
    }

    private val KM = Regex("""(?<![\p{L}\d.,])(\d+(?:\.\d+)?)\s?km(?![\p{L}\d/])""")
    private val KMH = Regex("""(?<![\p{L}\d.,])(\d+(?:\.\d+)?)\s?km/h""")

    /** [text] with every "N km" and "N km/h" rewritten as miles and mph for inches. Centimetres leave it as written. */
    fun inUnit(text: String, unit: LengthUnit): String {
        if (unit == LengthUnit.CENTIMETRES || !text.contains("km")) return text
        val speeds = KMH.replace(text) { m -> speed(m.groupValues[1].toDouble(), unit) }
        return KM.replace(speeds) { m ->
            val km = m.groupValues[1].toDouble()
            format(km, unit, decimals = if (km / KM_PER_MILE < 10.0) 1 else 0)
        }
    }

    /** "5 km/h" or "3 mph" for a speed given in km/h. */
    fun speed(kmh: Double, unit: LengthUnit): String = when (unit) {
        LengthUnit.CENTIMETRES -> "${kmh.roundToLong()} km/h"
        LengthUnit.INCHES -> "${(kmh / KM_PER_MILE).roundToLong()} mph"
    }
}
