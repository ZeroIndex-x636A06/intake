package com.zeroindex.intake.domain.drinks

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.domain.hydration.DrinkType
import com.zeroindex.intake.domain.units.VolumeUnit
import org.junit.Test

class DrinkCategoryTest {

    private fun cat(name: String, category: String = "", tags: List<String> = emptyList()) =
        DrinkCategorizer.categorize(name, category, tags)

    @Test
    fun `common drinks land in their category by name`() {
        assertThat(cat("Cola")).isEqualTo(DrinkCategory.SODA)
        assertThat(cat("Diet cola")).isEqualTo(DrinkCategory.SODA)
        assertThat(cat("Orange juice")).isEqualTo(DrinkCategory.JUICE)
        assertThat(cat("Tea, black, brewed")).isEqualTo(DrinkCategory.TEA)
        assertThat(cat("Coffee, brewed")).isEqualTo(DrinkCategory.COFFEE)
        assertThat(cat("Latte")).isEqualTo(DrinkCategory.COFFEE)
        assertThat(cat("Milk, whole")).isEqualTo(DrinkCategory.MILK)
        assertThat(cat("Beer")).isEqualTo(DrinkCategory.ALCOHOL)
        assertThat(cat("Red Bull")).isEqualTo(DrinkCategory.SPORTS_ENERGY)
        assertThat(cat("Gatorade")).isEqualTo(DrinkCategory.SPORTS_ENERGY)
        assertThat(cat("Sparkling water")).isEqualTo(DrinkCategory.WATER)
    }

    @Test
    fun `a soda that mentions beer or ginger is still a soda`() {
        assertThat(cat("Root beer")).isEqualTo(DrinkCategory.SODA)
        assertThat(cat("Ginger ale")).isEqualTo(DrinkCategory.SODA)
    }

    @Test
    fun `foods that merely mention a drink word are not drinks`() {
        assertThat(cat("Beans, canned, in water")).isNull()
        assertThat(cat("Milk chocolate bar")).isNull()
        assertThat(cat("Protein powder")).isNull()
        assertThat(cat("Coffee creamer")).isNull()
        assertThat(cat("Cheese made from milk")).isNull()
        assertThat(cat("Tea concentrate")).isNull()
    }

    @Test
    fun `a source category tag is the strongest statement`() {
        assertThat(cat("Fizzy thing", tags = listOf("en:sodas"))).isEqualTo(DrinkCategory.SODA)
        assertThat(cat("Oat drink", tags = listOf("en:plant-based-milks"))).isEqualTo(DrinkCategory.MILK)
        assertThat(cat("Brew", tags = listOf("en:beers"))).isEqualTo(DrinkCategory.ALCOHOL)
    }

    @Test
    fun `an unmatched beverage is Other and an unmatched food is not a drink`() {
        assertThat(cat("Kombucha", category = "Beverages")).isEqualTo(DrinkCategory.OTHER)
        assertThat(cat("Strawberry yogurt")).isNull()
        assertThat(cat("")).isNull()
    }

    @Test
    fun `soups broths oils and sauces are never drinks`() {
        listOf(
            "Soup, chicken noodle, canned", "Chicken broth", "Beef stock", "Bouillon cubes", "Oil, olive, salad or cooking",
            "Coconut oil", "Soy sauce", "Tomato sauce", "Gravy, beef", "Salad dressing", "Apple cider vinegar",
        ).forEach { name ->
            assertThat(DrinkRule.isDrink(name)).isFalse()
            // Not even when the source files it under beverages.
            assertThat(DrinkRule.isDrink(name, sourceCategory = "Beverages")).isFalse()
            assertThat(DrinkRule.isDrink(name, offTags = listOf("en:beverages"))).isFalse()
        }
    }

    @Test
    fun `milk and plant milks are drinks`() {
        listOf("Milk, whole, 3.25% milkfat", "Oat milk", "Almond milk, unsweetened", "Soy milk", "Soymilk, original", "Coconut milk drink").forEach {
            assertThat(DrinkRule.categoryOf(it)).isEqualTo(DrinkCategory.MILK)
        }
        // Milk in the name of a food is not a drink.
        assertThat(DrinkRule.isDrink("Milk, condensed, sweetened")).isFalse()
        assertThat(DrinkRule.isDrink("Milk, dry, nonfat")).isFalse()
        assertThat(DrinkRule.isDrink("Milk chocolate")).isFalse()
    }

    @Test
    fun `the source category decides before the name does`() {
        // USDA files it under Beverages, Open Food Facts tags it: a drink even when the name gives nothing away.
        assertThat(DrinkRule.categoryOf("Horchata", sourceCategory = "Beverages")).isEqualTo(DrinkCategory.OTHER)
        assertThat(DrinkRule.categoryOf("Original Taste", offTags = listOf("en:beverages", "en:carbonated-drinks", "en:sodas"))).isEqualTo(DrinkCategory.SODA)
        assertThat(DrinkRule.categoryOf("Zero Sugar", offTags = listOf("en:beverages"))).isEqualTo(DrinkCategory.OTHER)
        // No category from the source: the name rules.
        assertThat(DrinkRule.categoryOf("Coke")).isEqualTo(DrinkCategory.SODA)
        assertThat(DrinkRule.isDrink("Chicken McNuggets")).isFalse()
        assertThat(DrinkRule.isDrink("Chicken McNuggets", sourceCategory = "Fast Foods")).isFalse()
        assertThat(DrinkRule.isDrink("Cheddar", offTags = listOf("en:dairies", "en:cheeses"))).isFalse()
    }

    @Test
    fun `drinks named with an excluded word are still drinks and near misses are not`() {
        assertThat(DrinkRule.categoryOf("Cream soda")).isEqualTo(DrinkCategory.SODA)
        assertThat(DrinkRule.categoryOf("Lipton Ice Tea Peach")).isEqualTo(DrinkCategory.TEA)
        assertThat(DrinkRule.isDrink("Water chestnuts")).isFalse()
        assertThat(DrinkRule.isDrink("Tea biscuits")).isFalse()
        assertThat(DrinkRule.isDrink("Coffee beans")).isFalse()
        assertThat(DrinkRule.isDrink("Crackers, soda")).isFalse()
    }

    @Test
    fun `each search keeps its own kind`() {
        assertThat(SearchScope.FOODS.keeps(isDrink = false)).isTrue()
        assertThat(SearchScope.FOODS.keeps(isDrink = true)).isFalse()
        assertThat(SearchScope.DRINKS.keeps(isDrink = true)).isTrue()
        assertThat(SearchScope.DRINKS.keeps(isDrink = false)).isFalse()
        // A food on the phone is a drink exactly when its stored category is set.
        assertThat(DrinkRule.isStoredDrink("SODA")).isTrue()
        assertThat(DrinkRule.isStoredDrink(null)).isFalse()
        assertThat(DrinkRule.isStoredDrink("")).isFalse()
    }

    @Test
    fun `stored category names read back and unknown ones are Other`() {
        assertThat(DrinkCategory.fromStored(null)).isNull()
        assertThat(DrinkCategory.fromStored("")).isNull()
        assertThat(DrinkCategory.fromStored("SODA")).isEqualTo(DrinkCategory.SODA)
        assertThat(DrinkCategory.fromStored("FROM_A_NEWER_BACKUP")).isEqualTo(DrinkCategory.OTHER)
    }

    @Test
    fun `the hydration type follows the category and the name`() {
        assertThat(DrinkKinds.typeOf(DrinkCategory.WATER, "Sparkling water", null)).isEqualTo(DrinkType.SPARKLING)
        assertThat(DrinkKinds.typeOf(DrinkCategory.WATER, "Water", null)).isEqualTo(DrinkType.WATER)
        assertThat(DrinkKinds.typeOf(DrinkCategory.ALCOHOL, "Lager", 5.0)).isEqualTo(DrinkType.BEER)
        assertThat(DrinkKinds.typeOf(DrinkCategory.ALCOHOL, "Red wine", 12.0)).isEqualTo(DrinkType.WINE)
        assertThat(DrinkKinds.typeOf(DrinkCategory.ALCOHOL, "Vodka", 40.0)).isEqualTo(DrinkType.SPIRITS)
        assertThat(DrinkKinds.typeOf(DrinkCategory.SODA, "Cola", null)).isEqualTo(DrinkType.SODA)
    }

    @Test
    fun `every category has a label for its chip`() {
        assertThat(DrinkCategory.entries.map { it.label }).containsAtLeast("Water", "Coffee", "Tea", "Soda", "Juice", "Sports & energy", "Milk & alternatives", "Alcohol", "Other")
    }

    @Test
    fun `quick sizes follow the unit and add saved sizes once`() {
        val us = DrinkSizes.defaults(VolumeUnit.US_FLUID_OUNCES)
        assertThat(us.map { it.label }).containsExactly("8 fl oz glass", "12 fl oz can", "16.9 fl oz bottle", "20 fl oz bottle").inOrder()
        assertThat(DrinkSizes.defaults(VolumeUnit.MILLILITRES).map { it.ml }).containsExactly(250.0, 330.0, 500.0, 750.0).inOrder()
        // 501 ml is within 2 ml of the 500 ml button, 600 ml is new.
        val merged = DrinkSizes.buttons(VolumeUnit.MILLILITRES, listOf(DrinkSize("501", 501.0), DrinkSize("600", 600.0), DrinkSize("zero", 0.0)))
        assertThat(merged.map { it.ml }).containsExactly(250.0, 330.0, 500.0, 600.0, 750.0).inOrder()
    }

    @Test
    fun `a typed size becomes whole millilitres`() {
        assertThat(DrinkSizes.toMl(VolumeUnit.US_FLUID_OUNCES, 12.0)).isEqualTo(355.0)
        assertThat(DrinkSizes.toMl(VolumeUnit.MILLILITRES, 0.0)).isEqualTo(1.0)
    }
}
