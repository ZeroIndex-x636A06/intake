package com.zeroindex.intake.domain.goals

import com.zeroindex.intake.domain.units.UnitConverter
import com.zeroindex.intake.domain.units.WeightUnit
import kotlin.math.abs
import kotlin.math.roundToInt

enum class WarningLevel { INFO, WARN }

/** Safety rails are shown as plain warnings. Only the calorie floor and the under-18 rule change a number. */
data class PlanWarning(val level: WarningLevel, val text: String)

/** The daily calorie target for a phase, with the working shown. */
data class CalorieTarget(
    val kcal: Int,
    /** kcal minus burn; negative in a deficit. */
    val offsetKcal: Int,
    /** The offset as a percent of burn, signed. */
    val offsetPercent: Double,
    /** Expected body-weight change in kg per week from this offset (negative = losing). */
    val kgPerWeek: Double,
    /** The same change as a percent of body weight per week, signed. */
    val percentBodyWeightPerWeek: Double,
    val why: String,
    val warnings: List<PlanWarning>,
    /** Reverse diet only: kcal for each week, ending when it reaches the burn. */
    val reverseSchedule: List<Int> = emptyList(),
)

object PhasePlanner {

    /** Never go below calories at rest x this, unless the person overrides it in Advanced. */
    const val FLOOR_OF_BMR = 1.1

    /** A loss faster than this share of body weight a week gets a warning. */
    const val FAST_LOSS_PERCENT = 1.0

    const val ADULT_AGE = 18

    const val CONTEST_MIN_PCT = 0.5
    const val CONTEST_MAX_PCT = 1.0
    const val REVERSE_MIN_STEP = 50
    const val REVERSE_MAX_STEP = 100

    /** The starting offset for each phase and pace, as a fraction of daily burn (negative = deficit). */
    fun offsetFraction(phase: Phase, pace: Pace): Double = when (phase) {
        Phase.CUT -> when (pace) { Pace.GENTLE -> -0.15; Pace.STEADY -> -0.20; Pace.FAST -> -0.25 }
        Phase.LEAN_BULK -> when (pace) { Pace.GENTLE -> 0.05; Pace.STEADY -> 0.075; Pace.FAST -> 0.10 }
        Phase.BULK -> when (pace) { Pace.GENTLE -> 0.10; Pace.STEADY -> 0.15; Pace.FAST -> 0.20 }
        Phase.RECOMP -> when (pace) { Pace.GENTLE -> -0.05; Pace.STEADY -> -0.075; Pace.FAST -> -0.10 }
        Phase.MAINTAIN, Phase.CONTEST_PREP, Phase.REVERSE_DIET -> 0.0
    }

    /**
     * @param tdee daily burn the plan is built on
     * @param bmr calories at rest
     * @param averageIntake what the person eats now; the reverse diet starts there
     */
    fun calories(
        phase: Phase,
        pace: Pace,
        tdee: Double,
        bmr: Double,
        weightKg: Double,
        ageYears: Int,
        contestWeeklyLossPct: Double,
        reverseStepKcal: Int,
        averageIntake: Double?,
        allowBelowFloor: Boolean,
        /** The person typed their own calories; the phase offset is skipped, the safety rails still apply. */
        manualKcal: Int? = null,
        /** "measured burn", "estimated burn" or just "burn": how the why line names the number. */
        burnLabel: String = "burn",
    ): CalorieTarget {
        val warnings = mutableListOf<PlanWarning>()
        val burn = tdee.roundToInt()
        var reverse: List<Int> = emptyList()

        var raw: Double = when {
            manualKcal != null -> manualKcal.toDouble()
            phase == Phase.CONTEST_PREP -> {
                val pct = contestWeeklyLossPct.coerceIn(CONTEST_MIN_PCT, CONTEST_MAX_PCT)
                tdee - weightKg * pct / 100.0 * UnitConverter.KCAL_PER_KG / 7.0
            }
            phase == Phase.REVERSE_DIET -> {
                val step = reverseStepKcal.coerceIn(REVERSE_MIN_STEP, REVERSE_MAX_STEP)
                val start = (averageIntake ?: (tdee * 0.85)).coerceIn(bmr * FLOOR_OF_BMR, tdee)
                reverse = reverseSchedule(start.roundToInt(), burn, step)
                start
            }
            else -> tdee * (1 + offsetFraction(phase, pace))
        }

        // Under 18: never plan a deficit, whatever the phase says.
        val minor = ageYears < ADULT_AGE
        if (minor && raw < tdee) {
            raw = tdee
            reverse = emptyList()
            warnings += PlanWarning(
                WarningLevel.WARN,
                "Under 18: Intake does not plan a calorie deficit for growing bodies. Your target is your " +
                    "daily burn. Talk to a doctor or a coach before trying to lose weight.",
            )
        }

        val floor = bmr * FLOOR_OF_BMR
        var kcal = raw.roundToInt()
        if (kcal < floor.roundToInt()) {
            if (allowBelowFloor) {
                warnings += PlanWarning(
                    WarningLevel.WARN,
                    "This is below ${floor.roundToInt().grouped()} kcal (1.1 x your calories at rest). You turned " +
                        "the floor off in Advanced, so it is kept. Eating this low for long costs muscle, " +
                        "energy and mood.",
                )
            } else {
                kcal = floor.roundToInt()
                warnings += PlanWarning(
                    WarningLevel.WARN,
                    "Raised to ${kcal.grouped()} kcal, which is 1.1 x your calories at rest. Going lower is only " +
                        "possible with the Advanced floor override.",
                )
            }
        }

        val offset = kcal - burn
        val pctOffset = if (burn > 0) offset * 100.0 / burn else 0.0
        val kgPerWeek = offset * 7.0 / UnitConverter.KCAL_PER_KG
        val pctBw = if (weightKg > 0) kgPerWeek / weightKg * 100.0 else 0.0

        if (-pctBw > FAST_LOSS_PERCENT) {
            warnings += PlanWarning(
                WarningLevel.WARN,
                "This loses about ${fmt1(-pctBw)}% of your body weight a week, which is faster than the " +
                    "usual 1% limit. Fast losses cost muscle. A slower pace is easier to keep.",
            )
        }

        return CalorieTarget(
            kcal = kcal,
            offsetKcal = offset,
            offsetPercent = pctOffset,
            kgPerWeek = kgPerWeek,
            percentBodyWeightPerWeek = pctBw,
            why = whyLine(phase, pace, kcal, burn, pctOffset, reverse, contestWeeklyLossPct, minor, manualKcal != null, burnLabel),
            warnings = warnings,
            reverseSchedule = reverse,
        )
    }

    /** Weekly kcal from [start] up to [burn] in steps of [step]; the last week is capped at the burn. */
    fun reverseSchedule(start: Int, burn: Int, step: Int): List<Int> {
        if (start >= burn) return listOf(burn)
        val weeks = mutableListOf<Int>()
        var kcal = start
        while (kcal < burn && weeks.size < 52) {
            weeks += kcal
            kcal += step
        }
        weeks += burn
        return weeks
    }

    private fun whyLine(
        phase: Phase,
        pace: Pace,
        kcal: Int,
        burn: Int,
        pct: Double,
        reverse: List<Int>,
        contestPct: Double,
        minor: Boolean,
        manual: Boolean,
        burnLabel: String,
    ): String {
        val sign = if (pct < 0) "−" else "+"
        val pctText = "${abs(pct).roundToInt()}%"
        return when {
            manual && !(minor && phase.losesWeight) ->
                "${kcal.grouped()} kcal is the number you chose, $pctText ${if (pct < 0) "below" else "above"} " +
                    "your daily burn of ${burn.grouped()}."
            minor && phase.losesWeight -> "${kcal.grouped()} kcal = your daily burn, because Intake does not plan a deficit under 18."
            phase == Phase.MAINTAIN -> "${kcal.grouped()} kcal = your daily burn ${burn.grouped()}, to hold your weight."
            phase == Phase.CONTEST_PREP ->
                "${kcal.grouped()} kcal = your $burnLabel ${burn.grouped()} $sign $pctText, aiming at " +
                    "${fmt1(contestPct.coerceIn(CONTEST_MIN_PCT, CONTEST_MAX_PCT))}% of body weight lost a week."
            phase == Phase.REVERSE_DIET ->
                "${kcal.grouped()} kcal this week, then +${if (reverse.size >= 2) (reverse[1] - reverse[0]) else 0} " +
                    "a week for ${(reverse.size - 1).coerceAtLeast(0)} weeks until you reach your burn of ${burn.grouped()}."
            else -> "${kcal.grouped()} kcal = your $burnLabel ${burn.grouped()} $sign $pctText for a ${pace.label.lowercase()} ${phase.phrase}."
        }
    }

    /**
     * "About 0.5% of body weight per week (\u22480.4 kg)". The direction is left to the caller
     * (the screen already says cut or bulk). [weightUnit] picks kg or lb for the bracket.
     */
    fun paceInWords(kgPerWeek: Double, weightKg: Double, weightUnit: WeightUnit = WeightUnit.KILOGRAMS): String {
        val change = abs(kgPerWeek)
        if (weightKg <= 0 || change < 0.05) return "Weight stays about the same"
        val pct = change / weightKg * 100.0
        val amount = UnitConverter.round(weightUnit.fromKg(change), 1)
        return "About ${fmt1(pct)}% of body weight per week (\u2248${fmt1(amount)} ${weightUnit.label})"
    }
}
