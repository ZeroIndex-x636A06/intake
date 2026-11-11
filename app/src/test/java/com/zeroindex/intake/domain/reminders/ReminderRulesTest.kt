package com.zeroindex.intake.domain.reminders

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.domain.training.ScheduleDay
import com.zeroindex.intake.domain.training.TodayPlan
import com.zeroindex.intake.domain.training.WorkoutSchedule
import org.junit.Test

class ReminderRulesTest {
    private val monday = 1
    private val tuesday = 2
    private val pushDay = ScheduleDay(dayIndex = 0, name = "Push", weekday = monday)
    private val trainToday = WorkoutSchedule.planFor(listOf(pushDay), 0, isoToday = monday)
    private val restToday = WorkoutSchedule.planFor(listOf(pushDay), 0, isoToday = tuesday)
    private val doneToday = WorkoutSchedule.planFor(listOf(pushDay), 0, isoToday = monday, trainedToday = true)

    private fun at(hour: Int, minute: Int = 0) = hour * 60 + minute
    private val dayMs = 86_400_000L

    // ---- Workout ----

    @Test
    fun `workout is due on a training day once its time has come`() {
        assertThat(trainToday).isInstanceOf(TodayPlan.Train::class.java)
        assertThat(ReminderRules.workoutDue(at(17, 0), at(17, 0), trainToday)).isTrue()
        assertThat(ReminderRules.workoutDue(at(20, 0), at(17, 0), trainToday)).isTrue()
    }

    @Test
    fun `workout is not due before its time`() {
        assertThat(ReminderRules.workoutDue(at(16, 59), at(17, 0), trainToday)).isFalse()
    }

    @Test
    fun `workout is not due on a rest day`() {
        assertThat(restToday).isInstanceOf(TodayPlan.Rest::class.java)
        assertThat(ReminderRules.workoutDue(at(18, 0), at(17, 0), restToday)).isFalse()
    }

    @Test
    fun `workout is not due once today's workout is finished`() {
        assertThat(doneToday).isInstanceOf(TodayPlan.Rest::class.java)
        assertThat(ReminderRules.workoutDue(at(18, 0), at(17, 0), doneToday)).isFalse()
    }

    @Test
    fun `workout is not due with no program days`() {
        assertThat(ReminderRules.workoutDue(at(18, 0), at(17, 0), TodayPlan.NoDays)).isFalse()
    }

    // ---- Meals ----

    @Test
    fun `meal is not due when its slot already has something logged`() {
        assertThat(ReminderRules.mealDue(at(8, 0), at(7, 30), slotLoggedToday = true)).isFalse()
    }

    @Test
    fun `meal is due at its time when nothing is logged for that slot`() {
        assertThat(ReminderRules.mealDue(at(7, 30), at(7, 30), slotLoggedToday = false)).isTrue()
        assertThat(ReminderRules.mealDue(at(9, 0), at(7, 30), slotLoggedToday = false)).isTrue()
    }

    @Test
    fun `meal is not due before its time`() {
        assertThat(ReminderRules.mealDue(at(7, 29), at(7, 30), slotLoggedToday = false)).isFalse()
    }

    // ---- Water ----

    @Test
    fun `water is not due when the person is not behind`() {
        val behind = ReminderRules.waterBehind(hour = 14, waterSoFarMl = 2_000.0, waterTargetMl = 2_500.0)
        assertThat(behind).isFalse()
        assertThat(ReminderRules.waterDue(14, 8, 20, behind)).isFalse()
    }

    @Test
    fun `water is not due outside the water hours`() {
        assertThat(ReminderRules.waterDue(hour = 7, startHour = 8, endHour = 20, behind = true)).isFalse()
        assertThat(ReminderRules.waterDue(hour = 21, startHour = 8, endHour = 20, behind = true)).isFalse()
    }

    @Test
    fun `water is due every two hours from the start hour when behind`() {
        assertThat(ReminderRules.waterDue(hour = 8, startHour = 8, endHour = 20, behind = true)).isTrue()
        assertThat(ReminderRules.waterDue(hour = 10, startHour = 8, endHour = 20, behind = true)).isTrue()
        assertThat(ReminderRules.waterDue(hour = 20, startHour = 8, endHour = 20, behind = true)).isTrue()
        assertThat(ReminderRules.waterDue(hour = 9, startHour = 8, endHour = 20, behind = true)).isFalse()
        assertThat(ReminderRules.waterDue(hour = 11, startHour = 8, endHour = 20, behind = true)).isFalse()
    }

    @Test
    fun `water is never due when the hours are inverted`() {
        assertThat(ReminderRules.waterDue(hour = 10, startHour = 20, endHour = 8, behind = true)).isFalse()
    }

    @Test
    fun `behind means well below the expected share for the hour`() {
        // At 18:00 about 73% should be done; with 10% done the person is behind.
        assertThat(ReminderRules.waterBehind(hour = 18, waterSoFarMl = 250.0, waterTargetMl = 2_500.0)).isTrue()
        // With no target there is nothing to be behind on.
        assertThat(ReminderRules.waterBehind(hour = 18, waterSoFarMl = 0.0, waterTargetMl = 0.0)).isFalse()
    }

    // ---- Progress photo ----

    @Test
    fun `photo is due after the chosen weeks, from 10 in the morning`() {
        val now = 40 * dayMs
        val lastPhoto = now - 21 * dayMs // three weeks ago
        assertThat(ReminderRules.photoDue(hour = 10, weeks = 3, newestPhotoAtMillis = lastPhoto, nowMillis = now)).isTrue()
        assertThat(ReminderRules.photoDue(hour = 9, weeks = 3, newestPhotoAtMillis = lastPhoto, nowMillis = now)).isFalse()
    }

    @Test
    fun `photo is not due when a recent photo exists or the reminder is off`() {
        val now = 40 * dayMs
        val recent = now - 5 * dayMs
        assertThat(ReminderRules.photoDue(hour = 12, weeks = 2, newestPhotoAtMillis = recent, nowMillis = now)).isFalse()
        assertThat(ReminderRules.photoDue(hour = 12, weeks = 0, newestPhotoAtMillis = null, nowMillis = now)).isFalse()
    }

    @Test
    fun `photo is due at once when there has never been one`() {
        assertThat(ReminderRules.photoDue(hour = 12, weeks = 4, newestPhotoAtMillis = null, nowMillis = dayMs)).isTrue()
    }
}
