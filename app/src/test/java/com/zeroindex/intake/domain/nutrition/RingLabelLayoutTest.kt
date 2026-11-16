package com.zeroindex.intake.domain.nutrition

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

class RingLabelLayoutTest {

    private val outer = 62.0
    private val gap = 6.0
    private val label = RingLabelLayout.Size(30.0, 16.0)

    @Test
    fun aLabelNeverTouchesTheRingAtAnyAngle() {
        for (angle in listOf(0f, 30f, 45f, 60f, 90f, 120f, 135f, 150f, 180f, 210f, 225f, 270f, 315f, 359f)) {
            val box = RingLabelLayout.place(listOf(angle), listOf(label), outer, gap).single()
            assertWithMessage("angle $angle").that(box.nearestDistance()).isAtLeast(outer + gap - 1e-9)
        }
    }

    @Test
    fun theNearestEdgeSitsExactlyTheGapOutsideOnTheAxes() {
        val top = RingLabelLayout.place(listOf(0f), listOf(label), outer, gap).single()
        assertThat(top.bottom).isWithin(1e-9).of(-(outer + gap))
        val right = RingLabelLayout.place(listOf(90f), listOf(label), outer, gap).single()
        assertThat(right.left).isWithin(1e-9).of(outer + gap)
        val bottom = RingLabelLayout.place(listOf(180f), listOf(label), outer, gap).single()
        assertThat(bottom.top).isWithin(1e-9).of(outer + gap)
        val left = RingLabelLayout.place(listOf(270f), listOf(label), outer, gap).single()
        assertThat(left.right).isWithin(1e-9).of(-(outer + gap))
    }

    @Test
    fun aWideLabelIsPushedFurtherOutThanANarrowOneAtTheSide() {
        val narrow = RingLabelLayout.place(listOf(90f), listOf(RingLabelLayout.Size(10.0, 16.0)), outer, gap).single()
        val wide = RingLabelLayout.place(listOf(90f), listOf(RingLabelLayout.Size(40.0, 16.0)), outer, gap).single()
        assertThat(wide.centerX).isGreaterThan(narrow.centerX)
        assertThat(wide.nearestDistance()).isWithin(1e-9).of(narrow.nearestDistance())
    }

    @Test
    fun wideLabelsClearTheRingAtEveryAngleAndSize() {
        for (w in listOf(14.0, 24.0, 38.0, 60.0)) {
            for (h in listOf(10.0, 16.0, 30.0)) {
                var angle = 0f
                while (angle < 360f) {
                    val box = RingLabelLayout.place(listOf(angle), listOf(RingLabelLayout.Size(w, h)), outer, gap).single()
                    assertWithMessage("w=$w h=$h angle=$angle").that(box.nearestDistance()).isAtLeast(outer + gap - 1e-9)
                    angle += 7.5f
                }
            }
        }
    }

    @Test
    fun labelsOfNeighbouringSegmentsDoNotOverlapAndStillClearTheRing() {
        // A 3% protein sliver next to a 4% carb sliver: their midpoints are a few degrees apart.
        val boxes = RingLabelLayout.place(listOf(5f, 12f, 40f), listOf(label, label, label), outer, gap)
        for (i in boxes.indices) {
            assertThat(boxes[i].nearestDistance()).isAtLeast(outer + gap - 1e-9)
            for (j in i + 1 until boxes.size) assertThat(boxes[i].overlaps(boxes[j])).isFalse()
        }
        // The later label moved outward, the first stayed where it was.
        val first = RingLabelLayout.place(listOf(5f), listOf(label), outer, gap).single()
        assertThat(boxes[0]).isEqualTo(first)
        assertThat(Math.hypot(boxes[1].centerX, boxes[1].centerY)).isGreaterThan(Math.hypot(first.centerX, first.centerY))
    }

    @Test
    fun theTypicalThreeSegmentSplitFromTheScreenshotKeepsEveryLabelClear() {
        // 26% protein, 48% carbs, 26% fat of a 40% full ring: mids at 18, 56 and 96 degrees (see MacroRingSplit).
        val mids = MacroRingSplit.segments(62.0, 114.0, 28.0, 0.27f).map { it.midDegrees }
        val boxes = RingLabelLayout.place(mids, mids.map { label }, outer, gap)
        boxes.forEach { assertThat(it.nearestDistance()).isAtLeast(outer + gap - 1e-9) }
        for (i in boxes.indices) for (j in i + 1 until boxes.size) assertThat(boxes[i].overlaps(boxes[j])).isFalse()
    }

    @Test
    fun zeroSizedAndEmptyInputsAreFine() {
        assertThat(RingLabelLayout.place(emptyList(), emptyList(), outer)).isEmpty()
        val p = RingLabelLayout.place(listOf(90f), listOf(RingLabelLayout.Size(0.0, 0.0)), outer, gap).single()
        assertThat(p.centerX).isWithin(1e-9).of(outer + gap)
    }
}
