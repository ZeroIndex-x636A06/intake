package com.zeroindex.intake.domain.plan

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.domain.units.WeightUnit
import org.junit.Test

class ShoppingQuantitiesTest {

    private fun total(vararg lines: String, scale: Double = 1.0) =
        ShoppingQuantities.aggregate(lines.mapIndexed { i, l -> ShoppingContribution(l, "Meal ${i + 1}", scale = scale) })

    @Test fun `counts and a weight of the same item fold together with an approximate mark`() {
        val t = total("2 medium zucchini", "200 g zucchini").single()
        assertThat(t.key).isEqualTo("zucchini")
        // A medium zucchini is about 196 g: 2 x 196 + 200.
        assertThat(t.quantity).isEqualTo("≈ 592 g")
    }

    @Test fun `when no average weight is known the parts are shown side by side`() {
        val t = total("2 large shallots", "100 g shallots").single()
        assertThat(t.quantity).isEqualTo("100 g + 2 large")
        assertThat(ShoppingQuantities.display(t.quantity, WeightUnit.KILOGRAMS)).isEqualTo("100 g + 2 large")
    }

    @Test fun `teaspoons plus tablespoons add up as volume`() {
        val t = total("2 tsp olive oil", "1 tbsp olive oil", "1 tablespoon Olive Oil").single()
        // 2 tsp + 2 tbsp = 9.86 + 29.57 ml
        assertThat(t.quantity).isEqualTo("39.4 ml")
        assertThat(ShoppingQuantities.display(t.quantity, WeightUnit.KILOGRAMS)).isEqualTo("39 ml")
        assertThat(ShoppingQuantities.display(t.quantity, WeightUnit.POUNDS)).isEqualTo("2.7 tbsp")
    }

    @Test fun `weights in different units add up and show metric or imperial`() {
        val t = total("8 oz (220g) pasta", "1 lb pasta", "300 g pasta").single()
        assertThat(t.quantity).isEqualTo("973.6 g")
        assertThat(ShoppingQuantities.display(t.quantity, WeightUnit.KILOGRAMS)).isEqualTo("974 g")
        assertThat(ShoppingQuantities.display("1200 g", WeightUnit.KILOGRAMS)).isEqualTo("1.2 kg")
        assertThat(ShoppingQuantities.display(t.quantity, WeightUnit.POUNDS)).isEqualTo("2.15 lb")
        assertThat(ShoppingQuantities.display("150 g", WeightUnit.POUNDS)).isEqualTo("5.3 oz")
    }

    @Test fun `volume shows as litres, cups and fluid measures`() {
        assertThat(ShoppingQuantities.display("1500 ml", WeightUnit.KILOGRAMS)).isEqualTo("1.5 l")
        assertThat(ShoppingQuantities.display("473.2 ml", WeightUnit.POUNDS)).isEqualTo("2 cups")
        assertThat(ShoppingQuantities.display("60 ml", WeightUnit.POUNDS)).isEqualTo("0.25 cup")
        assertThat(ShoppingQuantities.display("10 ml", WeightUnit.POUNDS)).isEqualTo("2 tsp")
    }

    @Test fun `counts stay counts and singular and plural names meet`() {
        val t = total("3 cloves garlic", "2 cloves garlic, minced").single()
        assertThat(t.quantity).isEqualTo("5 clove")
        assertThat(ShoppingQuantities.display(t.quantity, WeightUnit.KILOGRAMS)).isEqualTo("5 cloves")
        val eggs = total("2 eggs", "1 egg").single()
        assertThat(eggs.key).isEqualTo("egg")
        assertThat(eggs.quantity).isEqualTo("3")
    }

    @Test fun `a line with no amount is listed once with no quantity`() {
        val t = total("Fresh basil", "fresh basil or parsley")
        assertThat(t.map { it.quantity }.distinct()).containsExactly("")
        val basil = total("Fresh basil", "Fresh basil").single()
        assertThat(basil.quantity).isEmpty()
        // An amount from another recipe is kept.
        assertThat(total("Fresh basil", "1/4 cup fresh basil").single().quantity).isEqualTo("59.1 ml")
    }

    @Test fun `scaling by planned servings over recipe servings`() {
        val t = total("200 g chicken breast", scale = 1.5).single()
        assertThat(t.quantity).isEqualTo("300 g")
    }

    @Test fun `sources are kept for where is this used`() {
        val t = ShoppingQuantities.aggregate(
            listOf(ShoppingContribution("1 cup rice", "Burrito bowls"), ShoppingContribution("2 cups rice", "Fried rice")),
        ).single()
        assertThat(t.sources).containsExactly("Burrito bowls", "Fried rice").inOrder()
        val line = ShoppingListBuilder.generateFromLines(
            listOf(ShoppingContribution("1 cup rice", "Burrito bowls"), ShoppingContribution("2 cups rice", "Fried rice")),
        ).single()
        assertThat(line.note).isEqualTo("from Burrito bowls, Fried rice")
    }

    @Test fun `old stored quantities and free text still display`() {
        assertThat(ShoppingListBuilder.displayQuantity("450 g", WeightUnit.KILOGRAMS)).isEqualTo("450 g")
        assertThat(ShoppingListBuilder.displayQuantity("1000 g", WeightUnit.POUNDS)).isEqualTo("2.2 lb")
        assertThat(ShoppingListBuilder.displayQuantity("1 bunch", WeightUnit.POUNDS)).isEqualTo("1 bunch")
        assertThat(ShoppingListBuilder.displayQuantity("a handful", WeightUnit.POUNDS)).isEqualTo("a handful")
        assertThat(ShoppingListBuilder.displayQuantity("3 lemons", WeightUnit.POUNDS)).isEqualTo("3 lemons")
    }
}
