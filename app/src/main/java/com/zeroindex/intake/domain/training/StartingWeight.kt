package com.zeroindex.intake.domain.training

/**
 * The first prescription for a lift that has no history yet, when the program
 * (usually a program script's `weight:` line) names a starting weight. Once a
 * session exists the progression rule takes over and this is never used.
 */
object StartingWeight {
    const val REASON = "Starting weight from your program"

    fun prescription(startWeightKg: Double?, target: ExerciseTarget): Prescription? {
        val kg = startWeightKg?.takeIf { it > 0.0 } ?: return null
        return Prescription(
            weightKg = kg,
            sets = target.sets,
            repsMin = target.repsMin,
            repsMax = target.repsMax,
            targetRpe = target.targetRpe,
            reason = "$REASON: ${ProgressionEvaluator.fmt(kg)} kg. It moves on from your first session.",
        )
    }
}
