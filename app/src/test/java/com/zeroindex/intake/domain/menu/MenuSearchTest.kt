package com.zeroindex.intake.domain.menu

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MenuSearchTest {

    private data class Item(val name: String, val restaurant: String)

    private val items = listOf(
        Item("Classic Roast Beef", "Arby’s"),
        Item("Roast Beef Gyro", "Arby’s"),
        Item("Big Mac", "McDonald's"),
        Item("Chicken Roast Sandwich", "Cafe Roast"),
    )

    private fun find(q: String) = MenuSearch.search(items, q, { it.name }, { it.restaurant }).map { it.name }

    @Test
    fun `every word must match, in the name or the restaurant`() {
        assertThat(find("roast beef")).containsExactly("Classic Roast Beef", "Roast Beef Gyro")
        assertThat(find("arbys gyro")).containsExactly("Roast Beef Gyro")
    }

    @Test
    fun `apostrophes and case are ignored`() {
        assertThat(find("MCDONALDS big")).containsExactly("Big Mac")
        assertThat(find("arby's")).hasSize(2)
    }

    @Test
    fun `a name that starts with the first word comes before one that only contains it`() {
        assertThat(find("roast").first()).isEqualTo("Roast Beef Gyro")
    }

    @Test
    fun `a blank query and a miss find nothing`() {
        assertThat(find("  ")).isEmpty()
        assertThat(find("pizza")).isEmpty()
    }

    @Test
    fun `the limit applies after ranking`() {
        assertThat(MenuSearch.search(items, "roast", { it.name }, { it.restaurant }, limit = 1)).hasSize(1)
    }

    @Test
    fun `an item with no ingredient text or stated allergens is name-only, one with either is not`() {
        assertThat(MenuSearch.hasNoIngredientInfo("")).isTrue()
        assertThat(MenuSearch.hasNoIngredientInfo("   ")).isTrue()
        assertThat(MenuSearch.hasNoIngredientInfo("Contains: Milk")).isFalse()
        assertThat(MenuSearch.hasNoIngredientInfo("Restaurant chart: no major allergens listed")).isFalse()
    }
}
