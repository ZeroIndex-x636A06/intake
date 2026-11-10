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
