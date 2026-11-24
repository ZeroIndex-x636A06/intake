package com.zeroindex.intake.domain.hydration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TargetSummaryTest {
    private val fmt: (Double) -> String = { "${it.toInt()} ml" }
    private val num: (Double) -> String = { it.toInt().toString() }

    @Test fun noAdjustmentsMeansNoLine() {
        val b = HydrationPlan.compose(manualBaseMl = 3050.0, bodyweightKg = null, dynamic = true)
        assertNull(b.summaryLine(fmt, num))
    }

    @Test fun staticTargetHasNoLine() {
        val b = HydrationPlan.compose(3050.0, null, dynamic = false, sessions = listOf(SessionLoad(60.0, null)))
        assertNull(b.summaryLine(fmt, num))
    }

    @Test fun oneWorkoutIsBasePlusWorkout() {
        val b = HydrationPlan.compose(3050.0, null, dynamic = true, sessions = listOf(SessionLoad(46.0, null)))
        // 46 min at the moderate 0.8 L/h = 613 ml, rounded to 600.
        assertEquals("Base 3050 ml + workout +600", b.summaryLine(fmt, num))
        assertEquals(3650.0, b.totalMl, 0.0)
    }

    @Test fun twoWorkoutsAddTheirMinutesNotAJump() {
        // The Pass 8 case: 46 min and 60 min in one day is 106 min of sweat (1.4 L), not one short session.
        val one = HydrationPlan.compose(3050.0, null, dynamic = true, sessions = listOf(SessionLoad(46.0, null)))
        val two = HydrationPlan.compose(3050.0, null, dynamic = true, sessions = listOf(SessionLoad(46.0, null), SessionLoad(60.0, null)))
        assertEquals(1400.0, two.totalMl - two.baseMl, 0.0)
        assertEquals(600.0, one.totalMl - one.baseMl, 0.0)
    }

    @Test fun moreThanTwoAdjustmentsAreCounted() {
        val b = TargetBreakdown(
            TargetLine(TargetLine.Id.BASE, "Base", 3000.0, "base", ""),
            listOf(
                TargetLine(TargetLine.Id.SWEAT, "Workout sweat", 500.0, "workout", ""),
                TargetLine(TargetLine.Id.WEATHER, "Weather", 250.0, "heat", ""),
                TargetLine(TargetLine.Id.CAFFEINE, "Caffeine", 100.0, "caffeine", ""),
            ),
        )
        assertEquals("Base 3000 ml + workout +500 + heat +250 + 1 more", b.summaryLine(fmt, num))
    }
}
