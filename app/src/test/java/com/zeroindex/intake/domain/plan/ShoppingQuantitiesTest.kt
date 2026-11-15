package com.zeroindex.intake.domain.plan

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.domain.model.StoreCategory
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

    // ---- Q2-6: what a person buys is dry weight and liquid volume --------------------------------

    @Test fun `cooked rice and spaghetti are listed as the dry weight to buy`() {
        val rice = total("640 g White rice, cooked").single()
        assertThat(rice.name).isEqualTo("White rice")
        // 640 g cooked is about 229 g dry (cooked rice is about 2.8 times the dry weight), marked as approximate.
        assertThat(rice.quantity).isEqualTo("≈ 228.6 g")
        assertThat(ShoppingQuantities.display(rice.quantity, WeightUnit.POUNDS)).isEqualTo("≈ 8.1 oz")
        val pasta = total("840 g Spaghetti, cooked").single()
        assertThat(pasta.quantity).isEqualTo("≈ 365.2 g")
        assertThat(ShoppingQuantities.display(pasta.quantity, WeightUnit.POUNDS)).isEqualTo("≈ 12.9 oz")
    }

    @Test fun `dry and cooked lines of one grain add up as dry weight`() {
        val t = total("200 g white rice", "560 g cooked white rice").single()
        assertThat(t.key).isEqualTo("white rice")
        assertThat(t.name).isEqualTo("White rice")
        assertThat(t.quantity).isEqualTo("≈ 400 g")
    }

    @Test fun `a cooked cup measure is turned into dry grams too`() {
        // 2 cups cooked rice is about 316 g cooked, 113 g dry.
        assertThat(total("2 cups cooked rice").single().quantity).isEqualTo("≈ 112.9 g")
    }

    @Test fun `scaling applies after the cooked to dry conversion`() {
        assertThat(total("560 g rice, cooked", scale = 2.0).single().quantity).isEqualTo("≈ 400 g")
    }

    @Test fun `cooked meat is bought raw, and brown rice has its own ratio`() {
        assertThat(total("480 g Chicken breast, cooked").single().quantity).isEqualTo("≈ 640 g")
        assertThat(total("300 g brown rice, cooked").single().quantity).isEqualTo("≈ 100 g")
    }

    @Test fun `a line that is not cooked is left alone`() {
        assertThat(total("200 g white rice").single().quantity).isEqualTo("200 g")
        assertThat(total("300 g uncooked rice").single().quantity).isEqualTo("300 g")
        assertThat(total("100 g cheddar cheese, cooked into the sauce").single().quantity).isEqualTo("100 g")
    }

    @Test fun `milk is a volume, not a weight`() {
        val milk = total("488 g milk").single()
        assertThat(milk.quantity).isEqualTo("≈ 473.8 ml")
        assertThat(ShoppingQuantities.display(milk.quantity, WeightUnit.POUNDS)).isEqualTo("≈ 2 cups")
        assertThat(ShoppingQuantities.display(milk.quantity, WeightUnit.KILOGRAMS)).isEqualTo("≈ 474 ml")
    }

    @Test fun `milk in cups and in grams add up as volume`() {
        val t = total("1 cup milk", "244 g milk").single()
        assertThat(t.quantity).isEqualTo("≈ 473.5 ml")
    }

    @Test fun `broth cream and oil in grams become volume, other things do not`() {
        assertThat(total("500 g chicken broth").single().quantity).isEqualTo("≈ 500 ml")
        assertThat(total("30 g olive oil").single().quantity).isEqualTo("≈ 32.6 ml")
        assertThat(total("250 g heavy cream").single().quantity).isEqualTo("≈ 250 ml")
        assertThat(total("200 g milk chocolate").single().quantity).isEqualTo("200 g")
        assertThat(total("100 g sour cream").single().quantity).isEqualTo("100 g")
        assertThat(total("200 g cheddar").single().quantity).isEqualTo("200 g")
        assertThat(total("1 can coconut milk").single().quantity).isEqualTo("1 can")
    }

    @Test fun `large volumes show as quarts and gallons for imperial and litres for metric`() {
        assertThat(ShoppingQuantities.display("1000 ml", WeightUnit.POUNDS)).isEqualTo("1.06 qt")
        assertThat(ShoppingQuantities.display("946.4 ml", WeightUnit.POUNDS)).isEqualTo("1 qt")
        assertThat(ShoppingQuantities.display("3000 ml", WeightUnit.POUNDS)).isEqualTo("3.17 qt")
        assertThat(ShoppingQuantities.display("4000 ml", WeightUnit.POUNDS)).isEqualTo("1.06 gal")
        assertThat(ShoppingQuantities.display("700 ml", WeightUnit.POUNDS)).isEqualTo("2.96 cups")
        assertThat(ShoppingQuantities.display("1000 ml", WeightUnit.KILOGRAMS)).isEqualTo("1 l")
    }

    @Test fun `the QA week gives buyable amounts`() {
        val list = ShoppingListBuilder.generateFromLines(
            listOf(
                ShoppingContribution("640 g White rice, cooked", "Chicken fajita bowls"),
                ShoppingContribution("840 g Spaghetti, cooked", "Baked mac and cheese"),
                ShoppingContribution("480 g Chicken breast, cooked", "Chicken fajita bowls"),
                ShoppingContribution("488 g Milk", "Baked mac and cheese"),
                ShoppingContribution("340 g Cheddar cheese", "Baked mac and cheese"),
                ShoppingContribution("56 g Butter", "Baked mac and cheese"),
                ShoppingContribution("260 g Black beans", "Chicken fajita bowls"),
            ),
        ).associateBy { it.key }
        assertThat(ShoppingQuantities.display(list.getValue("white rice").quantity, WeightUnit.POUNDS)).isEqualTo("≈ 8.1 oz")
        assertThat(ShoppingQuantities.display(list.getValue("spaghetti").quantity, WeightUnit.POUNDS)).isEqualTo("≈ 12.9 oz")
        assertThat(ShoppingQuantities.display(list.getValue("milk").quantity, WeightUnit.POUNDS)).isEqualTo("≈ 2 cups")
        assertThat(ShoppingQuantities.display(list.getValue("chicken breast").quantity, WeightUnit.POUNDS)).isEqualTo("≈ 1.41 lb")
        // Rice and spaghetti are both plain grains; milk, cheese and butter are all dairy.
        assertThat(list.getValue("white rice").category).isEqualTo(StoreCategory.PANTRY)
        assertThat(list.getValue("white rice").isPantryStaple).isFalse()
        assertThat(list.getValue("spaghetti").category).isEqualTo(StoreCategory.PANTRY)
        assertThat(list.getValue("spaghetti").isPantryStaple).isFalse()
        assertThat(list.getValue("milk").category).isEqualTo(StoreCategory.DAIRY)
        assertThat(list.getValue("cheddar cheese").category).isEqualTo(StoreCategory.DAIRY)
        assertThat(list.getValue("butter").category).isEqualTo(StoreCategory.DAIRY)
        assertThat(list.getValue("black bean").category).isEqualTo(StoreCategory.PANTRY)
        assertThat(list.values.none { it.category == StoreCategory.OTHER }).isTrue()
    }
}
