package com.zeroindex.intake.wear

import com.zeroindex.intake.data.db.entity.SetLogEntity
import com.zeroindex.intake.data.repository.ExercisePrescription
import com.zeroindex.intake.wearprotocol.ActiveWorkout
import com.zeroindex.intake.wearprotocol.WatchExercise
import com.zeroindex.intake.wearprotocol.WatchSet

/**
 * What the watch shows for the session in progress: every exercise on the
 * program day with its prescribed sets, the ones already logged marked done
 * with what was actually lifted. Warm-ups are left off - the watch is for
 * working sets.
 */
object WatchWorkoutMapper {

    fun build(
        sessionId: String,
        title: String,
        prescriptions: List<ExercisePrescription>,
        logged: List<SetLogEntity>,
        weightUnit: String,
        now: Long,
    ): ActiveWorkout = ActiveWorkout(
        sessionId = sessionId,
        title = title,
        weightUnit = weightUnit,
        updatedAt = now,
        exercises = prescriptions.map { p ->
            val pe = p.programExercise
            val done = logged.filter { it.exerciseId == p.exercise.id && !it.isWarmup && it.deletedAt == null }.associateBy { it.setIndex }
            val planned = p.prescription?.sets ?: pe?.targetSets ?: 3
            val weight = p.prescription?.weightKg ?: p.lastSession.maxOfOrNull { it.weightKg } ?: 0.0
            val reps = p.prescription?.repsMin ?: pe?.targetRepsMin ?: 5
            // setIndex is 0-based, as on the phone's set grid.
            val count = maxOf(planned, (done.keys.maxOrNull() ?: -1) + 1)
            WatchExercise(
                exerciseId = p.exercise.id,
                name = p.exercise.name,
                restSec = pe?.restSec ?: p.exercise.defaultRestSec,
                sets = (0 until count).map { i ->
                    done[i]?.let { WatchSet(i, it.weightKg, it.reps, done = true, rpe = it.rpe) } ?: WatchSet(i, weight, reps)
                },
            )
        },
    )
}
