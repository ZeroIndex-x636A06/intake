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
    /** The YouTube video this workout was made from, if any. */
    val videoId: String? = null,
)

/** One logged set with its exercise's name and muscles, for the weekly volume and the post-workout summary. */
data class SetWithExercise(
    val exerciseId: String,
    val exerciseName: String,
    val primaryMuscle: String,
    val secondaryMuscles: String,
    val weightKg: Double,
    val reps: Int,
    val completedAt: Long,
    val isWarmup: Boolean,
)

/** A program day's name and its program's, for titling a session in the Training log. Soft-deleted rows are included on purpose. */
data class DayLabel(val dayId: String, val dayName: String, val programName: String, val standalone: Boolean)

/** One finished session with its totals, for the recent sessions list on Train. */
data class SessionStat(
    val sessionId: String,
    val startedAt: Long,
    val finishedAt: Long?,
    val dayName: String?,
    val volumeKg: Double,
    val workingSets: Int,
)

/** A logged row with no food behind it (a recipe serving, a quick add, a template): its name and the latest time. */
data class RecentPrepared(val name: String, val at: Long)
