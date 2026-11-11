package com.zeroindex.intake.domain.nutrition

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MacroSharesTest {

    @Test fun `shares are the calories from each macro`() {
        // 30 g protein = 120 kcal, 50 g carbs = 200 kcal, 10 g fat = 90 kcal; 410 kcal in all.
        val s = MacroShares.of(30.0, 50.0, 10.0)!!
        assertThat(s).isEqualTo(MacroShares.Shares(29, 49, 22))
    }

    @Test fun `the rounded percentages always add up to exactly 100`() {
        // Equal calories: 33.33 each, so the spare point goes to the first macro.
        val s = MacroShares.of(10.0, 10.0, 40.0 / 9.0)!!
        assertThat(s).isEqualTo(MacroShares.Shares(34, 33, 33))
        assertThat(s.total).isEqualTo(100)
    }

    @Test fun `many random splits all sum to 100`() {
        val rnd = java.util.Random(7)
        repeat(2000) {
            val s = MacroShares.of(rnd.nextDouble() * 200, rnd.nextDouble() * 200, rnd.nextDouble() * 80)
            if (s != null) assertThat(s.total).isEqualTo(100)
        }
    }

    @Test fun `all zero gives no shares`() {
        assertThat(MacroShares.of(0.0, 0.0, 0.0)).isNull()
    }

    @Test fun `negative and non-finite amounts count as zero`() {
        assertThat(MacroShares.of(-5.0, 0.0, 0.0)).isNull()
        assertThat(MacroShares.of(Double.NaN, 0.0, 0.0)).isNull()
        assertThat(MacroShares.of(Double.POSITIVE_INFINITY, 10.0, 0.0)).isEqualTo(MacroShares.Shares(0, 100, 0))
    }

    @Test fun `one macro alone is 100 percent and the others are zero`() {
        assertThat(MacroShares.of(25.0, 0.0, 0.0)).isEqualTo(MacroShares.Shares(100, 0, 0))
        assertThat(MacroShares.of(0.0, 0.0, 9.0)).isEqualTo(MacroShares.Shares(0, 0, 100))
    }

    @Test fun `fat counts 9 kcal a gram, so the same grams weigh more as fat`() {
        // 10 g of each: 40 kcal protein, 40 kcal carbs, 90 kcal fat; 170 kcal in all.
        // Exact: 23.5, 23.5, 52.9. The floors give 98; the spare points go to fat (.94) and protein (.53, earlier).
        assertThat(MacroShares.of(10.0, 10.0, 10.0)).isEqualTo(MacroShares.Shares(24, 23, 53))
    }
}
