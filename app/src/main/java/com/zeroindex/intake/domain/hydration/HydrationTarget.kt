package com.zeroindex.intake.domain.hydration

import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Amendment §3: a daily water target that moves with the day rather than one
 * fixed number.
 *
 * - The base is either a number the user set, or 35 ml per kg of bodyweight -
 *   the middle of the commonly cited 30-40 ml/kg range for active adults -
 *   falling back to 2,500 ml before any weigh-in exists.
 * - A training day adds a flat amount the user sets, [TRAINING_DAY_BONUS_ML]
 *   by default.
 * - Measured activity adds 1 ml per net active kcal (the usual sweat-loss rule
 *   of thumb), capped at [ACTIVITY_CAP_ML] so one long hike does not produce
 *   an absurd target. "Net" means BMR already subtracted (amendment §2), so the
 *   resting burn is not counted twice here either.
 *
 * Every adjustment comes back as a [Reason], so the card can say why today's
 * number is what it is.
 */
object HydrationTarget {

    const val ML_PER_KG = 35.0
    const val FALLBACK_BASE_ML = 2_500.0
    const val TRAINING_DAY_BONUS_ML = 500.0
    const val ACTIVITY_CAP_ML = 1_000.0
    const val ML_PER_ACTIVE_KCAL = 1.0

    data class Reason(val label: String, val ml: Double)

    data class Target(val totalMl: Double, val baseMl: Double, val reasons: List<Reason>)

    fun compute(
        manualBaseMl: Double?,
        bodyweightKg: Double?,
        dynamic: Boolean,
        trainedToday: Boolean,
        activeKcalNet: Double?,
        trainingDayBonusMl: Double = TRAINING_DAY_BONUS_ML,
    ): Target {
        val base = roundTo50(
            manualBaseMl?.takeIf { it > 0 }
                ?: bodyweightKg?.takeIf { it > 0 }?.let { it * ML_PER_KG }
                ?: FALLBACK_BASE_ML,
        )
        if (!dynamic) return Target(base, base, emptyList())

        val reasons = buildList {
            if (trainedToday && trainingDayBonusMl > 0) add(Reason("training day", roundTo50(trainingDayBonusMl)))
            val activity = activeKcalNet?.takeIf { it > 0 }?.let {
                roundTo50(min(it * ML_PER_ACTIVE_KCAL, ACTIVITY_CAP_ML))
            }
            if (activity != null && activity > 0) add(Reason("active calories", activity))
        }
        return Target(base + reasons.sumOf { it.ml }, base, reasons)
    }

    /** Targets read better as round numbers; 50 ml is below any glass size. */
    internal fun roundTo50(ml: Double): Double = (ml / 50.0).roundToInt() * 50.0
}
