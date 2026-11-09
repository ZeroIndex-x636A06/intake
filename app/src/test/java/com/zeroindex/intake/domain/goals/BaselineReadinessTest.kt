package com.zeroindex.intake.domain.goals

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.domain.goals.BaselineReadiness.FoodDay
import com.zeroindex.intake.domain.goals.BaselineReadiness.Status
import org.junit.Test

class BaselineReadinessTest {

    private val start = 20_000L

    private fun full(day: Long) = FoodDay(day, entryCount = 4, kcal = 2_200.0)

    private fun evaluate(
        today: Long,
        food: List<FoodDay>,
        weighIns: List<Long>,
    ) = BaselineReadiness.evaluate(start, today, food, weighIns)

    @Test
    fun `ready on day seven with five logged days and four weigh-ins`() {
        val p = evaluate(
            today = start + 6,
            food = (0L..4L).map { full(start + it) },
            weighIns = listOf(0L, 2L, 4L, 6L).map { start + it },
        )
        assertThat(p.dayNumber).isEqualTo(7)
        assertThat(p.status).isEqualTo(Status.READY)
        assertThat(p.ready).isTrue()
        assertThat(p.missing).isEmpty()
        assertThat(p.headline).isEqualTo("Baseline: ready")
    }

    @Test
    fun `day six is never ready however complete the data`() {
        val p = evaluate(
            today = start + 5,
            food = (0L..5L).map { full(start + it) },
            weighIns = (0L..5L).map { start + it },
        )
        assertThat(p.dayNumber).isEqualTo(6)
        assertThat(p.ready).isFalse()
        assertThat(p.status).isEqualTo(Status.COLLECTING)
        assertThat(p.missing.single().what).isEqualTo("1 more day")
        assertThat(p.headline).isEqualTo("Baseline: day 6 of 7")
    }

    @Test
    fun `a partial day does not count as logged`() {
        // Five days exist but two of them are a coffee and a snack.
        val food = listOf(
            full(start), full(start + 1), full(start + 2),
            FoodDay(start + 3, entryCount = 1, kcal = 120.0),
            FoodDay(start + 4, entryCount = 2, kcal = 799.0),
        )
        val p = evaluate(start + 6, food, (0L..3L).map { start + it })
        assertThat(p.loggedDays).isEqualTo(3)
        assertThat(p.ready).isFalse()
        assertThat(p.missing.map { it.what }).containsExactly("2 more days of food")
    }

    @Test
    fun `exactly 800 kcal with one entry counts`() {
        assertThat(FoodDay(1, 1, 800.0).counts).isTrue()
        assertThat(FoodDay(1, 1, 799.9).counts).isFalse()
        assertThat(FoodDay(1, 0, 2_000.0).counts).isFalse()
    }

    @Test
    fun `missing weigh-ins are named with the count`() {
        val p = evaluate(
            today = start + 6,
            food = (0L..5L).map { full(start + it) },
            weighIns = listOf(start, start + 3),
        )
        assertThat(p.ready).isFalse()
        assertThat(p.weighInDays).isEqualTo(2)
        assertThat(p.missing.map { it.what }).containsExactly("2 more weigh-ins")
        assertThat(p.missing.single().why).isNotEmpty()
    }

    @Test
    fun `two weigh-ins on one day count once`() {
        val p = evaluate(
            today = start + 6,
            food = (0L..5L).map { full(start + it) },
            weighIns = listOf(start, start, start + 1, start + 1, start + 2),
        )
        assertThat(p.weighInDays).isEqualTo(3)
        assertThat(p.missing.single().what).isEqualTo("1 more weigh-in")
    }

    @Test
    fun `data from before the start day is ignored`() {
        val p = evaluate(
            today = start + 6,
            food = (-5L..-1L).map { full(start + it) },
            weighIns = (-5L..-1L).map { start + it },
        )
        assertThat(p.loggedDays).isEqualTo(0)
        assertThat(p.weighInDays).isEqualTo(0)
    }

    @Test
    fun `day ten without enough data is still collecting`() {
        val p = evaluate(start + 9, listOf(full(start)), listOf(start))
        assertThat(p.dayNumber).isEqualTo(10)
        assertThat(p.status).isEqualTo(Status.COLLECTING)
        assertThat(BaselineReadiness.extensionExplanation(p)).isNull()
    }

    @Test
    fun `past day ten the baseline is extended and says why`() {
        val p = evaluate(start + 10, (0L..2L).map { full(start + it) }, listOf(start, start + 5))
        assertThat(p.dayNumber).isEqualTo(11)
        assertThat(p.status).isEqualTo(Status.EXTENDED)
        val text = BaselineReadiness.extensionExplanation(p)
        assertThat(text).contains("2 more days of food")
        assertThat(text).contains("2 more weigh-ins")
        assertThat(p.headline).isEqualTo("Baseline: day 11")
    }

    @Test
    fun `an extended baseline becomes ready the moment the rule is met`() {
        val p = evaluate(
            today = start + 12,
            food = listOf(0L, 1L, 6L, 9L, 12L).map { full(start + it) },
            weighIns = listOf(0L, 5L, 9L, 12L).map { start + it },
        )
        assertThat(p.status).isEqualTo(Status.READY)
    }

    @Test
    fun `evaluate is deterministic`() {
        val food = (0L..4L).map { full(start + it) }
        val w = listOf(start, start + 2, start + 4, start + 6)
        assertThat(evaluate(start + 6, food, w)).isEqualTo(evaluate(start + 6, food, w))
    }
}
