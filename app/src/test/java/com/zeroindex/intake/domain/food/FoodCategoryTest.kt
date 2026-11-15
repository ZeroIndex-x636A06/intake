package com.zeroindex.intake.domain.food

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The icon a food gets when it has no photo. Names come from the seed and from USDA-style labels (QA Q1-25). */
class FoodCategoryTest {

    @Test
    fun vegetablesAndPotatoesAreNotBakedGoods() {
        assertThat(FoodCategory.of("Sweet potato, baked")).isEqualTo(FoodCategory.VEGETABLE)
        assertThat(FoodCategory.of("Sweet potatoes, cooked, boiled")).isEqualTo(FoodCategory.VEGETABLE)
        assertThat(FoodCategory.of("Sweet corn, yellow, canned")).isEqualTo(FoodCategory.VEGETABLE)
        assertThat(FoodCategory.of("Peppers, sweet, red, raw")).isEqualTo(FoodCategory.VEGETABLE)
        assertThat(FoodCategory.of("Yam, cooked")).isEqualTo(FoodCategory.VEGETABLE)
        assertThat(FoodCategory.of("Potatoes, mashed")).isEqualTo(FoodCategory.VEGETABLE)
        assertThat(FoodCategory.of("Broccoli, steamed")).isEqualTo(FoodCategory.VEGETABLE)
        assertThat(FoodCategory.of("Spinach, raw")).isEqualTo(FoodCategory.VEGETABLE)
    }

    @Test
    fun bakedGoodsAndSweetsStillGetTheirIcon() {
        assertThat(FoodCategory.of("Cookie, chocolate chip")).isEqualTo(FoodCategory.SWEET)
        assertThat(FoodCategory.of("Carrot cake")).isEqualTo(FoodCategory.SWEET)
        assertThat(FoodCategory.of("Brownie")).isEqualTo(FoodCategory.SWEET)
        assertThat(FoodCategory.of("Sugar, white")).isEqualTo(FoodCategory.SWEET)
        assertThat(FoodCategory.of("Dark chocolate 70%")).isEqualTo(FoodCategory.SWEET)
        assertThat(FoodCategory.of("Ice cream, vanilla")).isEqualTo(FoodCategory.SWEET)
    }

    @Test
    fun meatAndMealsWithMeatGetAMeatIcon() {
        assertThat(FoodCategory.of("Turkey chili")).isEqualTo(FoodCategory.MEAT)
        assertThat(FoodCategory.of("Chicken breast, roasted")).isEqualTo(FoodCategory.MEAT)
        assertThat(FoodCategory.of("Beef, ground, 90% lean")).isEqualTo(FoodCategory.MEAT)
        assertThat(FoodCategory.of("Salmon fillet, baked")).isEqualTo(FoodCategory.FISH)
        assertThat(FoodCategory.of("Egg, whole, cooked")).isEqualTo(FoodCategory.EGG)
    }

    @Test
    fun dairyNutsFruitGrainsAndDrinks() {
        assertThat(FoodCategory.of("Milk, whole")).isEqualTo(FoodCategory.DAIRY)
        assertThat(FoodCategory.of("Cheddar cheese")).isEqualTo(FoodCategory.DAIRY)
        assertThat(FoodCategory.of("Peanut butter")).isEqualTo(FoodCategory.NUT)
        assertThat(FoodCategory.of("Almonds, dry roasted")).isEqualTo(FoodCategory.NUT)
        assertThat(FoodCategory.of("Apple, raw")).isEqualTo(FoodCategory.FRUIT)
        assertThat(FoodCategory.of("Banana")).isEqualTo(FoodCategory.FRUIT)
        assertThat(FoodCategory.of("Whole wheat bread")).isEqualTo(FoodCategory.GRAIN)
        assertThat(FoodCategory.of("Rice, white, cooked")).isEqualTo(FoodCategory.GRAIN)
        assertThat(FoodCategory.of("Coffee, brewed")).isEqualTo(FoodCategory.DRINK)
    }

    @Test
    fun unknownNamesGetThePlainPlate() {
        assertThat(FoodCategory.of("Whey protein powder")).isEqualTo(FoodCategory.OTHER)
        assertThat(FoodCategory.of("Mystery stew")).isEqualTo(FoodCategory.OTHER)
        assertThat(FoodCategory.of("")).isEqualTo(FoodCategory.OTHER)
    }

    @Test
    fun theCategoryTextCanDecideWhenTheNameCannot() {
        assertThat(FoodCategory.of("Mystery stew", "Beef Products")).isEqualTo(FoodCategory.MEAT)
        assertThat(FoodCategory.of("Sweet", "Potatoes")).isEqualTo(FoodCategory.VEGETABLE)
    }
}
