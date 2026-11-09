package com.zeroindex.intake.domain.coach

import com.zeroindex.intake.domain.goals.DietBreakPlan
import com.zeroindex.intake.domain.goals.Pace
import com.zeroindex.intake.domain.goals.Phase
import com.zeroindex.intake.domain.goals.fmt1
import com.zeroindex.intake.domain.goals.grouped
import com.zeroindex.intake.domain.tdee.TrendPoint
import com.zeroindex.intake.domain.tdee.WeightTrend
import com.zeroindex.intake.domain.training.generator.Muscles
import com.zeroindex.intake.domain.training.generator.VolumeGuide
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The weekly check-in (SPEC-v3 "Coach engine"). Pure and deterministic: the same week of data and
 * the same [CoachConfig] always give the same suggestions, in the same order. No network, no AI.
 * docs/COACH.md lists every rule and threshold.
 */
object CoachEngine {

    fun review(i: WeekInputs, c: CoachConfig = CoachConfig()): CheckIn {
        val window = (i.today - c.windowDays + 1)..i.today
        val logged = i.foodDays.filter { it.dayIndex in window && it.kcal >= c.loggedDayMinKcal }
        val weighDays = i.weighIns.filter { it.dayIndex in window }.map { it.dayIndex }.distinct().size
        val trend = trendReading(i, c)
        val summary = summarise(i, c, window, logged, weighDays, trend)

        val out = mutableListOf<Suggestion>()
        val gateFailed = logged.size < c.minLoggedDays || weighDays < c.minWeighIns

        if (gateFailed) {
            out += dataGap(logged.size, weighDays, c)
        } else if (!i.calorieCooldown) {
            weightTrend(i, c, trend)?.let { out += it }
        }
        dietBreak(i, c)?.let { out += it }
        val recovery = recoverySignals(i, c)
        recoverySuggestion(i, c, recovery)?.let { out += it }
        strengthStall(i, c)?.let { out += it }
        protein(i, c, window)?.let { out += it }
        volume(i, c, recovery.tripped.size)?.let { out += it }
        water(i, c, window)?.let { out += it }

        val shown = out
            .filter { it.key !in i.suppressedKeys }
            .sortedWith(compareBy({ it.priority }, { it.key }))
            .take(c.maxSuggestions)
        val headline = when {
            shown.isEmpty() -> "All on track"
            shown.size == 1 -> "1 suggestion this week"
            else -> "${shown.size} suggestions this week"
        }
        return CheckIn(summary, shown, headline)
    }

    // ------------------------------------------------------------------------------ weight trend

    data class TrendReading(
        /** Percent of body weight per week over the last 7 days; negative = losing. Null without enough points. */
        val pct7: Double?,
        val pct14: Double?,
        val trendNowKg: Double?,
        val change14Kg: Double?,
        val spanDays: Int,
        val weighInDays14: Int,
    ) {
        val usable: Boolean get() = pct7 != null && pct14 != null
    }

    fun trendReading(i: WeekInputs, c: CoachConfig): TrendReading {
        val points = WeightTrend.smooth(i.weighIns.filter { it.dayIndex <= i.today })
        if (points.isEmpty()) return TrendReading(null, null, null, null, 0, 0)
        val now = points.last().trendKg
        val w7 = points.filter { it.dayIndex > i.today - 7 }
        val w14 = points.filter { it.dayIndex > i.today - 14 }
        fun pct(window: List<TrendPoint>, minPoints: Int): Double? {
            if (window.size < minPoints) return null
            val slope = WeightTrend.slopeKgPerDay(window) ?: return null
            return slope * 7.0 / now * 100.0
        }
        val span = if (w14.size >= 2) (w14.last().dayIndex - w14.first().dayIndex).toInt() else 0
        val enough14 = w14.size >= c.minWeighInsForTrend && span >= c.minTrendSpanDays
        return TrendReading(
            pct7 = pct(w7, 3),
            pct14 = if (enough14) pct(w14, c.minWeighInsForTrend) else null,
            trendNowKg = now,
            change14Kg = if (w14.size >= 2) w14.last().trendKg - w14.first().trendKg else null,
            spanDays = span,
            weighInDays14 = w14.size,
        )
    }

    /** The pace the phase is aiming for, as signed percent of body weight per week (negative = losing). */
    fun targetPct(phase: Phase, pace: Pace, contestPct: Double): Double = when (phase) {
        Phase.CUT -> -when (pace) { Pace.GENTLE -> 0.45; Pace.STEADY -> 0.6; Pace.FAST -> 0.75 }
        Phase.CONTEST_PREP -> -contestPct.coerceIn(0.5, 1.0)
        Phase.LEAN_BULK -> when (pace) { Pace.GENTLE -> 0.15; Pace.STEADY -> 0.2; Pace.FAST -> 0.3 }
        Phase.BULK -> when (pace) { Pace.GENTLE -> 0.25; Pace.STEADY -> 0.35; Pace.FAST -> 0.5 }
        Phase.RECOMP -> -when (pace) { Pace.GENTLE -> 0.2; Pace.STEADY -> 0.3; Pace.FAST -> 0.4 }
        Phase.MAINTAIN, Phase.REVERSE_DIET -> 0.0
    }

    private fun weightTrend(i: WeekInputs, c: CoachConfig, t: TrendReading): Suggestion? {
        if (!t.usable || i.currentKcal <= 0) return null
        val r7 = t.pct7!!
        val r14 = t.pct14!!
        val target = targetPct(i.phase, i.pace, i.contestWeeklyLossPct)
        val numbers = "Your trend moved ${signedPct(r14)} a week over 2 weeks (${signedPct(r7)} this week); " +
            "the aim is ${signedPct(target)}."

        // A direction and a step, or nothing. +1 = eat more, -1 = eat less.
        var direction = 0
        var large = false
        var holdNote: String? = null
        val contest = i.phase == Phase.CONTEST_PREP

        when (i.phase) {
            Phase.CUT, Phase.CONTEST_PREP -> {
                val p = abs(target)
                val slow = if (contest) c.contestSlowShare else c.slowShare
                val fast = if (contest) c.contestFastShare else c.fastShare
                val loss7 = -r7
                val loss14 = -r14
                when {
                    loss14 < slow * p && loss7 < slow * p -> {
                        direction = -1
                        large = loss14 <= 0.25 * p
                    }
                    (loss14 > fast * p && loss7 > fast * p) ||
                        (loss14 > c.fastLossPctPerWeek && loss7 > c.fastLossPctPerWeek) -> {
                        direction = +1
                        large = loss14 > c.fastLossPctPerWeek || loss14 > 2 * p
                    }
                }
            }
            Phase.LEAN_BULK, Phase.BULK -> {
                val p = abs(target)
                when {
                    r14 < c.slowShare * p && r7 < c.slowShare * p -> {
                        direction = +1
                        large = r14 <= 0.25 * p
                    }
                    r14 > c.fastShare * p && r7 > c.fastShare * p -> {
                        direction = -1
                        large = r14 > 2 * p
                    }
                }
            }
            Phase.MAINTAIN, Phase.RECOMP -> {
                val band = c.maintainBandPct
                when {
                    r14 < target - band && r7 < target - band -> { direction = +1; large = r14 < target - 2 * band }
                    r14 > target + band && r7 > target + band -> { direction = -1; large = r14 > target + 2 * band }
                }
            }
            Phase.REVERSE_DIET -> {
                val ceiling = i.maintenanceKcal
                when {
                    ceiling != null && i.currentKcal >= ceiling -> return null
                    r14 > 2 * c.maintainBandPct && r7 > 2 * c.maintainBandPct ->
                        holdNote = "Your weight is climbing faster than a reverse diet should. Hold your calories this week."
                    else -> {
                        val step = i.reverseStepKcal.coerceIn(50, 100)
                        val to = min(i.currentKcal + step, ceiling ?: Int.MAX_VALUE)
                        if (to <= i.currentKcal) return null
                        return Suggestion(
                            key = "cal:up",
                            kind = SuggestionKind.WEIGHT_TREND,
                            title = "Add ${to - i.currentKcal} calories",
                            why = "Your weight is steady (${signedPct(r14)} a week), so it is time for the next reverse-diet step: " +
                                "${i.currentKcal.grouped()} to ${to.grouped()} kcal.",
                            details = macroLine(i, to) + " A reverse diet adds a small step every week your weight holds. " +
                                "Weight is judged on the smoothed trend, not a single weigh-in.",
                            action = CoachAction.SetCalories(i.currentKcal, to),
                            priority = SuggestionKind.WEIGHT_TREND.priority,
                        )
                    }
                }
            }
        }

        if (holdNote != null) {
            return Suggestion(
                key = "cal:hold",
                kind = SuggestionKind.WEIGHT_TREND,
                title = "Hold your calories",
                why = numbers,
                details = holdNote,
                action = CoachAction.Acknowledge("Holding at ${i.currentKcal.grouped()} kcal"),
                priority = SuggestionKind.WEIGHT_TREND.priority,
            )
        }
        if (direction == 0) return null

        val small = if (contest) c.contestSmallStepKcal else c.smallStepKcal
        val big = if (contest) c.contestLargeStepKcal else c.largeStepKcal
        val step = if (large) big else small

        if (direction < 0) {
            val floor = i.floorKcal ?: 0
            val to = max(i.currentKcal - step, floor)
            if (!i.canReduceCalories || to >= i.currentKcal) {
                val why = if (!i.canReduceCalories) {
                    "Intake does not plan a calorie cut under 18."
                } else {
                    "You are already at your calorie floor of ${floor.grouped()} kcal, so Intake will not go lower."
                }
                return Suggestion(
                    key = "cal:floor",
                    kind = SuggestionKind.WEIGHT_TREND,
                    title = "Your weight is not moving as planned",
                    why = numbers,
                    details = "$why More daily movement or a slower pace are the safe ways forward. " +
                        "The Advanced setting can lower the floor, but eating this low for long costs muscle, energy and mood.",
                    action = CoachAction.Acknowledge("Noted"),
                    priority = SuggestionKind.WEIGHT_TREND.priority,
                )
            }
            val title = "Eat ${i.currentKcal - to} fewer calories"
            return Suggestion(
                key = "cal:down",
                kind = SuggestionKind.WEIGHT_TREND,
                title = title,
                why = numbers + " That is less than ${if (contest) 80 else 50}% of the pace you set.",
                details = macroLine(i, to) + " Small steps, then wait two weeks: the trend needs time to show the effect. " +
                    "Never below ${(i.floorKcal ?: 0).takeIf { it > 0 }?.grouped() ?: "your own limit"} kcal.",
                action = CoachAction.SetCalories(i.currentKcal, to),
                priority = SuggestionKind.WEIGHT_TREND.priority,
            )
        }
        val to = i.currentKcal + step
        val tooFast = (i.phase.losesWeight && -r14 > c.fastLossPctPerWeek)
        return Suggestion(
            key = "cal:up",
            kind = SuggestionKind.WEIGHT_TREND,
            title = "Eat $step more calories",
            why = numbers + if (i.phase.losesWeight && !tooFast) " That is faster than planned." else "",
            details = macroLine(i, to) + " " + when {
                tooFast -> "Losing more than 1% of your body weight a week costs muscle."
                i.phase == Phase.MAINTAIN || i.phase == Phase.RECOMP -> "A steady weight is the goal here."
                else -> "A little more food keeps the pace you chose."
            },
            action = CoachAction.SetCalories(i.currentKcal, to),
            priority = if (tooFast) SuggestionKind.WEIGHT_TREND.priority - 5 else SuggestionKind.WEIGHT_TREND.priority,
        )
    }

    /**
     * Where the calories land. Protein stays; a cut takes carbs first, then fat down to its floor
     * (0.6 g/kg is not known here, so fat is only touched once carbs would drop under 100 g).
     */
    fun macrosFor(i: WeekInputs, toKcal: Int): Triple<Int, Int, Int> = macrosFor(
        proteinG = i.targetProteinG, carbsG = i.currentCarbsG, fatG = i.currentFatG, fromKcal = i.currentKcal, toKcal = toKcal,
    )

    fun macrosFor(proteinG: Int, carbsG: Int, fatG: Int, fromKcal: Int, toKcal: Int, weightKg: Double? = null): Triple<Int, Int, Int> {
        val delta = toKcal - fromKcal
        var carbs = carbsG
        var fat = fatG
        if (delta >= 0) {
            carbs += (delta / 4.0).roundToInt()
        } else {
            val cut = -delta
            val carbRoom = max(0, carbs - CARBS_FLOOR_G)
            val fromCarbs = min(cut, carbRoom * 4)
            carbs -= (fromCarbs / 4.0).roundToInt()
            val rest = cut - fromCarbs
            if (rest > 0) {
                val fatFloor = max(
                    ceil((weightKg ?: 0.0) * 0.6).toInt(),
                    ceil(0.20 * toKcal / 9.0).toInt(),
                )
                val fromFat = min(rest / 9.0, max(0, fat - fatFloor).toDouble())
                fat -= fromFat.roundToInt()
                val left = rest - (fromFat * 9).roundToInt()
                if (left > 0) carbs = max(0, carbs - (left / 4.0).roundToInt())
            }
        }
        return Triple(proteinG, carbs, fat)
    }

    private fun macroLine(i: WeekInputs, toKcal: Int): String {
        if (i.currentCarbsG <= 0 && i.currentFatG <= 0) return "New target: ${toKcal.grouped()} kcal."
        val (p, carbs, fat) = macrosFor(i, toKcal)
        return "New target: ${toKcal.grouped()} kcal, protein $p g (unchanged), carbs $carbs g (was ${i.currentCarbsG}), fat $fat g (was ${i.currentFatG})."
    }

    // ------------------------------------------------------------------------------ data gate

    private fun dataGap(loggedDays: Int, weighDays: Int, c: CoachConfig): Suggestion {
        val needs = buildList {
            if (loggedDays < c.minLoggedDays) add("food on ${c.minLoggedDays - loggedDays} more day${plural(c.minLoggedDays - loggedDays)}")
            if (weighDays < c.minWeighIns) add("${c.minWeighIns - weighDays} more weigh-in${plural(c.minWeighIns - weighDays)}")
        }
        return Suggestion(
            key = "gap",
            kind = SuggestionKind.DATA_GAP,
            title = "Not enough data this week to adjust",
            why = "You logged food on $loggedDays of ${c.windowDays} days and weighed in on $weighDays " +
                "(need ${c.minLoggedDays} and ${c.minWeighIns}). Here's what to log: ${needs.joinToString(" and ")}.",
            details = "Your calories stay as they are. Changing them from a thin week would be a guess, and the point of the " +
                "coach is to work from your real numbers. A weigh-in on the same morning each day, and logging every meal " +
                "(even a rough one), is all it needs.",
            action = CoachAction.Acknowledge("Noted"),
            priority = SuggestionKind.DATA_GAP.priority,
        )
    }

    // ------------------------------------------------------------------------------ diet break

    private fun dietBreak(i: WeekInputs, c: CoachConfig): Suggestion? {
        val every = i.dietBreakEveryWeeks ?: return null
        val start = i.dietStartDay ?: return null
        if (!DietBreakPlan.appliesTo(i.phase)) return null
        if (every !in DietBreakPlan.MIN_WEEKS..DietBreakPlan.MAX_WEEKS) return null
        val week = ((i.today - start) / 7).toInt() + 1
        if (week < 1) return null
        val plan = DietBreakPlan(every, i.maintenanceKcal ?: i.currentKcal)
        val upcoming = plan.nextBreakWeek(week - 1)
        return when {
            plan.isBreakWeek(week) -> Suggestion(
                key = "break:now:$week",
                kind = SuggestionKind.DIET_BREAK,
                title = "This is a diet break week",
                why = "You have dieted $every weeks in a row; week $week is the planned break at about ${plan.maintenanceKcal.grouped()} kcal.",
                details = "A week at maintenance lets hunger, energy and training recover, then the cut resumes. Change your calories " +
                    "in Your plan if you want to take it; nothing changes unless you do.",
                action = CoachAction.Acknowledge("Noted"),
                priority = SuggestionKind.DIET_BREAK.priority,
            )
            upcoming - week in 1..c.dietBreakLeadWeeks -> Suggestion(
                key = "break:soon:$upcoming",
                kind = SuggestionKind.DIET_BREAK,
                title = "A diet break is due next week",
                why = "You are in week $week of dieting; your plan has a maintenance week (about ${plan.maintenanceKcal.grouped()} kcal) in week $upcoming.",
                details = "Breaks every $every weeks are a setting you chose in Advanced. Plan the week so it suits your calendar.",
                action = CoachAction.Acknowledge("Noted"),
                priority = SuggestionKind.DIET_BREAK.priority,
            )
            else -> null
        }
    }

    // ------------------------------------------------------------------------------ recovery

    data class RecoveryReading(val tripped: List<String>, val available: Int)

    fun recoverySignals(i: WeekInputs, c: CoachConfig): RecoveryReading {
        val recentRange = (i.today - 6)..i.today
        val baseRange = (i.today - c.baselineDays - 6)..(i.today - 7)
        val tripped = mutableListOf<String>()
        var available = 0

        // Sleep: nights under 6 h.
        val sleeps = i.health.filter { it.dayIndex in recentRange }.mapNotNull { it.sleepMinutes?.takeIf { m -> m > 0 } }
        if (sleeps.size >= c.sleepMinNights) {
            available++
            val low = sleeps.count { it < c.sleepLowMinutes }
            if (low >= c.sleepLowNights) tripped += "you slept under ${c.sleepLowMinutes / 60} hours on $low of ${sleeps.size} nights"
        }

        // Resting heart rate against the 28-day baseline before this week.
        val recentHr = i.health.filter { it.dayIndex in recentRange }.mapNotNull { it.restingHr?.takeIf { v -> v > 0 } }
        val baseHr = i.health.filter { it.dayIndex in baseRange }.mapNotNull { it.restingHr?.takeIf { v -> v > 0 } }
        if (recentHr.size >= c.minRecentDays && baseHr.size >= c.minBaselineDays) {
            available++
            val rise = recentHr.average() - baseHr.average()
            if (rise > c.restingHrRiseBpm) {
                tripped += "your resting heart rate is ${fmt1(rise)} bpm above your usual ${baseHr.average().roundToInt()}"
            }
        }

        // HRV, when a device provides it.
        val recentHrv = i.health.filter { it.dayIndex in recentRange }.mapNotNull { it.hrvMs?.takeIf { v -> v > 0 } }
        val baseHrv = i.health.filter { it.dayIndex in baseRange }.mapNotNull { it.hrvMs?.takeIf { v -> v > 0 } }
        if (recentHrv.size >= c.minRecentDays && baseHrv.size >= c.minBaselineDays) {
            available++
            val drop = (baseHrv.average() - recentHrv.average()) / baseHrv.average() * 100.0
            if (drop > c.hrvDropPct) tripped += "your HRV is down ${drop.roundToInt()}% from your usual"
        }

        // Effort: most logged sets at RPE 9 or more.
        val rpes = i.setRpes.filter { it.dayIndex in recentRange }.mapNotNull { it.rpe }
        if (rpes.size >= c.rpeMinSets) {
            available++
            val high = rpes.count { it >= c.rpeHigh }
            if (high.toDouble() / rpes.size > c.rpeHighShare) {
                tripped += "$high of ${rpes.size} sets were logged at RPE ${c.rpeHigh.roundToInt()} or harder"
            }
        }
        return RecoveryReading(tripped, available)
    }

    private fun recoverySuggestion(i: WeekInputs, c: CoachConfig, r: RecoveryReading): Suggestion? {
        if (r.tripped.size < c.signalsForEasierDay) return null
        val deloadRecent = i.lastDeloadDay != null && i.today - i.lastDeloadDay <= c.deloadRecentDays
        val wantsDeload = (r.tripped.size >= c.signalsForDeload || i.easyDayApplied) && !deloadRecent
        val why = "This week ${r.tripped.joinToString("; ")}."
        val caution = "This is a pattern worth noticing, not a diagnosis. If you feel unwell, or this carries on for weeks, " +
            "rest and talk to a doctor. Intake is not medical advice."
        return if (wantsDeload) {
            Suggestion(
                key = "recovery:deload",
                kind = SuggestionKind.RECOVERY,
                title = "Take an easier week",
                why = why,
                details = "A deload week means ${c.deloadPercent}% less weight on every lift for ${c.deloadDays} days. $caution",
                action = CoachAction.Deload(i.programId, i.programName, c.deloadPercent, c.deloadDays),
                priority = SuggestionKind.RECOVERY.priority,
            )
        } else {
            Suggestion(
                key = "recovery:easy",
                kind = SuggestionKind.RECOVERY,
                title = "Make your next session a little easier",
                why = why,
                details = "Your next session drops ${c.easierDaySetsLess} set from every exercise. $caution",
                action = CoachAction.EasierDay(c.easierDaySetsLess, c.easierDayDays),
                priority = SuggestionKind.RECOVERY.priority,
            )
        }
    }

    // ------------------------------------------------------------------------------ strength stalls

    data class Stall(val exercise: ExerciseHistoryIn, val first: ExerciseSession, val last: ExerciseSession)

    fun stalls(i: WeekInputs, c: CoachConfig): List<Stall> = i.exercises.filter { it.inActiveProgram }.mapNotNull { ex ->
        val recent = ex.sessions.filter { it.dayIndex > i.today - c.stallWindowDays }.sortedBy { it.dayIndex }.takeLast(c.stallSessions)
        if (recent.size < c.stallSessions) return@mapNotNull null
        val first = recent.first()
        val later = recent.drop(1)
        val progressed = later.any { it.bestE1rmKg > first.bestE1rmKg * (1 + c.stallMinGain) } ||
            recent.last().volumeKg >= first.volumeKg * (1 + c.stallVolumeGain)
        if (progressed) null else Stall(ex, first, recent.last())
    }.sortedWith(compareBy({ it.exercise.name }))

    private fun strengthStall(i: WeekInputs, c: CoachConfig): Suggestion? {
        val found = stalls(i, c)
        if (found.isEmpty()) return null
        val deloadRecent = i.lastDeloadDay != null && i.today - i.lastDeloadDay <= c.deloadRecentDays
        val names = found.take(3).joinToString(", ") { it.exercise.name }
        val numbers = found.take(2).joinToString("; ") {
            "${it.exercise.name} ${fmt1(it.first.bestE1rmKg)} to ${fmt1(it.last.bestE1rmKg)} kg estimated max"
        }
        val why = "$names has not gone up in the last ${c.stallSessions} sessions ($numbers)."
        if (!deloadRecent) {
            return Suggestion(
                key = "stall:deload",
                kind = SuggestionKind.STRENGTH_STALL,
                title = "Take a deload week",
                why = why,
                details = "No deload in the last ${c.deloadRecentDays} days. ${c.deloadPercent}% less weight for ${c.deloadDays} days lets fatigue drop; " +
                    "most lifters come back stronger. Estimated max uses Epley: weight x (1 + reps / 30).",
                action = CoachAction.Deload(i.programId, i.programName, c.deloadPercent, c.deloadDays),
                priority = SuggestionKind.STRENGTH_STALL.priority,
            )
        }
        // A deload was recent, so the lift itself needs a change: swap it for a close cousin.
        for (stall in found) {
            val ex = stall.exercise
            val pick = i.swapPool
                .filter { it.exerciseId != ex.exerciseId && it.muscle == ex.muscle && it.equipment == ex.equipment }
                .sortedWith(compareBy({ -it.timesTrained }, { it.name }))
                .firstOrNull() ?: continue
            return Suggestion(
                key = "stall:swap:${ex.exerciseId}",
                kind = SuggestionKind.STRENGTH_STALL,
                title = "Swap ${ex.name} for ${pick.name}",
                why = "${ex.name} has not gone up in ${c.stallSessions} sessions (${fmt1(stall.first.bestE1rmKg)} to " +
                    "${fmt1(stall.last.bestE1rmKg)} kg estimated max) and you already had a deload in the last ${c.deloadRecentDays} days.",
                details = "${pick.name} trains the same muscle (${ex.muscle}) with the same kit (${ex.equipment}), so your week stays balanced. " +
                    "A fresh movement often restarts progress. You can swap back any time in the program.",
                action = CoachAction.SwapExercise(ex.exerciseId, ex.name, pick.exerciseId, pick.name),
                priority = SuggestionKind.STRENGTH_STALL.priority,
            )
        }
        return null
    }

    // ------------------------------------------------------------------------------ protein, water

    private fun protein(i: WeekInputs, c: CoachConfig, window: LongRange): Suggestion? {
        if (i.targetProteinG <= 0) return null
        val days = i.foodDays.filter { it.dayIndex in window && it.kcal >= c.loggedDayMinKcal }
        if (days.size < c.proteinMinLoggedDays) return null
        val avg = days.map { it.proteinG }.average()
        val share = avg / i.targetProteinG
        if (share >= c.proteinShare) return null
        val ideas = i.proteinIdeas.take(3)
        return Suggestion(
            key = "protein",
            kind = SuggestionKind.PROTEIN,
            title = "Protein is running low",
            why = "You averaged ${avg.roundToInt()} g of protein on the ${days.size} days you logged; the target is " +
                "${i.targetProteinG} g (${(share * 100).roundToInt()}%).",
            details = if (ideas.isEmpty()) {
                "Protein protects muscle while you diet and builds it when you train. Try adding a protein food to each meal."
            } else {
                "Protein protects muscle while you diet and builds it when you train. Easy ways to add some: ${ideas.joinToString("; ")}."
            },
            action = CoachAction.Acknowledge("Noted"),
            priority = SuggestionKind.PROTEIN.priority,
        )
    }

    private fun water(i: WeekInputs, c: CoachConfig, window: LongRange): Suggestion? {
        val days = i.waterDays.filter { it.dayIndex in window && it.targetMl > 0 }
        if (days.count { it.consumedMl > 0 } < c.waterMinDaysWithLogs) return null
        val avg = days.map { it.consumedMl }.average()
        val target = days.map { it.targetMl }.average()
        if (avg >= c.waterShare * target) return null
        return Suggestion(
            key = "water",
            kind = SuggestionKind.WATER,
            title = "Water is a bit low",
            why = "You averaged ${avg.roundToInt().grouped()} ml a day against a target of ${target.roundToInt().grouped()} ml " +
                "(${(avg / target * 100).roundToInt()}%).",
            details = "A glass with each meal and one by your workout is an easy way to close the gap. The target moves with your weight " +
                "and training days.",
            action = CoachAction.Acknowledge("Noted"),
            priority = SuggestionKind.WATER.priority,
        )
    }

    // ------------------------------------------------------------------------------ volume

    private fun volume(i: WeekInputs, c: CoachConfig, recoverySignals: Int): Suggestion? {
        if (i.programMuscles.isEmpty()) return null
        val thisWeek = (i.today - 6)..i.today
        val lastWeek = (i.today - 13)..(i.today - 7)
        fun sets(range: LongRange) = i.setsByDay.filter { it.first in range }.groupingBy { it.second }.eachCount()
        val now = sets(thisWeek)
        val before = sets(lastWeek)
        val sessionsNow = i.sessionDays.count { it in thisWeek }
        val sessionsBefore = i.sessionDays.count { it in lastWeek }

        data class Gap(val muscle: String, val gap: Int, val now: Int, val before: Int, val range: IntRange)

        val muscles = Muscles.PROGRAMMED.filter { it in i.programMuscles }
        // Well below the range for two weeks running, while actually training.
        if (sessionsNow >= c.volumeMinSessionsPerWeek && sessionsBefore >= c.volumeMinSessionsPerWeek) {
            val low = muscles.mapNotNull { m ->
                val range = VolumeGuide.allowed(i.experience, m, focus = false)
                val limit = range.first * c.volumeBelowShare
                val n = now[m] ?: 0
                val b = before[m] ?: 0
                if (n < limit && b < limit) Gap(m, range.first - n, n, b, range) else null
            }.sortedWith(compareBy({ -it.gap }, { it.muscle })).firstOrNull()
            if (low != null) {
                return Suggestion(
                    key = "volume:up:${low.muscle}",
                    kind = SuggestionKind.VOLUME,
                    title = "Add ${c.volumeStepSets} sets for ${low.muscle}",
                    why = "You did ${low.now} hard sets for ${low.muscle} this week and ${low.before} the week before; " +
                        "a ${i.experience.label.lowercase()} lifter usually needs ${low.range.first} to ${low.range.last}.",
                    details = "Hard sets are working sets, not warm-ups. Two more sets across your ${low.muscle} exercises is a small step; " +
                        "you can undo it in the program.",
                    action = CoachAction.MuscleSets(low.muscle, +c.volumeStepSets),
                    priority = SuggestionKind.VOLUME.priority,
                )
            }
        }
        // Above the range while recovery looks poor.
        if (recoverySignals >= 1) {
            val high = muscles.mapNotNull { m ->
                val range = VolumeGuide.allowed(i.experience, m, focus = false)
                val n = now[m] ?: 0
                if (n > range.last) Gap(m, n - range.last, n, before[m] ?: 0, range) else null
            }.sortedWith(compareBy({ -it.gap }, { it.muscle })).firstOrNull()
            if (high != null) {
                return Suggestion(
                    key = "volume:down:${high.muscle}",
                    kind = SuggestionKind.VOLUME,
                    title = "Trim ${c.volumeStepSets} sets from ${high.muscle}",
                    why = "You did ${high.now} hard sets for ${high.muscle} this week, above the usual ${high.range.first} to ${high.range.last}, " +
                        "and your recovery signs are down.",
                    details = "More sets only help while you recover from them. Cutting two for now is a small, reversible step.",
                    action = CoachAction.MuscleSets(high.muscle, -c.volumeStepSets),
                    priority = SuggestionKind.VOLUME.priority,
                )
            }
        }
        return null
    }

    // ------------------------------------------------------------------------------ summary

    private fun summarise(
        i: WeekInputs,
        c: CoachConfig,
        window: LongRange,
        logged: List<FoodDayIn>,
        weighDays: Int,
        t: TrendReading,
    ): WeekSummary {
        val sleeps = i.health.filter { it.dayIndex in window }.mapNotNull { it.sleepMinutes?.takeIf { m -> m > 0 } }
        return WeekSummary(
            trendChangeKg = weekChange(i),
            trendPctPerWeek = t.pct14 ?: t.pct7,
            avgKcal = logged.takeIf { it.isNotEmpty() }?.map { it.kcal }?.average()?.roundToInt(),
            avgProteinG = logged.takeIf { it.isNotEmpty() }?.map { it.proteinG }?.average()?.roundToInt(),
            daysLogged = logged.size,
            weighIns = weighDays,
            workouts = i.sessionDays.count { it in window },
            avgSleepHours = sleeps.takeIf { it.isNotEmpty() }?.average()?.div(60.0),
            daysInWindow = c.windowDays,
        )
    }

    /** Trend change over the last 7 days, when two or more points exist. */
    private fun weekChange(i: WeekInputs): Double? {
        val pts = WeightTrend.smooth(i.weighIns.filter { it.dayIndex <= i.today })
        val w = pts.filter { it.dayIndex > i.today - 7 }
        return if (w.size >= 2) w.last().trendKg - w.first().trendKg else null
    }

    private fun signedPct(v: Double): String = (if (v >= 0) "+" else "−") + fmt1(abs(v)) + "%"

    private fun plural(n: Int) = if (n == 1) "" else "s"

    const val CARBS_FLOOR_G = 100
}
