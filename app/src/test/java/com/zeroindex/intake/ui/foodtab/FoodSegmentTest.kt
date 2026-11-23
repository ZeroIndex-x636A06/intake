package com.zeroindex.intake.ui.foodtab

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FoodSegmentTest {
    @Test
    fun `the Food tab has Diary, Plan, Recipes and Library in that order and opens on Diary`() {
        assertThat(FoodSegment.entries.map { it.name }).containsExactly("Diary", "Plan", "Recipes", "Library").inOrder()
        assertThat(FoodSegment.Default).isEqualTo(FoodSegment.Diary)
    }

    @Test
    fun `a bad saved index falls back to Diary`() {
        assertThat(FoodSegment.fromIndex(-1)).isEqualTo(FoodSegment.Diary)
        assertThat(FoodSegment.fromIndex(9)).isEqualTo(FoodSegment.Diary)
        assertThat(FoodSegment.fromIndex(3)).isEqualTo(FoodSegment.Library)
    }

    @Test
    fun `the Library lists every place the old More list and the diary reached`() {
        assertThat(LibraryEntry.entries.map { it.name })
            .containsExactly("TEMPLATES", "SHOPPING", "EATING_OUT", "MENUS", "MY_FOODS", "DRINKS").inOrder()
    }
}
