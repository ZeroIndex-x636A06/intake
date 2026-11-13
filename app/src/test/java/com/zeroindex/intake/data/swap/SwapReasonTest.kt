package com.zeroindex.intake.data.swap

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.domain.swap.SwapAllergen
import com.zeroindex.intake.ui.addfood.AddFoodTab
import com.zeroindex.intake.ui.allergen.AllergenStripState
import org.junit.Test

class SwapReasonTest {

    @Test
    fun `the reason names what was matched, and a clear option has none`() {
        assertThat(SwapRepository.reasonFor(SwapAllergen.CLEAR, listOf("milk"))).isNull()
        assertThat(SwapRepository.reasonFor(SwapAllergen.CONTAINS, listOf("sesame"))).isEqualTo("Contains sesame")
        assertThat(SwapRepository.reasonFor(SwapAllergen.CONTAINS, listOf("milk", "peanuts", "sesame"))).isEqualTo("Contains milk, peanuts and sesame")
        assertThat(SwapRepository.reasonFor(SwapAllergen.MAY_CONTAIN, listOf("milk"))).isEqualTo("May contain milk")
        assertThat(SwapRepository.reasonFor(SwapAllergen.CHECK_LABEL, listOf("teriyaki"))).isEqualTo("Check the label: teriyaki")
    }

    @Test
    fun `a flagged option always has a reason even with no terms`() {
        assertThat(SwapRepository.reasonFor(SwapAllergen.CONTAINS, emptyList())).isEqualTo("Contains an allergen you avoid")
        assertThat(SwapRepository.reasonFor(SwapAllergen.MAY_CONTAIN, emptyList())).isEqualTo("May contain an allergen you avoid")
        assertThat(SwapRepository.reasonFor(SwapAllergen.CHECK_LABEL, emptyList())).isEqualTo("Check the label")
    }

    @Test
    fun `the strip's status line reads like the frame`() {
        assertThat(AllergenStripState(listOf("Milk"), strict = true).title).isEqualTo("Milk · strict")
        assertThat(AllergenStripState(listOf("Milk", "Peanuts"), strict = false).title).isEqualTo("Milk and Peanuts · avoid")
        assertThat(AllergenStripState(listOf("Milk", "Peanuts", "Sesame", "Egg"), strict = true).title).isEqualTo("4 allergens · strict")
    }

    @Test
    fun `log food modes read from the route and fall back to search`() {
        assertThat(AddFoodTab.fromName("BARCODE")).isEqualTo(AddFoodTab.BARCODE)
        assertThat(AddFoodTab.fromName("PHOTO")).isEqualTo(AddFoodTab.PHOTO)
        assertThat(AddFoodTab.fromName("MY_FOODS")).isEqualTo(AddFoodTab.MY_FOODS)
        assertThat(AddFoodTab.fromName("nonsense")).isEqualTo(AddFoodTab.SEARCH)
        assertThat(AddFoodTab.fromName(null)).isEqualTo(AddFoodTab.SEARCH)
        assertThat(AddFoodTab.entries.take(3).map { it.label }).containsExactly("Search", "Scan", "Photo").inOrder()
    }
}
