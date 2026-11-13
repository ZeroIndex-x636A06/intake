package com.zeroindex.intake.domain.ai

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HuntRulesTest {

    private val query = RecipeQuery(minProteinPerServing = 40, maxKcalPerServing = 650, maxMinutes = 60)

    // --- dedupe ---

    @Test
    fun `the same page with tracking, www, slash or fragment is one url`() {
        val a = HuntKeys.urlKey("https://www.Example.com/recipes/bowl/?utm_source=x&fbclid=1#top")
        val b = HuntKeys.urlKey("http://example.com/recipes/bowl")
        assertThat(a).isEqualTo(b)
    }

    @Test
    fun `a real query parameter keeps two pages apart`() {
        assertThat(HuntKeys.urlKey("https://e.com/r?id=1")).isNotEqualTo(HuntKeys.urlKey("https://e.com/r?id=2"))
    }

    @Test
    fun `dedupe catches a repeated url or a repeated title and remembers everything it saw`() {
        val d = HuntDedupe()
        assertThat(d.add("https://a.com/x", "Chicken Burrito Bowl")).isTrue()
        assertThat(d.add("https://www.a.com/x/", "Something else")).isFalse()
        assertThat(d.add("https://b.com/y", "chicken burrito-bowl!")).isFalse()
        assertThat(d.add("https://b.com/z", "Turkey chili")).isTrue()
    }

    // --- constraint check ---

    @Test
    fun `inside every limit is a match`() {
        assertThat(RecipeFit.assess(44.0, 600.0, 45, query).fit).isEqualTo(Fit.MATCH)
        assertThat(RecipeFit.assess(40.0, 650.0, 60, query).fit).isEqualTo(Fit.MATCH)
    }

    @Test
    fun `within ten percent past a limit is close and says why`() {
        val r = RecipeFit.assess(37.0, 600.0, 45, query)
        assertThat(r.fit).isEqualTo(Fit.CLOSE)
        assertThat(r.reason).isEqualTo("37 g protein per serving, goal ≥ 40 g")
        assertThat(RecipeFit.assess(44.0, 700.0, 45, query).fit).isEqualTo(Fit.CLOSE)
        assertThat(RecipeFit.assess(44.0, 600.0, 66, query).fit).isEqualTo(Fit.CLOSE)
    }

    @Test
    fun `further out than ten percent is out, and out beats close`() {
        assertThat(RecipeFit.assess(35.0, 600.0, 45, query).fit).isEqualTo(Fit.OUT)
        assertThat(RecipeFit.assess(37.0, 800.0, 45, query).fit).isEqualTo(Fit.OUT)
        assertThat(RecipeFit.assess(44.0, 600.0, 90, query).reason).isEqualTo("Takes 90 min, goal ≤ 60")
    }

    @Test
    fun `numbers the page does not state are not held against it`() {
        assertThat(RecipeFit.assess(null, null, null, query).fit).isEqualTo(Fit.UNCHECKED)
        assertThat(RecipeFit.assess(null, 600.0, null, query).fit).isEqualTo(Fit.MATCH)
        // Time still counts when nutrition is missing.
        assertThat(RecipeFit.assess(null, null, 120, query).fit).isEqualTo(Fit.OUT)
    }

    @Test
    fun `a limit that is switched off is not checked`() {
        val open = RecipeQuery(minProteinPerServing = null, maxKcalPerServing = null, maxMinutes = null)
        assertThat(RecipeFit.assess(5.0, 3000.0, 600, open).fit).isEqualTo(Fit.MATCH)
    }

    // --- stop rules ---

    private val policy = HuntPolicy(targetKept = 12, maxRounds = 4, capUsd = 0.08)

    private fun stop(kept: Int, rounds: Int, cost: Double, last: Double = 0.02, new: Int? = 10) =
        HuntRules.stopReason(policy, kept, rounds, cost, last, new)

    @Test
    fun `the first round always runs`() {
        assertThat(stop(kept = 0, rounds = 0, cost = 0.0, last = 0.0, new = null)).isNull()
    }

    @Test
    fun `stops when the target is reached`() {
        assertThat(stop(12, 1, 0.02)).isEqualTo(HuntStop.TARGET)
        assertThat(stop(11, 1, 0.02)).isNull()
    }

    @Test
    fun `stops at the round cap`() {
        assertThat(stop(3, 4, 0.04)).isEqualTo(HuntStop.ROUNDS)
        assertThat(stop(3, 3, 0.04)).isNull()
    }

    @Test
    fun `stops before a round that would step over the cost cap`() {
        assertThat(stop(3, 2, cost = 0.05, last = 0.03)).isNull()
        assertThat(stop(3, 2, cost = 0.07, last = 0.03)).isEqualTo(HuntStop.COST)
    }

    @Test
    fun `stops when a round found nothing new`() {
        assertThat(stop(3, 2, 0.04, new = 0)).isEqualTo(HuntStop.DRIED_UP)
    }

    @Test
    fun `reaching the target wins over the other reasons`() {
        assertThat(stop(12, 4, 0.5, new = 0)).isEqualTo(HuntStop.TARGET)
    }

    @Test
    fun `every missed limit is listed with a short chip each`() {
        val r = RecipeFit.assess(32.0, 760.0, 45, query)
        assertThat(r.fit).isEqualTo(Fit.OUT)
        assertThat(r.reasons).hasSize(2)
        assertThat(r.issues.map { it.chip }).containsExactly("Under protein goal", "Over calorie cap").inOrder()
        assertThat(r.reason).contains("32 g protein").also { assertThat(r.reason).contains("760 kcal") }
    }

    @Test
    fun `the ai read budget holds the cap and the page limit`() {
        val b = AiReadBudget(remainingUsd = 0.007, remainingReads = 5)
        assertThat(b.tryReserve(0.003)).isTrue()
        assertThat(b.tryReserve(0.003)).isTrue()
        // 0.001 is left: another 0.003 read does not fit under the cap.
        assertThat(b.tryReserve(0.003)).isFalse()
        b.record(0.003, 0.001)
        b.record(0.003, 0.0025)
        assertThat(b.spentUsd).isWithin(1e-9).of(0.0035)
        assertThat(b.reads).isEqualTo(2)
        // A cheaper real cost frees room.
        assertThat(b.tryReserve(0.003)).isTrue()
        val few = AiReadBudget(1.0, 1)
        assertThat(few.tryReserve(0.001)).isTrue()
        assertThat(few.tryReserve(0.001)).isFalse()
    }
}
