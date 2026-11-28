package com.zeroindex.intake.domain.training

import com.zeroindex.intake.domain.goals.GoalDefaults
import com.zeroindex.intake.domain.training.generator.GeneratedProgram

/** A program day as the schedule sees it. */
data class ScheduleDay(val dayIndex: Int, val name: String, val weekday: Int?)

/** What today holds. */
sealed interface TodayPlan {
    /** Today is a training day (or the program has no weekdays): [day] is the next one in the rotation. */
    data class Train(val day: ScheduleDay, val byWeekday: Boolean) : TodayPlan

    /** Today is not a training day; [next] is the next workout, carrying the weekday it falls on, [daysAway] off (1 = tomorrow). */
    data class Rest(val next: ScheduleDay, val daysAway: Int) : TodayPlan

    data object NoDays : TodayPlan
}

/**
 * Scheduling program days on weekdays (SPEC-v3, "Use the plan creator's answers").
 *
 * A program day may carry an ISO weekday (1 Monday to 7 Sunday). Together those weekdays are the
 * person's training days: on one of them, today's workout is the next day in the **rotation** (the
 * program's "next day" pointer, which only moves when a workout is finished); any other weekday is a
 * rest day, and the card says which workout is next and when. A program whose days carry no weekday
 * (older programs, imports, programs built by hand) offers the next day in the rotation every day.
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
     * [isoToday] is today's ISO weekday. [trainedToday] is true once a workout has been finished today, so the
     * card says what comes next and when, and does not offer a second workout straight away.
     */
    fun planFor(days: List<ScheduleDay>, rotationIndex: Int, isoToday: Int, trainedToday: Boolean = false): TodayPlan {
        if (days.isEmpty()) return TodayPlan.NoDays
        val scheduled = days.filter { it.weekday != null }
        if (scheduled.isEmpty()) {
            val i = rotationIndex.coerceIn(0, days.lastIndex)
            return TodayPlan.Train(days[i], byWeekday = false)
        }
        // The weekdays say WHEN the person trains; the rotation says WHICH workout is next. So a new program starts
        // with its first day whatever weekday it is made on, a missed day is not skipped, and a workout that was
        // started and discarded is still the one on offer.
        val weekdays = scheduled.mapNotNull { it.weekday }.toSet()
        val upNext = days[rotationIndex.coerceIn(0, days.lastIndex)]
        if (isoToday in weekdays && !trainedToday) return TodayPlan.Train(upNext, byWeekday = true)
        // Not a training day, or today's workout is already done: the next one is on the next training day after today.
        fun daysUntil(weekday: Int) = Math.floorMod(weekday - isoToday - 1, 7) + 1
        val nextWeekday = weekdays.minBy(::daysUntil)
        return TodayPlan.Rest(upNext.copy(weekday = nextWeekday), daysAway = daysUntil(nextWeekday))
    }

    /** "Monday", "Tue" and so on, from an ISO weekday. */
    fun weekdayName(iso: Int, short: Boolean = false): String {
        val full = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
        val name = full.getOrNull(iso - 1) ?: return ""
        return if (short) name.take(3) else name
    }
}
