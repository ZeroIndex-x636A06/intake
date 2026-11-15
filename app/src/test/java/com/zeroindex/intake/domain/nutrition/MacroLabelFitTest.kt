package com.zeroindex.intake.domain.nutrition

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MacroLabelFitTest {

    @Test fun `every label shows when each segment is wide enough`() {
        // 300 dp bar: 29% is 87 dp, 49% is 147 dp, 22% is 66 dp; a 28 dp label fits all of them.
        assertThat(MacroLabelFit.visible(listOf(29, 49, 22), 300f, 28f)).containsExactly(true, true, true).inOrder()
    }

    @Test fun `a 2 percent segment on a narrow bar gets no label`() {
        // The Drinks card case: 98% and 2%. On a 200 dp bar the 2% segment is 4 dp wide.
        assertThat(MacroLabelFit.visible(listOf(98, 2), 200f, 28f)).containsExactly(true, false).inOrder()
    }

    @Test fun `only the segments too narrow lose their label`() {
        // 4%, 93%, 3% on 240 dp: 9.6 dp, 223 dp, 7.2 dp. The two small ones are hidden, the large one shows.
        assertThat(MacroLabelFit.visible(listOf(4, 93, 3), 240f, 28f)).containsExactly(false, true, false).inOrder()
    }

    @Test fun `a label is shown exactly when its segment is at least as wide as the label`() {
        // 10% of 280 dp is 28 dp: exactly the label width, so it fits; 9% is 25.2 dp and does not.
        assertThat(MacroLabelFit.visible(listOf(10, 9), 280f, 28f)).containsExactly(true, false).inOrder()
    }

    @Test fun `no usable bar width means no labels`() {
        assertThat(MacroLabelFit.visible(listOf(50, 50), 0f, 28f)).containsExactly(false, false).inOrder()
        assertThat(MacroLabelFit.visible(listOf(50, 50), Float.NaN, 28f)).containsExactly(false, false).inOrder()
    }

    @Test fun `a zero share never shows a label`() {
        assertThat(MacroLabelFit.visible(listOf(0, 100), 300f, 28f)).containsExactly(false, true).inOrder()
    }

    @Test fun `a shown label always has room in its own segment`() {
        // Property check over many splits: a label that is drawn is never wider than the segment it sits in.
        val bar = 180f
        val label = 28f
        for (a in 0..100 step 3) for (b in 0..(100 - a) step 3) {
            val c = 100 - a - b
            val pcts = listOf(a, b, c).filter { it > 0 }
            val shown = MacroLabelFit.visible(pcts, bar, label)
            pcts.zip(shown).forEach { (pct, on) -> if (on) assertThat(pct * bar / 100f).isAtLeast(label) }
        }
    }
}
