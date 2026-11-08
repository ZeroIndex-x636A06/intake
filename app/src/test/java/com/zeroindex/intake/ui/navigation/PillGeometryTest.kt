package com.zeroindex.intake.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PillGeometryTest {

    // Five 100px items laid end to end: 0-100, 100-200, ... 400-500.
    private val bounds = (0 until 5).map { ItemBounds(it * 100f, (it + 1) * 100f) }

    @Test fun `a position inside an item hits that item`() {
        assertEquals(0, PillHitTest.indexAt(1f, bounds))
        assertEquals(2, PillHitTest.indexAt(250f, bounds))
        assertEquals(4, PillHitTest.indexAt(499f, bounds))
    }

    @Test fun `a boundary belongs to the item that starts there`() {
        assertEquals(1, PillHitTest.indexAt(100f, bounds))
        assertEquals(3, PillHitTest.indexAt(300f, bounds))
    }

    @Test fun `sliding past either end clamps to the first or last item`() {
        assertEquals(0, PillHitTest.indexAt(-80f, bounds))
        assertEquals(4, PillHitTest.indexAt(900f, bounds))
    }

    @Test fun `a gap between items goes to the nearer one`() {
        val gapped = listOf(ItemBounds(0f, 90f), ItemBounds(110f, 200f))
        assertEquals(0, PillHitTest.indexAt(98f, gapped))
        assertEquals(1, PillHitTest.indexAt(102f, gapped))
    }

    @Test fun `an empty pill has no index`() {
        assertEquals(-1, PillHitTest.indexAt(10f, emptyList()))
    }

    @Test fun `down selects under the finger without a tick`() {
        val tracker = PillSlideTracker()
        val update = tracker.down(250f, bounds)
        assertEquals(SlideUpdate(2, tick = false), update)
        assertTrue(tracker.isActive)
    }

    @Test fun `a tap commits the item it landed on`() {
        val tracker = PillSlideTracker()
        tracker.down(150f, bounds)
        assertEquals(1, tracker.up())
        assertFalse(tracker.isActive)
    }

    @Test fun `sliding across items ticks once per tab crossed and commits the last`() {
        val tracker = PillSlideTracker()
        tracker.down(50f, bounds)
        val ticks = mutableListOf<Int>()
        var x = 50f
        while (x < 450f) {
            x += 10f
            val update = tracker.move(x, bounds)
            if (update.tick) ticks += update.hoverIndex
        }
        assertEquals(listOf(1, 2, 3, 4), ticks)
        assertEquals(4, tracker.up())
    }

    @Test fun `moving within one item never ticks`() {
        val tracker = PillSlideTracker()
        tracker.down(210f, bounds)
        listOf(220f, 280f, 205f, 299f).forEach { assertFalse(tracker.move(it, bounds).tick) }
        assertEquals(2, tracker.up())
    }

    @Test fun `sliding back over a tab ticks again`() {
        val tracker = PillSlideTracker()
        tracker.down(150f, bounds)
        assertTrue(tracker.move(250f, bounds).tick)
        assertTrue(tracker.move(150f, bounds).tick)
        assertEquals(1, tracker.up())
    }

    @Test fun `hysteresis stops a finger resting on a boundary from buzzing`() {
        val tracker = PillSlideTracker(hysteresisPx = 8f)
        tracker.down(95f, bounds)
        // Jitter either side of the 100 boundary stays on item 0 until it is 8px past.
        listOf(101f, 99f, 105f, 98f).forEach { assertFalse(tracker.move(it, bounds).tick) }
        val crossed = tracker.move(110f, bounds)
        assertTrue(crossed.tick)
        assertEquals(1, crossed.hoverIndex)
        // And back the other way needs the same margin.
        assertFalse(tracker.move(95f, bounds).tick)
        assertTrue(tracker.move(88f, bounds).tick)
    }

    @Test fun `cancel commits nothing`() {
        val tracker = PillSlideTracker()
        tracker.down(150f, bounds)
        tracker.move(350f, bounds)
        tracker.cancel()
        assertNull(tracker.up())
        assertEquals(PillSlideTracker.NONE, tracker.hoverIndex)
    }

    @Test fun `moves before a down are ignored`() {
        val tracker = PillSlideTracker()
        assertEquals(SlideUpdate(PillSlideTracker.NONE, tick = false), tracker.move(250f, bounds))
        assertNull(tracker.up())
    }

    @Test fun `the system gesture strip at each screen edge is not a slide start`() {
        // 1080px window, 48px back-gesture strip on each side.
        assertTrue(PillHitTest.isInSystemGestureZone(10f, 1080f, 48f, 48f))
        assertTrue(PillHitTest.isInSystemGestureZone(1070f, 1080f, 48f, 48f))
        assertFalse(PillHitTest.isInSystemGestureZone(48f, 1080f, 48f, 48f))
        assertFalse(PillHitTest.isInSystemGestureZone(540f, 1080f, 48f, 48f))
        assertFalse(PillHitTest.isInSystemGestureZone(1032f, 1080f, 48f, 48f))
    }

    @Test fun `item bounds report their centre and distance`() {
        val b = ItemBounds(100f, 200f)
        assertEquals(150f, b.center, 0f)
        assertEquals(0f, b.distanceTo(150f), 0f)
        assertEquals(30f, b.distanceTo(70f), 0f)
        assertEquals(5f, b.distanceTo(205f), 0f)
    }
}
