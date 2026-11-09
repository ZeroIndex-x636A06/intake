package com.zeroindex.intake.domain.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BubblePlacementTest {

    private val w = 1000f
    private val h = 2000f

    private fun place(target: PxRect, bw: Float = 600f, bh: Float = 300f) =
        BubblePlacement.place(target, w, h, bw, bh, gap = 20f, margin = 32f, topInset = 80f, bottomInset = 100f)

    @Test
    fun goesBelowATargetNearTheTop() {
        val p = place(PxRect(100f, 200f, 900f, 600f))
        assertEquals(620f, p.y, 0.01f)
    }

    @Test
    fun goesAboveATargetNearTheBottom() {
        val p = place(PxRect(100f, 1500f, 900f, 1800f))
        assertEquals(1500f - 20f - 300f, p.y, 0.01f)
    }

    @Test
    fun staysInsideTheScreenHorizontally() {
        val left = place(PxRect(0f, 200f, 120f, 300f))
        assertEquals(32f, left.x, 0.01f)
        val right = place(PxRect(880f, 200f, 1000f, 300f))
        assertEquals(w - 600f - 32f, right.x, 0.01f)
    }

    @Test
    fun aTallNarrowRailGetsTheBubbleBesideIt() {
        val rail = PxRect(24f, 700f, 150f, 1300f)
        val p = place(rail, bw = 500f)
        assertEquals(rail.right + 20f, p.x, 0.01f)
        assertEquals(rail.centerY - 150f, p.y, 0.01f)
    }

    @Test
    fun aTargetThatFillsTheScreenStillGetsABubbleInsideTheBars() {
        val p = place(PxRect(0f, 0f, 1000f, 2000f))
        assertTrue(p.y >= 80f + 32f)
        assertTrue(p.y + 300f <= h - 100f - 32f)
    }
}
