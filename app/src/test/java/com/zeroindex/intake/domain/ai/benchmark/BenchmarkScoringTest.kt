package com.zeroindex.intake.domain.ai.benchmark

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BenchmarkScoringTest {

    private val menu = MenuFixture(
        id = "m",
        restaurant = "Test Grill",
        text = "",
        items = listOf(
            FixtureItem("Grilled Chicken Plate", 540.0, 46.0, dairy = false),
            FixtureItem("Cheddar Burger", 820.0, 41.0, dairy = true),
            FixtureItem("Garden Salad", 180.0, 4.0, dairy = false),
        ),
    )

    private fun item(name: String, kcal: Double?, protein: Double?, dairy: Boolean = false) =
        ObservedItem(name, kcal, protein, dairy)

    private val perfect = listOf(
        item("Grilled Chicken Plate", 540.0, 46.0),
        item("Cheddar Burger", 820.0, 41.0, dairy = true),
        item("Garden Salad", 180.0, 4.0),
    )

    @Test
    fun `a faithful answer scores full marks`() {
        val score = BenchmarkScoring.scoreMenu(menu, perfect)
        assertThat(score.accuracy).isEqualTo(1.0)
        assertThat(score.itemRecall).isEqualTo(1.0)
        assertThat(score.macroAccuracy).isEqualTo(1.0)
        assertThat(score.passed).isTrue()
    }

    @Test
    fun `kcal is accepted within 15 percent and not beyond`() {
        // 540 * 0.15 = 81: 620 is inside, 625 is not.
        val inside = perfect.map { if (it.name == "Grilled Chicken Plate") it.copy(kcal = 620.0) else it }
        val outside = perfect.map { if (it.name == "Grilled Chicken Plate") it.copy(kcal = 625.0) else it }
        assertThat(BenchmarkScoring.scoreMenu(menu, inside).accuracy).isEqualTo(1.0)
        assertThat(BenchmarkScoring.scoreMenu(menu, outside).accuracy).isWithin(1e-9).of(2.0 / 3)
    }

    @Test
    fun `protein is accepted within 20 percent`() {
        // 46 * 0.2 = 9.2
        val inside = perfect.map { if (it.name == "Grilled Chicken Plate") it.copy(proteinG = 37.0) else it }
        val outside = perfect.map { if (it.name == "Grilled Chicken Plate") it.copy(proteinG = 36.0) else it }
        assertThat(BenchmarkScoring.scoreMenu(menu, inside).accuracy).isEqualTo(1.0)
        assertThat(BenchmarkScoring.scoreMenu(menu, outside).accuracy).isLessThan(1.0)
    }

    @Test
    fun `a few grams of protein get an absolute slack`() {
        // 4 g * 20% would be under a gram.
        val off = perfect.map { if (it.name == "Garden Salad") it.copy(proteinG = 5.0) else it }
        assertThat(BenchmarkScoring.scoreMenu(menu, off).accuracy).isEqualTo(1.0)
    }

    @Test
    fun `a missing number is wrong`() {
        val blank = perfect.map { if (it.name == "Garden Salad") it.copy(kcal = null) else it }
        assertThat(BenchmarkScoring.scoreMenu(menu, blank).accuracy).isWithin(1e-9).of(2.0 / 3)
    }

    @Test
    fun `leaving an item out lowers recall`() {
        val score = BenchmarkScoring.scoreMenu(menu, perfect.filter { it.name != "Garden Salad" })
        assertThat(score.itemRecall).isWithin(1e-9).of(2.0 / 3)
        assertThat(score.macroAccuracy).isEqualTo(1.0)
        assertThat(score.accuracy).isWithin(1e-9).of(2.0 / 3)
    }

    @Test
    fun `names match loosely`() {
        val renamed = perfect.map { it.copy(name = "The ${it.name} (lunch)") }
        assertThat(BenchmarkScoring.scoreMenu(menu, renamed).itemRecall).isEqualTo(1.0)
    }

    @Test
    fun `a missed dairy item fails the whole fixture`() {
        val missed = perfect.map { if (it.name == "Cheddar Burger") it.copy(raisesDairy = false) else it }
        val score = BenchmarkScoring.scoreMenu(menu, missed)
        assertThat(score.allergenMissed).isTrue()
        assertThat(score.accuracy).isEqualTo(0.0)
        assertThat(score.passed).isFalse()
    }

    @Test
    fun `raising dairy on a clean item is not penalised`() {
        val cautious = perfect.map { it.copy(raisesDairy = true) }
        assertThat(BenchmarkScoring.scoreMenu(menu, cautious).passed).isTrue()
    }

    @Test
    fun `an empty menu reply scores nothing`() {
        assertThat(BenchmarkScoring.scoreMenu(menu, emptyList())).isEqualTo(FixtureScore.UNPARSED)
    }

    @Test
    fun `dairy is recognised by its common names`() {
        assertThat(BenchmarkScoring.raisesDairy(listOf("Dairy"))).isTrue()
        assertThat(BenchmarkScoring.raisesDairy(listOf("cheddar cheese"))).isTrue()
        assertThat(BenchmarkScoring.raisesDairy(listOf("yoghurt"))).isTrue()
        assertThat(BenchmarkScoring.raisesDairy(listOf("peanuts", "gluten"))).isFalse()
        assertThat(BenchmarkScoring.raisesDairy(emptyList())).isFalse()
        assertThat(BenchmarkScoring.raisesDairy(listOf("butternut squash"))).isFalse()
    }

    private val recipe = RecipeFixture("r", "", servings = 4, kcalPerServing = 560.0, proteinPerServing = 45.0, dairy = true)

    @Test
    fun `recipe macros are scored per serving`() {
        val good = BenchmarkScoring.scoreRecipe(recipe, item("x", 560.0, 45.0, dairy = true))
        val kcalOnly = BenchmarkScoring.scoreRecipe(recipe, item("x", 560.0, 10.0, dairy = true))
        assertThat(good.accuracy).isEqualTo(1.0)
        assertThat(kcalOnly.accuracy).isEqualTo(0.5)
    }

    @Test
    fun `a dairy recipe returned without a dairy warning fails`() {
        val score = BenchmarkScoring.scoreRecipe(recipe, item("x", 560.0, 45.0, dairy = false))
        assertThat(score.allergenMissed).isTrue()
        assertThat(score.passed).isFalse()
    }

    @Test
    fun `an unparsed recipe scores nothing`() {
        assertThat(BenchmarkScoring.scoreRecipe(recipe, null)).isEqualTo(FixtureScore.UNPARSED)
    }

    private val planB = PlanBFixture(
        id = "p",
        menuText = "",
        kcalLeft = 700,
        proteinLeft = 50,
        items = listOf(
            FixtureItem("Grilled Chicken Sandwich", 430.0, 38.0, dairy = false),
            FixtureItem("Cheeseburger", 750.0, 36.0, dairy = true),
            FixtureItem("Fruit Cup", 90.0, 1.0, dairy = false),
        ),
        acceptable = listOf("Grilled Chicken Sandwich"),
    )

    @Test
    fun `plan b rewards an acceptable pick`() {
        val score = BenchmarkScoring.scorePlanB(planB, listOf(item("Grilled Chicken Sandwich", 430.0, 38.0)))
        assertThat(score.accuracy).isEqualTo(1.0)
    }

    @Test
    fun `plan b counts a poor or invented option against the answer`() {
        val score = BenchmarkScoring.scorePlanB(
            planB,
            listOf(item("Grilled Chicken Sandwich", 430.0, 38.0), item("Fruit Cup", 90.0, 1.0), item("Imaginary Wrap", 300.0, 30.0)),
        )
        assertThat(score.accuracy).isWithin(1e-9).of(1.0 / 3)
    }

    @Test
    fun `plan b fails when a dairy item is offered without a warning`() {
        val score = BenchmarkScoring.scorePlanB(
            planB,
            listOf(item("Grilled Chicken Sandwich", 430.0, 38.0), item("Cheeseburger", 750.0, 36.0, dairy = false)),
        )
        assertThat(score.allergenMissed).isTrue()
        assertThat(score.accuracy).isEqualTo(0.0)
    }

    @Test
    fun `plan b with no options scores nothing`() {
        assertThat(BenchmarkScoring.scorePlanB(planB, emptyList())).isEqualTo(FixtureScore.UNPARSED)
    }

    private val digest = InsightFixture(
        "i",
        listOf("Protein: averaging 118 g against a 160 g target", "Steps: averaging 7,900 a day"),
    )

    private fun insight(text: String, focus: String = "protein", basedOn: List<String> = listOf("Protein: averaging 118 g")) =
        ObservedInsight(text, focus, basedOn)

    @Test
    fun `an insight that follows the rules scores full marks`() {
        val score = BenchmarkScoring.scoreInsight(digest, insight("Protein is averaging 118 g, 42 g short. Add a shake after training."))
        assertThat(score.accuracy).isEqualTo(1.0)
    }

    @Test
    fun `each broken insight rule costs a quarter`() {
        val tooLong = insight("Protein is averaging 118 g. " + "x".repeat(250))
        val badFocus = insight("Protein is averaging 118 g.", focus = "vibes")
        val noEvidence = insight("Protein is averaging 118 g.", basedOn = listOf(" "))
        val noNumber = insight("Eat more protein.")
        listOf(tooLong, badFocus, noEvidence, noNumber).forEach {
            assertThat(BenchmarkScoring.scoreInsight(digest, it).accuracy).isEqualTo(0.75)
        }
    }

    @Test
    fun `numbers with thousands separators still count as cited`() {
        val score = BenchmarkScoring.scoreInsight(digest, insight("You walk 7900 steps a day; add a ten minute loop."))
        assertThat(score.accuracy).isEqualTo(1.0)
    }

    @Test
    fun `an insight suggesting dairy fails`() {
        val score = BenchmarkScoring.scoreInsight(digest, insight("Protein is 118 g. Have Greek yogurt as a snack."))
        assertThat(score.allergenMissed).isTrue()
        assertThat(score.accuracy).isEqualTo(0.0)
    }
}
