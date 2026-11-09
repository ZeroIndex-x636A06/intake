package com.zeroindex.intake.domain.goals

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.domain.training.generator.Experience
import com.zeroindex.intake.domain.training.generator.TrainingGoal
import com.zeroindex.intake.domain.training.generator.generateProgram
import org.junit.Test

class GoalBuilderTest {

    private val profile = UserProfile(
        sex = Sex.MALE,
        birthYear = 1996,
        heightCm = 180.0,
        phase = Phase.CUT,
        pace = Pace.STEADY,
        activity = ActivityLevel.STANDING,
        trainingDaysPerWeek = 4,
    )

    private val baseline = MeasuredTdee(
        tdee = 3_000.0, averageIntake = 2_500.0, slopeKgPerDay = -0.065,
        loggedDays = 7, weighInDays = 5, spanDays = 7,
    )

    private fun inputs(
        p: UserProfile = profile,
        weight: Double = 80.0,
        measured: MeasuredTdee? = null,
        steps: Double? = null,
    ) = GoalInputs(profile = p, weightKg = weight, currentYear = 2026, averageSteps = steps, baseline = measured)

    @Test
    fun `day one plan is built from the formula and says so`() {
        val plan = GoalBuilder.build(inputs())
        // BMR = 800 + 1125 - 150 + 5 = 1780; factor = 1.375 + 4 x 0.03 = 1.495.
        assertThat(plan.bmr.kcal).isWithin(0.01).of(1_780.0)
        assertThat(plan.activity.value).isWithin(1e-9).of(1.495)
        assertThat(plan.estimate).isWithin(0.01).of(1_780.0 * 1.495)
        assertThat(plan.tdeeSource).isEqualTo(TdeeSource.ESTIMATE)
        assertThat(plan.tdee).isEqualTo(plan.estimate)
        assertThat(plan.calories.kcal).isEqualTo(Math.round(plan.estimate * 0.8).toInt())
        assertThat(plan.warnings.any { it.text.contains("starts from a formula") }).isTrue()
        assertThat(plan.calorieCard.why).contains("estimated burn")
    }

    @Test
    fun `with a baseline the card names the measured burn and its numbers`() {
        val plan = GoalBuilder.build(inputs(measured = baseline))
        assertThat(plan.tdeeSource).isEqualTo(TdeeSource.BLENDED)
        val burn = Math.round(plan.tdee).toInt()
        val kcal = plan.calories.kcal
        val why = plan.calorieCard.why
        assertThat(why).contains("%,d".format(java.util.Locale.US, kcal))
        assertThat(why).contains("%,d".format(java.util.Locale.US, burn))
        assertThat(why).contains("measured-and-estimated burn")
        assertThat(why).contains("20%")
        assertThat(why).contains("steady cut")
        assertThat(plan.calorieCard.detail).contains("3,000")
        assertThat(plan.calorieCard.detail).contains("2,500")
        assertThat(plan.tdeeWhy).contains("80%")
    }

    @Test
    fun `every card carries a headline a why and a detail with the key numbers`() {
        val plan = GoalBuilder.build(inputs(measured = baseline))
        val m = plan.macros
        assertThat(plan.macroCard.headline).contains("${m.proteinG} g")
        assertThat(plan.macroCard.headline).contains("${m.carbsG} g")
        assertThat(plan.macroCard.headline).contains("${m.fatG} g")
        assertThat(plan.macroCard.why).contains("2.4 g/kg")
        assertThat(plan.macroCard.detail).contains("${m.proteinG} g")
        // 80 kg x 35 ml = 2,800 ml.
        assertThat(plan.waterCard.headline).contains("2,800")
        assertThat(plan.waterCard.why).contains("35 ml per kg")
        assertThat(plan.waterCard.why).contains("80.0 kg")
        listOf(plan.calorieCard, plan.macroCard, plan.waterCard).forEach {
            assertThat(it.why).isNotEmpty()
            assertThat(it.detail).isNotEmpty()
            assertThat(it.headline).isNotEmpty()
        }
    }

    @Test
    fun `macros add up to the calorie target`() {
        Phase.entries.forEach { phase ->
            val plan = GoalBuilder.build(inputs(p = profile.copy(phase = phase), measured = baseline))
            val diff = kotlin.math.abs(plan.macros.gramsKcal - plan.calories.kcal)
            assertThat(diff.toDouble() / plan.calories.kcal).isAtMost(0.01)
        }
    }

    @Test
    fun `body fat switches to katch mcardle and lean mass protein`() {
        val plan = GoalBuilder.build(inputs(p = profile.copy(bodyFatPercent = 20.0)))
        assertThat(plan.bmr.formula).isEqualTo(BmrFormula.KATCH_MCARDLE)
        assertThat(plan.macros.proteinBasis).isEqualTo(ProteinBasis.LEAN_MASS)
        assertThat(plan.macros.proteinG).isEqualTo(Math.round(64.0 * 2.4).toInt())
    }

    @Test
    fun `steps from health connect replace the chosen activity`() {
        val withSteps = GoalBuilder.build(inputs(steps = 14_000.0))
        assertThat(withSteps.activity.value).isWithin(1e-9).of(1.55 + 4 * 0.03)
        assertThat(withSteps.activity.why).contains("14,000 steps")
    }

    @Test
    fun `simple mode ignores advanced g per kg values and advanced mode uses them`() {
        val tuned = profile.copy(proteinPerKg = 3.0, fatPerKg = 1.2)
        val simple = GoalBuilder.build(inputs(p = tuned.copy(mode = ProfileMode.SIMPLE)))
        assertThat(simple.macros.proteinPerKg).isWithin(0.01).of(2.4)
        val advanced = GoalBuilder.build(inputs(p = tuned.copy(mode = ProfileMode.ADVANCED)))
        assertThat(advanced.macros.proteinPerKg).isWithin(0.01).of(3.0)
        assertThat(advanced.macros.fatPerKg).isWithin(0.01).of(1.2)
    }

    @Test
    fun `edits override calories protein and water and the rest follows`() {
        val auto = GoalBuilder.build(inputs(measured = baseline))
        val edited = GoalBuilder.build(
            inputs(measured = baseline),
            PlanOverrides(kcal = 2_600, proteinPerKg = 2.0, waterMl = 3_500),
        )
        assertThat(edited.calories.kcal).isEqualTo(2_600)
        assertThat(edited.calorieCard.why).contains("number you chose")
        assertThat(edited.macros.proteinG).isEqualTo(160)
        assertThat(kotlin.math.abs(edited.macros.gramsKcal - 2_600)).isAtMost(8)
        assertThat(edited.water.baseMl).isEqualTo(3_500)
        assertThat(edited.water.why).contains("number you chose")
        assertThat(auto.calories.kcal).isNotEqualTo(2_600)
    }

    @Test
    fun `an edit below the floor is still raised without the advanced override`() {
        val plan = GoalBuilder.build(inputs(), PlanOverrides(kcal = 1_000))
        assertThat(plan.calories.kcal).isEqualTo(Math.round(1_780.0 * 1.1).toInt())
        assertThat(plan.warnings.any { it.text.contains("Raised") }).isTrue()
        val allowed = GoalBuilder.build(
            inputs(p = profile.copy(mode = ProfileMode.ADVANCED, allowBelowFloor = true)),
            PlanOverrides(kcal = 1_000),
        )
        assertThat(allowed.calories.kcal).isEqualTo(1_000)
        // The override only counts in Advanced mode.
        val simpleWithFlag = GoalBuilder.build(inputs(p = profile.copy(allowBelowFloor = true)), PlanOverrides(kcal = 1_000))
        assertThat(simpleWithFlag.calories.kcal).isGreaterThan(1_000)
    }

    @Test
    fun `under 18 gets no deficit through the whole builder`() {
        val teen = GoalBuilder.build(inputs(p = profile.copy(birthYear = 2010)))
        assertThat(teen.calories.offsetKcal).isEqualTo(0)
        assertThat(teen.warnings.any { it.text.contains("Under 18") }).isTrue()
    }

    @Test
    fun `week pattern and diet breaks appear only in advanced mode`() {
        val fancy = profile.copy(
            carbCycling = true, refeedDaysPerWeek = 1, dietBreakEveryWeeks = 10, mode = ProfileMode.SIMPLE,
        )
        val simple = GoalBuilder.build(inputs(p = fancy))
        assertThat(simple.week).isNull()
        assertThat(simple.dietBreak).isNull()
        val advanced = GoalBuilder.build(inputs(p = fancy.copy(mode = ProfileMode.ADVANCED), measured = baseline))
        assertThat(advanced.week).isNotNull()
        assertThat(advanced.week!!.pattern.days).hasSize(7)
        assertThat(advanced.dietBreak!!.everyWeeks).isEqualTo(10)
        assertThat(advanced.dietBreak!!.maintenanceKcal).isEqualTo(Math.round(advanced.tdee).toInt())
        // Not a cut: no diet break.
        val bulk = GoalBuilder.build(inputs(p = fancy.copy(mode = ProfileMode.ADVANCED, phase = Phase.BULK)))
        assertThat(bulk.dietBreak).isNull()
    }

    @Test
    fun `the builder is deterministic`() {
        val a = GoalBuilder.build(inputs(measured = baseline, steps = 9_100.0))
        val b = GoalBuilder.build(inputs(measured = baseline, steps = 9_100.0))
        assertThat(a).isEqualTo(b)
    }

    @Test
    fun `a profile without basics cannot be built`() {
        val result = runCatching { GoalBuilder.build(inputs(p = UserProfile())) }
        assertThat(result.isFailure).isTrue()
        assertThat(UserProfile().hasBasics).isFalse()
        assertThat(profile.hasBasics).isTrue()
    }

    // --- Water ----------------------------------------------------------------------------

    @Test
    fun `water is the same maths as the today card plus a training bonus`() {
        val w = WaterPlanner.plan(weightKg = 84.0, trainingDaysPerWeek = 4)
        // 84 x 35 = 2,940, rounded to 2,950, like the Today water card.
        assertThat(w.baseMl).isEqualTo(2_950)
        assertThat(w.trainingDayMl).isEqualTo(3_450)
        assertThat(w.why).contains("2,950")
        assertThat(w.why).contains("4 training days")
    }

    @Test
    fun `water takes extra adjustments without changing the function`() {
        val w = WaterPlanner.plan(80.0, 3, extras = listOf(WaterAdjustment("for hot weather", 400)))
        assertThat(w.baseMl).isEqualTo(3_200)
        assertThat(w.why).contains("plus 400 ml for hot weather")
    }

    // --- Profile -> generator ------------------------------------------------------------

    @Test
    fun `the profile feeds the program generator`() {
        val p = profile.copy(
            trainingDaysPerWeek = 3, sessionMinutes = 45, experience = Experience.NEW, trainingGoal = null,
        )
        val inputs = p.toGeneratorInputs()
        assertThat(inputs.daysPerWeek).isEqualTo(3)
        assertThat(inputs.sessionMinutes).isEqualTo(45)
        assertThat(inputs.experience).isEqualTo(Experience.NEW)
        // A cut with no explicit goal trains for fat loss.
        assertThat(inputs.goal).isEqualTo(TrainingGoal.LOSE_FAT)
        val program = generateProgram(inputs)
        val summary = TrainingSummaries.of(program, p)
        assertThat(summary.headline).contains("3 days a week")
        assertThat(summary.why).contains("3 days")
        assertThat(summary.why).contains("45 minutes")
        assertThat(summary.lines).isNotEmpty()
    }

    @Test
    fun `an explicit training goal beats the phase default`() {
        assertThat(profile.copy(trainingGoal = TrainingGoal.GET_STRONGER).toGeneratorInputs().goal)
            .isEqualTo(TrainingGoal.GET_STRONGER)
        assertThat(profile.copy(phase = Phase.BULK).effectiveTrainingGoal).isEqualTo(TrainingGoal.BUILD_MUSCLE)
    }

    @Test
    fun `spread weekdays match the number of days`() {
        (1..7).forEach { assertThat(GoalDefaults.spreadWeekdays(it)).hasSize(it) }
    }

    @Test
    fun `build picker is copy only and every build has a classic name`() {
        BuildType.entries.forEach {
            assertThat(it.classicName).isNotEmpty()
            assertThat(it.blurb).isNotEmpty()
        }
        // Same body, different build: identical numbers.
        val a = GoalBuilder.build(inputs(p = profile.copy(build = BuildType.SLIM)))
        val b = GoalBuilder.build(inputs(p = profile.copy(build = BuildType.HEAVIER)))
        assertThat(a).isEqualTo(b)
    }
}
