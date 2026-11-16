package com.zeroindex.intake.domain.nutrition

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DayNutritionTest {

    private val all = emptySet<NutrientKey>()
    private fun entry(kcal: Double, fiber: Double = 0.0, sodium: Double = 0.0, unlisted: Set<NutrientKey> = all) =
        DayEntry(Macros(kcal = kcal, protein = kcal / 10, carbs = kcal / 8, fat = kcal / 20, fiber = fiber, sodium = sodium), unlisted)

    @Test
    fun `an empty day is a true zero, not unknown`() {
        val day = DayNutrition.aggregate(emptyList())
        assertThat(day.kcal).isEqualTo(0.0)
        assertThat(day.fiber.coverage).isEqualTo(Coverage.NONE_LOGGED)
        assertThat(day.fiber.value).isEqualTo(0.0)
        assertThat(day.fiber.note).isNull()
    }

    @Test
    fun `a nutrient no logged food listed is not listed`() {
        val day = DayNutrition.aggregate(listOf(entry(200.0, unlisted = setOf(NutrientKey.FIBER)), entry(300.0, unlisted = setOf(NutrientKey.FIBER))))
        assertThat(day.fiber.coverage).isEqualTo(Coverage.ALL_UNLISTED)
        assertThat(day.fiber.value).isNull()
        assertThat(day.kcal).isEqualTo(500.0)
    }

    @Test
    fun `a partly known total is a floor and says how many foods lack it`() {
        val day = DayNutrition.aggregate(
            listOf(entry(200.0, fiber = 4.0), entry(300.0, fiber = 6.0), entry(100.0, fiber = 0.0, unlisted = setOf(NutrientKey.FIBER))),
        )
        assertThat(day.fiber.coverage).isEqualTo(Coverage.PARTIAL)
        assertThat(day.fiber.value).isEqualTo(10.0)
        assertThat(day.fiber.note).isEqualTo("1 food has no fibre value")
        val two = DayNutrition.aggregate(listOf(entry(1.0, fiber = 1.0), entry(1.0, unlisted = setOf(NutrientKey.FIBER)), entry(1.0, unlisted = setOf(NutrientKey.FIBER))))
        assertThat(two.fiber.note).isEqualTo("2 foods have no fibre value")
    }

    @Test
    fun `salt is sodium times two and a half`() {
        val day = DayNutrition.aggregate(listOf(entry(100.0, sodium = 1000.0), entry(100.0, sodium = 400.0)))
        assertThat(day.saltGrams).isWithin(1e-9).of(3.5)
        val none = DayNutrition.aggregate(listOf(entry(100.0, unlisted = setOf(NutrientKey.SODIUM))))
        assertThat(none.saltGrams).isNull()
    }

    @Test
    fun `the dashboard shows not listed for unknown and keeps targets`() {
        val day = DayNutrition.aggregate(
            listOf(entry(800.0, unlisted = setOf(NutrientKey.FIBER, NutrientKey.SODIUM)), entry(200.0, unlisted = setOf(NutrientKey.FIBER, NutrientKey.SODIUM))),
        )
        val model = NutritionDashboard.build(day, MacroTarget(kcal = 2000, proteinG = 150, carbsG = 200, fatG = 60), saltInsteadOfSodium = false)
        assertThat(model.calories.eaten).isEqualTo(1000)
        assertThat(model.calories.left).isEqualTo(1000)
        assertThat(model.calories.over).isFalse()
        assertThat(model.calories.fraction).isWithin(1e-6f).of(0.5f)
        val fiber = model.others.first { it.id == "fiber" }
        assertThat(NutritionDashboard.valueText(fiber)).isEqualTo("not listed")
        assertThat(fiber.fraction).isEqualTo(0f)
        val sodium = model.others.first { it.id == "sodium" }
        assertThat(NutritionDashboard.valueText(sodium)).isEqualTo("not listed")
        // Sugar was listed (as 0 g) on both foods: a real zero shows as 0, not "not listed".
        assertThat(NutritionDashboard.valueText(model.others.first { it.id == "sugar" })).isEqualTo("0 g")
        assertThat(model.macros.map { it.id }).containsExactly("protein", "carbs", "fat").inOrder()
    }

    @Test
    fun `over target is flagged and the bar never exceeds full`() {
        val day = DayNutrition.aggregate(listOf(entry(2600.0)))
        val model = NutritionDashboard.build(day, MacroTarget(2000, 150, 200, 60), saltInsteadOfSodium = false)
        assertThat(model.calories.over).isTrue()
        assertThat(model.calories.left).isEqualTo(-600)
        assertThat(model.calories.fraction).isEqualTo(1f)
        assertThat(model.macros.first { it.id == "protein" }.fraction).isEqualTo(1f)
    }

    @Test
    fun `no target means no left and no fractions`() {
        val model = NutritionDashboard.build(DayNutrition.aggregate(listOf(entry(500.0))), null, saltInsteadOfSodium = false)
        assertThat(model.calories.target).isNull()
        assertThat(model.calories.left).isNull()
        assertThat(model.macros.all { it.fraction == 0f }).isTrue()
    }

    @Test
    fun `the salt setting turns the sodium row into salt in grams`() {
        val day = DayNutrition.aggregate(listOf(entry(500.0, sodium = 1640.0)))
        val salt = NutritionDashboard.build(day, null, saltInsteadOfSodium = true).others.first { it.id == "salt" }
        assertThat(salt.unit).isEqualTo("g")
        assertThat(salt.value).isWithin(1e-9).of(4.1)
        assertThat(salt.reference).isTrue()
        val sodium = NutritionDashboard.build(day, null, saltInsteadOfSodium = false).others.first { it.id == "sodium" }
        assertThat(NutritionDashboard.valueText(sodium)).isEqualTo("1,640 mg")
    }

    @Test
    fun `notes list each nutrient with missing values`() {
        val day = DayNutrition.aggregate(listOf(entry(100.0, fiber = 2.0), entry(100.0, unlisted = setOf(NutrientKey.FIBER))))
        assertThat(NutritionDashboard.build(day, null, false).notes).containsExactly("1 food has no fibre value")
    }
}
