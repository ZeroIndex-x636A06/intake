package com.zeroindex.intake.domain.goals

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.domain.training.ScheduleDay
import com.zeroindex.intake.domain.training.TodayPlan
import com.zeroindex.intake.domain.training.WorkoutSchedule
import com.zeroindex.intake.domain.training.generator.BodyArea
import com.zeroindex.intake.domain.training.generator.Experience
import com.zeroindex.intake.domain.training.generator.GeneratorInputs
import com.zeroindex.intake.domain.training.generator.generateProgram
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Test

/** The plan creator's answers reach the generator, the schedule and the "how built" text. */
class TrainingAnswersTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `focus muscles and areas to go easy on reach the generator`() {
        val profile = UserProfile(focusMuscles = setOf("back", "triceps", "not-a-muscle"), avoidAreas = setOf(BodyArea.KNEE, BodyArea.SHOULDER))
        val inputs = profile.toGeneratorInputs()
        assertThat(inputs.focusMuscles).containsExactly("back", "triceps")
        assertThat(inputs.avoid).containsExactly(BodyArea.KNEE, BodyArea.SHOULDER)

        val plain = generateProgram(UserProfile().toGeneratorInputs())
        val focused = generateProgram(inputs)
        assertThat(focused.weeklySets.getValue("back")).isAtLeast(plain.weeklySets.getValue("back"))
        // Nothing that loads a protected joint is in the plan.
        val loaded = focused.days.flatMap { it.exercises }.mapNotNull { com.zeroindex.intake.domain.training.generator.ExerciseCatalog.byId(it.exerciseId) }
            .flatMap { it.loads }.toSet()
        assertThat(loaded.intersect(setOf(BodyArea.KNEE, BodyArea.SHOULDER))).isEmpty()
    }

    @Test
    fun `chosen weekdays set the number of training days`() {
        val p = UserProfile(trainingDaysPerWeek = 4, trainingWeekdays = setOf(1, 3, 5))
        assertThat(p.effectiveTrainingDays).isEqualTo(3)
        assertThat(p.toGeneratorInputs().daysPerWeek).isEqualTo(3)
        assertThat(UserProfile(trainingDaysPerWeek = 5).toGeneratorInputs().daysPerWeek).isEqualTo(5)
    }

    @Test
    fun `the answers survive the stored profile and older values load`() {
        val p = UserProfile(focusMuscles = setOf("chest"), avoidAreas = setOf(BodyArea.LOWER_BACK), trainingWeekdays = setOf(2, 4))
        assertThat(json.decodeFromString<UserProfile>(json.encodeToString(p))).isEqualTo(p)
        val old = json.decodeFromString<UserProfile>("""{"sex":"MALE","trainingDaysPerWeek":3}""")
        assertThat(old.focusMuscles).isEmpty()
        assertThat(old.avoidAreas).isEmpty()
    }

    // ---------------------------------------------------------------- schedule

    @Test
    fun `chosen weekdays are given to the days in order, spread when none are chosen`() {
        assertThat(WorkoutSchedule.weekdaysFor(3, setOf(5, 1, 3))).containsExactly(1, 3, 5).inOrder()
        assertThat(WorkoutSchedule.weekdaysFor(3, emptySet())).containsExactly(1, 3, 5).inOrder()
        assertThat(WorkoutSchedule.weekdaysFor(4, setOf(1, 3, 5))).containsExactly(null, null, null, null)
        assertThat(WorkoutSchedule.weekdaysFor(0, emptySet())).isEmpty()
    }

    @Test
    fun `a generated program is scheduled on the profile weekdays`() {
        val p = UserProfile(trainingWeekdays = setOf(2, 4, 6))
        val program = WorkoutSchedule.schedule(generateProgram(p.toGeneratorInputs()), p.trainingWeekdays)
        assertThat(program.days.map { it.weekday }).containsExactly(2, 4, 6).inOrder()
    }

    private val week = listOf(ScheduleDay(0, "Upper A", 1), ScheduleDay(1, "Lower A", 3), ScheduleDay(2, "Upper B", 5))

    @Test
    fun `today's workout follows the weekday plan`() {
        val monday = WorkoutSchedule.planFor(week, rotationIndex = 2, isoToday = 1)
        assertThat(monday).isEqualTo(TodayPlan.Train(week[0], byWeekday = true))
        val tuesday = WorkoutSchedule.planFor(week, 0, isoToday = 2) as TodayPlan.Rest
        assertThat(tuesday.next.name).isEqualTo("Lower A")
        assertThat(tuesday.daysAway).isEqualTo(1)
        val sunday = WorkoutSchedule.planFor(week, 0, isoToday = 7) as TodayPlan.Rest
        assertThat(sunday.next.name).isEqualTo("Upper A")
        assertThat(sunday.daysAway).isEqualTo(1)
        val saturday = WorkoutSchedule.planFor(week, 0, isoToday = 6) as TodayPlan.Rest
        assertThat(saturday.next.name).isEqualTo("Upper A")
        assertThat(saturday.daysAway).isEqualTo(2)
    }

    @Test
    fun `a program without weekdays keeps the rotation`() {
        val rotation = week.map { it.copy(weekday = null) }
        assertThat(WorkoutSchedule.planFor(rotation, 1, isoToday = 4)).isEqualTo(TodayPlan.Train(rotation[1], byWeekday = false))
        assertThat(WorkoutSchedule.planFor(rotation, 9, isoToday = 4)).isEqualTo(TodayPlan.Train(rotation[2], byWeekday = false))
        assertThat(WorkoutSchedule.planFor(emptyList(), 0, 1)).isEqualTo(TodayPlan.NoDays)
    }

    // ---------------------------------------------------------------- explanations

    @Test
    fun `every exercise says why it is there, from the answers that picked it`() {
        val inputs = GeneratorInputs(daysPerWeek = 4, focusMuscles = setOf("back"), avoid = setOf(BodyArea.KNEE))
        val program = generateProgram(inputs)
        val all = program.days.flatMap { it.exercises }
        assertThat(all.all { it.why.startsWith(it.name + ":") && it.why.endsWith(".") }).isTrue()
        val pulldown = all.first { it.name == "Lat pulldown" }
        assertThat(pulldown.why).contains("back is a focus")
        assertThat(pulldown.why).contains("of your 4 days train back")
        assertThat(pulldown.why).contains("cable machine available")
        assertThat(pulldown.why).contains("does not load your knee")
        assertThat(all.all { it.prescription.isNotBlank() }).isTrue()
    }

    @Test
    fun `how built lists what was used and what was not`() {
        val profile = UserProfile(
            trainingGoal = null, phase = Phase.CUT, trainingWeekdays = setOf(1, 3, 5), focusMuscles = setOf("glutes"),
            experience = Experience.NEW,
        )
        val inputs = profile.toGeneratorInputs(hiddenExerciseIds = setOf("x"))
        val program = WorkoutSchedule.schedule(generateProgram(inputs), profile.trainingWeekdays)
        val hb = TrainingExplainer.howBuilt(inputs, program, profile)
        assertThat(hb.used.any { it.startsWith("Training goal: Lose fat") && "cut" in it }).isTrue()
        assertThat(hb.used.any { it.contains("Mon, Wed, Fri") }).isTrue()
        assertThat(hb.used.any { it.startsWith("Focus muscles: glutes") }).isTrue()
        assertThat(hb.used.any { it.startsWith("Hidden exercises: 1") }).isTrue()
        assertThat(hb.used.any { it.startsWith("Starting weights: none") }).isTrue()
        assertThat(hb.notUsed).contains("Your build and age do not change exercise choice; they affect calories.")
        assertThat(hb.notUsed.any { it.startsWith("Areas to go easy on: none") }).isTrue()
        assertThat(hb.text).contains("Not used:")
    }

    @Test
    fun `how built mentions chosen avoid areas and starting weights`() {
        val profile = UserProfile(avoidAreas = setOf(BodyArea.WRIST), trainingGoal = com.zeroindex.intake.domain.training.generator.TrainingGoal.GET_STRONGER)
        val inputs = profile.toGeneratorInputs()
        val program = com.zeroindex.intake.domain.training.StartingWeightEstimator.apply(
            generateProgram(inputs),
            com.zeroindex.intake.domain.training.StartingWeightContext(Experience.SOME, 80.0, Sex.MALE),
        ) { emptyList() }
        val hb = TrainingExplainer.howBuilt(inputs, program, profile)
        assertThat(hb.used.any { it.startsWith("Go easy on: wrist") }).isTrue()
        assertThat(hb.used.any { it.contains("careful guess from your body weight") }).isTrue()
        assertThat(hb.notUsed.any { it.contains("They did not pick the training goal") }).isTrue()
    }
}
