package com.zeroindex.intake.domain.menu

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MenuItemMatcherTest {

    @Test
    fun `names compare without case, punctuation, trademark signs and size words`() {
        assertThat(MenuItemMatcher.normalise("Big Mac®")).isEqualTo(MenuItemMatcher.normalise("BIG MAC"))
        assertThat(MenuItemMatcher.normalise("Sausage & Egg McMuffin®")).isEqualTo(MenuItemMatcher.normalise("Sausage and Egg McMuffin"))
        assertThat(MenuItemMatcher.normalise("Fries (Large)")).isEqualTo("fries")
        assertThat(MenuItemMatcher.normalise("Mayo Dip 19g")).isEqualTo("mayo dip")
        // "Double" and "Mini" change the product, so they stay.
        assertThat(MenuItemMatcher.normalise("Double Cheeseburger")).isEqualTo("double cheeseburger")
        assertThat(MenuItemMatcher.normalise("Mini McFlurry® Oreo®")).isNotEqualTo(MenuItemMatcher.normalise("McFlurry® Oreo®"))
    }

    @Test
    fun `sizes are read as words or quantities`() {
        assertThat(MenuItemMatcher.sizeOf("Latte (Regular)")).isEqualTo("regular")
        assertThat(MenuItemMatcher.sizeOf("Large Fries")).isEqualTo("large")
        assertThat(MenuItemMatcher.sizeOf("Mayo Dip 19g")).isEqualTo("19g")
        assertThat(MenuItemMatcher.sizeOf("Big Mac")).isNull()
    }

    @Test
    fun `sized variants meet their own size`() {
        val chart = listOf("Fries (Small)", "Fries (Medium)", "Fries (Large)")
        val nutrition = listOf("Large Fries", "Small Fries", "Medium Fries")
        val pairs = MenuItemMatcher.pair(chart, nutrition)
        assertThat(pairs).containsExactly(
            MenuItemMatcher.Pair(2, 0), MenuItemMatcher.Pair(0, 1), MenuItemMatcher.Pair(1, 2),
        )
    }

    @Test
    fun `a chart row with no size stands for every size of that item`() {
        val pairs = MenuItemMatcher.pair(listOf("Cola"), listOf("Cola Small", "Cola Large"))
        assertThat(pairs).containsExactly(MenuItemMatcher.Pair(0, 0), MenuItemMatcher.Pair(0, 1))
    }

    @Test
    fun `an unsized nutrition item takes a single sized chart row, but not one of several`() {
        assertThat(MenuItemMatcher.pair(listOf("Latte (Regular)"), listOf("Latte"))).contains(MenuItemMatcher.Pair(0, 0))
        val ambiguous = MenuItemMatcher.pair(listOf("Latte (Regular)", "Latte (Large)"), listOf("Latte"))
        assertThat(ambiguous).containsExactly(
            MenuItemMatcher.Pair(null, 0), MenuItemMatcher.Pair(0, null), MenuItemMatcher.Pair(1, null),
        )
    }

    @Test
    fun `items found in only one file come through as one-sided pairs`() {
        val pairs = MenuItemMatcher.pair(listOf("Big Mac", "Hash Brown"), listOf("big mac®", "McFlurry"))
        assertThat(pairs).containsExactly(
            MenuItemMatcher.Pair(0, 0), MenuItemMatcher.Pair(null, 1), MenuItemMatcher.Pair(1, null),
        )
    }

    @Test
    fun `allergens of two files are the union, a tick beats an asterisk, and nothing is dropped`() {
        val a = StatedAllergens(contains = listOf("Milk"), mayContain = listOf("Sesame", "Egg"))
        val b = StatedAllergens(contains = listOf("egg", "Wheat"), mayContain = listOf("Soy"))
        val u = MenuItemMatcher.union(a, b)
        assertThat(u.contains.map { it.lowercase() }).containsExactly("milk", "egg", "wheat")
        assertThat(u.mayContain).containsExactly("Sesame", "Soy")
        assertThat(MenuItemMatcher.union(StatedAllergens.NONE, StatedAllergens.NONE).isEmpty).isTrue()
    }

    // --- the follow-up card ----------------------------------------------------------------------------

    @Test
    fun `the card is asked for only after an import with no calories at all`() {
        assertThat(NutritionPrompt.shouldPrompt(listOf(null, null), skipped = false)).isTrue()
        assertThat(NutritionPrompt.shouldPrompt(listOf(null, 120.0), skipped = false)).isFalse()
        assertThat(NutritionPrompt.shouldPrompt(emptyList(), skipped = false)).isFalse()
    }

    @Test
    fun `skip dismisses the card but the source row keeps its action`() {
        val kcals = listOf<Double?>(null, null, null)
        assertThat(NutritionPrompt.shouldPrompt(kcals, skipped = true)).isFalse()
        assertThat(NutritionPrompt.showsRowAction(kcals)).isTrue()
        assertThat(NutritionPrompt.showsRowAction(listOf(250.0, null))).isFalse()
    }

    @Test
    fun `the result says how many matched`() {
        assertThat(NutritionPrompt.summary(98, 156)).isEqualTo("Matched 98 of 156 items.")
    }
}
