package com.zeroindex.intake.domain.ai.cost

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.domain.ai.AiFeature
import com.zeroindex.intake.domain.ai.AiTask
import com.zeroindex.intake.domain.ai.ModelInfo
import org.junit.Test

class AiCostModelTest {

    private fun model(prompt: Double, completion: Double, perImage: Double = 0.0, perRequest: Double = 0.0) = ModelInfo(
        id = "test/m", name = "Test", description = "", contextLength = 1_000_000,
        promptPerMillion = prompt, completionPerMillion = completion, perImage = perImage, perRequest = perRequest,
        acceptsImages = true, acceptsFiles = false, outputsText = true, structuredOutputs = true, tools = true,
    )

    private fun input(
        feature: AiFeature,
        model: ModelInfo? = model(1.0, 2.0),
        enabled: Boolean = true,
        uses: Double = feature.defaultUsesPerDay,
        measured: MeasuredUsage? = null,
        benchmark: Double? = null,
    ) = FeatureCostInput(feature, enabled, uses, model, measured, benchmark)

    @Test
    fun `per call is tokens times price plus images searches and request fees`() {
        // Hunter: 18,000 in at $1/M, 3,000 out at $2/M, three web searches at the search fee.
        val c = AiCostModel.feature(input(AiFeature.RECIPE_HUNTER))
        val expected = 18_000 * 1.0 / 1e6 + 3_000 * 2.0 / 1e6 + 3 * AiTask.WEB_SEARCH_USD
        assertThat(c.perCallUsd).isWithin(1e-9).of(expected)
        assertThat(c.source).isEqualTo(CostSource.ESTIMATE)

        val photo = AiCostModel.feature(input(AiFeature.PHOTO_ESTIMATE, model(1.0, 2.0, perImage = 0.001, perRequest = 0.0002)))
        assertThat(photo.perCallUsd).isWithin(1e-9).of(1_800 * 1.0 / 1e6 + 600 * 2.0 / 1e6 + 0.001 + 0.0002)
    }

    @Test
    fun `per day and per month follow uses per day and a thirty day month`() {
        val c = AiCostModel.feature(input(AiFeature.PHOTO_ESTIMATE, uses = 2.0))
        assertThat(c.perDayUsd).isWithin(1e-6).of(c.perCallUsd!! * 2.0)
        assertThat(c.perMonthUsd).isWithin(1e-5).of(c.perDayUsd * 30)
    }

    @Test
    fun `money is rounded to a millionth of a dollar`() {
        assertThat(AiMoney.round(0.1234564)).isEqualTo(0.123456)
        assertThat(AiMoney.round(0.1234566)).isEqualTo(0.123457)
        assertThat(AiMoney.round(1.0 / 3.0)).isEqualTo(0.333333)
        val c = AiCostModel.feature(input(AiFeature.DAILY_INSIGHT, model(0.3333333, 0.7777777)))
        assertThat(c.perCallUsd).isEqualTo(AiMoney.round(c.perCallUsd!!))
        assertThat(c.perMonthUsd).isEqualTo(AiMoney.round(c.perMonthUsd))
    }

    @Test
    fun `a missing model has no price and costs zero but is reported as unpriced`() {
        val summary = AiCostModel.estimate(listOf(input(AiFeature.PLAN_B, model = null), input(AiFeature.PHOTO_ESTIMATE)))
        val planB = summary.rows.first { it.feature == AiFeature.PLAN_B }
        assertThat(planB.perCallUsd).isNull()
        assertThat(planB.priced).isFalse()
        assertThat(planB.perDayUsd).isEqualTo(0.0)
        assertThat(summary.unpriced).containsExactly(AiFeature.PLAN_B)
        assertThat(summary.perDayUsd).isEqualTo(summary.rows.first { it.feature == AiFeature.PHOTO_ESTIMATE }.perDayUsd)
    }

    @Test
    fun `zero uses a day costs nothing and is not reported as unpriced`() {
        val summary = AiCostModel.estimate(listOf(input(AiFeature.PLAN_B, model = null, uses = 0.0)))
        assertThat(summary.perDayUsd).isEqualTo(0.0)
        assertThat(summary.perMonthUsd).isEqualTo(0.0)
        assertThat(summary.unpriced).isEmpty()
        assertThat(AiCostModel.feature(input(AiFeature.PHOTO_ESTIMATE, uses = -3.0)).usesPerDay).isEqualTo(0.0)
    }

    @Test
    fun `a feature that is switched off counts as zero uses but still shows its per call price`() {
        val c = AiCostModel.feature(input(AiFeature.DAILY_INSIGHT, enabled = false, uses = 1.0))
        assertThat(c.usesPerDay).isEqualTo(0.0)
        assertThat(c.perDayUsd).isEqualTo(0.0)
        assertThat(c.perMonthUsd).isEqualTo(0.0)
        assertThat(c.perCallUsd).isGreaterThan(0.0)

        val on = AiCostModel.feature(input(AiFeature.DAILY_INSIGHT, enabled = true, uses = 1.0))
        val total = AiCostModel.estimate(listOf(input(AiFeature.DAILY_INSIGHT, enabled = false), input(AiFeature.DAILY_INSIGHT, enabled = true)))
        assertThat(total.perDayUsd).isEqualTo(on.perDayUsd)
    }

    @Test
    fun `measured averages beat the built in defaults`() {
        val defaults = AiCostModel.feature(input(AiFeature.DAILY_INSIGHT))
        val measured = AiCostModel.feature(
            input(AiFeature.DAILY_INSIGHT, measured = MeasuredUsage(calls = 5, avgPromptTokens = 1_000.0, avgCompletionTokens = 100.0)),
        )
        assertThat(measured.source).isEqualTo(CostSource.MEASURED)
        assertThat(measured.inputTokens).isEqualTo(1_000)
        assertThat(measured.outputTokens).isEqualTo(100)
        assertThat(measured.perCallUsd).isWithin(1e-9).of(1_000 * 1.0 / 1e6 + 100 * 2.0 / 1e6)
        assertThat(measured.perCallUsd).isLessThan(defaults.perCallUsd)
    }

    @Test
    fun `measured data also beats the benchmark cost, and too few calls do not count`() {
        val measuredUsage = MeasuredUsage(calls = AiCostModel.MIN_MEASURED_CALLS, avgPromptTokens = 500.0, avgCompletionTokens = 50.0)
        val both = AiCostModel.feature(input(AiFeature.DAILY_INSIGHT, measured = measuredUsage, benchmark = 0.5))
        assertThat(both.source).isEqualTo(CostSource.MEASURED)

        val thin = AiCostModel.feature(input(AiFeature.DAILY_INSIGHT, measured = measuredUsage.copy(calls = 2)))
        assertThat(thin.source).isEqualTo(CostSource.ESTIMATE)
        assertThat(thin.inputTokens).isEqualTo(AiFeature.DAILY_INSIGHT.profile.inputTokens)
    }

    @Test
    fun `the benchmark cost beats the estimate and web searches are added on top`() {
        val c = AiCostModel.feature(input(AiFeature.PLAN_B, benchmark = 0.01))
        assertThat(c.source).isEqualTo(CostSource.BENCHMARK)
        assertThat(c.perCallUsd).isWithin(1e-9).of(0.01 + 3 * AiTask.WEB_SEARCH_USD)

        val noModel = AiCostModel.feature(input(AiFeature.PLAN_B, model = null, benchmark = 0.01))
        assertThat(noModel.source).isEqualTo(CostSource.ESTIMATE)
    }

    @Test
    fun `measured tokens keep the search and image extras of the feature`() {
        val c = AiCostModel.feature(
            input(AiFeature.RECIPE_HUNTER, measured = MeasuredUsage(10, 10_000.0, 1_000.0)),
        )
        assertThat(c.perCallUsd).isWithin(1e-9).of(10_000 * 1.0 / 1e6 + 1_000 * 2.0 / 1e6 + 3 * AiTask.WEB_SEARCH_USD)
    }

    @Test
    fun `the benchmark is not part of the estimate`() {
        val summary = AiCostModel.estimate(AiFeature.entries.map { input(it) })
        assertThat(summary.rows.map { it.feature }).doesNotContain(AiFeature.BENCHMARK)
    }

    @Test
    fun `default uses per day are the documented ones`() {
        assertThat(AiFeature.PHOTO_ESTIMATE.defaultUsesPerDay).isEqualTo(2.0)
        assertThat(AiFeature.RECIPE_HUNTER.defaultUsesPerDay).isEqualTo(0.3)
        assertThat(AiFeature.PLAN_B.defaultUsesPerDay).isEqualTo(0.2)
        assertThat(AiFeature.MENU_IMPORT.defaultUsesPerDay).isEqualTo(0.1)
        assertThat(AiFeature.DAILY_INSIGHT.defaultUsesPerDay).isEqualTo(1.0)
        assertThat(AiFeature.COACH_REWORD.defaultUsesPerDay).isWithin(1e-12).of(1.0 / 7.0)
        assertThat(AiFeature.RECIPE_TRY_AI.defaultUsesPerDay).isEqualTo(0.05)
    }

    @Test
    fun `a router with a variable negative price is never priced or suggested`() {
        val router = model(-1_000_000.0, -1_000_000.0)
        assertThat(router.hasKnownPrice).isFalse()
        assertThat(AiTask.PLAN_B.canRun(router)).isFalse()
        val c = AiCostModel.feature(input(AiFeature.PLAN_B, model = router))
        assertThat(c.perCallUsd).isNull()
        assertThat(c.perDayUsd).isEqualTo(0.0)
        val spend = AiSpend.resolve(null, 100, 100, router, AiFeature.PLAN_B.profile)
        assertThat(spend.usd).isEqualTo(0.0)
        assertThat(AiSpend.resolve(0.01, 100, 100, router, AiFeature.PLAN_B.profile).usd).isEqualTo(0.01)
    }

    @Test
    fun `money is shown with enough places to read small amounts`() {
        assertThat(AiMoney.format(0.0)).isEqualTo("\$0.00")
        assertThat(AiMoney.format(0.00004)).isEqualTo("<\$0.0001")
        assertThat(AiMoney.format(0.0004)).isEqualTo("\$0.0004")
        assertThat(AiMoney.format(0.0123)).isEqualTo("\$0.012")
        assertThat(AiMoney.format(0.5)).isEqualTo("\$0.50")
        assertThat(AiMoney.format(2.0)).isEqualTo("\$2.00")
        assertThat(AiMoney.format(12.3456)).isEqualTo("\$12.35")
    }
}
