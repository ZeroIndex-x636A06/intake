package com.zeroindex.intake.domain.plan

import com.zeroindex.intake.domain.model.PlanLoopMode
import java.time.LocalDate

/** Where "today" falls in a plan. */
enum class ScheduleState { NOT_STARTED, RUNNING, FINISHED }

data class PlanPosition(
    /** The week to show, 1..[length] (week 1 before the start, the last week once finished). */
    val week: Int,
    val length: Int,
    val state: ScheduleState,
    /** Which run through the plan this is, from 1. */
    val cycle: Int,
    /** Total runs for REPEAT_N, null for NONE and REPEAT. */
    val cycles: Int?,
    val repeats: Boolean,
    /** Days until week 1 begins when [state] is NOT_STARTED. */
    val daysUntilStart: Long = 0,
) {
    /** "Week 3 of 8 · repeats", "Week 8 of 8 · finished", "Week 1 of 8 · starts in 3 days". */
    fun label(): String {
        val base = "Week $week of $length"
        val tail = when (state) {
            ScheduleState.NOT_STARTED ->
                "starts in $daysUntilStart ${if (daysUntilStart == 1L) "day" else "days"}"
            ScheduleState.FINISHED -> "finished"
            ScheduleState.RUNNING -> when {
                cycles != null -> "run $cycle of $cycles"
                repeats -> "repeats"
                else -> null
            }
        }
        return if (tail == null) base else "$base · $tail"
    }
}

/**
 * The loop rule (pure; days are epoch days, see MealPlanEntity.startDate):
 *
 * - `index = floor((today - start) / 7)`, the 0-based week since the start. Before the start the
 *   plan is NOT_STARTED and shows week 1.
 * - NONE: weeks 1..N, then FINISHED (the last week stays shown).
 * - REPEAT: `week = index mod N + 1`, forever.
 * - REPEAT_N with count C (C >= 2): like REPEAT for the first C * N weeks, then FINISHED.
 *   A count below 2 means a single run, the same as NONE.
 *
 * "Jump to week k" does not store an override: it moves the start date so that today falls on
 * week k of the first run ([startForWeek]).
 */
object PlanSchedule {
    const val MAX_WEEKS = 104

    fun position(
        startDay: Long,
        today: Long,
        length: Int,
        mode: PlanLoopMode,
        loopCount: Int,
    ): PlanPosition {
        val n = length.coerceAtLeast(1)
        val shownCycles = if (mode == PlanLoopMode.REPEAT_N && loopCount >= 2) loopCount else null
        val repeats = mode == PlanLoopMode.REPEAT || shownCycles != null
        val days = today - startDay
        if (days < 0) {
            return PlanPosition(1, n, ScheduleState.NOT_STARTED, 1, shownCycles, repeats, -days)
        }
        val index = days / 7
        val runs: Long? = when (mode) {
            PlanLoopMode.NONE -> 1L
            PlanLoopMode.REPEAT -> null
            PlanLoopMode.REPEAT_N -> loopCount.coerceAtLeast(1).toLong()
        }
        if (runs != null && index >= runs * n) {
            return PlanPosition(n, n, ScheduleState.FINISHED, runs.toInt(), shownCycles, repeats)
        }
        return PlanPosition(
            week = (index % n).toInt() + 1,
            length = n,
            state = ScheduleState.RUNNING,
            cycle = (index / n).toInt() + 1,
            cycles = shownCycles,
            repeats = repeats,
        )
    }

    /** The start date that makes [today] the first day of [week] (1-based). */
    fun startForWeek(today: Long, week: Int): Long = today - (week.coerceAtLeast(1) - 1) * 7L

    fun epochDay(date: LocalDate): Long = date.toEpochDay()
    fun date(epochDay: Long): LocalDate = LocalDate.ofEpochDay(epochDay)

    /** "repeat:" value of the script header: `no`, `yes` or `N times`. */
    fun repeatText(mode: PlanLoopMode, count: Int): String = when (mode) {
        PlanLoopMode.NONE -> "no"
        PlanLoopMode.REPEAT -> "yes"
        PlanLoopMode.REPEAT_N -> "$count times"
    }

    /** Inverse of [repeatText]; null when the text is not a repeat value. "1 time" means no repeat. */
    fun parseRepeat(text: String): Pair<PlanLoopMode, Int>? {
        val t = text.trim().lowercase()
        when (t) {
            "no", "none", "off", "false", "never" -> return PlanLoopMode.NONE to 0
            "yes", "on", "true", "forever", "always", "loop" -> return PlanLoopMode.REPEAT to 0
        }
        val m = Regex("""^(\d{1,3})\s*(?:x|times?)?$""").matchEntire(t) ?: return null
        val n = m.groupValues[1].toInt()
        return when {
            n < 1 -> null
            n == 1 -> PlanLoopMode.NONE to 0
            else -> PlanLoopMode.REPEAT_N to n
        }
    }
}
