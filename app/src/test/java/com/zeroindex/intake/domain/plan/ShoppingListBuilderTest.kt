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

    @Test fun `recipe groups become a note on the merged line, and ungrouped lines have none`() {
        val lines = ShoppingListBuilder.generate(
            listOf(
                ShoppingIngredient("butter", 14.0, "For the Chicken"),
                ShoppingIngredient("butter", 113.0, "For the Sauce"),
                ShoppingIngredient("butter", 20.0, "For the Sauce"),
                ShoppingIngredient("Onion", 150.0),
                ShoppingIngredient("garlic", null, ""),
            ),
        )
        assertThat(lines.first { it.key == "butter" }.note).isEqualTo("For the Chicken, For the Sauce")
        assertThat(lines.first { it.key == "butter" }.quantity).isEqualTo("147 g")
        assertThat(lines.first { it.key == "onion" }.note).isNull()
        assertThat(lines.first { it.key == "garlic" }.note).isNull()
    }

    @Test fun `the share text carries the group note`() {
        val sections = ShoppingListBuilder.group(listOf(item("Butter", StoreCategory.OTHER).copy(note = "For the Sauce")))
        assertThat(ShoppingListBuilder.shareText("List", sections, WeightUnit.KILOGRAMS)).contains("[ ] Butter (For the Sauce)")
    }

    @Test fun `unknown amounts leave the quantity empty`() {
        val l = ShoppingListBuilder.generate(listOf(ShoppingIngredient("Lemon", null))).single()
        assertThat(l.quantity).isEmpty()
    }

    @Test fun `dairy and eggs have their own aisle and look-alikes do not land in it`() {
        for (k in listOf("milk", "whole milk", "cheddar cheese", "butter", "greek yogurt", "heavy cream", "sour cream", "cream cheese", "eggs", "egg", "almond milk", "oat milk", "buttermilk", "mozzarella", "parmesan"))
            assertThat(ShoppingListBuilder.categoryOf(k)).isEqualTo(StoreCategory.DAIRY)
        assertThat(ShoppingListBuilder.categoryOf("half and half")).isEqualTo(StoreCategory.DAIRY)
        assertThat(ShoppingListBuilder.categoryOf("peanut butter")).isEqualTo(StoreCategory.PANTRY)
        assertThat(ShoppingListBuilder.categoryOf("coconut milk")).isEqualTo(StoreCategory.PANTRY)
        assertThat(ShoppingListBuilder.categoryOf("milk powder")).isEqualTo(StoreCategory.PANTRY)
        assertThat(ShoppingListBuilder.categoryOf("milk chocolate")).isEqualTo(StoreCategory.PANTRY)
        assertThat(ShoppingListBuilder.categoryOf("egg noodles")).isEqualTo(StoreCategory.PANTRY)
        assertThat(ShoppingListBuilder.categoryOf("ice cream")).isEqualTo(StoreCategory.FROZEN)
        assertThat(ShoppingListBuilder.categoryOf("cream of mushroom soup")).isEqualTo(StoreCategory.PANTRY)
        assertThat(ShoppingListBuilder.categoryOf("eggplant")).isNotEqualTo(StoreCategory.DAIRY)
        assertThat(ShoppingListBuilder.sectionTitle(StoreCategory.DAIRY)).isEqualTo("Dairy & eggs")
    }

    @Test fun `rice and every pasta shape are grains in one section, and broth is pantry whatever animal`() {
        for (k in listOf("rice", "white rice", "brown rice", "spaghetti", "penne", "pasta", "macaroni", "linguine", "quinoa", "black bean", "lentil"))
            assertThat(ShoppingListBuilder.categoryOf(k)).isEqualTo(StoreCategory.PANTRY)
        for (k in listOf("rice", "spaghetti", "pasta", "penne")) assertThat(ShoppingListBuilder.isPantryStaple(k)).isFalse()
        assertThat(ShoppingListBuilder.categoryOf("chicken broth")).isEqualTo(StoreCategory.PANTRY)
        assertThat(ShoppingListBuilder.categoryOf("beef stock")).isEqualTo(StoreCategory.PANTRY)
        assertThat(ShoppingListBuilder.categoryOf("chicken breast")).isEqualTo(StoreCategory.MEAT)
    }

    @Test fun `the dairy section sits between meat and pantry`() {
        val g = ShoppingListBuilder.group(
            listOf(item("Rice", StoreCategory.PANTRY), item("Milk", StoreCategory.DAIRY), item("Beef", StoreCategory.MEAT), item("Apples", StoreCategory.PRODUCE)),
        )
        assertThat(g.sections.map { it.first }).containsExactly(StoreCategory.PRODUCE, StoreCategory.MEAT, StoreCategory.DAIRY, StoreCategory.PANTRY).inOrder()
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
