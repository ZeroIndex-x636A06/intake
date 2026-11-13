package com.zeroindex.intake.domain.menu

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.data.ai.menu.MenuNutritionMerge
import com.zeroindex.intake.data.db.entity.AllergenStatus
import com.zeroindex.intake.data.db.entity.MenuItemEntity
import org.junit.Test

/** A "nothing found" scan is clear only when the item's source stated allergen information. */
class MenuAllergenEvidenceTest {

    private val chartLine = "Restaurant allergen chart: no major allergens listed"

    @Test
    fun `an AI estimate states nothing even when the model wrote ingredients`() {
        val description = "${MenuAllergenEvidence.ESTIMATE_NOTE} · Guessed ingredients: pancakes, syrup"
        assertThat(MenuAllergenEvidence.statesNothing(description, "pancakes, syrup, butter")).isTrue()
        assertThat(MenuAllergenEvidence.statesNothing(description, "")).isTrue()
        // Even a "May contain" line that came from the model's own claims does not make an estimate a stated source.
        assertThat(MenuAllergenEvidence.statesNothing(description, MenuAllergenEvidence.claimsAsText(listOf("milk")))).isTrue()
    }

    @Test
    fun `a nutrition-only item with no allergen column states nothing`() {
        assertThat(MenuAllergenEvidence.statesNothing("Sandwiches · 120 g serving", "")).isTrue()
    }

    @Test
    fun `an item from a chart states allergens, including a chart line that lists none`() {
        assertThat(MenuAllergenEvidence.statesNothing("Sandwiches", "Contains: Milk, Egg")).isFalse()
        assertThat(MenuAllergenEvidence.statesNothing("Sandwiches", chartLine)).isFalse()
    }

    @Test
    fun `model claims are stored as a may-contain line, never as a published contains`() {
        val text = MenuAllergenEvidence.claimsAsText(listOf("Milk", " egg ", "milk", "wheat", ""))
        assertThat(text).isEqualTo("May contain: Milk, egg, wheat")
        assertThat(StatedAllergens.parse(text).contains).isEmpty()
        val stated = StatedAllergens.parse(text)
        assertThat(stated.contains).isEmpty()
        assertThat(stated.mayContain.map { it.lowercase() }).containsExactly("milk", "egg", "wheat").inOrder()
        assertThat(MenuAllergenEvidence.claimsAsText(emptyList())).isEmpty()
    }

    private fun item(name: String, kcal: Double?, status: AllergenStatus, stated: String, description: String) = MenuItemEntity(
        id = "i-$name", sourceId = "S", restaurantName = "R", name = name, description = description, ingredientsText = stated,
        kcal = kcal, proteinG = kcal?.let { 10.0 }, macrosPublished = kcal != null, allergenStatus = status,
        importedAt = 1, createdAt = 1, updatedAt = 1,
    )

    @Test
    fun `merged chart and nutrition items follow the chart's statement, and nutrition-only items stay unstated`() {
        val merged = MenuNutritionMerge.merge(
            chart = listOf(
                item("Fries", null, AllergenStatus.CLEAR, chartLine, "chart"),
                item("Shake", null, AllergenStatus.CONTAINS, "Contains: Milk", "chart"),
            ),
            nutrition = listOf(
                item("Fries", 300.0, AllergenStatus.CHECK_LABEL, "", "nutrition"),
                item("Shake", 500.0, AllergenStatus.CHECK_LABEL, "", "nutrition"),
                item("Apple Pie", 250.0, AllergenStatus.CHECK_LABEL, "", "nutrition"),
            ),
            sourceId = "S", now = 9,
        ).items.associateBy { it.name }
        // The chart states "none listed" for Fries: its verdicts apply and a clean scan may be clear.
        assertThat(MenuAllergenEvidence.statesNothing(merged.getValue("Fries").description, merged.getValue("Fries").ingredientsText)).isFalse()
        // The chart states milk for the shake: the stated list is kept.
        assertThat(merged.getValue("Shake").ingredientsText).contains("Milk")
        assertThat(merged.getValue("Shake").allergenStatus).isEqualTo(AllergenStatus.CONTAINS)
        // The pie is in the nutrition file only: nothing states its allergens.
        val pie = merged.getValue("Apple Pie")
        assertThat(MenuAllergenEvidence.statesNothing(pie.description, pie.ingredientsText)).isTrue()
    }
}
