package com.zeroindex.intake.domain.nutrition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MealSummaryTest {

    private fun item(kcal: Double, p: Double, c: Double, f: Double, vararg unlisted: CoreMacro) =
        MealItemFacts(kcal, p, c, f, unlisted.toSet())

    @Test fun totalsAddUpAndRoundKcal() {
        val t = MealSummary.totals(listOf(item(120.4, 3.0, 20.0, 2.0), item(99.7, 1.0, 9.0, 8.0)))
        assertEquals(2, t.items)
        assertEquals(220, t.kcal)
        assertEquals(4.0, t.protein.grams!!, 1e-9)
        assertEquals("29 g", t.carbs.text)
        assertEquals("10 g", t.fat.text)
    }

    @Test fun anEmptyMealIsATrueZero() {
        val t = MealSummary.totals(emptyList())
        assertEquals(0, t.kcal)
        assertEquals("0 g", t.protein.text)
    }

    @Test fun aMacroEveryItemLeftOutIsNotListedNeverZero() {
        val t = MealSummary.totals(listOf(item(100.0, 0.0, 10.0, 5.0, CoreMacro.PROTEIN), item(50.0, 0.0, 5.0, 1.0, CoreMacro.PROTEIN)))
        assertNull(t.protein.grams)
        assertEquals("not listed", t.protein.text)
        assertEquals(0.0, t.splitProtein, 0.0)
        assertEquals("15 g", t.carbs.text)
    }

    @Test fun aMacroSomeItemsLeftOutIsAFloor() {
        val t = MealSummary.totals(listOf(item(100.0, 8.0, 10.0, 5.0), item(50.0, 0.0, 5.0, 1.0, CoreMacro.PROTEIN)))
        assertEquals("at least 8 g", t.protein.text)
        assertTrue(t.protein.partial)
        assertEquals("Protein at least 8 g · Carbs 15 g · Fat 6 g", MealSummary.macroLine(t))
    }

    @Test fun stepUpGoesByTheStepForTheSize() {
        assertEquals(35.0, PortionStep.up(30.0), 1e-9)
        assertEquals(60.0, PortionStep.up(50.0), 1e-9)
        assertEquals(200.0, PortionStep.up(195.0), 1e-9)
        assertEquals(225.0, PortionStep.up(200.0), 1e-9)
    }

    @Test fun stepDownMirrorsStepUpAndLandsOnTheGrid() {
        assertEquals(25.0, PortionStep.down(30.0)!!, 1e-9)
        assertEquals(30.0, PortionStep.down(33.0)!!, 1e-9)
        assertEquals(50.0, PortionStep.down(60.0)!!, 1e-9)
        assertEquals(190.0, PortionStep.down(200.0)!!, 1e-9)
        assertEquals(PortionStep.up(190.0), 200.0, 1e-9)
    }

    @Test fun stepDownToNothingIsRefused() {
        assertNull(PortionStep.down(5.0))
        assertNull(PortionStep.down(3.0))
        assertEquals(5.0, PortionStep.down(10.0)!!, 1e-9)
    }
}
