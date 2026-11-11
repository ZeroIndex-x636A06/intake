package com.zeroindex.intake.domain.training

/**
 * The planned easy week of a generated program ("deload every 6 weeks"). The program counts its weeks as its days are
 * finished; when a week that is a multiple of the cadence begins, the next [DAYS] days are a deload: every weight
 * [PERCENT]% lighter and one set fewer per exercise. It uses the same switch as the coach's deload week, so there is
 * one place that makes a workout easier and the workout screen says why.
 */
object ScheduledDeload {
    const val PERCENT = 10
    const val DAYS = 7
    const val SETS_LESS = 1

    /** True when [weekStarting] (1 is the first week) is a deload week for a program with this cadence. */
    fun startsWith(weekStarting: Int, everyWeeks: Int?): Boolean =
        everyWeeks != null && everyWeeks >= 2 && weekStarting >= everyWeeks && weekStarting % everyWeeks == 0

    /** "Week 6 is an easy week: 10% lighter and one set fewer." for the card and the notice. */
    fun line(week: Int): String = "Week $week is an easy week: $PERCENT% lighter and one set fewer."
}
