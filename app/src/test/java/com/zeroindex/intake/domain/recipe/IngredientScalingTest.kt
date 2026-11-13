package com.zeroindex.intake.domain.recipe

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class IngredientScalingTest {
    private fun s(line: String, f: Double) = IngredientScaling.scaleLine(line, f)

    @Test
    fun `factor is servings over servings and safe on nonsense`() {
        assertThat(IngredientScaling.factor(6, 4)).isEqualTo(1.5)
        assertThat(IngredientScaling.factor(2, 4)).isEqualTo(0.5)
        assertThat(IngredientScaling.factor(0, 4)).isEqualTo(1.0)
        assertThat(IngredientScaling.factor(3, 0)).isEqualTo(1.0)
    }

    @Test
    fun `whole numbers and fractions`() {
        assertThat(s("2 cups flour", 1.5)).isEqualTo("3 cups flour")
        assertThat(s("1 1/2 cups flour", 2.0)).isEqualTo("3 cups flour")
        assertThat(s("1/2 tsp salt", 3.0)).isEqualTo("1 1/2 tsp salt")
        assertThat(s("1 cup milk", 1.0 / 3)).isEqualTo("1/3 cup milk")
        assertThat(s("3 tbsp oil", 0.5)).isEqualTo("1 1/2 tbsp oil")
    }

    @Test
    fun `unicode fractions are read`() {
        assertThat(s("½ cup rice", 2.0)).isEqualTo("1 cup rice")
        assertThat(s("1½ lb chicken", 2.0)).isEqualTo("3 lb chicken")
    }

    @Test
    fun `awkward results fall back to one decimal`() {
        assertThat(s("1 cup stock", 1.1)).isEqualTo("1.1 cup stock")
        assertThat(s("100 g butter", 1.25)).isEqualTo("125 g butter")
        assertThat(s("3 eggs", 2.5)).isEqualTo("7 1/2 eggs")
        assertThat(s("1 onion", 1.5)).isEqualTo("1 1/2 onion")
    }

    @Test
    fun `ranges scale at both ends and keep their separator`() {
        assertThat(s("2-3 cloves garlic", 2.0)).isEqualTo("4-6 cloves garlic")
        assertThat(s("1 to 2 tsp chili flakes", 2.0)).isEqualTo("2 to 4 tsp chili flakes")
        assertThat(s("3–4 sprigs thyme", 0.5)).isEqualTo("1 1/2–2 sprigs thyme")
    }

    @Test
    fun `lines with no amount are left alone`() {
        assertThat(s("salt to taste", 2.0)).isEqualTo("salt to taste")
        assertThat(s("Fresh basil, to serve", 3.0)).isEqualTo("Fresh basil, to serve")
        assertThat(s("2% milk", 2.0)).isEqualTo("2% milk")
    }

    @Test
    fun `a bracketed weight that restates the amount scales, a per-can weight does not`() {
        assertThat(s("8 oz (225 g) pasta", 2.0)).isEqualTo("16 oz (450 g) pasta")
        assertThat(s("2 (14 oz) cans tomatoes", 2.0)).isEqualTo("4 (14 oz) cans tomatoes")
    }

    @Test
    fun `a factor of one returns the line untouched`() {
        assertThat(s("1½ cups   flour", 1.0)).isEqualTo("1½ cups   flour")
        assertThat(s("2 cups flour", 0.0)).isEqualTo("2 cups flour")
        assertThat(s("2 cups flour", Double.NaN)).isEqualTo("2 cups flour")
    }

    @Test
    fun `bullets are kept`() {
        assertThat(s("- 2 cups flour", 2.0)).isEqualTo("- 4 cups flour")
    }

    @Test
    fun `scaleAll maps every line`() {
        assertThat(IngredientScaling.scaleAll(listOf("2 eggs", "pinch of salt"), 2.0)).containsExactly("4 eggs", "pinch of salt").inOrder()
    }

    @Test
    fun `formatAmount`() {
        assertThat(IngredientScaling.formatAmount(0.33)).isEqualTo("1/3")
        assertThat(IngredientScaling.formatAmount(0.67)).isEqualTo("2/3")
        assertThat(IngredientScaling.formatAmount(1.99)).isEqualTo("2")
        assertThat(IngredientScaling.formatAmount(12.4)).isEqualTo("12")
        assertThat(IngredientScaling.formatAmount(0.05)).isEqualTo("0.1")
    }
}
