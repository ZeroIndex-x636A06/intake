package com.zeroindex.intake.domain.ai.benchmark

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.domain.ai.AiFailureKind
import com.zeroindex.intake.domain.ai.AiTask
import com.zeroindex.intake.domain.ai.ModelInfo
import org.junit.Test

class BenchmarkCandidatesTest {

    private fun model(
        id: String,
        prompt: Double = 0.1,
        completion: Double = 0.2,
        strict: Boolean = true,
        jsonMode: Boolean = true,
        tools: Boolean = true,
        images: Boolean = false,
        context: Long = 300_000,
    ) = ModelInfo(
        id = id, name = id, description = "", contextLength = context, promptPerMillion = prompt,
        completionPerMillion = completion, perImage = 0.0, perRequest = 0.0, acceptsImages = images, acceptsFiles = false,
        outputsText = true, structuredOutputs = strict, tools = tools, responseFormat = jsonMode,
    )

    private fun ep(model: String, tag: String, vararg params: String, status: Int = 0) =
        EndpointInfo(model, tag, params.toSet(), status)

    private val none = EndpointKnowledge()

    @Test
    fun `free variants are never offered`() {
        val catalog = listOf(model("a/x:free", 0.0, 0.0), model("b/y"), model("c/z", 0.0, 0.0))
        val plan = BenchmarkCandidates.plan(AiTask.MENU_INGESTION, catalog, emptyList(), false, none)
        assertThat(plan.selected.map { it.id }).containsExactly("b/y")
        assertThat(plan.excluded.map { it.reason }.toSet()).containsExactly(ExclusionReason.FREE_TIER)
        assertThat(plan.excluded.map { it.model.id }).containsExactly("a/x:free", "c/z")
    }

    @Test
    fun `ranked by capability first and then price`() {
        val catalog = listOf(
            model("cheap/json-only", 0.01, 0.01, strict = false),
            model("mid/strict", 0.50, 0.50),
            model("dear/strict-big", 2.0, 2.0, context = 1_000_000),
            model("cheap/strict", 0.05, 0.05),
        )
        val ids = BenchmarkCandidates.ranked(AiTask.MENU_INGESTION, catalog, emptyList()).map { it.id }
        // Strict with a big context window first, then strict by price, and the cheap JSON-mode model last.
        assertThat(ids).containsExactly("dear/strict-big", "cheap/strict", "mid/strict", "cheap/json-only").inOrder()
    }

    @Test
    fun `web tasks need tool support and the plan keeps at most four models`() {
        val catalog = (1..8).map { model("m/$it", prompt = it.toDouble()) } + model("m/notools", 0.001, tools = false)
        val plan = BenchmarkCandidates.plan(AiTask.PLAN_B, catalog, emptyList(), false, none)
        assertThat(plan.selected).hasSize(BenchmarkCandidates.PER_TASK)
        assertThat(plan.selected.map { it.id }).doesNotContain("m/notools")
    }

    @Test
    fun `a model with no zero retention endpoint is left out when privacy is strict`() {
        val catalog = listOf(model("a/private"), model("b/open"))
        val knowledge = EndpointKnowledge(zdr = listOf(ep("a/private", "p1", "structured_outputs")))
        val plan = BenchmarkCandidates.plan(AiTask.MENU_INGESTION, catalog, emptyList(), requireZdr = true, knowledge = knowledge)
        assertThat(plan.selected.map { it.id }).containsExactly("a/private")
        assertThat(plan.excluded.single().reason).isEqualTo(ExclusionReason.PRIVACY)
        assertThat(plan.excluded.single().model.id).isEqualTo("b/open")
    }

    @Test
    fun `any provider privacy does not filter on the zero retention list`() {
        val catalog = listOf(model("b/open"))
        val knowledge = EndpointKnowledge(zdr = emptyList())
        val plan = BenchmarkCandidates.plan(AiTask.MENU_INGESTION, catalog, emptyList(), requireZdr = false, knowledge = knowledge)
        assertThat(plan.selected.map { it.id }).containsExactly("b/open")
    }

    @Test
    fun `the zero retention endpoint must also accept the schema parameter`() {
        val m = model("a/x")
        val knowledge = EndpointKnowledge(
            zdr = listOf(ep("a/x", "zdr-host", "temperature")),
            perModel = mapOf(
                "a/x" to listOf(
                    ep("a/x", "zdr-host", "temperature"), // private, but cannot do structured output
                    ep("a/x", "other-host", "structured_outputs"), // can, but is not private
                ),
            ),
        )
        assertThat(BenchmarkCandidates.verdict(m, requireZdr = true, knowledge = knowledge)).isEqualTo(ExclusionReason.PRIVACY)
        // Without a privacy requirement the second endpoint serves it.
        assertThat(BenchmarkCandidates.verdict(m, requireZdr = false, knowledge = knowledge)).isNull()
    }

    @Test
    fun `an endpoint that is down does not count`() {
        val m = model("a/x")
        val knowledge = EndpointKnowledge(perModel = mapOf("a/x" to listOf(ep("a/x", "t", "structured_outputs", status = -5))))
        assertThat(BenchmarkCandidates.verdict(m, requireZdr = false, knowledge = knowledge)).isEqualTo(ExclusionReason.NO_ENDPOINT)
    }

    @Test
    fun `a json-mode model needs response_format, not structured_outputs`() {
        val m = model("a/x", strict = false)
        assertThat(BenchmarkCandidates.requiredParameter(m)).isEqualTo("response_format")
        val knowledge = EndpointKnowledge(perModel = mapOf("a/x" to listOf(ep("a/x", "t", "response_format"))))
        assertThat(BenchmarkCandidates.verdict(m, false, knowledge)).isNull()
    }

    @Test
    fun `what is not known never excludes a model`() {
        assertThat(BenchmarkCandidates.verdict(model("a/x"), requireZdr = true, knowledge = none)).isNull()
    }

    @Test
    fun `models are looked up only near the top of the ranking`() {
        val catalog = (1..30).map { model("m/$it", prompt = it.toDouble()) }
        assertThat(BenchmarkCandidates.lookahead(AiTask.MENU_INGESTION, catalog, emptyList())).hasSize(BenchmarkCandidates.LOOKAHEAD)
    }

    @Test
    fun `models that cannot see photos or search the web are not chosen for them`() {
        val noVision = model("a/blind", images = false)
        val sees = model("b/sees", images = true)
        assertThat(AiTask.VISION_TRACKER.canRun(noVision)).isFalse()
        assertThat(AiTask.VISION_TRACKER.canRun(sees)).isTrue()
        assertThat(AiTask.RECIPE_HUNTER.canRun(model("c/notools", tools = false))).isFalse()
        assertThat(AiTask.PLAN_B.canRun(model("d/tools"))).isTrue()
        assertThat(AiTask.PLAN_B.canRun(model("e/nojson", strict = false, jsonMode = false))).isFalse()
    }

    // --- the compact summary -------------------------------------------------------------------------

    @Test
    fun `the summary is one line with counts and reasons`() {
        val skipped = listOf(
            SkippedEntry("Menu", "a/x", "rl", AiFailureKind.RATE_LIMITED),
            SkippedEntry("Menu", "b/x", "rl", AiFailureKind.RATE_LIMITED),
            SkippedEntry("Plan B", "a/x", "rl", AiFailureKind.RATE_LIMITED),
            SkippedEntry("Menu", "c/x", "OpenRouter did not report a cost for this model", null),
        )
        val line = BenchmarkSummary.headline(2, skipped)
        assertThat(line).isEqualTo("2 saved · 4 skipped (3 rate limited, 1 openrouter did not report a cost for this model)")
        assertThat(line.lines()).hasSize(1)
        assertThat(BenchmarkSummary.headline(3, emptyList())).isEqualTo("3 saved")
        assertThat(BenchmarkSummary.groups(skipped).first().first).isEqualTo("Rate limited")
        assertThat(BenchmarkSummary.groups(skipped).first().second).hasSize(3)
    }
}
