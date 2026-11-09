package com.zeroindex.intake.domain.ai.cost

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.domain.ai.AiFeature
import com.zeroindex.intake.domain.ai.AiTask
import com.zeroindex.intake.domain.ai.AiTrigger
import com.zeroindex.intake.domain.ai.ModelInfo
import com.zeroindex.intake.domain.ai.TokenProfile
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Test

class UsageAndBudgetTest {

    private val utc: ZoneId = ZoneOffset.UTC

    private fun at(y: Int, m: Int, d: Int, h: Int = 12): Long =
        LocalDateTime.of(y, m, d, h, 0).atZone(utc).toInstant().toEpochMilli()

    private fun rec(
        feature: AiFeature,
        at: Long,
        usd: Double = 0.01,
        prompt: Int? = 1000,
        completion: Int? = 100,
        success: Boolean = true,
    ) = AiUsageRecord(feature, "m/x", AiTrigger.USER_ACTION, prompt, completion, usd, true, success, at)

    // --- the meter ----------------------------------------------------------------------------------------

    @Test
    fun `calls and dollars are totalled per feature for today the week and the month`() {
        val now = at(2026, 11, 11) // a Wednesday
        val records = listOf(
            rec(AiFeature.PHOTO_ESTIMATE, now - 1_000, 0.002),
            rec(AiFeature.PHOTO_ESTIMATE, at(2026, 11, 9), 0.003), // Monday: this week, not today
            rec(AiFeature.PHOTO_ESTIMATE, at(2026, 11, 2), 0.004), // earlier this month
            rec(AiFeature.PLAN_B, at(2026, 11, 10), 0.05),
            rec(AiFeature.PHOTO_ESTIMATE, at(2026, 10, 30), 9.0), // last month
        )
        val s = UsageSummarizer.summarize(records, now, utc)
        val photo = s.features.first { it.feature == AiFeature.PHOTO_ESTIMATE }
        assertThat(photo.today).isEqualTo(PeriodTotals(1, 0.002))
        assertThat(photo.week).isEqualTo(PeriodTotals(2, 0.005))
        assertThat(photo.month).isEqualTo(PeriodTotals(3, 0.009))
        assertThat(s.features.map { it.feature }).containsExactly(AiFeature.PHOTO_ESTIMATE, AiFeature.PLAN_B).inOrder()
        assertThat(s.month).isEqualTo(PeriodTotals(4, 0.059))
        assertThat(s.today).isEqualTo(PeriodTotals(1, 0.002))
    }

    @Test
    fun `a failed call is still a call and costs what it cost`() {
        val now = at(2026, 11, 11)
        val s = UsageSummarizer.summarize(listOf(rec(AiFeature.PLAN_B, now - 1, 0.0, null, null, success = false)), now, utc)
        assertThat(s.month).isEqualTo(PeriodTotals(1, 0.0))
    }

    @Test
    fun `reset counters hides earlier calls from the meter but not from the budget`() {
        val now = at(2026, 11, 11)
        val reset = at(2026, 11, 8)
        val records = listOf(rec(AiFeature.PLAN_B, at(2026, 11, 5), 0.5), rec(AiFeature.PLAN_B, at(2026, 11, 10), 0.1))
        val meter = UsageSummarizer.summarize(records, now, utc, resetAt = reset)
        assertThat(meter.month).isEqualTo(PeriodTotals(1, 0.1))
        // Resetting the display is not a way around the cap.
        assertThat(UsageSummarizer.monthSpend(records, now, utc)).isEqualTo(0.6)
    }

    @Test
    fun `the budget starts again on the first of the next month`() {
        val records = listOf(rec(AiFeature.PLAN_B, at(2026, 11, 28), 1.9))
        val lastDay = at(2026, 11, 30, 23)
        val nextDay = at(2026, 12, 1, 0)
        assertThat(UsageSummarizer.monthSpend(records, lastDay, utc)).isEqualTo(1.9)
        assertThat(UsageSummarizer.monthSpend(records, nextDay, utc)).isEqualTo(0.0)
        assertThat(UsagePeriods.nextMonthStart(lastDay, utc)).isEqualTo(nextDay)
        assertThat(UsagePeriods.monthStart(nextDay, utc)).isEqualTo(nextDay)
    }

    @Test
    fun `month boundaries follow the person's time zone not UTC`() {
        val tokyo = ZoneId.of("Asia/Tokyo")
        // 2026-11-30 16:00 UTC is already 1 December 01:00 in Tokyo.
        val instant = at(2026, 11, 30, 16)
        assertThat(UsagePeriods.monthStart(instant, tokyo))
            .isEqualTo(LocalDateTime.of(2026, 12, 1, 0, 0).atZone(tokyo).toInstant().toEpochMilli())
        assertThat(UsagePeriods.monthStart(instant, utc)).isEqualTo(at(2026, 11, 1, 0))
    }

    @Test
    fun `measured averages use successful calls that reported tokens`() {
        val now = at(2026, 11, 11)
        val records = listOf(
            rec(AiFeature.DAILY_INSIGHT, now, prompt = 1000, completion = 100),
            rec(AiFeature.DAILY_INSIGHT, now, prompt = 3000, completion = 300),
            rec(AiFeature.DAILY_INSIGHT, now, prompt = null, completion = null),
            rec(AiFeature.DAILY_INSIGHT, now, prompt = 9999, completion = 999, success = false),
            rec(AiFeature.PLAN_B, now, prompt = 5, completion = 5),
        )
        val m = UsageSummarizer.measured(records, AiFeature.DAILY_INSIGHT)!!
        assertThat(m.calls).isEqualTo(2)
        assertThat(m.avgPromptTokens).isEqualTo(2000.0)
        assertThat(m.avgCompletionTokens).isEqualTo(200.0)
        assertThat(UsageSummarizer.measured(emptyList(), AiFeature.PLAN_B)).isNull()
    }

    // --- the budget ---------------------------------------------------------------------------------------

    @Test
    fun `the budget notices at eighty percent and blocks at a hundred`() {
        assertThat(AiBudget.status(2.0, 1.59).level).isEqualTo(BudgetLevel.OK)
        assertThat(AiBudget.status(2.0, 1.6).level).isEqualTo(BudgetLevel.NOTICE)
        assertThat(AiBudget.status(2.0, 1.99).level).isEqualTo(BudgetLevel.NOTICE)
        assertThat(AiBudget.status(2.0, 2.0).level).isEqualTo(BudgetLevel.BLOCKED)
        assertThat(AiBudget.status(2.0, 5.0).blocked).isTrue()
        assertThat(AiBudget.status(2.0, 5.0).fraction).isEqualTo(1.0)
        assertThat(AiBudget.status(2.0, 0.5).remainingUsd).isEqualTo(1.5)
    }

    @Test
    fun `raising the cap lifts the block`() {
        assertThat(AiBudget.status(2.0, 2.5).blocked).isTrue()
        assertThat(AiBudget.status(5.0, 2.5).blocked).isFalse()
    }

    @Test
    fun `a cap of zero or less blocks everything`() {
        assertThat(AiBudget.status(0.0, 0.0).blocked).isTrue()
        assertThat(AiBudget.status(-1.0, 0.0).blocked).isTrue()
    }

    @Test
    fun `typed caps are parsed or refused`() {
        assertThat(AiBudget.parseCap("2")).isEqualTo(2.0)
        assertThat(AiBudget.parseCap(" $5.50 ")).isEqualTo(5.5)
        assertThat(AiBudget.parseCap("0")).isNull()
        assertThat(AiBudget.parseCap("-1")).isNull()
        assertThat(AiBudget.parseCap("abc")).isNull()
        assertThat(AiBudget.parseCap("")).isNull()
        assertThat(AiBudget.DEFAULT_CAP_USD).isEqualTo(2.0)
    }

    // --- what a call cost ---------------------------------------------------------------------------------

    private val priced = ModelInfo(
        id = "m/x", name = "X", description = "", contextLength = 1_000_000,
        promptPerMillion = 1.0, completionPerMillion = 2.0, perImage = 0.0, perRequest = 0.0,
        acceptsImages = false, acceptsFiles = false, outputsText = true, structuredOutputs = true, tools = false,
    )

    @Test
    fun `the cost OpenRouter reports is used as given`() {
        val r = AiSpend.resolve(0.000421, 10, 5, priced, TokenProfile(1, 1))
        assertThat(r.usd).isEqualTo(0.000421)
        assertThat(r.reported).isTrue()
    }

    @Test
    fun `a missing cost falls back to catalog prices times the real tokens`() {
        val r = AiSpend.resolve(null, 1_000, 500, priced, TokenProfile(9_999, 9_999))
        assertThat(r.usd).isEqualTo(0.002)
        assertThat(r.reported).isFalse()
    }

    @Test
    fun `a missing cost and missing tokens fall back to the feature's typical size`() {
        val profile = AiTask.DAILY_INSIGHT.let { TokenProfile(it.typicalInputTokens, it.typicalOutputTokens) }
        val r = AiSpend.resolve(null, null, null, priced, profile)
        assertThat(r.usd).isEqualTo(AiMoney.round((profile.inputTokens * 1.0 + profile.outputTokens * 2.0) / 1e6))
        assertThat(r.reported).isFalse()
    }

    @Test
    fun `an unknown model and no reported cost is counted as zero and marked estimated`() {
        val r = AiSpend.resolve(null, 100, 100, null, TokenProfile(1, 1))
        assertThat(r.usd).isEqualTo(0.0)
        assertThat(r.reported).isFalse()
    }

    @Test
    fun `a nonsense reported cost is ignored`() {
        assertThat(AiSpend.resolve(-1.0, 1_000, 500, priced, TokenProfile(1, 1)).reported).isFalse()
        assertThat(AiSpend.resolve(Double.NaN, 1_000, 500, priced, TokenProfile(1, 1)).reported).isFalse()
    }
}
