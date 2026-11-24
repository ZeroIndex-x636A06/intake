package com.zeroindex.intake.domain.training

import com.zeroindex.intake.domain.units.WeightText
import com.zeroindex.intake.domain.units.WeightUnit

/** The words for the records of one exercise, in the person's unit. Shared by the exercise sheet and the Training stats screen. */
object RecordText {

    data class Line(val title: String, val value: String, val detail: String? = null)

    /** Heaviest weight, best estimated 1RM, best session volume, and the bodyweight rep record: only the ones that exist. */
    fun lines(r: ExerciseRecords, unit: WeightUnit): List<Line> = buildList {
        r.heaviest?.let { add(Line(PrKind.HEAVIEST_WEIGHT.label, WeightText.format(it.weightKg, unit), "x ${it.reps}")) }
        r.bestEstimatedMax?.let {
            add(Line("Best estimated 1RM", WeightText.formatEstimate(it.estimatedKg, unit), "from ${WeightText.format(it.weightKg, unit)} x ${it.reps}"))
        }
        r.bestVolume?.let { add(Line(PrKind.BEST_VOLUME.label, "${TrainText.thousands(unit.fromKg(it.volumeKg))} ${unit.label}", "${it.sets} sets")) }
        r.bodyweightReps?.let { add(Line("Most reps, no added weight", "$it reps")) }
    }

    /** "100 lb x 5" per weight lifted, heaviest first, for the "most reps at a weight" chips. Bodyweight reads "Bodyweight x 12". */
    fun repsAtWeight(r: ExerciseRecords, unit: WeightUnit): List<String> =
        r.repRecords.sortedByDescending { it.weightKg }.map {
            val w = if (it.weightKg == 0.0) "Bodyweight" else WeightText.format(it.weightKg, unit)
            "$w x ${it.reps}"
        }
}
