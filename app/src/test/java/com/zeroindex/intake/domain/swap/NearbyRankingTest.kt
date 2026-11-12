package com.zeroindex.intake.domain.swap

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.domain.nutrition.Macros
import org.junit.Test

class NearbyRankingTest {

    private val planned = Macros(kcal = 640.0, protein = 48.0)

    private fun opt(
        key: String,
        kcal: Double,
        protein: Double,
        allergen: SwapAllergen = SwapAllergen.CLEAR,
        score: Double? = null,
    ) = SwapOption(key, key, null, SwapKind.NEARBY, Macros(kcal = kcal, protein = protein), estimate = true, allergen = allergen, score = score)

    // ---- Swap mode: closeness to the planned meal ----

    @Test
    fun `an identical option is fully close and a far one is not`() {
        assertThat(NearbyRanking.closeness(planned, planned)).isWithin(1e-9).of(1.0)
        assertThat(NearbyRanking.closeness(Macros(kcal = 1400.0, protein = 0.0), planned)).isWithin(1e-9).of(0.0)
    }

    @Test
    fun `missing protein or extra calories each lower closeness`() {
        assertThat(NearbyRanking.closeness(Macros(kcal = 640.0, protein = 10.0), planned)).isLessThan(0.7)
        assertThat(NearbyRanking.closeness(Macros(kcal = 1000.0, protein = 48.0), planned)).isLessThan(0.8)
    }

    @Test
    fun `swap order ranks by calories and protein together`() {
        val sorted = SwapMath.order(
            listOf(
                opt("kcal-match-no-protein", 640.0, 5.0),
                opt("both-near", 600.0, 44.0),
                opt("far", 250.0, 10.0),
            ),
            planned,
        )
        assertThat(sorted.map { it.key }).containsExactly("both-near", "kcal-match-no-protein", "far").inOrder()
    }

    @Test
    fun `a blocked option is last in swap order even when it is the closest`() {
        val sorted = SwapMath.order(listOf(opt("blocked", 640.0, 48.0, SwapAllergen.CONTAINS), opt("ok", 400.0, 20.0)), planned)
        assertThat(sorted.map { it.key }).containsExactly("ok", "blocked").inOrder()
    }

    @Test
    fun `a planned meal with no protein is judged on a floor`() {
        val noProtein = Macros(kcal = 500.0, protein = 0.0)
        assertThat(NearbyRanking.closeness(Macros(kcal = 500.0, protein = 2.0), noProtein)).isGreaterThan(0.85)
    }

    // ---- Healthier mode ----

    @Test
    fun `more protein per calorie scores higher`() {
        val lean = NearbyRanking.healthier(MealNutrition(500.0, 50.0), 800, 90)
        val heavy = NearbyRanking.healthier(MealNutrition(500.0, 15.0), 800, 90)
        assertThat(lean.score).isGreaterThan(heavy.score)
        assertThat(lean.reason).contains("High protein for the calories")
    }

    @Test
    fun `fibre helps and saturated fat sugar and salt hurt`() {
        val base = MealNutrition(500.0, 30.0, fiberG = 4.0, satFatG = 5.0, sugarG = 8.0, sodiumMg = 500.0)
        val good = NearbyRanking.healthier(base, null, null).score
        assertThat(NearbyRanking.healthier(base.copy(fiberG = 12.0), null, null).score).isGreaterThan(good)
        assertThat(NearbyRanking.healthier(base.copy(satFatG = 25.0), null, null).score).isLessThan(good)
        assertThat(NearbyRanking.healthier(base.copy(sugarG = 60.0), null, null).score).isLessThan(good)
        assertThat(NearbyRanking.healthier(base.copy(sodiumMg = 2400.0), null, null).score).isLessThan(good)
    }

    @Test
    fun `an unknown value is skipped, not treated as zero`() {
        val m = MealNutrition(500.0, 20.0)
        val known = NearbyRanking.healthier(m.copy(sodiumMg = 2000.0), null, null).score
        val unknown = NearbyRanking.healthier(m, null, null).score
        val zero = NearbyRanking.healthier(m.copy(sodiumMg = 0.0), null, null).score
        // Unknown sodium must not read as the best possible value (0 mg), nor as the worst.
        assertThat(unknown).isLessThan(zero)
        assertThat(unknown).isGreaterThan(known)
        // With only calories and protein known, the score is the protein part alone: 20 g * 4 / 500 kcal = 16% of 40%.
        assertThat(unknown).isWithin(1e-9).of(0.16 / NearbyRanking.PROTEIN_SHARE_FULL)
    }

    @Test
    fun `reasons are only given for what is known`() {
        val r = NearbyRanking.healthier(MealNutrition(400.0, 10.0), null, null)
        assertThat(r.reason).doesNotContain("fibre")
        assertThat(r.reason).doesNotContain("salt")
        assertThat(r.reason).doesNotContain("sugar")
        val full = NearbyRanking.healthier(MealNutrition(400.0, 40.0, fiberG = 9.0, satFatG = 2.0, sugarG = 3.0, sodiumMg = 300.0), null, null)
        assertThat(full.reason.split(" · ")).hasSize(2)
    }

    @Test
    fun `fit with what is left today counts when targets are known`() {
        val fits = NearbyRanking.healthier(MealNutrition(500.0, 40.0), 700, 60)
        val blowsDay = NearbyRanking.healthier(MealNutrition(500.0, 40.0), 150, 60)
        assertThat(fits.score).isGreaterThan(blowsDay.score)
        assertThat(fits.reason).contains("Fits what is left today")
        assertThat(NearbyRanking.healthier(MealNutrition(500.0, 40.0), null, null).reason).doesNotContain("left today")
    }

    @Test
    fun `calories left alone still count when there is no protein target`() {
        val fits = NearbyRanking.healthier(MealNutrition(500.0, 40.0), 700, null)
        val over = NearbyRanking.healthier(MealNutrition(500.0, 40.0), 100, null)
        assertThat(fits.score).isGreaterThan(over.score)
    }

    @Test
    fun `protein still needed raises a high protein option above a low protein one`() {
        val high = NearbyRanking.healthier(MealNutrition(600.0, 60.0), 900, 80).score
        val low = NearbyRanking.healthier(MealNutrition(600.0, 12.0), 900, 80).score
        assertThat(high).isGreaterThan(low)
    }

    @Test
    fun `score stays within zero and one`() {
        listOf(
            MealNutrition(0.0, 0.0),
            MealNutrition(3000.0, 0.0, 0.0, 200.0, 300.0, 9000.0),
            MealNutrition(200.0, 80.0, 50.0, 0.0, 0.0, 0.0),
        ).forEach {
            val s = NearbyRanking.healthier(it, 100, 50).score
            assertThat(s).isAtLeast(0.0)
            assertThat(s).isAtMost(1.0)
        }
    }

    @Test
    fun `healthier order puts the healthiest first and a blocked one last however healthy`() {
        val sorted = NearbyRanking.orderHealthier(
            listOf(
                opt("meh", 500.0, 20.0, score = 0.4),
                opt("blocked-best", 500.0, 50.0, SwapAllergen.CONTAINS, score = 0.99),
                opt("best", 500.0, 50.0, score = 0.8),
                opt("may-contain", 500.0, 45.0, SwapAllergen.MAY_CONTAIN, score = 0.7),
            ),
        )
        assertThat(sorted.map { it.key }).containsExactly("best", "may-contain", "meh", "blocked-best").inOrder()
    }

    @Test
    fun `a may contain option stays choosable but keeps its warning`() {
        val o = opt("m", 500.0, 40.0, SwapAllergen.MAY_CONTAIN, score = 0.5)
        assertThat(o.blocked).isFalse()
        assertThat(o.allergen).isEqualTo(SwapAllergen.MAY_CONTAIN)
    }
}
