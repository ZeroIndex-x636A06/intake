package com.zeroindex.intake.domain.nutrition

import com.zeroindex.intake.domain.model.MealSlot
import com.zeroindex.intake.domain.time.DayRange
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NutritionDayTest {

    private val today = LocalDate.of(2026, 11, 13)
    private val la = ZoneId.of("America/Los_Angeles")

    @Test fun backwardsGoesOneDayAtATime() {
        assertEquals(LocalDate.of(2026, 11, 12), NutritionDay.shift(today, -1, today))
        assertEquals(LocalDate.of(2026, 10, 31), NutritionDay.shift(LocalDate.of(2026, 11, 1), -1, today))
    }

    @Test fun forwardsStopsAtToday() {
        assertEquals(today, NutritionDay.shift(today, 1, today))
        assertEquals(today, NutritionDay.shift(today.minusDays(1), 1, today))
        assertEquals(today, NutritionDay.shift(today.minusDays(1), 5, today))
    }

    @Test fun nextIsOnlyAllowedBeforeToday() {
        assertFalse(NutritionDay.canGoNext(today, today))
        assertTrue(NutritionDay.canGoNext(today.minusDays(1), today))
    }

    @Test fun futureDaysAreClampedToToday() {
        assertTrue(NutritionDay.isFuture(today.plusDays(1), today))
        assertFalse(NutritionDay.isFuture(today, today))
        assertEquals(today, NutritionDay.clamp(today.plusDays(30), today))
        assertEquals(today.minusDays(3), NutritionDay.clamp(today.minusDays(3), today))
    }

    @Test fun pickerMillisAreTheCalendarDateInUtcWhateverTheZone() {
        val d = LocalDate.of(2026, 11, 9)
        val millis = NutritionDay.toPickerMillis(d)
        assertEquals(d.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), millis)
        assertEquals(d, NutritionDay.fromPickerMillis(millis, today))
        // A picker answer later than today is clamped, never kept as a future day.
        assertEquals(today, NutritionDay.fromPickerMillis(NutritionDay.toPickerMillis(today.plusDays(2)), today))
    }

    @Test fun labelsTodayYesterdayAndOthers() {
        assertEquals("Today", NutritionDay.label(today, today))
        assertEquals("Yesterday", NutritionDay.label(today.minusDays(1), today))
        assertTrue(NutritionDay.label(today.minusDays(5), today).contains("8"))
        assertTrue(NutritionDay.label(LocalDate.of(2025, 12, 31), today).contains("2025"))
    }

    @Test fun todayLogsNowUnlessATimeWasEntered() {
        val now = today.atTime(10, 17, 42).atZone(la).toInstant().toEpochMilli()
        assertEquals(now, NutritionDay.loggingMillis(today, MealSlot.BREAKFAST, now, la))
        val entered = NutritionDay.loggingMillis(today, MealSlot.BREAKFAST, now, la, LocalTime.of(7, 45))
        assertEquals(today.atTime(7, 45).atZone(la).toInstant().toEpochMilli(), entered)
    }

    @Test fun aPastDayLogsAtTheMealsUsualTimeOnThatDay() {
        val now = today.atTime(10, 17).atZone(la).toInstant().toEpochMilli()
        val day = today.minusDays(2)
        val ms = NutritionDay.loggingMillis(day, MealSlot.DINNER, now, la)
        assertEquals(day.atTime(18, 30).atZone(la).toInstant().toEpochMilli(), ms)
        assertEquals(NutritionDay.usualTime(MealSlot.DINNER), NutritionDay.timeOf(ms, la))
        assertTrue(ms in DayRange.of(day, la))
    }

    @Test fun anEnteredTimeOnAPastDayStaysOnThatDay() {
        val now = today.atTime(10, 17).atZone(la).toInstant().toEpochMilli()
        val day = today.minusDays(1)
        val ms = NutritionDay.loggingMillis(day, MealSlot.SNACK, now, la, LocalTime.of(23, 59))
        assertTrue(ms in DayRange.of(day, la))
        val early = NutritionDay.loggingMillis(day, MealSlot.SNACK, now, la, LocalTime.MIDNIGHT)
        assertEquals(DayRange.of(day, la).startInclusive, early)
    }

    @Test fun theDayBoundaryIsLocalMidnightNotUtc() {
        // 23:30 on Nov 12 in Los Angeles is already Nov 13 in UTC, but it is still Nov 12 for the person.
        val lateEvening = LocalDate.of(2026, 11, 12).atTime(23, 30).atZone(la).toInstant().toEpochMilli()
        val nowNextMorning = today.atTime(9, 0).atZone(la).toInstant().toEpochMilli()
        assertTrue(lateEvening in DayRange.of(LocalDate.of(2026, 11, 12), la))
        assertFalse(lateEvening in DayRange.of(today, la))
        // Logging "now" for the 13th does not land on the 12th, and a past-day entry at the 12th's usual time stays on the 12th.
        assertEquals(nowNextMorning, NutritionDay.loggingMillis(today, MealSlot.BREAKFAST, nowNextMorning, la))
        val past = NutritionDay.loggingMillis(LocalDate.of(2026, 11, 12), MealSlot.SNACK, nowNextMorning, la)
        assertTrue(past in DayRange.of(LocalDate.of(2026, 11, 12), la))
    }

    @Test fun aSpringForwardGapStillLandsOnTheSameDay() {
        // 2026-03-08 02:30 does not exist in Los Angeles; the result still falls on that local day.
        val day = LocalDate.of(2026, 3, 8)
        val now = LocalDate.of(2026, 3, 20).atTime(9, 0).atZone(la).toInstant().toEpochMilli()
        val ms = NutritionDay.loggingMillis(day, MealSlot.SNACK, now, la, LocalTime.of(2, 30))
        assertTrue(ms in DayRange.of(day, la))
    }

    @Test fun timeOfDropsSeconds() {
        val ms = today.atTime(10, 17, 59).atZone(la).toInstant().toEpochMilli()
        assertEquals(LocalTime.of(10, 17), NutritionDay.timeOf(ms, la))
    }
}
