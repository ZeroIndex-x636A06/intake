package com.zeroindex.intake.domain.units

import java.util.Locale

/** "178.4 lb" / "80.9 kg": a stored kilogram value in the person's weight unit, to a tenth. */
fun WeightUnit.format(kg: Double): String = String.format(Locale.US, "%.1f %s", fromKg(kg), label)

/** "70.0 in" / "178.0 cm": a stored centimetre value in the person's length unit, to a tenth. */
fun LengthUnit.format(cm: Double): String = String.format(Locale.US, "%.1f %s", fromCm(cm), label)
