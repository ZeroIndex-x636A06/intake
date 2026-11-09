package com.zeroindex.intake.domain.coach

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DailyFocusTest {

    private fun base() = FocusInputs(
        pendingSuggestions = 0,
        weighInMissing = false,
        hourOfDay = 14,
        proteinSoFarG = 100.0,
        proteinTargetG = 170,
        workoutScheduledToday = false,
    )

    @Test fun aPendingCheckInComesFirst() {
        val f = DailyFocusPicker.pick(
            base().copy(pendingSuggestions = 3, weighInMissing = true, workoutScheduledToday = true, baselineMissing = "weigh-ins"),
        )
        assertThat(f.target).isEqualTo(FocusTarget.CHECK_IN)
        assertThat(f.line).contains("3 suggestions")
    }

    @Test fun thenAMissingWeighIn() {
        val f = DailyFocusPicker.pick(base().copy(weighInMissing = true, workoutScheduledToday = true, hourOfDay = 20, proteinSoFarG = 10.0))
        assertThat(f.target).isEqualTo(FocusTarget.LOG_WEIGHT)
    }

    @Test fun thenProteinFarBehindButOnlyInTheEvening() {
        val evening = DailyFocusPicker.pick(base().copy(hourOfDay = 19, proteinSoFarG = 60.0, workoutScheduledToday = true))
        assertThat(evening.target).isEqualTo(FocusTarget.LOG_FOOD)
        assertThat(evening.line).contains("60 of 170 g")
        val afternoon = DailyFocusPicker.pick(base().copy(hourOfDay = 15, proteinSoFarG = 60.0, workoutScheduledToday = true))
        assertThat(afternoon.target).isEqualTo(FocusTarget.WORKOUT)
    }

    @Test fun proteinAboveSixtyPercentIsNotFarBehind() {
        val f = DailyFocusPicker.pick(base().copy(hourOfDay = 20, proteinSoFarG = 110.0))
        assertThat(f.title).isNotEqualTo("Protein is behind")
    }

    @Test fun thenTodaysWorkoutUnlessItIsDone() {
        val f = DailyFocusPicker.pick(base().copy(workoutScheduledToday = true, workoutName = "Push A"))
        assertThat(f.target).isEqualTo(FocusTarget.WORKOUT)
        assertThat(f.line).contains("Push A")
        val done = DailyFocusPicker.pick(base().copy(workoutScheduledToday = true, workoutDoneToday = true))
        assertThat(done.target).isNotEqualTo(FocusTarget.WORKOUT)
    }

    @Test fun thenWaterBehindForTheTimeOfDay() {
        val behind = DailyFocusPicker.pick(base().copy(hourOfDay = 16, waterSoFarMl = 300.0, waterTargetMl = 2_500.0))
        assertThat(behind.target).isEqualTo(FocusTarget.WATER)
        val fine = DailyFocusPicker.pick(base().copy(hourOfDay = 16, waterSoFarMl = 1_500.0, waterTargetMl = 2_500.0))
        assertThat(fine.target).isNotEqualTo(FocusTarget.WATER)
        val early = DailyFocusPicker.pick(base().copy(hourOfDay = 9, waterSoFarMl = 0.0, waterTargetMl = 2_500.0))
        assertThat(early.target).isNotEqualTo(FocusTarget.WATER)
    }

    @Test fun thenBaselineItems() {
        val f = DailyFocusPicker.pick(base().copy(baselineMissing = "2 more weigh-ins"))
        assertThat(f.target).isEqualTo(FocusTarget.BASELINE)
        assertThat(f.line).contains("2 more weigh-ins")
    }

    @Test fun otherwiseAnEncouragingLineWithARealNumber() {
        val streak = DailyFocusPicker.pick(base().copy(loggingStreakDays = 3))
        assertThat(streak.onTrack).isTrue()
        assertThat(streak.line).isEqualTo("3-day logging streak. Keep it going.")
        assertThat(streak.buttonLabel).isNull()
        assertThat(DailyFocusPicker.pick(base().copy(daysLoggedThisWeek = 4)).line).isEqualTo("4 of 7 days logged this week.")
        assertThat(DailyFocusPicker.pick(base().copy(workoutsThisWeek = 2)).line).isEqualTo("2 workouts done this week.")
    }

    @Test fun anEmptyAppAsksForTheFirstMeal() {
        val f = DailyFocusPicker.pick(base())
        assertThat(f.onTrack).isFalse()
        assertThat(f.target).isEqualTo(FocusTarget.LOG_FOOD)
    }

    @Test fun weighInDaysFollowTheHabit() {
        val today = 1_000L
        assertThat(DailyFocusPicker.weighInExpected(today, listOf(today))).isFalse()
        // Weighs most days.
        assertThat(DailyFocusPicker.weighInExpected(today, listOf(today - 1, today - 2, today - 3, today - 5))).isTrue()
        // Weighs on this weekday weekly.
        assertThat(DailyFocusPicker.weighInExpected(today, listOf(today - 7, today - 14))).isTrue()
        // Rare weigh-ins on other days.
        assertThat(DailyFocusPicker.weighInExpected(today, listOf(today - 3, today - 9))).isFalse()
        assertThat(DailyFocusPicker.weighInExpected(today, emptyList(), baselineNeedsWeighIns = true)).isTrue()
    }

    @Test fun theStreakCountsBackFromTodayOrYesterday() {
        assertThat(DailyFocusPicker.streak(10, setOf(10L, 9L, 8L, 5L))).isEqualTo(3)
        assertThat(DailyFocusPicker.streak(10, setOf(9L, 8L))).isEqualTo(2)
        assertThat(DailyFocusPicker.streak(10, setOf(7L))).isEqualTo(0)
    }
}
