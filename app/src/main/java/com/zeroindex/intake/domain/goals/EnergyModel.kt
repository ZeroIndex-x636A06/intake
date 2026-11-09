package com.zeroindex.intake.domain.goals

import com.zeroindex.intake.domain.model.TdeeConfidence
import com.zeroindex.intake.domain.tdee.WeightPoint
import com.zeroindex.intake.domain.tdee.WeightTrend
import com.zeroindex.intake.domain.units.UnitConverter
import java.util.Locale
import kotlin.math.roundToInt

/** "2,350" with a thousands separator, the same in every locale so strings are deterministic. */
internal fun Int.grouped(): String = String.format(Locale.US, "%,d", this)

internal fun fmt1(value: Double): String = String.format(Locale.US, "%.1f", value)

enum class BmrFormula(val label: String) {
    MIFFLIN_ST_JEOR("Mifflin-St Jeor"),
    KATCH_MCARDLE("Katch-McArdle"),
}

/** Calories at rest, with the formula that produced them. */
data class Bmr(val kcal: Double, val formula: BmrFormula, val why: String)

/** The multiplier from "calories at rest" to "daily calorie burn", and where it came from. */
data class ActivityFactor(val value: Double, val why: String)

/** Which number the plan treats as the person's daily calorie burn. */
enum class TdeeSource(val label: String) {
    ESTIMATE("estimated"),
    BLENDED("part measured"),
    MEASURED("measured"),
}

/**
 * Day-one energy maths (SPEC-v3 "Engines"). Plain names for the screen: BMR is "Calories at
 * rest", TDEE is "Daily calorie burn".
 */
object EnergyModel {

    /** Katch-McArdle only makes sense for body fat inside a believable range. */
    private val BODY_FAT_RANGE = 3.0..60.0

    /**
     * Mifflin-St Jeor (1990): 10 x kg + 6.25 x cm - 5 x age + 5 (male) or - 161 (female).
     * Katch-McArdle when body fat is known: 370 + 21.6 x lean kg.
     */
    fun bmr(weightKg: Double, heightCm: Double, ageYears: Int, sex: Sex, bodyFatPercent: Double?): Bmr {
        val bf = bodyFatPercent?.takeIf { it in BODY_FAT_RANGE }
        if (bf != null) {
            val lean = weightKg * (1 - bf / 100.0)
            val kcal = 370.0 + 21.6 * lean
            return Bmr(
                kcal = kcal,
                formula = BmrFormula.KATCH_MCARDLE,
                why = "${kcal.roundToInt().grouped()} kcal at rest from your lean mass (${fmt1(lean)} kg, " +
                    "${fmt1(bf)}% body fat) using Katch-McArdle.",
            )
        }
        val kcal = 10 * weightKg + 6.25 * heightCm - 5 * ageYears + if (sex == Sex.MALE) 5 else -161
        return Bmr(
            kcal = kcal,
            formula = BmrFormula.MIFFLIN_ST_JEOR,
            why = "${kcal.roundToInt().grouped()} kcal at rest from your weight, height, age and sex " +
                "using Mifflin-St Jeor.",
        )
    }

    /** Steps at or below this count as the lowest everyday movement. */
    const val STEPS_LOW = 3_000.0
    const val STEPS_HIGH = 14_000.0
    private const val STEP_FACTOR_LOW = 1.2
    private const val STEP_FACTOR_HIGH = 1.55

    /** Each weekly training day adds this much to the multiplier (6 days = +0.18). */
    const val TRAINING_DAY_FACTOR = 0.03

    /**
     * The multiplier on calories at rest.
     *
     * The base is everyday movement outside training: the chosen [level], or, once Health Connect
     * has steps, a straight line from 1.2 at 3,000 steps a day to 1.55 at 14,000. Training adds
     * [TRAINING_DAY_FACTOR] per weekly session on top. Steps and the training bonus are kept
     * apart on purpose, so lifting is never counted twice.
     */
    fun activityFactor(level: ActivityLevel, trainingDaysPerWeek: Int, averageSteps: Double?): ActivityFactor {
        val days = trainingDaysPerWeek.coerceIn(0, 7)
        val training = days * TRAINING_DAY_FACTOR
        val base: Double
        val baseWhy: String
        if (averageSteps != null && averageSteps > 0) {
            val t = ((averageSteps - STEPS_LOW) / (STEPS_HIGH - STEPS_LOW)).coerceIn(0.0, 1.0)
            base = STEP_FACTOR_LOW + t * (STEP_FACTOR_HIGH - STEP_FACTOR_LOW)
            baseWhy = "${averageSteps.roundToInt().grouped()} steps a day"
        } else {
            base = level.factor
            baseWhy = level.label.lowercase()
        }
        val value = base + training
        val trainingWhy = if (days == 0) "no training" else "$days training day${if (days == 1) "" else "s"} a week"
        return ActivityFactor(
            value = value,
            why = "x${String.format(Locale.US, "%.2f", value)}: $baseWhy plus $trainingWhy.",
        )
    }

    fun dayOneTdee(bmr: Double, factor: Double): Double = bmr * factor
}

/** A burn worked out from what the person actually ate and how their weight moved. */
data class MeasuredTdee(
    val tdee: Double,
    val averageIntake: Double,
    /** Kilograms per day from the weight trend; negative while losing. */
    val slopeKgPerDay: Double,
    val loggedDays: Int,
    val weighInDays: Int,
    val spanDays: Int,
) {
    /** The weekly change in kilograms, for the screen. */
    val kgPerWeek: Double get() = slopeKgPerDay * 7
}

/**
 * Measured TDEE from a baseline week, built on the same pieces as the nightly adaptive estimate
 * (WeightTrend and the 7700 kcal/kg constant), but gated for a week of data instead of 14 days.
 *
 *     burn = average logged intake - (trend weight change per day x 7700 kcal/kg)
 *
 * One week of weights is noisy and an exponential average lags a short series, so the baseline
 * uses a straight least-squares line through the daily weights (alpha 1.0 means "no smoothing
 * before the line", the line is the smoother). The ~7700 kcal/kg figure is an average, not a
 * constant: see docs/GOALS.md for the caveat.
 */
object MeasuredEnergy {

    /** Alpha 1.0 passes the daily-averaged weights straight to the least-squares slope. */
    const val BASELINE_ALPHA = 1.0

    /** The most a one-week baseline may count for in the blend, however clean it is. */
    const val MAX_BASELINE_TRUST = 0.8

    /** A measured burn outside this multiple of calories at rest is a logging mistake, not a metabolism. */
    private val PLAUSIBLE_OF_BMR = 1.0..3.0

    fun fromBaseline(
        weights: List<WeightPoint>,
        foodDays: List<BaselineReadiness.FoodDay>,
    ): MeasuredTdee? {
        val counted = foodDays.filter { it.counts }
        if (counted.isEmpty()) return null
        val trend = WeightTrend.smooth(weights, BASELINE_ALPHA)
        if (trend.size < 2) return null
        val slope = WeightTrend.slopeKgPerDay(trend) ?: return null
        val avg = counted.map { it.kcal }.average()
        return MeasuredTdee(
            tdee = avg - slope * UnitConverter.KCAL_PER_KG,
            averageIntake = avg,
            slopeKgPerDay = slope,
            loggedDays = counted.map { it.dayIndex }.distinct().size,
            weighInDays = trend.size,
            spanDays = (trend.last().dayIndex - trend.first().dayIndex + 1).toInt(),
        )
    }

    /**
     * How much to lean on the measurement, 0 to [MAX_BASELINE_TRUST]. The weakest of three links
     * sets it: days of food (of 7), days with a weigh-in (of 5) and the days between the first
     * and last weigh-in (of 6).
     */
    fun trust(measured: MeasuredTdee): Double {
        val food = (measured.loggedDays / 7.0).coerceAtMost(1.0)
        val weigh = (measured.weighInDays / BaselineReadiness.RECOMMENDED_WEIGH_IN_DAYS.toDouble()).coerceAtMost(1.0)
        val span = (measured.spanDays / 6.0).coerceAtMost(1.0)
        return minOf(food, weigh, span) * MAX_BASELINE_TRUST
    }

    fun isPlausible(measured: MeasuredTdee, bmrKcal: Double): Boolean =
        measured.tdee in (bmrKcal * PLAUSIBLE_OF_BMR.start)..(bmrKcal * PLAUSIBLE_OF_BMR.endInclusive)

    /** The nightly adaptive estimate, passed in when the app already has one. */
    data class Adaptive(val tdee: Double, val confidence: TdeeConfidence)

    data class Blend(
        val tdee: Double,
        val source: TdeeSource,
        /** 0..1 share given to the measured number. */
        val measuredShare: Double,
        val why: String,
    )

    /**
     * Blends the formula estimate toward the best measurement available.
     *
     * - A MEDIUM or HIGH adaptive estimate (14+ days) outranks the baseline: HIGH counts fully,
     *   MEDIUM at 0.85.
     * - Otherwise the baseline counts at [trust], if it is plausible.
     * - Otherwise the formula stands alone, and the "why" says so.
     */
    fun blend(
        estimate: Double,
        bmrKcal: Double,
        measured: MeasuredTdee?,
        adaptive: Adaptive?,
    ): Blend {
        val estimateText = estimate.roundToInt().grouped()
        if (adaptive != null && adaptive.confidence != TdeeConfidence.LOW) {
            val share = if (adaptive.confidence == TdeeConfidence.HIGH) 1.0 else 0.85
            val tdee = share * adaptive.tdee + (1 - share) * estimate
            return Blend(
                tdee = tdee,
                source = if (share >= 1.0) TdeeSource.MEASURED else TdeeSource.BLENDED,
                measuredShare = share,
                why = "Your measured burn is ${adaptive.tdee.roundToInt().grouped()} kcal from weeks of " +
                    "your own food log and weight trend" +
                    if (share < 1.0) {
                        ", blended ${(share * 100).roundToInt()}% with the formula estimate of $estimateText."
                    } else {
                        "."
                    },
            )
        }
        if (measured != null && isPlausible(measured, bmrKcal)) {
            val share = trust(measured)
            if (share > 0.0) {
                val tdee = share * measured.tdee + (1 - share) * estimate
                return Blend(
                    tdee = tdee,
                    source = TdeeSource.BLENDED,
                    measuredShare = share,
                    why = "Your baseline week says you burn ${measured.tdee.roundToInt().grouped()} kcal: you ate " +
                        "${measured.averageIntake.roundToInt().grouped()} a day and your weight moved " +
                        "${fmt1(measured.kgPerWeek)} kg a week. That is blended ${(share * 100).roundToInt()}% with " +
                        "the formula estimate of $estimateText, because a week is short.",
                )
            }
        }
        val reason = when {
            measured == null -> "There is no baseline yet, so this is a formula estimate."
            !isPlausible(measured, bmrKcal) ->
                "Your baseline looked off (${measured.tdee.roundToInt().grouped()} kcal), so it was ignored and the " +
                    "formula estimate is used. Missed logging is the usual cause."
            else -> "There is too little baseline data to trust, so this is a formula estimate."
        }
        return Blend(estimate, TdeeSource.ESTIMATE, 0.0, "$reason Your burn is about $estimateText kcal.")
    }
}
