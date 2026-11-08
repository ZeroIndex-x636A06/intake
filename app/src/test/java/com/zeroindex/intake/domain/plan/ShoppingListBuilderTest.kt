package com.zeroindex.intake.domain.plan

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.data.db.entity.ShoppingListItemEntity
import com.zeroindex.intake.domain.model.StoreCategory
import com.zeroindex.intake.domain.units.WeightUnit
import org.junit.Test

class ShoppingListBuilderTest {
    private fun item(
        name: String,
        cat: StoreCategory,
        staple: Boolean = false,
        qty: String = "",
        checked: Boolean = false,
        deleted: Boolean = false,
    ) = ShoppingListItemEntity(
        id = name, planWeekId = "w", name = name, quantity = qty, category = cat, isChecked = checked,
        isPantryStaple = staple, createdAt = 0, updatedAt = 0, deletedAt = if (deleted) 5L else null,
    )

    @Test fun `same ingredient across recipes is one line with grams added`() {
        val lines = ShoppingListBuilder.generate(
            listOf(ShoppingIngredient("Ground beef", 400.0), ShoppingIngredient("ground beef ", 600.0), ShoppingIngredient("Onion", 150.0)),
        )
        assertThat(lines.map { it.key }).containsExactly("ground beef", "onion").inOrder()
        assertThat(lines.first().quantity).isEqualTo("1000 g")
    }

    @Test fun `unknown amounts leave the quantity empty`() {
        val l = ShoppingListBuilder.generate(listOf(ShoppingIngredient("Lemon", null))).single()
        assertThat(l.quantity).isEmpty()
    }

    @Test fun `categories and staples`() {
        assertThat(ShoppingListBuilder.categoryOf("chicken thigh, boneless")).isEqualTo(StoreCategory.MEAT)
        assertThat(ShoppingListBuilder.categoryOf("cucumber")).isEqualTo(StoreCategory.PRODUCE)
        assertThat(ShoppingListBuilder.categoryOf("frozen peas")).isEqualTo(StoreCategory.FROZEN)
        assertThat(ShoppingListBuilder.categoryOf("basmati rice, dry")).isEqualTo(StoreCategory.PANTRY)
        assertThat(ShoppingListBuilder.categoryOf("mystery item")).isEqualTo(StoreCategory.OTHER)
        assertThat(ShoppingListBuilder.isPantryStaple("olive oil")).isTrue()
        assertThat(ShoppingListBuilder.isPantryStaple("bell peppers")).isFalse()
        assertThat(ShoppingListBuilder.isPantryStaple("chicken")).isFalse()
    }

    @Test fun `grouping puts staples apart and walks the store in order`() {
        val g = ShoppingListBuilder.group(
            listOf(
                item("Beef", StoreCategory.MEAT), item("Apples", StoreCategory.PRODUCE),
                item("Salt", StoreCategory.PANTRY, staple = true),
                item("Gone", StoreCategory.PRODUCE, deleted = true), item("Bananas", StoreCategory.PRODUCE),
            ),
        )
        assertThat(g.sections.map { it.first }).containsExactly(StoreCategory.PRODUCE, StoreCategory.MEAT).inOrder()
        assertThat(g.sections.first().second.map { it.name }).containsExactly("Apples", "Bananas").inOrder()
        assertThat(g.pantry.map { it.name }).containsExactly("Salt")
        assertThat(g.count).isEqualTo(4)
    }

    @Test fun `quantities follow the weight unit`() {
        assertThat(ShoppingListBuilder.displayQuantity("450 g", WeightUnit.KILOGRAMS)).isEqualTo("450 g")
        assertThat(ShoppingListBuilder.displayQuantity("1200 g", WeightUnit.KILOGRAMS)).isEqualTo("1.2 kg")
        assertThat(ShoppingListBuilder.displayQuantity("1000 g", WeightUnit.POUNDS)).isEqualTo("2.2 lb")
        assertThat(ShoppingListBuilder.displayQuantity("1 bunch", WeightUnit.POUNDS)).isEqualTo("1 bunch")
    }

    @Test fun `share text lists sections then pantry with check marks`() {
        val g = ShoppingListBuilder.group(
            listOf(item("Beef", StoreCategory.MEAT, qty = "900 g", checked = true), item("Salt", StoreCategory.PANTRY, staple = true)),
        )
        val text = ShoppingListBuilder.shareText("Week 1", g, WeightUnit.KILOGRAMS)
        assertThat(text).contains("Week 1")
        assertThat(text).contains("Meat & fish")
        assertThat(text).contains("[x] Beef - 900 g")
        assertThat(text.indexOf("Pantry staples")).isGreaterThan(text.indexOf("Meat & fish"))
        assertThat(text).contains("[ ] Salt")
    }
}
