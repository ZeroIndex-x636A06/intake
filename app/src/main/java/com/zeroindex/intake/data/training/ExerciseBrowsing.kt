package com.zeroindex.intake.data.training

import com.zeroindex.intake.data.db.entity.ExerciseEntity
import com.zeroindex.intake.data.db.entity.SetLogEntity
import com.zeroindex.intake.domain.training.OneRepMax
import com.zeroindex.intake.domain.training.generator.ExerciseCatalog

/** Search and filter for the exercise library and the picker. Pure, so it is easy to test. */
object ExerciseFilter {

    fun apply(
        exercises: List<ExerciseEntity>,
        query: String,
        muscle: String?,
        equipment: String?,
    ): List<ExerciseEntity> {
        val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return exercises.filter { e ->
            (muscle == null || e.primaryMuscle.equals(muscle, true) || e.secondaryMuscles.split(',').any { it.trim().equals(muscle, true) }) &&
                (equipment == null || e.equipment.equals(equipment, true)) &&
                words.all { w ->
                    e.name.lowercase().contains(w) || e.primaryMuscle.lowercase().contains(w) || e.equipment.lowercase().contains(w)
                }
        }.sortedBy { it.name.lowercase() }
    }

    /** True for an exercise the person added; built-in ones come from the catalog. */
    fun isCustom(exercise: ExerciseEntity): Boolean = ExerciseCatalog.byId(exercise.id) == null

    /** The distinct equipment values in a list, for the filter chips. */
    fun equipmentOf(exercises: List<ExerciseEntity>): List<String> =
        exercises.map { it.equipment.trim().lowercase() }.filter { it.isNotEmpty() }.distinct().sorted()
}

/** One day of an exercise's history. */
data class HistoryDay(
    val startedAt: Long,
    /** Working sets that day, heaviest first is not assumed: they are in the order lifted. */
    val sets: List<SetLogEntity>,
)

data class ExerciseHistory(
    val days: List<HistoryDay>,
    /** Best estimated one-rep max in kg, or null with no weighted sets. */
    val bestEstimatedMaxKg: Double?,
    val heaviestKg: Double?,
    val totalSets: Int,
)

object ExerciseHistoryCalc {
    private const val DAY_MS = 86_400_000L

    /** Groups working sets by calendar day (UTC buckets are fine for a summary), newest first. */
    fun summarize(sets: List<SetLogEntity>, zoneOffsetMillis: Long = 0L): ExerciseHistory {
        val working = sets.filter { !it.isWarmup && it.deletedAt == null }
        val days = working
            .groupBy { (it.completedAt + zoneOffsetMillis).floorDiv(DAY_MS) }
            .entries.sortedByDescending { it.key }
            .map { (_, list) -> HistoryDay(list.minOf { it.completedAt }, list.sortedBy { it.setIndex }) }
        val weighted = working.filter { it.weightKg > 0 && it.reps >= 1 }
        return ExerciseHistory(
            days = days,
            bestEstimatedMaxKg = weighted.maxOfOrNull { OneRepMax.epley(it.weightKg, it.reps) },
            heaviestKg = weighted.maxOfOrNull { it.weightKg },
            totalSets = working.size,
        )
    }
}
