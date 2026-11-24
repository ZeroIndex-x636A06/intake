package com.zeroindex.intake.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SegmentedWidthsTest {
    @Test fun spareRoomIsSharedEqually() {
        // 3 segments, 2 px gaps: room 296, natural 120 -> 176 spare, 58 each.
        assertEquals(listOf(88, 108, 98), segmentWidths(listOf(30, 50, 40), 2, 300))
    }

    @Test fun everySegmentKeepsItsNaturalWidthWhenItFits() {
        val w = segmentWidths(listOf(60, 120, 80, 100), 2, 600)
        assertTrue(w.zip(listOf(60, 120, 80, 100)).all { (a, n) -> a >= n })
        assertTrue(w.sum() + 6 <= 600)
    }

    @Test fun shrinksInProportionWhenItDoesNotFit() {
        val w = segmentWidths(listOf(100, 200, 100, 200), 2, 400)
        assertTrue(w.sum() + 6 <= 400)
        assertEquals(w[1], w[3])
        assertTrue(w[1] > w[0])
    }

    @Test fun emptyAndZeroRoom() {
        assertEquals(emptyList<Int>(), segmentWidths(emptyList(), 2, 100))
        assertEquals(listOf(0, 0), segmentWidths(listOf(10, 10), 2, 1))
    }
}
