package com.zeroindex.intake.domain.coach

import com.zeroindex.intake.domain.units.VolumeUnit
import kotlin.math.roundToInt

/** Where the Today card's one button goes. */
enum class FocusTarget { CHECK_IN, LOG_WEIGHT, LOG_FOOD, WORKOUT, WATER, BASELINE, NONE }

data class DailyFocus(
    val title: String,
    val line: String,
    val buttonLabel: String?,
    val target: FocusTarget,
    /** True for the encouraging fallback, which the card shows more quietly. */
    val onTrack: Boolean = false,
)

data class FocusInputs(
    val pendingSuggestions: Int,
    /** A weigh-in is normal today and none is logged yet. */
    val weighInMissing: Boolean,
    val hourOfDay: Int,
    val proteinSoFarG: Double,
    val proteinTargetG: Int,
    val workoutScheduledToday: Boolean,
    val workoutName: String? = null,
    val workoutDoneToday: Boolean = false,
    val waterSoFarMl: Double = 0.0,
    val waterTargetMl: Double = 0.0,
    /** The unit the water line is written in; the amounts stay in millilitres. */
    val volumeUnit: VolumeUnit = VolumeUnit.MILLILITRES,
    val baselineMissing: String? = null,
    /** Consecutive days with food logged, counting today if it has some. */
    val loggingStreakDays: Int = 0,
    val daysLoggedThisWeek: Int = 0,
    val workoutsThisWeek: Int = 0,
)

/**
 * "One thing to do today" (SPEC-v3). Exactly one item, chosen by this fixed order, no AI:
 * pending check-in, missing weigh-in, protein far behind in the evening, today's workout,
 * water behind for the time of day, baseline items still missing, else an "on track" line.
 */
object DailyFocusPicker {

    const val EVENING_HOUR = 18
    const val PROTEIN_BEHIND_SHARE = 0.6
    const val WATER_FIRST_HOUR = 11
    const val WATER_GAP = 0.25
    private const val WAKE = 7
    private const val SLEEP = 22

    /** The share of the day's water that should be done by [hour]: 0 at 07:00 rising to 1 at 22:00. */
    fun expectedWaterShare(hour: Int): Double = ((hour - WAKE).toDouble() / (SLEEP - WAKE)).coerceIn(0.0, 1.0)

    fun pick(i: FocusInputs): DailyFocus {
        if (i.pendingSuggestions > 0) {
            return DailyFocus(
                "Your weekly check-in is ready",
                "${i.pendingSuggestions} suggestion${if (i.pendingSuggestions == 1) "" else "s"} waiting. Nothing changes until you tap Apply.",
                "Open check-in", FocusTarget.CHECK_IN,
            )
        }
        if (i.weighInMissing) {
            return DailyFocus(
                "Weigh in today",
                "You usually weigh in about now. One reading a day keeps your trend honest.",
                "Log weight", FocusTarget.LOG_WEIGHT,
            )
        }
        if (i.proteinTargetG > 0 && i.hourOfDay >= EVENING_HOUR && i.proteinSoFarG < PROTEIN_BEHIND_SHARE * i.proteinTargetG) {
            val left = (i.proteinTargetG - i.proteinSoFarG).roundToInt()
            return DailyFocus(
                "Protein is behind",
                "${i.proteinSoFarG.roundToInt()} of ${i.proteinTargetG} g so far. A high-protein dinner or snack covers the last $left g.",
                "Log food", FocusTarget.LOG_FOOD,
            )
        }
        if (i.workoutScheduledToday && !i.workoutDoneToday) {
            return DailyFocus(
                "Today's workout",
                i.workoutName?.let { "$it is on your plan today." } ?: "A session is on your plan today.",
                "Open workout", FocusTarget.WORKOUT,
            )
        }
        if (i.waterTargetMl > 0 && i.hourOfDay >= WATER_FIRST_HOUR) {
            val share = i.waterSoFarMl / i.waterTargetMl
            val expected = expectedWaterShare(i.hourOfDay)
            if (share < expected - WATER_GAP) {
                return DailyFocus(
                    "Drink some water",
                    "${i.volumeUnit.formatNumber(i.waterSoFarMl)} of ${i.volumeUnit.format(i.waterTargetMl)} so far, a little behind for this time of day.",
                    "Log water", FocusTarget.WATER,
                )
            }
        }
        i.baselineMissing?.let {
            return DailyFocus("Finish your baseline week", "Still needed: $it", "See my baseline", FocusTarget.BASELINE)
        }
        val line = when {
            i.loggingStreakDays >= 3 -> "${i.loggingStreakDays}-day logging streak. Keep it going."
            i.daysLoggedThisWeek >= 1 -> "${i.daysLoggedThisWeek} of 7 days logged this week."
            i.workoutsThisWeek >= 1 -> "${i.workoutsThisWeek} workout${if (i.workoutsThisWeek == 1) "" else "s"} done this week."
            else -> null
        }
        return if (line != null) {
            DailyFocus("You're on track", line, null, FocusTarget.NONE, onTrack = true)
        } else {
            DailyFocus("Start with one meal", "Log your first meal today and Intake can start working for you.", "Log food", FocusTarget.LOG_FOOD)
        }
    }

    /**
     * A weigh-in is expected today when the person weighs in most days, or has weighed in on
     * this weekday in at least two of the last four weeks, or is in a baseline week that needs them.
     */
    fun weighInExpected(
        today: Long,
        weighInDays: Collection<Long>,
        baselineNeedsWeighIns: Boolean = false,
    ): Boolean {
        val days = weighInDays.toSet()
        if (today in days) return false
        if (baselineNeedsWeighIns) return true
        val lastWeek = (today - 7 until today).count { it in days }
        if (lastWeek >= 4) return true
        val sameWeekday = (1..4).count { (today - 7L * it) in days }
        return sameWeekday >= 2
    }

    /** Consecutive logged days ending today, or yesterday when today is still empty. */
    fun streak(today: Long, loggedDays: Set<Long>): Int {
        var day = if (today in loggedDays) today else today - 1
        var n = 0
        while (day in loggedDays) {
            n++
            day--
        }
        return n
    }
}
