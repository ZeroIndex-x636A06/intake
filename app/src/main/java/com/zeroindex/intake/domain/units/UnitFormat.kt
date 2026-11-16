package com.zeroindex.intake.domain.units

import java.util.Locale

/** "178.4 lb" / "80.9 kg": a stored kilogram value in the person's weight unit, to a tenth. */
fun WeightUnit.format(kg: Double): String = String.format(Locale.US, "%.1f %s", fromKg(kg), label)

/** A stored kilogram value as it goes in an edit field in this unit: "79.4" or "36" (a whole ".0" is dropped). */
fun WeightUnit.fieldText(kg: Double): String = UnitConverter.round(fromKg(kg), 1).toString().removeSuffix(".0")

/**
 * What a weight edit field should show now that the stored value is [storedKg] and the box holds [typed]: null to
 * leave the box alone (it already means the stored value, or there is nothing stored), else the text to show. The
 * stored profile can arrive after the first composition, so the field must follow it (QA Q4-15).
 */
fun WeightUnit.fieldSync(storedKg: Double?, typed: String): String? {
    if (storedKg == null) return null
    val typedKg = typed.toDoubleOrNull()?.let(::toKg)
    return if (typedKg != null && kotlin.math.abs(typedKg - storedKg) < 0.06) null else fieldText(storedKg)
}

/** "70.0 in" / "178.0 cm": a stored centimetre value in the person's length unit, to a tenth. */
fun LengthUnit.format(cm: Double): String = String.format(Locale.US, "%.1f %s", fromCm(cm), label)
