package com.zeroindex.intake.domain.ai.benchmark

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.domain.ai.AiTask
import com.zeroindex.intake.domain.ai.ModelInfo
import org.junit.Assert.assertThrows
import org.junit.Test

class BenchmarkSuiteTest {

    private fun model(id: String, prompt: Double, completion: Double, perRequest: Double = 0.0, description: String = "") = ModelInfo(
        id = id, name = id, description = description, contextLength = 1_000_000,
        promptPerMillion = prompt, completionPerMillion = completion, perImage = 0.0, perRequest = perRequest,
        acceptsImages = true, acceptsFiles = false, outputsText = true, structuredOutputs = true, tools = true,
    )

    private fun outcome(accuracy: Double, parsed: Boolean = true, latency: Long = 1_000, cost: Double? = 0.001) =
        RequestOutcome(FixtureScore(accuracy), parsed, latency, cost)

    @Test
    fun `a score is the mean accuracy, first-try parse rate, median latency and mean cost`() {
        val score = BenchmarkSuite.summarize(
            AiTask.MENU_INGESTION,
            "x/y",
            listOf(
                outcome(1.0, latency = 900, cost = 0.002),
                outcome(0.5, latency = 3_000, cost = 0.004),
                outcome(0.0, parsed = false, latency = 1_200, cost = 0.003),
                outcome(1.0, latency = 800, cost = 0.003),
                outcome(0.5, latency = 1_000, cost = 0.003),
            ),
        )
        assertThat(score.accuracy).isWithin(1e-9).of(0.6)
        assertThat(score.jsonValidity).isWithin(1e-9).of(0.8)
        assertThat(score.fixtures).isEqualTo(5)
        assertThat(score.medianLatencyMs).isEqualTo(1_000)
        assertThat(score.costUsdPerRequest).isWithin(1e-12).of(0.003)
    }

    @Test
    fun `the median of an even count is the mean of the middle two`() {
        val score = BenchmarkSuite.summarize(
            AiTask.PLAN_B,
            "x/y",
            listOf(outcome(1.0, latency = 1_000), outcome(1.0, latency = 3_000)),
        )
        assertThat(score.medianLatencyMs).isEqualTo(2_000)
    }

    @Test
    fun `cost averages only the requests that reported one`() {
        val score = BenchmarkSuite.summarize(
            AiTask.PLAN_B,
            "x/y",
            listOf(outcome(1.0, cost = 0.002), outcome(1.0, cost = null), outcome(1.0, cost = 0.004)),
        )
        assertThat(score.costUsdPerRequest).isWithin(1e-12).of(0.003)
    }

    @Test
    fun `no reported cost stays unknown instead of becoming zero`() {
        val score = BenchmarkSuite.summarize(AiTask.PLAN_B, "x/y", listOf(outcome(1.0, cost = null)))
        assertThat(score.costUsdPerRequest).isNull()
    }

    @Test
    fun `a score needs at least one request`() {
        assertThrows(IllegalArgumentException::class.java) {
            BenchmarkSuite.summarize(AiTask.PLAN_B, "x/y", emptyList())
        }
    }

    @Test
    fun `vision is not part of the suite`() {
        assertThat(BenchmarkSuite.tasks).doesNotContain(AiTask.VISION_TRACKER)
        assertThat(BenchmarkSuite.tasks).containsExactly(
            AiTask.MENU_INGESTION, AiTask.RECIPE_HUNTER, AiTask.PLAN_B, AiTask.DAILY_INSIGHT,
        )
    }

    private val catalog = listOf(
        model("meta-llama/llama-3.3-70b-instruct", 0.10, 0.30),
        model("cheap/a", 0.01, 0.02),
        model("cheap/b", 0.02, 0.03),
        model("cheap/c", 0.03, 0.04),
        model("cheap/d", 0.04, 0.05),
        model("pricey/e", 5.0, 15.0),
        model("free/model:free", 0.0, 0.0),
        model("keeps/f", 0.001, 0.001, description = "Prompts may be retained by the provider."),
    )

    @Test
    fun `candidates are the suggestion plus the three cheapest others`() {
        val ids = BenchmarkSuite.defaultCandidates(AiTask.MENU_INGESTION, catalog, emptyList(), allowRetainingModels = false)
            .map { it.id }
        assertThat(ids).containsExactly(
            "meta-llama/llama-3.3-70b-instruct", "cheap/a", "cheap/b", "cheap/c",
        ).inOrder()
    }

    @Test
    fun `models that may keep prompts are left out unless the user allowed them`() {
        val withheld = BenchmarkSuite.defaultCandidates(AiTask.MENU_INGESTION, catalog, emptyList(), false).map { it.id }
        val allowed = BenchmarkSuite.defaultCandidates(AiTask.MENU_INGESTION, catalog, emptyList(), true).map { it.id }
        assertThat(withheld).containsNoneOf("free/model:free", "keeps/f")
        assertThat(allowed).containsAtLeast("free/model:free", "keeps/f")
    }

    @Test
    fun `an empty catalog has no candidates`() {
        assertThat(BenchmarkSuite.defaultCandidates(AiTask.PLAN_B, emptyList(), emptyList(), false)).isEmpty()
    }

    @Test
    fun `a request costs its tokens at the catalog price plus any per-request fee`() {
        val m = model("x/y", prompt = 2.0, completion = 10.0, perRequest = 0.001)
        // 3000 chars -> 1000 tokens in; 500 out.
        val usd = BenchmarkCost.requestUsd(m, BenchmarkCost.inputTokens(3_000), 500)
        assertThat(usd).isWithin(1e-12).of(1_000 * 2.0 / 1e6 + 500 * 10.0 / 1e6 + 0.001)
    }

    @Test
    fun `input tokens round up and assume three characters a token`() {
        assertThat(BenchmarkCost.inputTokens(3_001)).isEqualTo(1_001)
        assertThat(BenchmarkCost.inputTokens(0)).isEqualTo(0)
    }
}
