package com.zeroindex.intake.domain.training

/**
 * The words on the Train home and the active workout, kept out of Compose so they are unit tested.
 * Pure Kotlin: no Android imports.
 */
object TrainText {
    /** "4 × 8", "3 × 8–10" or "3 × max". */
    fun prescription(sets: Int, repsMin: Int, repsMax: Int): String = "$sets × ${RepTarget.short(repsMin, repsMax)}"

    /** "Bench press 4 × 8": one line of the exercise list on today's card. */
    fun line(name: String, sets: Int, repsMin: Int, repsMax: Int): String = "$name ${prescription(sets, repsMin, repsMax)}"

    /** "24:10", or "1:02:03" past an hour: the elapsed clock in the active workout header and the session bar. */
    fun clock(millis: Long): String {
        val total = (millis.coerceAtLeast(0) / 1000).toInt()
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    /** "TODAY · WEEK 3, DAY 2" is built by the screen from this: "Week 3, day 2". */
    fun weekAndDay(week: Int, dayNumber: Int): String = "Week ${week.coerceAtLeast(1)}, day ${dayNumber.coerceAtLeast(1)}"

    /** "Rest day, next: Pull A on Thursday" (or "tomorrow"). [weekday] is the ISO weekday of the next day, if it has one. */
    fun restLine(nextName: String, weekday: Int?, daysAway: Int): String {
        val when_ = when {
            daysAway == 1 -> " tomorrow"
            weekday != null -> " on ${WorkoutSchedule.weekdayName(weekday)}"
            else -> ""
        }
        return "Rest day, next: $nextName$when_"
    }

    /** "3 of 15 sets", for the header under the title. */
    fun setsProgress(done: Int, total: Int): String =
        if (total <= 0) "$done ${if (done == 1) "set" else "sets"} logged" else "set ${(done + 1).coerceAtMost(total)} of $total"

    /** "2 new records" / "1 new record". */
    fun recordsTitle(count: Int): String = if (count == 1) "1 new record" else "$count new records"

    /** A session's volume with a thousands separator: "11,420". */
    fun thousands(value: Double): String = "%,d".format(Math.round(value))

    /** "Done 3 of 4 sets" status for a collapsed exercise card. */
    fun doneLine(done: Int, total: Int): String = "$done/$total done"
}

/** The numbers behind the active workout's header. */
object SessionProgress {
    /** One exercise of the session: what was planned and how many working sets are logged. */
    data class Slot(val plannedSets: Int, val loggedWorkingSets: Int)

    /** Total sets to do: each exercise counts the larger of its plan and what was actually logged (extra sets add up). */
    fun total(slots: List<Slot>): Int = slots.sumOf { maxOf(it.plannedSets, it.loggedWorkingSets) }

    fun done(slots: List<Slot>): Int = slots.sumOf { it.loggedWorkingSets }

    /** The first exercise that still has sets left, or the last one when everything is done. */
    fun currentIndex(slots: List<Slot>): Int {
        if (slots.isEmpty()) return 0
        val i = slots.indexOfFirst { it.loggedWorkingSets < it.plannedSets }
        return if (i >= 0) i else slots.lastIndex
    }
}

/** What a set row holds before it is logged. */
data class SetRowPlan(val index: Int, val logged: Boolean)

object SetRows {
    /**
     * The rows of one exercise card: every planned set, then one spare so an extra set is always a tap away. A set that was
     * logged at a higher index than planned keeps its place. [addedExtra] is the number of "+ Add set" taps this session.
     */
    fun plan(plannedSets: Int, loggedIndexes: Set<Int>, addedExtra: Int = 0): List<SetRowPlan> {
        val base = maxOf(plannedSets, 1) + addedExtra.coerceAtLeast(0)
        val count = maxOf(base, (loggedIndexes.maxOrNull() ?: -1) + 1)
        return (0 until count).map { SetRowPlan(it, it in loggedIndexes) }
    }
}

/** Sessions this week, and what they added up to: the small numbers on Train. */
data class WeekStats(val sessions: Int, val volumeKg: Double, val workingSets: Int)
