package com.zeroindex.intake.domain.plan

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.data.db.entity.ShoppingListItemEntity
import com.zeroindex.intake.domain.allergen.AllergenAlias
import com.zeroindex.intake.domain.allergen.AllergenMatcher
import com.zeroindex.intake.domain.model.StoreCategory
import org.junit.Test

class ShoppingRecategoriseTest {
    private fun row(name: String, cat: StoreCategory, deleted: Boolean = false) = ShoppingListItemEntity(
        id = name, planWeekId = "w", name = name, category = cat, createdAt = 1, updatedAt = 1, deletedAt = if (deleted) 9L else null,
    )

    @Test fun `milk saved under Other moves to dairy`() {
        val moved = ShoppingListBuilder.recategorised(listOf(row("Milk", StoreCategory.OTHER)), now = 50)
        assertThat(moved).hasSize(1)
        assertThat(moved[0].category).isEqualTo(StoreCategory.DAIRY)
        assertThat(moved[0].updatedAt).isEqualTo(50)
        assertThat(moved[0].name).isEqualTo("Milk")
    }

    @Test fun `only Other rows move`() {
        val rows = listOf(row("milk", StoreCategory.PANTRY), row("cheddar", StoreCategory.OTHER), row("chicken breast", StoreCategory.OTHER))
        val moved = ShoppingListBuilder.recategorised(rows, now = 5)
        assertThat(moved.map { it.name to it.category }).containsExactly(
            "cheddar" to StoreCategory.DAIRY,
            "chicken breast" to StoreCategory.MEAT,
        )
    }

    @Test fun `a word the categoriser still does not know stays in Other, and removed rows are left alone`() {
        val rows = listOf(row("zzzz thing", StoreCategory.OTHER), row("milk", StoreCategory.OTHER, deleted = true))
        assertThat(ShoppingListBuilder.recategorised(rows, now = 5)).isEmpty()
    }

    @Test fun `a second pass changes nothing`() {
        val first = ShoppingListBuilder.recategorised(listOf(row("butter", StoreCategory.OTHER)), now = 5)
        assertThat(ShoppingListBuilder.recategorised(first, now = 6)).isEmpty()
    }

    private val dairy = listOf(AllergenAlias("dairy", "milk"), AllergenAlias("dairy", "butter"), AllergenAlias("dairy", "cheese"))

    private fun scan(name: String) = AllergenMatcher.scan(
        aliases = dairy, avoidedAllergens = setOf("dairy"), name = null, ingredients = name, checkLabelInIngredients = true,
    )

    @Test fun `a dairy item is marked for a dairy-avoiding user and a clear item is not`() {
        val scans = mapOf("milk" to scan("milk"), "chicken breast" to scan("chicken breast"))
        val flagged = ShoppingAllergenMarks.flagged(scans, includeCheckLabel = true)
        assertThat(flagged.keys).containsExactly("milk")
    }

    @Test fun `nothing is marked when the person avoids nothing`() {
        val none = AllergenMatcher.scan(aliases = dairy, avoidedAllergens = emptySet(), name = null, ingredients = "milk", checkLabelInIngredients = true)
        assertThat(ShoppingAllergenMarks.marks(none, includeCheckLabel = true)).isFalse()
    }
}
