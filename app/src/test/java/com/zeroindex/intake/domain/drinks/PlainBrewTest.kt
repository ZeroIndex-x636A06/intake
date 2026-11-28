package com.zeroindex.intake.domain.drinks

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.domain.nutrition.Macros
import org.junit.Test

class PlainBrewTest {

    /** What a user-edited Open Food Facts "Hot Tea" can look like: a few calories and half a gram of protein per 100 ml. */
    private val suspect = Macros(kcal = 3.9, protein = 0.52, carbs = 0.0, fat = 0.0, sodium = 4.0)

    @Test
    fun `a plain tea with impossible protein is replaced by the USDA brewed values`() {
        val c = PlainBrew.check(DrinkCategory.TEA, "Hot Tea – English Breakfast", "", suspect, "sugar,fiber,satfat")
        assertThat(c.replaced).isTrue()
        assertThat(c.per100.protein).isEqualTo(0.0)
        assertThat(c.per100.kcal).isEqualTo(1.0)
        assertThat(c.unlisted).isEmpty()
    }

    @Test
    fun `English breakfast tea with 3 tsp sugar at 16 fl oz has about 13 g carbs and no protein`() {
        val c = PlainBrew.check(DrinkCategory.TEA, "Hot Tea – English Breakfast", "", suspect, "sugar,fiber,satfat")
        val base = DrinkBase("Hot Tea", c.per100, unlisted = c.unlisted)
        val sugar = AddInCatalog.byKey("sugar")!!.toAddIn(3.0, AddInUnit.TSP)
        val r = CustomDrink.compute(base, 473.0, listOf(sugar))
        assertThat(r.total.protein).isLessThan(0.1)
        assertThat(r.total.sugar).isWithin(0.05).of(12.6)
        assertThat(r.total.kcal).isWithin(5.0).of(53.0)
        assertThat(CustomDrink.unlistedUnion(base, listOf(sugar))).isEmpty()
    }

    @Test
    fun `a plain tea inside the bounds is kept, with sugars known to be zero`() {
        val ok = Macros(kcal = 1.0, protein = 0.0, carbs = 0.3, sodium = 3.0)
        val c = PlainBrew.check(DrinkCategory.TEA, "Black tea", "", ok, "sugar,fiber,satfat,sodium")
        assertThat(c.replaced).isFalse()
        assertThat(c.per100.sugar).isEqualTo(0.0)
        // Sodium is not something a brew is known to lack: it stays unlisted.
        assertThat(c.unlisted).isEqualTo("sodium")
    }

    @Test
    fun `milk tea, sweetened tea and flavoured coffee are never touched`() {
        val milky = Macros(kcal = 40.0, protein = 1.2, carbs = 6.0, fat = 1.0)
        assertThat(PlainBrew.check(DrinkCategory.TEA, "Milk tea", "", milky, "").replaced).isFalse()
        assertThat(PlainBrew.check(DrinkCategory.TEA, "Black tea, ready-to-drink, lemon, sweetened", "", Macros(kcal = 45.0, carbs = 10.8), "").per100.kcal).isEqualTo(45.0)
        assertThat(PlainBrew.check(DrinkCategory.COFFEE, "Vanilla latte", "", suspect, "").replaced).isFalse()
        // A name that says nothing about milk but whose allergen text does is not plain.
        assertThat(PlainBrew.check(DrinkCategory.TEA, "House tea", "en:milk | milk", suspect, "").replaced).isFalse()
        // Other categories are never checked.
        assertThat(PlainBrew.check(DrinkCategory.JUICE, "Apple juice", "", suspect, "").replaced).isFalse()
    }

    @Test
    fun `restaurant espresso stays as it is`() {
        val espresso = Macros(kcal = 9.0, protein = 0.12, carbs = 1.67, fat = 0.18)
        assertThat(PlainBrew.check(DrinkCategory.COFFEE, "Espresso", "", espresso, "").replaced).isFalse()
    }
}
