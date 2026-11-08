package com.zeroindex.intake.domain.tdee

import com.zeroindex.intake.domain.model.TdeeConfidence
import com.zeroindex.intake.domain.nutrition.MacroSplit
import com.zeroindex.intake.domain.nutrition.MacroTarget
import com.zeroindex.intake.domain.units.UnitConverter
import kotlin.math.abs
import kotlin.math.roundToInt

/** One day's logged intake, by local day index. */
data class IntakeDay(
    val dayIndex: Long,
    val kcal: Double,
)

/** What the nightly job writes and the Progress tab reads. */
data class TdeeResult(
    val estimatedTdee: Double,
    val confidence: TdeeConfidence,
    val rollingIntakeAvg: Double,
    /** Kilograms per day; negative while losing. */
    val weightTrendSlopeKgPerDay: Double,
    val daysOfData: Int,
    val missingIntakeDays: Int,
)

/**
 * SPEC §8: maintenance is measured from the user's own data, not a formula.
 *
 *     TDEE = mean daily intake - (weight trend slope in kg/day x 7700 kcal/kg)
 *
 * Harris-Benedict and Mifflin-St Jeor are day-one seeds only, which is what
 * [seedEstimate] is for. When the data is too sparse to mean anything this
 * returns null rather than a confident-looking number, because a wrong TDEE
 * presented confidently is worse than no TDEE.
 */
object TdeeCalculator {

    /** SPEC §8: fewer than 14 days of overlap is not enough to measure anything. */
    const val MIN_DAYS = 14
    const val MEDIUM_MAX_DAYS = 28
    const val HIGH_MAX_MISSING = 3

    /** SPEC §8: recalculate nightly over a 28-day rolling window. */
    const val WINDOW_DAYS = 28

    /** SPEC §8: notify when the derived target shifts by more than this. */
    const val TARGET_SHIFT_NOTIFY_KCAL = 100

    /**
     * Returns null when fewer than [MIN_DAYS] days carry both a weigh-in and an
     * intake log, or when the weight series has no slope.
     */
    fun estimate(
        weights: List<WeightPoint>,
        intake: List<IntakeDay>,
        alpha: Double = WeightTrend.DEFAULT_ALPHA,
    ): TdeeResult? {
        val trend = WeightTrend.smooth(weights, alpha)
        if (trend.size < 2) return null

        val intakeByDay = intake.filter { it.kcal > 0 }.associate { it.dayIndex to it.kcal }
        if (intakeByDay.isEmpty()) return null

        // Only days the two series share can say anything about maintenance.
        val weightDays = trend.map { it.dayIndex }.toSet()
        val sharedDays = weightDays intersect intakeByDay.keys
        if (sharedDays.size < MIN_DAYS) return null

        val slope = WeightTrend.slopeKgPerDay(trend) ?: return null

        // SPEC §8: weight recent days more heavily. A linear ramp over the
        // window is enough - the point is that last week counts for more than
        // three weeks ago, not the exact curve.
        val orderedDays = sharedDays.sorted()
        val oldest = orderedDays.first()
        val newest = orderedDays.last()
        val span = (newest - oldest).coerceAtLeast(1L).toDouble()
        var weightedSum = 0.0
        var weightTotal = 0.0
        orderedDays.forEach { day ->
            val recency = 0.5 + 0.5 * ((day - oldest) / span)
            weightedSum += intakeByDay.getValue(day) * recency
            weightTotal += recency
        }
        val rollingIntakeAvg = weightedSum / weightTotal

        val tdee = rollingIntakeAvg - slope * UnitConverter.KCAL_PER_KG

        val spanDays = (newest - oldest + 1).toInt()
        val missing = (spanDays - sharedDays.size).coerceAtLeast(0)

        return TdeeResult(
            estimatedTdee = tdee,
            confidence = confidenceFor(sharedDays.size, missing),
            rollingIntakeAvg = rollingIntakeAvg,
            weightTrendSlopeKgPerDay = slope,
            daysOfData = sharedDays.size,
            missingIntakeDays = missing,
        )
    }

    /**
     * SPEC §8: fewer than 14 days is LOW, 14 to 28 is MEDIUM, more than 28 with
     * fewer than 3 missing logs is HIGH.
     */
    fun confidenceFor(daysOfData: Int, missingDays: Int): TdeeConfidence = when {
        daysOfData < MIN_DAYS -> TdeeConfidence.LOW
        daysOfData <= MEDIUM_MAX_DAYS -> TdeeConfidence.MEDIUM
        missingDays < HIGH_MAX_MISSING -> TdeeConfidence.HIGH
        else -> TdeeConfidence.MEDIUM
    }

    /**
     * Mifflin-St Jeor, used only until there is enough real data (SPEC §8).
     * Deliberately separate from [estimate] so a seed can never be mistaken for
     * a measurement.
     */
    fun seedEstimate(
        weightKg: Double,
        heightCm: Double,
        ageYears: Int,
        isMale: Boolean,
        activityFactor: Double = 1.375,
    ): Double {
        val bmr = 10 * weightKg + 6.25 * heightCm - 5 * ageYears + if (isMale) 5 else -161
        return bmr * activityFactor
    }

    /**
     * SPEC §8: derive the calorie target from measured TDEE and a rate goal,
     * never from a formula. [rateKgPerWeek] is negative to lose.
     */
    fun targetForRate(
        tdee: Double,
        rateKgPerWeek: Double,
        goalWeightKg: Double,
        proteinPerKg: Double = MacroSplit.PROTEIN_G_PER_KG_DEFAULT,
        fatPerKg: Double = MacroSplit.FAT_G_PER_KG_MIN,
    ): MacroTarget {
        val dailyAdjustment = rateKgPerWeek * UnitConverter.KCAL_PER_KG / 7.0
        // A floor keeps an aggressive rate from producing a nonsense target.
        val kcal = (tdee + dailyAdjustment).roundToInt().coerceAtLeast(MIN_SAFE_KCAL)
        return MacroSplit.forKcal(
            kcal = kcal,
            goalWeightKg = goalWeightKg,
            proteinPerKg = proteinPerKg,
            fatPerKg = fatPerKg,
        )
    }

    /** True when the target moved enough to be worth telling the user (SPEC §8). */
    fun shouldNotifyShift(previousKcal: Int, newKcal: Int): Boolean =
        abs(newKcal - previousKcal) > TARGET_SHIFT_NOTIFY_KCAL

    /** Not a medical limit, just a guard against a silly rate goal. */
    const val MIN_SAFE_KCAL = 1200
}
