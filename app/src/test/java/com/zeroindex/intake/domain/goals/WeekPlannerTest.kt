package com.zeroindex.intake.domain.goals

import com.google.common.truth.Truth.assertThat
import kotlin.math.abs
import org.junit.Test

class WeekPlannerTest {

    private val base = MacroPlanner.plan(2_400, 80.0, null, Phase.CUT)
    private val training = setOf(1, 3, 5)

    private fun build(
        cycling: Boolean = false,
        shift: Int = 25,
        refeeds: Int = 0,
        tdee: Int = 3_000,
        floor: Int = 1_980,
        trainingDays: Set<Int> = training,
        macros: MacroPlan = base,
    ) = WeekPlanner.build(macros, tdee, floor, trainingDays, cycling, shift, refeeds)

    private fun assertWeeklyTotalHolds(build: WeekBuild, macros: MacroPlan = base) {
        val expected = macros.kcal * 7
        // A handful of grams of rounding across seven days is all that may differ.
        assertThat(abs(build.pattern.weeklyKcal - expected).toDouble() / expected).isAtMost(0.005)
        assertThat(abs(build.pattern.weeklyCarbsG - macros.carbsG * 7)).isAtMost(4)
    }

    @Test
    fun `with nothing switched on every day is the same`() {
        val w = build()
        assertThat(w.pattern.days.map { it.carbsG }.toSet()).containsExactly(base.carbsG)
        assertWeeklyTotalHolds(w)
    }

    @Test
    fun `carb cycling moves carbs to training days and keeps the weekly total`() {
        val w = build(cycling = true, shift = 25)
        val trainCarbs = w.pattern.days.filter { it.kind == DayKind.TRAINING }.map { it.carbsG }.toSet()
        val restCarbs = w.pattern.days.filter { it.kind == DayKind.REST }.map { it.carbsG }.toSet()
        assertThat(trainCarbs).hasSize(1)
        assertThat(restCarbs).hasSize(1)
        assertThat(trainCarbs.single()).isGreaterThan(base.carbsG)
        assertThat(restCarbs.single()).isLessThan(base.carbsG)
        // Training days are 25% above the average of the seven days.
        assertThat(trainCarbs.single().toDouble() / base.carbsG).isWithin(0.02).of(1.25)
        assertWeeklyTotalHolds(w)
        // Protein and fat do not move.
        assertThat(w.pattern.days.map { it.proteinG }.toSet()).containsExactly(base.proteinG)
        assertThat(w.pattern.days.map { it.fatG }.toSet()).containsExactly(base.fatG)
    }

    @Test
    fun `a huge shift cannot empty the rest days`() {
        val w = build(cycling = true, shift = 100)
        val rest = w.pattern.days.first { it.kind == DayKind.REST }
        assertThat(rest.carbsG).isAtLeast((base.carbsG * 0.2).toInt() - 1)
        assertWeeklyTotalHolds(w)
    }

    @Test
    fun `cycling with no rest days falls back to a flat week and says so`() {
        val w = build(cycling = true, trainingDays = (1..7).toSet())
        assertThat(w.pattern.days.map { it.carbsG }.toSet()).hasSize(1)
        assertThat(w.notes.single()).contains("needs both")
        assertWeeklyTotalHolds(w)
    }

    @Test
    fun `refeed days reach maintenance and the other days pay for them`() {
        val w = build(refeeds = 1)
        val refeed = w.pattern.days.single { it.kind == DayKind.REFEED }
        // The last training day of the week.
        assertThat(refeed.weekday).isEqualTo(5)
        assertThat(abs(refeed.kcal - 3_000)).isAtMost(4)
        val others = w.pattern.days.filter { it.kind != DayKind.REFEED }
        assertThat(others.all { it.kcal < base.kcal }).isTrue()
        assertWeeklyTotalHolds(w)
    }

    @Test
    fun `two refeeds still hold the weekly total`() {
        val w = build(refeeds = 2)
        assertThat(w.pattern.days.count { it.kind == DayKind.REFEED }).isEqualTo(2)
        assertWeeklyTotalHolds(w)
    }

    @Test
    fun `refeeds and carb cycling together hold the weekly total`() {
        val w = build(cycling = true, refeeds = 1)
        assertThat(w.pattern.days.count { it.kind == DayKind.REFEED }).isEqualTo(1)
        assertThat(w.pattern.days.any { it.kind == DayKind.TRAINING }).isTrue()
        assertThat(w.pattern.days.any { it.kind == DayKind.REST }).isTrue()
        assertWeeklyTotalHolds(w)
    }

    @Test
    fun `refeeds are only for a deficit`() {
        val maintain = MacroPlanner.plan(3_000, 80.0, null, Phase.MAINTAIN)
        val w = build(refeeds = 2, macros = maintain, tdee = 3_000)
        assertThat(w.pattern.days.none { it.kind == DayKind.REFEED }).isTrue()
        assertThat(w.notes.single()).contains("deficit")
    }

    @Test
    fun `refeeds are skipped when the other days would fall under the floor`() {
        // A high floor leaves no room to borrow from the other days.
        val w = build(refeeds = 2, floor = 2_390)
        assertThat(w.pattern.days.count { it.kind == DayKind.REFEED }).isLessThan(2)
        assertThat(w.warnings.single().text).contains("floor")
        assertWeeklyTotalHolds(w)
    }

    @Test
    fun `refeeds need training days to sit on`() {
        val w = build(refeeds = 2, trainingDays = emptySet())
        assertThat(w.pattern.days.none { it.kind == DayKind.REFEED }).isTrue()
        assertWeeklyTotalHolds(w)
    }

    @Test
    fun `week pattern always has seven days in order`() {
        val w = build(cycling = true, refeeds = 1)
        assertThat(w.pattern.days.map { it.weekday }).containsExactly(1, 2, 3, 4, 5, 6, 7).inOrder()
        assertThat(w.pattern.forWeekday(5).weekday).isEqualTo(5)
    }

    // --- Diet breaks ------------------------------------------------------------------------

    @Test
    fun `a diet break follows every N weeks of dieting`() {
        val plan = DietBreakPlan(everyWeeks = 10, maintenanceKcal = 2_900)
        assertThat((1..10).none { plan.isBreakWeek(it) }).isTrue()
        assertThat(plan.isBreakWeek(11)).isTrue()
        assertThat((12..21).none { plan.isBreakWeek(it) }).isTrue()
        assertThat(plan.isBreakWeek(22)).isTrue()
        assertThat(plan.isBreakWeek(0)).isFalse()
        assertThat(plan.nextBreakWeek(1)).isEqualTo(11)
        assertThat(plan.nextBreakWeek(11)).isEqualTo(22)
        assertThat(plan.nextBreakWeek(15)).isEqualTo(22)
    }

    @Test
    fun `diet break spacing must be 8 to 12 weeks and only cuts use it`() {
        val ok = runCatching { DietBreakPlan(7, 2_500) }
        assertThat(ok.isFailure).isTrue()
        assertThat(runCatching { DietBreakPlan(13, 2_500) }.isFailure).isTrue()
        assertThat(runCatching { DietBreakPlan(8, 2_500) }.isSuccess).isTrue()
        assertThat(DietBreakPlan.appliesTo(Phase.CUT)).isTrue()
        assertThat(DietBreakPlan.appliesTo(Phase.CONTEST_PREP)).isTrue()
        assertThat(DietBreakPlan.appliesTo(Phase.BULK)).isFalse()
        assertThat(DietBreakPlan.appliesTo(Phase.MAINTAIN)).isFalse()
    }
}
