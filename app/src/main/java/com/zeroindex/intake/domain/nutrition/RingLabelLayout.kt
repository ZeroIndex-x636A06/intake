package com.zeroindex.intake.domain.nutrition

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Where the percentage labels go around the calorie ring. Pure.
 *
 * A label is a box of a known size. Putting the box's CENTRE at a fixed radius lets a wide label reach back over the
 * ring, so the centre is placed from the box's own size: along direction d = (sin a, -cos a) the box extends
 * |dx| * w / 2 + |dy| * h / 2 from its centre, so a centre at
 * `outerRadius + gap + |dx| * w / 2 + |dy| * h / 2` puts its nearest edge [gap] clear of the ring's outer edge at any
 * angle. Every point of the box then lies at least `outerRadius + gap` from the ring's centre.
 *
 * Units are whatever the caller uses (dp in the app); angles are degrees clockwise from 12 o'clock.
 */
object RingLabelLayout {

    /** A label's size. */
    data class Size(val width: Double, val height: Double)

    /** A placed label: its centre, relative to the ring's centre (x to the right, y down), and its size. */
    data class Placed(val centerX: Double, val centerY: Double, val width: Double, val height: Double) {
        val left get() = centerX - width / 2
        val right get() = centerX + width / 2
        val top get() = centerY - height / 2
        val bottom get() = centerY + height / 2

        /** The distance from the ring's centre to the nearest point of the box. */
        fun nearestDistance(): Double {
            val nx = if (0.0 in left..right) 0.0 else minOf(abs(left), abs(right))
            val ny = if (0.0 in top..bottom) 0.0 else minOf(abs(top), abs(bottom))
            return Math.hypot(nx, ny)
        }

        fun overlaps(other: Placed, margin: Double = 0.0): Boolean =
            left < other.right + margin && other.left < right + margin && top < other.bottom + margin && other.top < bottom + margin
    }

    /**
     * One box per entry of [midDegrees], each [gap] clear of the ring's outer edge ([outerRadius]). A box that would touch
     * an earlier one (within [spacing]) is moved straight outward, in [step] increments, until it clears it: moving out
     * only ever moves it further from the ring.
     */
    fun place(
        midDegrees: List<Float>,
        sizes: List<Size>,
        outerRadius: Double,
        gap: Double = 6.0,
        spacing: Double = 2.0,
        step: Double = 1.0,
    ): List<Placed> {
        require(midDegrees.size == sizes.size) { "one size per label" }
        val placed = ArrayList<Placed>(midDegrees.size)
        midDegrees.forEachIndexed { i, degrees ->
            val a = Math.toRadians(degrees.toDouble())
            val dx = sin(a)
            val dy = -cos(a)
            val w = sizes[i].width.coerceAtLeast(0.0)
            val h = sizes[i].height.coerceAtLeast(0.0)
            var r = outerRadius + gap + abs(dx) * w / 2 + abs(dy) * h / 2
            var box = Placed(r * dx, r * dy, w, h)
            var guard = 0
            while (placed.any { box.overlaps(it, spacing) } && guard++ < MAX_STEPS) {
                r += step
                box = Placed(r * dx, r * dy, w, h)
            }
            placed += box
        }
        return placed
    }

    private const val MAX_STEPS = 400
}
