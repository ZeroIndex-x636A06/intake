package com.zeroindex.intake.domain.training

import com.zeroindex.intake.domain.goals.GoalDefaults
import com.zeroindex.intake.domain.training.generator.GeneratedProgram

/** A program day as the schedule sees it. */
data class ScheduleDay(val dayIndex: Int, val name: String, val weekday: Int?)

/** What today holds. */
sealed interface TodayPlan {
    /** A day is scheduled for today (or the program has no weekdays and this one is next in the rotation). */
    data class Train(val day: ScheduleDay, val byWeekday: Boolean) : TodayPlan

    /** Nothing is scheduled today; [next] is the next scheduled day and [daysAway] how far off (1 = tomorrow). */
    data class Rest(val next: ScheduleDay, val daysAway: Int) : TodayPlan

    data object NoDays : TodayPlan
}

/**
 * Scheduling program days on weekdays (SPEC-v3, "Use the plan creator's answers").
 *
 * A program day may carry an ISO weekday (1 Monday to 7 Sunday). When it does, Today and the coach
 * follow the week: the day with today's weekday is today's workout, any other day is a rest day, and
 * the card says which day is next. A program whose days carry no weekday (older programs, imports,
 * programs built by hand) keeps the **rotation**: the next day in order, whichever weekday it is.
 */
object WorkoutSchedule {

    /**
     * Which weekday each of [dayCount] days goes on. Uses the person's chosen weekdays when there
     * are exactly [dayCount] of them; otherwise, if they chose none, an even spread; otherwise
     * (they chose a different number of days than the program has) nothing, so the rotation applies.
     */
    fun weekdaysFor(dayCount: Int, chosen: Set<Int>): List<Int?> {
        if (dayCount <= 0) return emptyList()
        val valid = chosen.filter { it in 1..7 }.toSortedSet()
        val use = when {
            valid.size == dayCount -> valid
            valid.isEmpty() && dayCount in 1..7 -> GoalDefaults.spreadWeekdays(dayCount).toSortedSet()
            else -> return List(dayCount) { null }
        }
        return use.toList()
    }

    /** The program with its days given weekdays by [weekdaysFor]. */
    fun schedule(program: GeneratedProgram, chosen: Set<Int>): GeneratedProgram {
        val weekdays = weekdaysFor(program.days.size, chosen)
        return program.copy(days = program.days.mapIndexed { i, d -> d.copy(weekday = weekdays.getOrNull(i)) })
    }

    /**
     * Today's plan. [rotationIndex] is the program's "next day" pointer, used when no day has a weekday.
     * [isoToday] is today's ISO weekday.
     */
    fun planFor(days: List<ScheduleDay>, rotationIndex: Int, isoToday: Int): TodayPlan {
        if (days.isEmpty()) return TodayPlan.NoDays
        val scheduled = days.filter { it.weekday != null }
        if (scheduled.isEmpty()) {
            val i = rotationIndex.coerceIn(0, days.lastIndex)
            return TodayPlan.Train(days[i], byWeekday = false)
        }
        scheduled.firstOrNull { it.weekday == isoToday }?.let { return TodayPlan.Train(it, byWeekday = true) }
        val next = scheduled.minBy { Math.floorMod(it.weekday!! - isoToday, 7) }
        return TodayPlan.Rest(next, daysAway = Math.floorMod(next.weekday!! - isoToday, 7))
    }

    /** "Monday", "Tue" and so on, from an ISO weekday. */
    fun weekdayName(iso: Int, short: Boolean = false): String {
        val full = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
        val name = full.getOrNull(iso - 1) ?: return ""
        return if (short) name.take(3) else name
    }
}
