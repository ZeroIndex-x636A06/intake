package com.zeroindex.intake.domain.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TourPlanTest {

    private val all = TourSteps.today

    @Test
    fun theTourHasBetweenFourAndSixSteps() {
        assertTrue(all.size in 4..6)
    }

    @Test
    fun missingTargetsAreSkippedAndTheOrderIsKept() {
        // No workout card and no coach card on screen.
        val shown = TourPlan.filter(all, setOf(TourIds.RING, TourIds.ADD_FOOD, TourIds.WATER, TourIds.PILL))
        assertEquals(
            listOf(TourIds.RING, TourIds.ADD_FOOD, TourIds.WATER, TourIds.PILL),
            shown.map { it.targetId },
        )
    }

    @Test
    fun theCoachStepAppearsOnlyWhenItsCardIsThere() {
        val without = TourPlan.filter(all, setOf(TourIds.RING, TourIds.PILL))
        assertTrue(without.none { it.targetId == TourIds.COACH })
        val with = TourPlan.filter(all, setOf(TourIds.RING, TourIds.PILL, TourIds.COACH))
        assertEquals(TourIds.COACH, with.last().targetId)
    }

    @Test
    fun noTargetsMeansNoSteps() {
        assertTrue(TourPlan.filter(all, emptySet()).isEmpty())
    }

    @Test
    fun unknownTargetsAreIgnored() {
        assertEquals(1, TourPlan.filter(all, setOf(TourIds.PILL, "something-else")).size)
    }

    @Test
    fun advanceEndsAfterTheLastStep() {
        assertEquals(1, TourPlan.advance(0, 3))
        assertEquals(2, TourPlan.advance(1, 3))
        assertNull(TourPlan.advance(2, 3))
        assertNull(TourPlan.advance(0, 0))
    }

    @Test
    fun previousNeverGoesBelowTheFirstStep() {
        assertEquals(0, TourPlan.previous(0))
        assertEquals(1, TourPlan.previous(2))
    }

    @Test
    fun labelIsOneBased() {
        assertEquals("1 of 5", TourPlan.label(0, 5))
    }
}
