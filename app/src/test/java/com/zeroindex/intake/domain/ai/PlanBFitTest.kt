package com.zeroindex.intake.domain.ai

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlanBFitTest {

    @Test
    fun `a meal inside the budget that covers the protein scores full`() {
        val fit = PlanBFit.score(kcal = 620.0, protein = 60.0, kcalLeft = 700, proteinLeft = 58)
        assertThat(fit.score).isWithin(1e-9).of(1.0)
        assertThat(fit.summary).isEqualTo("Fits 60 g of your 58 g protein left · 620 of 700 kcal left")
    }

    @Test
    fun `protein counts for more than calories`() {
        val highProteinOver = PlanBFit.score(kcal = 800.0, protein = 58.0, kcalLeft = 700, proteinLeft = 58)
        val lowProteinUnder = PlanBFit.score(kcal = 500.0, protein = 15.0, kcalLeft = 700, proteinLeft = 58)
        assertThat(highProteinOver.score).isGreaterThan(lowProteinUnder.score)
    }

    @Test
    fun `going over the budget falls away linearly`() {
        val fit = PlanBFit.score(kcal = 1_050.0, protein = 0.0, kcalLeft = 700, proteinLeft = 58)
        // 350 over a 700 budget: half the calorie score.
        assertThat(fit.score).isWithin(1e-9).of(PlanBFit.KCAL_WEIGHT * 0.5)
        assertThat(fit.summary).endsWith("350 kcal over")
    }

    @Test
    fun `with no target the order falls back to protein density`() {
        val dense = PlanBFit.score(kcal = 500.0, protein = 50.0, kcalLeft = null, proteinLeft = null)
        val sparse = PlanBFit.score(kcal = 900.0, protein = 20.0, kcalLeft = null, proteinLeft = null)
        assertThat(dense.score).isGreaterThan(sparse.score)
    }

    @Test
    fun `coordinates are rounded to about a kilometre`() {
        assertThat(PlanBFit.coarsen(33.748_995)).isEqualTo(33.75)
        assertThat(PlanBFit.coarsen(-84.387_982)).isEqualTo(-84.39)
    }
}
