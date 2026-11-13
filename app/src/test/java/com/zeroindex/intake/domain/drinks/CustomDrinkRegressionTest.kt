package com.zeroindex.intake.domain.drinks

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.data.seed.DrinkSeed
import com.zeroindex.intake.domain.nutrition.LabelFormat
import com.zeroindex.intake.domain.nutrition.Macros
import com.zeroindex.intake.domain.units.VolumeUnit
import org.junit.Test

/**
 * Round 5, item 13: English Breakfast tea with 3 tsp sugar at 16 fl oz showed 2 g protein, 65 kcal at the top and 70 on the
 * label, with sugars "not listed". These pin the maths for tea, coffee and milk at 8 and 16 fl oz, and the seeded
 * values against USDA FoodData Central (SR Legacy, per 100 g, a millilitre counted as a gram).
 */
class CustomDrinkRegressionTest {

    private val oz8 = VolumeUnit.US_FLUID_OUNCES.toMl(8.0)
    private val oz16 = VolumeUnit.US_FLUID_OUNCES.toMl(16.0)

    private fun seeded(name: String): DrinkBase {
        val s = DrinkSeed.SPECS.first { it.name == name }
        return DrinkBase(
            name = s.name,
            per100 = Macros(s.kcal, s.protein, s.carbs, s.fat, s.fiber, s.sugar, s.sodiumMg, s.saturatedFat),
            caffeineMgPer100ml = s.caffeineMg,
        )
    }

    private val tea get() = seeded("Black tea")
    private val coffee get() = seeded("Coffee, black")
    private fun sugar(tsp: Double) = AddInCatalog.byKey("sugar")!!.toAddIn(tsp, AddInUnit.TSP)
    private fun milk(ml: Double) = AddInCatalog.byKey("milk-whole")!!.toAddIn(ml, AddInUnit.ML)

    @Test
    fun `a teaspoon of sugar is 4_2 g, so 3 tsp is 12_6 g of sugar`() {
        val a = sugar(3.0)
        assertThat(a.grams).isWithin(1e-9).of(12.6)
        assertThat(a.nutrition().sugar).isWithin(0.05).of(12.6)
        assertThat(a.nutrition().kcal).isWithin(1.0).of(48.8)
        assertThat(a.unlisted).isEmpty()
    }

    @Test
    fun `tea with 3 tsp sugar at 16 fl oz is about 50 kcal, 13 g carbs, 12_6 g sugars and no protein`() {
        val r = CustomDrink.compute(tea, oz16, listOf(sugar(3.0)))
        assertThat(r.total.kcal).isWithin(5.0).of(50.0)
        assertThat(r.total.carbs).isWithin(1.5).of(13.0)
        assertThat(r.total.sugar).isWithin(0.05).of(12.6)
        assertThat(r.total.protein).isLessThan(0.1)
        assertThat(r.total.fat).isLessThan(0.1)
        // Sugar dissolves: the glass is still 16 fl oz.
        assertThat(r.totalVolumeMl).isWithin(1e-9).of(oz16)
    }

    @Test
    fun `the add-in is not scaled by the glass volume, so 8 and 16 fl oz differ only by the brew`() {
        val small = CustomDrink.compute(tea, oz8, listOf(sugar(3.0)))
        val big = CustomDrink.compute(tea, oz16, listOf(sugar(3.0)))
        assertThat(small.total.sugar).isWithin(1e-9).of(big.total.sugar)
        assertThat(big.total.kcal - small.total.kcal).isWithin(1e-6).of(tea.per100.kcal * (oz16 - oz8) / 100.0)
        // Per 100 ml, the sweeter small glass is more concentrated.
        assertThat(small.per100ml.sugar).isGreaterThan(big.per100ml.sugar)
    }

    @Test
    fun `coffee with sugar at 8 and 16 fl oz`() {
        val small = CustomDrink.compute(coffee, oz8, listOf(sugar(2.0)))
        val big = CustomDrink.compute(coffee, oz16, listOf(sugar(2.0)))
        assertThat(small.total.sugar).isWithin(0.05).of(8.4)
        assertThat(big.total.sugar).isWithin(0.05).of(8.4)
        assertThat(small.total.kcal).isWithin(1.0).of(8.4 * 3.87 + 2.37)
        assertThat(big.total.kcal).isWithin(1.0).of(8.4 * 3.87 + 4.73)
        assertThat(small.total.protein).isWithin(0.01).of(0.1 * oz8 / 100.0)
        assertThat(big.total.protein).isWithin(0.01).of(0.1 * oz16 / 100.0)
        // Caffeine of the brew: 40 mg per 100 ml.
        assertThat(big.caffeineMg).isWithin(0.01).of(40.0 * oz16 / 100.0)
    }

    @Test
    fun `a milk add-in adds its own protein and volume at 8 and 16 fl oz`() {
        for (oz in listOf(oz8, oz16)) {
            val r = CustomDrink.compute(tea, oz, listOf(milk(30.0)))
            assertThat(r.totalVolumeMl).isWithin(1e-9).of(oz + 30.0)
            // 30 ml of whole milk: 3.15 g protein per 100 ml.
            assertThat(r.total.protein).isWithin(0.01).of(3.15 * 0.3 + tea.per100.protein * oz / 100.0)
            assertThat(r.total.kcal).isWithin(0.01).of(61.0 * 0.3 + tea.per100.kcal * oz / 100.0)
            assertThat(r.per100ml.kcal * r.totalVolumeMl / 100.0).isWithin(1e-6).of(r.total.kcal)
        }
    }

    @Test
    fun `tea with sugar and milk keeps both add-ins at their own amounts`() {
        val r = CustomDrink.compute(tea, oz16, listOf(sugar(1.0), milk(60.0)))
        assertThat(r.total.sugar).isWithin(0.05).of(4.2 + 5.05 * 0.6)
        assertThat(r.total.protein).isWithin(0.01).of(3.15 * 0.6)
    }

    @Test
    fun `a sugar add-in lists its sugars even when the base lists none`() {
        val base = tea.copy(unlisted = "sugar")
        // The base still says sugar is unknown, so the finished drink does too: only a plain brew (PlainBrew) is known to be 0.
        assertThat(CustomDrink.unlistedUnion(base, listOf(sugar(3.0)))).isEqualTo("sugar")
        assertThat(CustomDrink.unlistedUnion(tea, listOf(sugar(3.0)))).isEmpty()
    }

    @Test
    fun `the seeded tea and coffee match USDA brewed values`() {
        // USDA SR Legacy: tea black brewed (173227) 1 kcal, 0 protein, 0.3 carbs, 0 sugars, 3 mg sodium; coffee brewed (171890)
        // 1 kcal, 0.12 protein, 0 carbs, 0 sugars, 2 mg sodium; tea green brewed (171917) 1 kcal, 0.22 protein, 0 carbs, 1 mg sodium.
        data class Usda(val name: String, val kcal: Double, val protein: Double, val carbs: Double, val sodium: Double)
        listOf(Usda("Black tea", 1.0, 0.0, 0.3, 3.0), Usda("Coffee, black", 1.0, 0.12, 0.0, 2.0), Usda("Green tea", 1.0, 0.22, 0.0, 1.0)).forEach { u ->
            val s = DrinkSeed.SPECS.first { it.name == u.name }
            assertThat(s.kcal).isWithin(1.0).of(u.kcal)
            assertThat(s.protein).isWithin(0.25).of(u.protein)
            assertThat(s.carbs).isWithin(0.35).of(u.carbs)
            assertThat(s.sodiumMg).isWithin(1.0).of(u.sodium)
            // The seed says sugars are 0, not unknown: a plain brew has none.
            assertThat(s.sugar).isEqualTo(0.0)
            assertThat(s.fat).isEqualTo(0.0)
        }
        // Caffeine: USDA lists 47 mg in a cup (237 g) of brewed black tea and 95 mg in a cup of brewed coffee.
        assertThat(DrinkSeed.SPECS.first { it.name == "Black tea" }.caffeineMg).isWithin(1.5).of(47.0 / 2.37)
        assertThat(DrinkSeed.SPECS.first { it.name == "Coffee, black" }.caffeineMg).isWithin(1.5).of(95.0 / 2.37)
    }

    @Test
    fun `the label rounds calories like a printed panel and the tiles use the same rounding for drinks`() {
        assertThat(LabelFormat.calories(65.0)).isEqualTo(70)
        assertThat(LabelFormat.calories(53.5)).isEqualTo(50)
        assertThat(LabelFormat.calories(47.0)).isEqualTo(45)
        assertThat(LabelFormat.grams(2.45)).isEqualTo("2.5")
        assertThat(LabelFormat.grams(0.3)).isEqualTo("0")
    }
}
