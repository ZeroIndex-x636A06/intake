package com.zeroindex.intake.domain.units

import kotlin.math.abs

/** The change chip on a body measurement (Q11-5): always a unit, one decimal, and a proper zero. */
object MeasurementDelta {
    /** [delta] is already in [unit]: "−0.4 in", "+1.2 cm", "±0.0 in" when it rounds to nothing, never "+-0". */
    fun text(delta: Double, unit: LengthUnit): String {
        val magnitude = "%.1f".format(abs(delta))
        val sign = when {
            magnitude == "0.0" -> "±"
            delta < 0 -> "−"
            else -> "+"
        }
        return "$sign$magnitude ${unit.label}"
    }
}
