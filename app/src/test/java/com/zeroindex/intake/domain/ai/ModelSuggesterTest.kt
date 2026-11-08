package com.zeroindex.intake.domain.ai

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ModelSuggesterTest {

    private fun model(
        id: String,
        prompt: Double,
        completion: Double,
        images: Boolean = true,
        tools: Boolean = true,
        json: Boolean = true,
        context: Long = 1_000_000,
        created: Long = 0,
        description: String = "",
    ) = ModelInfo(
        id = id, name = id, description = description, contextLength = context,
        promptPerMillion = prompt, completionPerMillion = completion, perImage = 0.0, perRequest = 0.0,
        acceptsImages = images, acceptsFiles = false, outputsText = true,
        structuredOutputs = json, tools = tools, createdAt = created,
    )

    private val catalog = listOf(
        model("openai/gpt-6-luna", 0.10, 0.50),
        model("deepseek/deepseek-v4.1-flash", 0.051, 0.567),
        model("google/gemini-3.5-flash", 1.5, 9.0, created = 100),
        model("google/gemini-3.8-flash", 0.825, 4.125, created = 300),
        model("google/gemini-3.5-flash-lite", 0.3, 2.5, created = 200),
        model("google/gemini-3.8-flash:batch", 0.4, 2.0, created = 300),
        model("text/only", 0.01, 0.01, images = false),
        model("no/json", 0.01, 0.01, json = false),
        model("cheap/free:free", 0.0, 0.0),
    )

    @Test
    fun `incapable, batch and alias models are never candidates`() {
        val ids = ModelSuggester.suggest(AiTask.VISION_TRACKER, catalog, emptyList(), false)
            .candidates.map { it.model.id }
        assertThat(ids).containsNoneOf("text/only", "no/json", "google/gemini-3.8-flash:batch")
    }

    @Test
    fun `without benchmarks the suggestion is the cheapest shortlisted model and says so`() {
        val s = ModelSuggester.suggest(AiTask.VISION_TRACKER, catalog, emptyList(), false)
        assertThat(s.basis).isEqualTo(SuggestionBasis.SHORTLIST)
        // deepseek v4.1 flash: (1800*0.051 + 600*0.567)/1e6 = 0.000432
        // gpt-6-luna:          (1800*0.10  + 600*0.50 )/1e6 = 0.000480
        assertThat(s.suggested!!.model.id).isEqualTo("deepseek/deepseek-v4.1-flash")
        assertThat(s.basis!!.explanation).contains("not benchmarked")
    }

    @Test
    fun `a free model that may keep prompts is not suggested unless allowed`() {
        val s = ModelSuggester.suggest(AiTask.DAILY_INSIGHT, catalog, emptyList(), false)
        assertThat(s.suggested!!.model.isFree).isFalse()
        assertThat(s.candidates.map { it.model.id }).contains("cheap/free:free")

        val allowed = ModelSuggester.suggest(AiTask.DAILY_INSIGHT, catalog, emptyList(), true)
        assertThat(allowed.candidates.first().model.id).isEqualTo("cheap/free:free")
    }

    @Test
    fun `a passing benchmark beats a cheaper unbenchmarked model`() {
        val s = ModelSuggester.suggest(
            AiTask.VISION_TRACKER,
            catalog,
            listOf(BenchmarkScore(AiTask.VISION_TRACKER, "openai/gpt-6-luna", 0.93, 1.0, 12)),
            false,
        )
        assertThat(s.basis).isEqualTo(SuggestionBasis.BENCHMARKED)
        assertThat(s.suggested!!.model.id).isEqualTo("openai/gpt-6-luna")
    }

    @Test
    fun `a failing or thin benchmark does not count`() {
        val s = ModelSuggester.suggest(
            AiTask.VISION_TRACKER,
            catalog,
            listOf(
                BenchmarkScore(AiTask.VISION_TRACKER, "openai/gpt-6-luna", 0.60, 1.0, 12),
                BenchmarkScore(AiTask.VISION_TRACKER, "google/gemini-3.8-flash", 0.99, 1.0, 2),
            ),
            false,
        )
        assertThat(s.basis).isEqualTo(SuggestionBasis.SHORTLIST)
    }

    @Test
    fun `latest family wildcard resolves to the newest versioned member only`() {
        val ids = ModelSuggester.resolveShortlist(AiTask.VISION_TRACKER, catalog)
        assertThat(ids).contains("google/gemini-3.8-flash")
        assertThat(ids).containsNoneOf("google/gemini-3.5-flash", "google/gemini-3.5-flash-lite")
    }

    @Test
    fun `web searches are part of the quoted price`() {
        val luna = catalog.first { it.id == "openai/gpt-6-luna" }
        val usd = ModelSuggester.estimateUsd(luna, AiTask.RECIPE_HUNTER)
        assertThat(usd).isWithin(1e-9).of((18_000 * 0.10 + 3_000 * 0.50) / 1e6 + 3 * AiTask.WEB_SEARCH_USD)
    }

    @Test
    fun `retention wording in a description triggers the warning`() {
        assertThat(model("x/y", 1.0, 1.0, description = "Prompts and outputs may be retained and used").mayRetainPrompts)
            .isTrue()
        assertThat(model("x/z", 1.0, 1.0).mayRetainPrompts).isFalse()
    }
}
