package com.zeroindex.intake.domain.training

import com.zeroindex.intake.domain.units.WeightUnit

/** What a person typed when changing a logged set, checked and turned into stored units. */
data class SetEditValues(val weightKg: Double, val reps: Int)

/** Editing a set of a finished session: the typed text becomes kilograms and reps, or nothing when it is not a set. */
object SetEdit {
    const val MAX_REPS = 999
    const val MAX_WEIGHT_DISPLAY = 2000.0

    /**
     * [weightText] is in [unit]; a blank weight is 0 (a bodyweight set). Reps must be a whole number from 1 up.
     * Returns null for anything else, so the Save button stays off.
     */
    fun parse(weightText: String, repsText: String, unit: WeightUnit): SetEditValues? {
        val weight = if (weightText.isBlank()) 0.0 else weightText.trim().toDoubleOrNull() ?: return null
        val reps = repsText.trim().toIntOrNull() ?: return null
        if (weight.isNaN() || weight < 0 || weight > MAX_WEIGHT_DISPLAY) return null
        if (reps < 1 || reps > MAX_REPS) return null
        return SetEditValues(unit.toKg(weight), reps)
    }

    /** A weight for a text field in the person's unit: "160", "62.5". */
    fun weightText(weightKg: Double, unit: WeightUnit): String {
        val v = unit.fromKg(weightKg)
        return if (Math.abs(v - Math.round(v)) < 0.05) Math.round(v).toString() else (Math.round(v * 10) / 10.0).toString()
    }
}
