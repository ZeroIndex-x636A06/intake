package com.zeroindex.intake.domain.nutrition

import kotlin.math.floor

/**
 * Grams per kilogram of body weight as the plan quotes it (Q11-8). The You card printed the Settings figure (1.9) beside a
 * protein target that works out to 1.95 g/kg for the weight on the scale (170 g at 87.1 kg), so the line did not match the
 * number above it. One rule: work it out from the target and the weight, round half up to one decimal (170 / 87.1 reads
 * "2.0"), and fall back to the setting only when there is no weight.
 */
object GramsPerKg {
    /** 1.95 reads "2.0", 1.94 reads "1.9": half up, not the banker's rounding of Kotlin's roundToInt on a tie. */
    fun round1(value: Double): Double = floor(value * 10.0 + 0.5 + 1e-9) / 10.0

    fun text(grams: Double, weightKg: Double?, fallbackPerKg: Double): String {
        val perKg = if (weightKg != null && weightKg > 0.0) grams / weightKg else fallbackPerKg
        return "%.1f".format(java.util.Locale.US, round1(perKg))
    }
}
