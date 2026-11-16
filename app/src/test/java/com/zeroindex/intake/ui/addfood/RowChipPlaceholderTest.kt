package com.zeroindex.intake.ui.addfood

import com.zeroindex.intake.domain.swap.SwapAllergen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A row never reads as clear only because its verdict has not arrived (Q1-6). */
class RowChipPlaceholderTest {
    @Test
    fun `a row with no verdict yet shows Checking when the person avoids something`() {
        val state = AddFoodUiState(avoidsAnything = true)
        val chip = state.chipFor("food-1")
        assertNotNull(chip)
        assertTrue(chip!!.checking)
        assertTrue(state.refChipFor(7)!!.checking)
    }

    @Test
    fun `no chip at all when the person avoids nothing`() {
        val state = AddFoodUiState(avoidsAnything = false)
        assertNull(state.chipFor("food-1"))
        assertNull(state.refChipFor(7))
    }

    @Test
    fun `a real verdict wins over the placeholder`() {
        val contains = RowAllergen(SwapAllergen.CONTAINS, "milk")
        val state = AddFoodUiState(avoidsAnything = true, rowAllergens = mapOf("a" to contains), refAllergens = mapOf(3 to contains))
        assertEquals(contains, state.chipFor("a"))
        assertEquals(contains, state.refChipFor(3))
    }

    @Test
    fun `the placeholder is not a clear`() {
        assertTrue(RowAllergen.Checking.verdict != SwapAllergen.CLEAR)
    }
}
