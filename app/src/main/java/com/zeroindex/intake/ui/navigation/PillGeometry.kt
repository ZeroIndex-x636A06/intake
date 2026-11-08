package com.zeroindex.intake.ui.navigation

/** One pill item's extent along the pill's long axis, in pixels from the pill's origin. */
data class ItemBounds(val start: Float, val end: Float) {
    val center: Float get() = (start + end) / 2f

    fun contains(position: Float): Boolean = position >= start && position < end

    /** 0 when [position] is inside, otherwise the distance to the nearest edge. */
    fun distanceTo(position: Float): Float = when {
        position < start -> start - position
        position >= end -> position - end
        else -> 0f
    }
}

/** Pure hit testing for the floating pill, kept free of Compose so it can be unit tested. */
object PillHitTest {
    /**
     * The item under [position], or the nearest one when the finger is in a gap or has slid past
     * either end of the pill, so a drag that leaves the pill sideways still selects the first or
     * last tab. Returns -1 only for an empty pill.
     */
    fun indexAt(position: Float, bounds: List<ItemBounds>): Int {
        if (bounds.isEmpty()) return -1
        bounds.forEachIndexed { index, item -> if (item.contains(position)) return index }
        var best = 0
        var bestDistance = Float.MAX_VALUE
        bounds.forEachIndexed { index, item ->
            val d = item.distanceTo(position)
            if (d < bestDistance) {
                best = index
                bestDistance = d
            }
        }
        return best
    }

    /**
     * A drag that starts inside a system back-gesture zone (the strip at each screen edge) is
     * not treated as a slide: the system owns it, and the touch can still be a plain tap.
     */
    fun isInSystemGestureZone(
        xInWindow: Float,
        windowWidth: Float,
        leftInset: Float,
        rightInset: Float,
    ): Boolean = xInWindow < leftInset || xInWindow > windowWidth - rightInset
}

/** What a gesture step produced: the item the highlight is on, and whether to play a haptic tick. */
data class SlideUpdate(val hoverIndex: Int, val tick: Boolean)

/**
 * The state machine behind "slide a finger along the pill". It is fed positions along the pill's
 * axis and never touches Compose:
 *  - [down] puts the highlight under the finger without a tick, so a plain tap stays quiet;
 *  - [move] changes the hovered item only once the finger is [hysteresisPx] beyond the current
 *    item's edge, and reports a tick each time that happens, so a finger resting on a boundary
 *    does not buzz;
 *  - [up] commits whichever item the highlight is on; [cancel] commits nothing.
 */
class PillSlideTracker(private val hysteresisPx: Float = 0f) {
    var hoverIndex: Int = NONE
        private set
    var isActive: Boolean = false
        private set

    fun down(position: Float, bounds: List<ItemBounds>): SlideUpdate {
        hoverIndex = PillHitTest.indexAt(position, bounds)
        isActive = hoverIndex != NONE
        return SlideUpdate(hoverIndex, tick = false)
    }

    fun move(position: Float, bounds: List<ItemBounds>): SlideUpdate {
        if (!isActive) return SlideUpdate(hoverIndex, tick = false)
        val current = bounds.getOrNull(hoverIndex)
        if (current != null &&
            position >= current.start - hysteresisPx &&
            position < current.end + hysteresisPx
        ) {
            return SlideUpdate(hoverIndex, tick = false)
        }
        val next = PillHitTest.indexAt(position, bounds)
        if (next == NONE || next == hoverIndex) return SlideUpdate(hoverIndex, tick = false)
        hoverIndex = next
        return SlideUpdate(next, tick = true)
    }

    /** The index to select, or null if no gesture was running. Resets the tracker. */
    fun up(): Int? {
        val result = if (isActive && hoverIndex != NONE) hoverIndex else null
        reset()
        return result
    }

    fun cancel() = reset()

    private fun reset() {
        hoverIndex = NONE
        isActive = false
    }

    companion object {
        const val NONE = -1
    }
}
