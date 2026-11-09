package com.zeroindex.intake.data.db.projection

import com.zeroindex.intake.domain.model.MealSlot

/** Summed macros. Nullable because SUM over no rows is NULL. */
data class MacroTotals(
    val kcal: Double?,
    val protein: Double?,
    val carbs: Double?,
    val fat: Double?,
) {
    val kcalOrZero: Double get() = kcal ?: 0.0
    val proteinOrZero: Double get() = protein ?: 0.0
    val carbsOrZero: Double get() = carbs ?: 0.0
    val fatOrZero: Double get() = fat ?: 0.0
}

/** Per-slot totals for the four meal cards on the Today screen (SPEC §5). */
data class SlotTotals(
    val mealSlot: MealSlot,
    val kcal: Double?,
    val protein: Double?,
)

/** One day's logged calories, for the adherence chart in SPEC §11. */
data class DailyIntake(
    val dayStartMillis: Long,
    val kcal: Double?,
)

/** Weekly working-set count per muscle, for the SPEC §10 under-10-sets flag. */
data class MuscleSetCount(
    val primaryMuscle: String,
    val setCount: Int,
)

/** One point on the estimated-1RM chart (SPEC §10). */
data class SetPoint(
    val completedAt: Long,
    val weightKg: Double,
    val reps: Int,
)

/** One saved workout for the "My workouts" list: its program, its one day and how many exercises it has. */
data class WorkoutSummary(
    val programId: String,
    val name: String,
    val dayId: String?,
    val exerciseCount: Int,
    val updatedAt: Long,
)
