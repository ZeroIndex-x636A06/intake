package com.zeroindex.intake.domain.coach

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.domain.training.Prescription
import java.time.LocalDate
import kotlinx.serialization.json.Json
import org.junit.Test

class CoachSupportTest {

    private val rx = Prescription(weightKg = 100.0, sets = 4, repsMin = 5, repsMax = 5, reason = "Add weight.")

    // ---- adjustments

    @Test fun aDeloadWeekLightensTheBarAndRoundsToThePlates() {
        val state = CoachState(deloadUntilMillis = 2_000, deloadPercent = 10)
        val out = CoachAdjustments.apply(rx, state, nowMillis = 1_000, incrementKg = 2.5)
        assertThat(out.weightKg).isEqualTo(90.0)
        assertThat(out.isDeload).isTrue()
        assertThat(out.reason).contains("deload")
        assertThat(CoachAdjustments.apply(rx.copy(weightKg = 60.0), state, 1_000, 2.5).weightKg).isEqualTo(55.0)
    }

    @Test fun anExpiredDeloadChangesNothing() {
        val state = CoachState(deloadUntilMillis = 500)
        assertThat(CoachAdjustments.apply(rx, state, nowMillis = 1_000, incrementKg = 2.5)).isEqualTo(rx)
    }

    @Test fun anEasierDayDropsOneSetButNeverBelowOne() {
        val state = CoachState(easyUntilMillis = 2_000, easySetsLess = 1)
        assertThat(CoachAdjustments.apply(rx, state, 1_000, 2.5).sets).isEqualTo(3)
        assertThat(CoachAdjustments.apply(rx.copy(sets = 1), state, 1_000, 2.5).sets).isEqualTo(1)
    }

    @Test fun bodyweightWorkIsNotLightened() {
        val state = CoachState(deloadUntilMillis = 2_000)
        assertThat(CoachAdjustments.apply(rx.copy(weightKg = 0.0), state, 1_000, 2.5).weightKg).isEqualTo(0.0)
    }

    @Test fun addedSetsGoToTheExerciseWithTheFewestAndStayInRange() {
        val out = CoachAdjustments.distributeSets(listOf("a" to 3, "b" to 4, "c" to 3), +2)
        assertThat(out).containsExactly("a", 4, "b", 4, "c", 4)
        val capped = CoachAdjustments.distributeSets(listOf("a" to 8, "b" to 8), +2)
        assertThat(capped.values.sum()).isEqualTo(16)
    }

    @Test fun removedSetsComeFromTheBiggestAndNeverBelowTwo() {
        val out = CoachAdjustments.distributeSets(listOf("a" to 5, "b" to 3), -2)
        assertThat(out).containsExactly("a", 3, "b", 3)
        assertThat(CoachAdjustments.distributeSets(listOf("a" to 2), -2)["a"]).isEqualTo(2)
    }

    // ---- schedule

    @Test fun theLatestScheduledDayIsTodayOrEarlier() {
        val monday = LocalDate.of(2026, 11, 9).toEpochDay()
        assertThat(CoachSchedule.latestScheduledDay(monday, 1)).isEqualTo(monday)
        assertThat(CoachSchedule.latestScheduledDay(monday + 2, 1)).isEqualTo(monday)
        assertThat(CoachSchedule.latestScheduledDay(monday + 6, 1)).isEqualTo(monday)
        assertThat(CoachSchedule.latestScheduledDay(monday + 1, 7)).isEqualTo(monday - 1)
    }

    @Test fun aCheckInIsDueOncePerScheduledDay() {
        val monday = LocalDate.of(2026, 11, 9).toEpochDay()
        assertThat(CoachSchedule.isDue(monday, 1, null)).isTrue()
        assertThat(CoachSchedule.isDue(monday, 1, monday)).isFalse()
        assertThat(CoachSchedule.isDue(monday + 3, 1, monday)).isFalse()
        assertThat(CoachSchedule.isDue(monday + 7, 1, monday)).isTrue()
        // A phone that was off on Monday still gets it on Tuesday.
        assertThat(CoachSchedule.isDue(monday + 1, 1, monday - 7)).isTrue()
    }

    @Test fun theWeekdayDefaultsToTheBaselineDayThenMonday() {
        assertThat(CoachSchedule.weekday(3, 5)).isEqualTo(3)
        assertThat(CoachSchedule.weekday(null, 5)).isEqualTo(5)
        assertThat(CoachSchedule.weekday(null, null)).isEqualTo(1)
    }

    // ---- history

    @Test fun decidedSuggestionsAreSuppressedForAWeek() {
        val h = listOf(
            SuggestionHistory.Decided("protein", SuggestionStatus.DISMISSED, 100),
            SuggestionHistory.Decided("water", SuggestionStatus.DISMISSED, 90),
            SuggestionHistory.Decided("gap", SuggestionStatus.PENDING, 100),
        )
        assertThat(SuggestionHistory.suppressedKeys(h, today = 103)).containsExactly("protein")
    }

    @Test fun anAppliedCalorieChangeStartsACooldown() {
        val h = listOf(SuggestionHistory.Decided("cal:down", SuggestionStatus.APPLIED, 100))
        assertThat(SuggestionHistory.calorieCooldown(h, 110)).isTrue()
        assertThat(SuggestionHistory.calorieCooldown(h, 114)).isFalse()
        assertThat(SuggestionHistory.calorieCooldown(listOf(SuggestionHistory.Decided("cal:down", SuggestionStatus.DISMISSED, 100)), 101)).isFalse()
    }

    // ---- reword

    @Test fun aRewriteMustKeepEveryNumber() {
        val original = "You averaged 120 g of protein; the target is 170 g (71%)."
        assertThat(CoachReword.accept(original, "Protein averaged 120 g against a 170 g goal, which is 71%.")).contains("goal")
        assertThat(CoachReword.accept(original, "Protein averaged 125 g against a 170 g goal, which is 71%.")).isEqualTo(original)
        assertThat(CoachReword.accept(original, "Protein is low. Eat 30 g more.")).isEqualTo(original)
        assertThat(CoachReword.accept(original, null)).isEqualTo(original)
        assertThat(CoachReword.accept(original, "  ")).isEqualTo(original)
        assertThat(CoachReword.accept(original, "x".repeat(500))).isEqualTo(original)
    }

    // ---- protein ideas

    @Test fun onlyRealProteinFoodsQualify() {
        val chicken = ProteinIdeas.Food("Chicken breast", 31.0, 165.0, 120.0)
        val bread = ProteinIdeas.Food("Bread", 9.0, 265.0, 40.0)
        val oil = ProteinIdeas.Food("Olive oil", 0.0, 884.0, 14.0)
        assertThat(ProteinIdeas.qualifies(chicken)).isTrue()
        assertThat(ProteinIdeas.qualifies(bread)).isFalse()
        assertThat(ProteinIdeas.qualifies(oil)).isFalse()
        assertThat(ProteinIdeas.pick(listOf(bread, oil, chicken))).containsExactly("Chicken breast (37 g protein in 120 g)")
    }

    // ---- state storage

    @Test fun coachStateSurvivesJson() {
        val s = CoachState(lastCheckInDay = 5, checkIns = listOf(CheckInRecord(5, 2, "2 suggestions")), deloadUntilMillis = 10)
        val json = Json { ignoreUnknownKeys = true }
        assertThat(json.decodeFromString<CoachState>(json.encodeToString(CoachState.serializer(), s))).isEqualTo(s)
        assertThat(json.decodeFromString<CoachState>("{}")).isEqualTo(CoachState())
    }

    @Test fun everyActionSurvivesJson() {
        val json = Json
        listOf(
            CoachAction.SetCalories(2_200, 2_050),
            CoachAction.Deload("p", "Upper", 10, 7),
            CoachAction.SwapExercise("a", "A", "b", "B"),
            CoachAction.EasierDay(1, 3),
            CoachAction.MuscleSets("chest", 2),
            CoachAction.Acknowledge("ok"),
        ).forEach { action ->
            val text = json.encodeToString(CoachAction.serializer(), action)
            assertThat(json.decodeFromString(CoachAction.serializer(), text)).isEqualTo(action)
        }
    }
}
