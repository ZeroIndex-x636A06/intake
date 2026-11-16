package com.zeroindex.intake.domain.drinks

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.domain.allergen.AllergenAlias
import com.zeroindex.intake.domain.allergen.AllergenMatcher
import com.zeroindex.intake.domain.nutrition.Macros
import org.junit.Test

class CustomDrinkTest {

    /** The seeded "Coffee, black": per 100 ml 1 kcal, 0.1 g protein, 2 mg sodium, 40 mg caffeine. */
    private val blackCoffee = DrinkBase("Coffee, black", Macros(kcal = 1.0, protein = 0.1, sodium = 2.0), caffeineMgPer100ml = 40.0)

    private fun sugar(tsp: Double) = AddInCatalog.byKey("sugar")!!.toAddIn(tsp, AddInUnit.TSP)
    private fun option(key: String) = AddInCatalog.byKey(key)!!

    @Test
    fun `black coffee with three teaspoons of sugar is about 51 kcal and 12_6 g sugar`() {
        val r = CustomDrink.compute(blackCoffee, 240.0, listOf(sugar(3.0)))
        // 3 tsp is 12.6 g of sugar (4.2 g a teaspoon): 48.8 kcal, plus 2.4 kcal of coffee.
        assertThat(r.total.kcal).isWithin(1.5).of(51.2)
        assertThat(r.total.sugar).isWithin(0.1).of(12.6)
        assertThat(r.total.carbs).isWithin(0.1).of(12.6)
        // Sugar dissolves: the glass is still 240 ml.
        assertThat(r.totalVolumeMl).isEqualTo(240.0)
    }

    @Test
    fun `no add-ins gives back the base drink`() {
        val r = CustomDrink.compute(blackCoffee, 240.0, emptyList())
        assertThat(r.total.kcal).isWithin(1e-9).of(2.4)
        assertThat(r.per100ml.kcal).isWithin(1e-9).of(1.0)
    }

    @Test
    fun `the saved drink stores the glass per 100 ml`() {
        val r = CustomDrink.compute(blackCoffee, 240.0, listOf(sugar(3.0)))
        // 12.6 g of sugar in 240 ml is 5.25 g per 100 ml.
        assertThat(r.per100ml.kcal).isWithin(1e-9).of(r.total.kcal / 2.4)
        assertThat(r.per100ml.sugar).isWithin(0.03).of(5.25)
        // And logging 240 ml of that saved drink reproduces the glass.
        assertThat(r.per100ml.kcal * 2.4).isWithin(1e-9).of(r.total.kcal)
    }

    @Test
    fun `a liquid add-in adds volume so per 100 ml is diluted but the totals are not`() {
        val milk = option("milk-whole").toAddIn(2.0, AddInUnit.TBSP) // 30 ml
        val r = CustomDrink.compute(blackCoffee, 240.0, listOf(milk))
        assertThat(r.totalVolumeMl).isEqualTo(270.0)
        assertThat(r.total.kcal).isWithin(1e-6).of(2.4 + 0.61 * 30.0)
        assertThat(r.per100ml.kcal).isWithin(1e-6).of(r.total.kcal / 2.7)
        // Caffeine in the glass is unchanged by the milk; per 100 ml it is lower.
        assertThat(r.caffeineMg).isWithin(1e-9).of(96.0)
        assertThat(r.caffeineMgPer100ml * 2.7).isWithin(1e-9).of(96.0)
    }

    @Test
    fun `an add-in measured in tablespoons or pumps is converted to millilitres`() {
        assertThat(option("milk-2").toAddIn(2.0, AddInUnit.TBSP).grams).isEqualTo(30.0)
        assertThat(option("milk-2").toAddIn(45.0, AddInUnit.ML).grams).isEqualTo(45.0)
        val syrup = option("syrup").toAddIn(2.0, AddInUnit.PUMP)
        assertThat(syrup.grams).isEqualTo(20.0)
        assertThat(syrup.volumeMl).isEqualTo(20.0)
        assertThat(syrup.nutrition().kcal).isWithin(1e-9).of(52.0)
    }

    @Test
    fun `sweetener adds nothing`() {
        val r = CustomDrink.compute(blackCoffee, 240.0, listOf(option("sweetener").toAddIn(2.0)))
        assertThat(r.total.kcal).isWithin(1e-9).of(2.4)
        assertThat(r.total.sugar).isEqualTo(0.0)
    }

    @Test
    fun `alcohol in the glass is kept when something is added`() {
        val wine = DrinkBase("Wine", Macros(kcal = 83.0), abvPercent = 12.0)
        val r = CustomDrink.compute(wine, 150.0, listOf(option("syrup").toAddIn(1.0, AddInUnit.PUMP)))
        assertThat(r.alcoholMl).isWithin(1e-9).of(18.0)
        assertThat(r.abvPercent * r.totalVolumeMl / 100.0).isWithin(1e-9).of(18.0)
    }

    @Test
    fun `the name lists the base and the add-ins`() {
        assertThat(CustomDrink.name("Black coffee", listOf(sugar(3.0)))).isEqualTo("Black coffee + 3 tsp sugar")
        assertThat(CustomDrink.name("Black coffee", listOf(sugar(1.5), option("milk-whole").toAddIn(30.0, AddInUnit.ML))))
            .isEqualTo("Black coffee + 1.5 tsp sugar + 30 ml whole milk")
        assertThat(CustomDrink.name("Tea", emptyList())).isEqualTo("Tea")
    }

    // ---- allergens: the union, read by the same local engine ----

    private val dairyAliases = listOf("milk", "cream", "half and half", "butter", "whey").map { AllergenAlias("dairy", it) }

    private fun dairyScan(name: String, allergenText: String) = AllergenMatcher.scan(
        aliases = dairyAliases,
        avoidedAllergens = setOf("dairy"),
        name = name,
        ingredients = allergenText.takeIf { it.isNotBlank() },
        structuredContains = allergenText.substringBefore(" | ").split(',').map { it.trim() }.filter { it.matches(Regex("[a-z]{2}:[a-z0-9-]+")) },
    )

    @Test
    fun `plain black coffee with sugar raises no dairy warning`() {
        val text = CustomDrink.allergenText(blackCoffee, listOf(sugar(3.0)))
        assertThat(dairyScan("Hot drink", text).hasAnything).isFalse()
    }

    @Test
    fun `a milk add-in raises dairy even when the drink's name says nothing`() {
        val addIns = listOf(option("milk-skim").toAddIn(2.0, AddInUnit.TBSP))
        val text = CustomDrink.allergenText(blackCoffee, addIns)
        assertThat(text).contains("en:milk")
        val scan = dairyScan("Morning cup", text)
        assertThat(scan.hasAnything).isTrue()
        assertThat(scan.blocks).isTrue()
    }

    @Test
    fun `cream and half-and-half raise dairy`() {
        for (key in listOf("cream", "half-half")) {
            val text = CustomDrink.allergenText(blackCoffee, listOf(option(key).toAddIn(1.0, AddInUnit.TBSP)))
            assertThat(dairyScan("Cup", text).hasAnything).isTrue()
        }
    }

    @Test
    fun `the base drink's own allergens are never dropped`() {
        val latte = DrinkBase("Latte", Macros(kcal = 42.0), allergenTags = "en:milk | Espresso, steamed whole milk")
        val text = CustomDrink.allergenText(latte, listOf(option("sweetener").toAddIn(1.0)))
        assertThat(text).contains("en:milk")
        assertThat(text).contains("Espresso, steamed whole milk")
        assertThat(dairyScan("Cup", text).blocks).isTrue()
    }

    @Test
    fun `a soy milk add-in carries the soy tag`() {
        assertThat(CustomDrink.allergenText(blackCoffee, listOf(option("soy").toAddIn(1.0, AddInUnit.TBSP)))).contains("en:soybeans")
    }

    @Test
    fun `a nutrient unlisted in the base or an add-in is unlisted in the result`() {
        val base = blackCoffee.copy(unlisted = "fiber")
        val a = sugar(1.0).copy(unlisted = "sodium")
        assertThat(CustomDrink.unlistedUnion(base, listOf(a)).split(',')).containsExactly("fiber", "sodium")
        assertThat(CustomDrink.unlistedUnion(blackCoffee, listOf(sugar(1.0)))).isEmpty()
    }

    @Test
    fun `a recipe round-trips and a damaged one decodes to null`() {
        val recipe = CustomDrinkRecipe("base-1", "Coffee, black", 240.0, listOf(sugar(3.0)))
        assertThat(CustomDrinkRecipe.decode(recipe.encode())).isEqualTo(recipe)
        assertThat(CustomDrinkRecipe.decode("not json")).isNull()
        assertThat(CustomDrinkRecipe.decode(null)).isNull()
    }
}
