package com.zeroindex.intake.domain.plan

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RecipeMacrosTest {
    private val chicken = IngredientNutrition("Chicken", 600.0, 180.0, 20.0, 0.0, 11.0)
    private val rice = IngredientNutrition("Rice", 300.0, 350.0, 8.0, 78.0, 1.0)

    @Test fun `total is per100g times grams over 100 summed`() {
        val t = RecipeMacros.total(listOf(chicken, rice))
        assertThat(t.kcal).isWithin(1e-9).of(180 * 6.0 + 350 * 3.0)
        assertThat(t.protein).isWithin(1e-9).of(20 * 6.0 + 8 * 3.0)
        assertThat(t.carbs).isWithin(1e-9).of(78 * 3.0)
        assertThat(t.fat).isWithin(1e-9).of(11 * 6.0 + 1 * 3.0)
    }

    @Test fun `per serving divides by servings`() {
        val s = RecipeMacros.perServing(listOf(chicken, rice), 4)
        assertThat(s.kcal).isWithin(1e-9).of((180 * 6.0 + 350 * 3.0) / 4)
        assertThat(s.protein).isWithin(1e-9).of((20 * 6.0 + 8 * 3.0) / 4)
    }

    @Test fun `zero servings counts as one and nothing divides by zero`() {
        assertThat(RecipeMacros.perServing(listOf(rice), 0).kcal).isWithin(1e-9).of(1050.0)
        assertThat(RecipeMacros.perServing(emptyList(), 3).kcal).isEqualTo(0.0)
    }

    @Test fun `negative grams contribute nothing`() {
        assertThat(RecipeMacros.total(listOf(rice.copy(grams = -50.0))).kcal).isEqualTo(0.0)
    }

    @Test fun `grams per serving`() {
        assertThat(RecipeMacros.gramsPerServing(listOf(chicken, rice), 3)).isWithin(1e-9).of(300.0)
    }

    @Test fun `missing ingredients make the result incomplete not silently smaller`() {
        val r = RecipeMacros.compute(listOf(rice), listOf("Taco seasoning", " "), 2)
        assertThat(r.isComplete).isFalse()
        assertThat(r.missing).containsExactly("Taco seasoning")
        assertThat(r.perServing.kcal).isWithin(1e-9).of(525.0)
    }

    @Test fun `no data at all is empty and full data is complete`() {
        assertThat(RecipeMacros.compute(emptyList(), listOf("Beef"), 1).isEmpty).isTrue()
        assertThat(RecipeMacros.compute(listOf(rice), emptyList(), 1).isComplete).isTrue()
    }
}
