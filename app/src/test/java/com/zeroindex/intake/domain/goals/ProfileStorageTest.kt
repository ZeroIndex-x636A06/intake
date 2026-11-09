package com.zeroindex.intake.domain.goals

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.domain.nutrition.MacroTarget
import com.zeroindex.intake.domain.training.generator.Experience
import com.zeroindex.intake.domain.training.generator.TrainingGoal
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Test

/** The profile lives in DataStore as JSON, so older and newer saved values must keep loading. */
class ProfileStorageTest {

    // The same configuration SettingsRepository uses.
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `a fully filled profile survives a round trip`() {
        val profile = UserProfile(
            sex = Sex.FEMALE, birthYear = 1991, heightCm = 168.5, bodyFatPercent = 27.0,
            build = BuildType.HEAVIER, phase = Phase.CONTEST_PREP, pace = Pace.FAST,
            activity = ActivityLevel.PHYSICAL, trainingDaysPerWeek = 5, sessionMinutes = 75,
            experience = Experience.EXPERIENCED, trainingGoal = TrainingGoal.GET_STRONGER,
            mode = ProfileMode.ADVANCED, proteinPerKg = 2.5, fatPerKg = 0.7, carbCycling = true,
            carbCycleShiftPct = 35, refeedDaysPerWeek = 2, dietBreakEveryWeeks = 10,
            contestWeeklyLossPct = 0.5, reverseStepKcal = 100, trainingWeekdays = setOf(1, 3, 5), allowBelowFloor = true,
        )
        assertThat(json.decodeFromString<UserProfile>(json.encodeToString(profile))).isEqualTo(profile)
    }

    @Test
    fun `an empty or older value loads with defaults`() {
        val empty = json.decodeFromString<UserProfile>("{}")
        assertThat(empty).isEqualTo(UserProfile())
        assertThat(empty.hasBasics).isFalse()
        val older = json.decodeFromString<UserProfile>("""{"sex":"MALE","birthYear":1990,"heightCm":180.0}""")
        assertThat(older.hasBasics).isTrue()
        assertThat(older.mode).isEqualTo(ProfileMode.SIMPLE)
        assertThat(older.phase).isEqualTo(Phase.MAINTAIN)
    }

    @Test
    fun `unknown keys from a newer version are ignored`() {
        val p = json.decodeFromString<UserProfile>("""{"sex":"FEMALE","someFutureField":42}""")
        assertThat(p.sex).isEqualTo(Sex.FEMALE)
    }

    @Test
    fun `baseline state and applied plans round trip`() {
        val state = BaselineState(startedAtMillis = 1_700_000_000_000, notifiedAtMillis = 1_700_600_000_000)
        assertThat(json.decodeFromString<BaselineState>(json.encodeToString(state))).isEqualTo(state)
        assertThat(state.active).isTrue()
        assertThat(state.copy(completed = true).active).isFalse()
        assertThat(BaselineState().active).isFalse()

        val plans = listOf(
            AppliedPlan(1_700_000_000_000, Phase.CUT, 2_400, 190, 270, 60, 2_800, 3_000, TdeeSource.BLENDED, "first"),
            AppliedPlan(1_690_000_000_000, Phase.MAINTAIN, 3_000, 150, 400, 80, 2_800, 3_000, TdeeSource.ESTIMATE, "older", "Program"),
        )
        assertThat(json.decodeFromString<List<AppliedPlan>>(json.encodeToString(plans))).isEqualTo(plans)
    }

    @Test
    fun `a week pattern round trips`() {
        val base = MacroPlanner.plan(2_400, 80.0, null, Phase.CUT)
        val week = WeekPlanner.build(base, 3_000, 1_980, setOf(1, 3, 5), true, 25, 1).pattern
        assertThat(json.decodeFromString<WeekPattern>(json.encodeToString(week))).isEqualTo(week)
    }

    // --- Applying a week pattern to a stored target ----------------------------------------

    private val base = MacroPlanner.plan(2_400, 80.0, null, Phase.CUT)
    private val pattern = WeekPlanner.build(base, 3_000, 1_980, setOf(1, 3, 5), true, 25, 1).pattern
    private val stored = MacroTarget(base.kcal, base.proteinG, base.carbsG, base.fatG)

    @Test
    fun `the pattern gives each weekday its own target while the stored target is unchanged`() {
        val monday = pattern.targetFor(stored, 1)
        val tuesday = pattern.targetFor(stored, 2)
        val friday = pattern.targetFor(stored, 5)
        assertThat(monday.carbsG).isGreaterThan(tuesday.carbsG)
        assertThat(friday.kcal).isGreaterThan(2_900)
        assertThat(monday.proteinG).isEqualTo(stored.proteinG)
    }

    @Test
    fun `a stored target that was edited since wins over the pattern`() {
        val edited = stored.copy(kcal = 2_200)
        assertThat(pattern.targetFor(edited, 5)).isEqualTo(edited)
    }
}
