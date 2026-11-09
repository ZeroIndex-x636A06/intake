package com.zeroindex.intake.domain.hydration

import kotlin.math.max
import kotlin.math.min

/** One row of the target breakdown: what it adds, a short label for the card, and a plain "why". */
data class TargetLine(
    val id: Id,
    val title: String,
    val ml: Double,
    /** The short text on the card, e.g. "33 °C today". */
    val short: String,
    val why: String,
) {
    enum class Id { BASE, TRAINING, SWEAT, ACTIVITY, WEATHER, CAFFEINE, ALCOHOL }
}

/** The day's target, in full: base plus each adjustment. [totalMl] is the sum of every line. */
data class TargetBreakdown(val base: TargetLine, val adjustments: List<TargetLine>) {
    val baseMl: Double get() = base.ml
    val totalMl: Double get() = base.ml + adjustments.sumOf { it.ml }

    /** The shape the older callers (the card, the daily insight) already use. */
    fun toTarget(): HydrationTarget.Target =
        HydrationTarget.Target(totalMl, baseMl, adjustments.map { HydrationTarget.Reason(it.short, it.ml) })
}

/**
 * The whole target: base + training or sweat + weather + caffeine and alcohol top-ups.
 * Each piece is documented where it is defined ([HydrationTarget], [SweatLoss], [WeatherAdjustment],
 * [CaffeineAlcohol]) and written up in docs/HYDRATION.md.
 *
 * Rules that matter:
 * - With "adjust the target for the day" off, the target is the base alone.
 * - A logged workout (5 minutes or more) replaces the flat training-day bonus with minutes x rate.
 *   The flat bonus is only the default for a day known to be a training day with no session timed yet.
 * - Measured active calories only add what is beyond the sweat already counted, so one workout is not
 *   counted twice.
 * - No weight means the 2,500 ml fallback base; no weather means no weather line.
 */
object HydrationPlan {

    fun compose(
        manualBaseMl: Double?,
        bodyweightKg: Double?,
        dynamic: Boolean,
        plannedTrainingDay: Boolean = false,
        trainingDayBonusMl: Double = HydrationTarget.TRAINING_DAY_BONUS_ML,
        sessions: List<SessionLoad> = emptyList(),
        measuredSweatLitresPerHour: Double? = null,
        activeKcalNet: Double? = null,
        weather: WeatherDay? = null,
        drinks: DayDrinkTotals = DayDrinkTotals.EMPTY,
    ): TargetBreakdown {
        val baseMl = HydrationTarget.baseMl(manualBaseMl, bodyweightKg)
        val base = TargetLine(
            TargetLine.Id.BASE,
            "Base",
            baseMl,
            "base",
            when {
                manualBaseMl != null && manualBaseMl > 0 -> "The daily amount you set yourself."
                bodyweightKg != null && bodyweightKg > 0 ->
                    "${HydrationTarget.ML_PER_KG.toInt()} ml per kg of your latest weight, a common middle of the 30 to 40 ml/kg range."
                else -> "A default of 2,500 ml until you log a weight."
            },
        )
        if (!dynamic) return TargetBreakdown(base, emptyList())

        val lines = mutableListOf<TargetLine>()

        val sweatMl = HydrationTarget.roundTo50(SweatLoss.totalMl(sessions, measuredSweatLitresPerHour))
        if (sweatMl > 0) {
            lines += TargetLine(
                TargetLine.Id.SWEAT,
                "Workout sweat",
                sweatMl,
                "workout",
                sweatWhy(sessions, measuredSweatLitresPerHour),
            )
        } else if (plannedTrainingDay && trainingDayBonusMl > 0) {
            lines += TargetLine(
                TargetLine.Id.TRAINING,
                "Training day",
                HydrationTarget.roundTo50(trainingDayBonusMl),
                "training day",
                "A flat amount for a training day. Once a workout is logged with a time, its sweat estimate replaces this.",
            )
        }

        activeKcalNet?.takeIf { it > 0 }?.let {
            val activity = HydrationTarget.roundTo50(
                min(it * HydrationTarget.ML_PER_ACTIVE_KCAL, HydrationTarget.ACTIVITY_CAP_ML),
            )
            val beyondWorkout = max(0.0, activity - sweatMl)
            if (beyondWorkout > 0) {
                lines += TargetLine(
                    TargetLine.Id.ACTIVITY,
                    "Other activity",
                    beyondWorkout,
                    "active calories",
                    "About 1 ml per active calorie your watch or phone measured, beyond the workout already counted.",
                )
            }
        }

        weather?.let { w ->
            val ml = WeatherAdjustment.adjustmentMl(w)
            if (ml > 0) {
                val humid = if (WeatherAdjustment.isHumid(w)) " and humid (${w.humidityPct?.toInt()}%)" else ""
                lines += TargetLine(
                    TargetLine.Id.WEATHER,
                    "Weather",
                    ml,
                    WeatherAdjustment.tempLabel(w),
                    "Today's high is ${w.tempMaxC.toInt()} °C$humid, so you are likely to sweat more. Cool days add nothing.",
                )
            }
        }

        val caffeineMl = CaffeineAlcohol.caffeineTopUpMl(drinks.caffeineMg)
        if (caffeineMl > 0) {
            lines += TargetLine(
                TargetLine.Id.CAFFEINE,
                "Caffeine",
                caffeineMl,
                "caffeine",
                "${CaffeineAlcohol.CAFFEINE_TOPUP_WHY} About ${drinks.caffeineMg.toInt()} mg so far today. " +
                    "Tea and coffee still count toward your water.",
            )
        }
        val alcoholMl = HydrationTarget.roundTo50(CaffeineAlcohol.alcoholTopUpMl(drinks.standardDrinks))
        if (alcoholMl > 0) {
            lines += TargetLine(
                TargetLine.Id.ALCOHOL,
                "Alcohol",
                alcoholMl,
                "alcohol",
                CaffeineAlcohol.ALCOHOL_TOPUP_WHY,
            )
        }
        return TargetBreakdown(base, lines)
    }

    private fun sweatWhy(sessions: List<SessionLoad>, measured: Double?): String {
        val counted = sessions.filter { it.minutes >= SweatLoss.MIN_SESSION_MINUTES }
        val minutes = SweatLoss.totalMinutes(sessions).toInt()
        return if (measured != null) {
            "$minutes min at your measured sweat rate of ${"%.2f".format(measured)} L/h."
        } else {
            val rates = counted.map { SweatLoss.intensityFor(it.avgRpe) }.distinct()
            val detail = rates.joinToString(" and ") { "${it.label} (${it.litresPerHour} L/h)" }
            "$minutes min of training at a $detail rate. Replaces the flat training-day amount."
        }
    }
}
