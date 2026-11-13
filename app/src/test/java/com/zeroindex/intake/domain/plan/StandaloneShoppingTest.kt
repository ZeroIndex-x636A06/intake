package com.zeroindex.intake.domain.plan

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.domain.model.StoreCategory
import com.zeroindex.intake.domain.units.WeightUnit
import org.junit.Test

class StandaloneShoppingTest {

    private fun lineFor(list: StandaloneList, word: String): GeneratedShoppingLine =
        (list.sections.flatMap { it.second } + list.pantry).first { it.name.lowercase().contains(word) }

    @Test
    fun `the same ingredient in a recipe and a template becomes one line with the amounts added`() {
        val list = StandaloneShopping.build(
            StandaloneShopping.fromRecipe("Chicken bowls", listOf("200 g rice", "600 g chicken thigh")) +
                StandaloneShopping.fromTemplate("Lunch box", listOf("Rice" to 150.0)),
        )
        val rice = lineFor(list, "rice")
        assertThat(rice.quantity).isEqualTo("350 g")
        assertThat(rice.sources).containsExactly("Chicken bowls", "Lunch box")
        assertThat((list.sections.flatMap { it.second } + list.pantry).count { it.name.lowercase().contains("rice") }).isEqualTo(1)
    }

    @Test
    fun `batches multiply the amounts`() {
        val one = StandaloneShopping.build(StandaloneShopping.fromRecipe("Bowls", listOf("600 g chicken thigh")))
        val two = StandaloneShopping.build(StandaloneShopping.fromRecipe("Bowls", listOf("600 g chicken thigh"), batches = 2))
        assertThat(lineFor(one, "chicken").quantity).isEqualTo("600 g")
        assertThat(lineFor(two, "chicken").quantity).isEqualTo("1200 g")
    }

    @Test
    fun `batches are kept between one and nine`() {
        val tooMany = StandaloneShopping.build(StandaloneShopping.fromRecipe("Bowls", listOf("100 g chicken thigh"), batches = 50))
        assertThat(lineFor(tooMany, "chicken").quantity).isEqualTo("900 g")
        val none = StandaloneShopping.build(StandaloneShopping.fromRecipe("Bowls", listOf("100 g chicken thigh"), batches = 0))
        assertThat(lineFor(none, "chicken").quantity).isEqualTo("100 g")
    }

    @Test
    fun `a line with no amount stays on the list with no quantity, never zero`() {
        val list = StandaloneShopping.build(StandaloneShopping.fromRecipe("Salad", listOf("fresh basil", "1 avocado")))
        assertThat(lineFor(list, "basil").quantity).isEmpty()
        assertThat(lineFor(list, "avocado").quantity).isNotEmpty()
    }

    @Test
    fun `blank lines and unusable template amounts are dropped`() {
        assertThat(StandaloneShopping.fromRecipe("X", listOf("", "   ", "1 egg"))).hasSize(1)
        assertThat(StandaloneShopping.fromTemplate("T", listOf("Oats" to 0.0, "" to 50.0, "Milk" to Double.NaN, "Rice" to 80.0))).hasSize(1)
        assertThat(StandaloneShopping.build(emptyList()).isEmpty).isTrue()
    }

    @Test
    fun `recipe groups are carried into the note`() {
        val list = StandaloneShopping.build(StandaloneShopping.fromRecipe("Stir fry", listOf("2 tbsp soy sauce"), listOf("For the sauce")))
        assertThat(lineFor(list, "soy").note).contains("For the sauce")
    }

    @Test
    fun `lines are grouped in store order with staples apart`() {
        val list = StandaloneShopping.build(
            StandaloneShopping.fromRecipe("Dinner", listOf("500 g chicken breast", "2 tomatoes", "1 tsp salt", "1 can chopped canned beans")),
        )
        assertThat(list.sections.map { it.first }).isEqualTo(list.sections.map { it.first }.sortedBy { it.ordinal })
        assertThat(list.sections.first { it.first == StoreCategory.MEAT }.second.map { it.name.lowercase() }.single()).contains("chicken")
        assertThat(list.pantry.map { it.name.lowercase() }.single()).contains("salt")
        assertThat(list.count).isEqualTo(list.sections.sumOf { it.second.size } + list.pantry.size)
    }

    @Test
    fun `share text lists unchecked first, marks checked and puts staples last`() {
        val list = StandaloneShopping.build(StandaloneShopping.fromRecipe("Dinner", listOf("500 g chicken breast", "600 g chicken thigh", "1 tsp salt")))
        val chickenBreast = lineFor(list, "breast")
        val text = StandaloneShopping.shareText("Shopping list", list, setOf(chickenBreast.key), WeightUnit.KILOGRAMS)
        assertThat(text.lines().first()).isEqualTo("Shopping list")
        assertThat(text).contains("Meat & fish")
        assertThat(text.indexOf("[ ]")).isLessThan(text.indexOf("[x]"))
        assertThat(text).contains("[x] ")
        assertThat(text.trimEnd().lines().last().lowercase()).contains("salt")
        assertThat(text.indexOf("Pantry staples")).isGreaterThan(text.indexOf("Meat & fish"))
    }
}
