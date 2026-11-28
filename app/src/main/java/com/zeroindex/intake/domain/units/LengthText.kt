package com.zeroindex.intake.domain.units

import java.util.Locale

/**
 * A stored length (cm) as the person types it back. The stored value is centimetres, so a height entered in inches and
 * converted back is 182.88 cm, not the 183 cm it was: the field must not write that back on its own.
 */
object LengthText {

    /** The number a field shows: one decimal in the chosen unit, so 183 cm reads "72.0" in inches (as the You page does). */
    fun entry(cm: Double, unit: LengthUnit): String =
        String.format(Locale.ROOT, "%.1f", UnitConverter.round(unit.fromCm(cm), 1))

    /**
     * The centimetres to store for [text], or null when the text is the value the field already shows at its one decimal.
     * Leaving a field alone, or retyping the same number, never moves the stored height.
     */
    fun typed(text: String, storedCm: Double?, unit: LengthUnit): Double? {
        val value = text.trim().toDoubleOrNull() ?: return null
        val cm = unit.toCm(value)
        if (storedCm != null && entry(storedCm, unit) == entry(cm, unit)) return null
        return cm
    }
}
