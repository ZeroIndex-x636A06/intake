package com.zeroindex.intake.domain.reminders

import com.zeroindex.intake.domain.coach.DailyFocusPicker
import com.zeroindex.intake.domain.photos.PhotoReminderRule
import com.zeroindex.intake.domain.training.TodayPlan

/**
 * Whether a daily reminder should fire right now (Settings > Reminders). Pure Kotlin: the worker reads the
 * settings and the data, then asks these questions. Each function answers only "is it due at this moment?".
 * The once-per-slot memory (already sent today?) is kept by the worker's ledger, not here.
 *
 * Times are minutes after midnight, local time.
 */
object ReminderRules {

    /** Progress photo reminders wait until this hour, so a due photo never arrives in the night. */
    const val PHOTO_EARLIEST_HOUR = 10

    /**
     * A time-of-day reminder is sent from its time for [LATE_WINDOW_MINUTES]. After that (say the phone was off all
     * evening) it is skipped rather than sent late, so a "log breakfast" nudge never arrives at night.
     */
    const val LATE_WINDOW_MINUTES = 6 * 60

    /** True from the chosen time until the late window closes. Each reminder fires at most once per slot per day. */
    fun inReminderWindow(nowMinute: Int, reminderMinute: Int): Boolean =
        nowMinute >= reminderMinute && nowMinute < reminderMinute + LATE_WINDOW_MINUTES

    /** Workout: only on a training day, and only while today's workout is not finished. */
    fun workoutDue(nowMinute: Int, reminderMinute: Int, plan: TodayPlan): Boolean =
        plan is TodayPlan.Train && inReminderWindow(nowMinute, reminderMinute)

    /** Meal: at the meal's time, and only if that meal slot has nothing logged today. */
    fun mealDue(nowMinute: Int, reminderMinute: Int, slotLoggedToday: Boolean): Boolean =
        !slotLoggedToday && inReminderWindow(nowMinute, reminderMinute)

    /** Water slots are every two hours from [startHour] up to and including [endHour]. */
    fun isWaterSlot(hour: Int, startHour: Int, endHour: Int): Boolean =
        endHour >= startHour && hour in startHour..endHour && (hour - startHour) % 2 == 0

    /**
     * Behind for the time of day. The same rule as the Today card's "Drink some water" line: the share done is
     * more than [DailyFocusPicker.WATER_GAP] below the share expected by [hour].
     */
    fun waterBehind(hour: Int, waterSoFarMl: Double, waterTargetMl: Double): Boolean {
        if (waterTargetMl <= 0.0) return false
        val share = waterSoFarMl / waterTargetMl
        return share < DailyFocusPicker.expectedWaterShare(hour) - DailyFocusPicker.WATER_GAP
    }

    /** Water: in one of the water slots, and only when the person is behind for the time of day. */
    fun waterDue(hour: Int, startHour: Int, endHour: Int, behind: Boolean): Boolean =
        behind && isWaterSlot(hour, startHour, endHour)

    /** Progress photo: the photo rule says it is due (2, 3 or 4 weeks since the newest photo), from 10:00 on. */
    fun photoDue(hour: Int, weeks: Int, newestPhotoAtMillis: Long?, nowMillis: Long): Boolean =
        hour >= PHOTO_EARLIEST_HOUR && PhotoReminderRule.isDue(weeks, newestPhotoAtMillis, nowMillis)
}
