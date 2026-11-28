package com.zeroindex.intake.domain.ai

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.domain.ai.benchmark.EndpointInfo
import com.zeroindex.intake.domain.ai.benchmark.EndpointKnowledge
import com.zeroindex.intake.domain.ai.benchmark.ExclusionReason
import org.junit.Test

/** Which models may be suggested, and in what order the gateway falls back, from the endpoint data alone. */
class ModelFitTest {

    private fun model(id: String, prompt: Double = 0.1, tools: Boolean = true, strict: Boolean = true) = ModelInfo(
        id = id, name = id, description = "", contextLength = 300_000, promptPerMillion = prompt,
        completionPerMillion = prompt, perImage = 0.0, perRequest = 0.0, acceptsImages = true, acceptsFiles = false,
        outputsText = true, structuredOutputs = strict, tools = tools, responseFormat = true,
    )

    private val all = setOf("structured_outputs", "max_tokens", "temperature", "tools")

    private fun ep(model: String, tag: String, params: Set<String> = all, status: Int = 0) =
        EndpointInfo(model, tag, params, status)

    private fun fit(zdr: List<EndpointInfo>?, perModel: Map<String, List<EndpointInfo>>, requireZdr: Boolean = true) =
        PrivacyFit(requireZdr, EndpointKnowledge(zdr, perModel))

    @Test
    fun `a model with no zero retention provider is not suggested and says why`() {
        val catalog = listOf(model("anthropic/claude-haiku-5.5", prompt = 0.05), model("deepseek/deepseek-v4.1-flash", prompt = 0.3))
        val f = fit(
            zdr = listOf(ep("deepseek/deepseek-v4.1-flash", "ds")),
            perModel = mapOf(
                "anthropic/claude-haiku-5.5" to listOf(ep("anthropic/claude-haiku-5.5", "anthropic")),
                "deepseek/deepseek-v4.1-flash" to listOf(ep("deepseek/deepseek-v4.1-flash", "ds")),
            ),
        )
        val s = ModelSuggester.suggest(AiTask.DAILY_INSIGHT, catalog, emptyList(), false, f)
        assertThat(s.suggested!!.model.id).isEqualTo("deepseek/deepseek-v4.1-flash")
        assertThat(s.unavailable).containsExactly("anthropic/claude-haiku-5.5", ExclusionReason.PRIVACY)
        // The unsuited model stays in the picker list.
        assertThat(s.candidates.map { it.model.id }).contains("anthropic/claude-haiku-5.5")
    }

    @Test
    fun `without a fit nothing is filtered, as before`() {
        val catalog = listOf(model("a/x"))
        val s = ModelSuggester.suggest(AiTask.DAILY_INSIGHT, catalog, emptyList(), false)
        assertThat(s.suggested!!.model.id).isEqualTo("a/x")
        assertThat(s.unavailable).isEmpty()
    }

    @Test
    fun `unknown endpoint data never excludes a model`() {
        val catalog = listOf(model("a/x"))
        val s = ModelSuggester.suggest(AiTask.DAILY_INSIGHT, catalog, emptyList(), false, fit(null, emptyMap()))
        assertThat(s.suggested!!.model.id).isEqualTo("a/x")
    }

    @Test
    fun `a web search task needs a provider that supports tools`() {
        val m = model("a/x")
        val noTools = fit(null, mapOf("a/x" to listOf(ep("a/x", "p", all - "tools"))), requireZdr = false)
        assertThat(ModelFit.verdict(m, AiTask.PLAN_B, noTools)).isEqualTo(ExclusionReason.NO_ENDPOINT)
        // The same model is fine for a task that sends no tools.
        assertThat(ModelFit.verdict(m, AiTask.DAILY_INSIGHT, noTools)).isNull()
    }

    @Test
    fun `a provider must accept the sampling settings every request sends`() {
        val m = model("a/x")
        val noTemp = fit(null, mapOf("a/x" to listOf(ep("a/x", "p", all - "temperature"))), requireZdr = false)
        assertThat(ModelFit.verdict(m, AiTask.DAILY_INSIGHT, noTemp)).isEqualTo(ExclusionReason.NO_ENDPOINT)
    }

    @Test
    fun `a model that takes no temperature is not required to have a provider that lists it`() {
        val m = model("anthropic/claude-haiku-5.5").copy(acceptsTemperature = false)
        val noTemp = fit(null, mapOf(m.id to listOf(ep(m.id, "p", all - "temperature"))), requireZdr = false)
        assertThat(ModelFit.requiredParameters(m, AiTask.DAILY_INSIGHT)).doesNotContain("temperature")
        assertThat(ModelFit.verdict(m, AiTask.DAILY_INSIGHT, noTemp)).isNull()
    }

    @Test
    fun `the schema parameter depends on the model`() {
        val jsonMode = model("a/x", strict = false)
        val f = fit(null, mapOf("a/x" to listOf(ep("a/x", "p", setOf("response_format", "max_tokens", "temperature")))), requireZdr = false)
        assertThat(ModelFit.requiredParameters(jsonMode, AiTask.DAILY_INSIGHT)).contains("response_format")
        assertThat(ModelFit.verdict(jsonMode, AiTask.DAILY_INSIGHT, f)).isNull()
    }

    @Test
    fun `the privacy and the parameter filter must be met by the same provider`() {
        val m = model("a/x")
        // p1 is zero retention but lacks tools; p2 has tools but is not zero retention.
        val f = fit(
            zdr = listOf(ep("a/x", "p1")),
            perModel = mapOf("a/x" to listOf(ep("a/x", "p1", all - "tools"), ep("a/x", "p2"))),
        )
        assertThat(ModelFit.verdict(m, AiTask.PLAN_B, f)).isNotNull()
    }

    @Test
    fun `the fallback order is the suggestion, then the shortlist, then the rest, all compatible`() {
        val catalog = listOf(
            model("anthropic/claude-haiku-5.5", prompt = 0.5),
            model("openai/gpt-6-luna", prompt = 0.2),
            model("other/cheap", prompt = 0.01),
            model("blocked/model", prompt = 0.001),
        )
        val f = fit(
            zdr = listOf(ep("anthropic/claude-haiku-5.5", "a"), ep("openai/gpt-6-luna", "o"), ep("other/cheap", "c")),
            perModel = emptyMap(),
        )
        val order = ModelFit.ordered(AiTask.DAILY_INSIGHT, catalog, emptyList(), false, f).map { it.id }
        assertThat(order).containsExactly("openai/gpt-6-luna", "anthropic/claude-haiku-5.5", "other/cheap").inOrder()
        assertThat(order).doesNotContain("blocked/model")
    }

    @Test
    fun `lookups cover the shortlist and the head of the ranking`() {
        val catalog = (1..10).map { model("m/$it", prompt = it.toDouble()) } + model("anthropic/claude-haiku-5.5", prompt = 50.0)
        val ids = ModelFit.lookupIds(AiTask.DAILY_INSIGHT, catalog, emptyList())
        assertThat(ids).contains("anthropic/claude-haiku-5.5")
        assertThat(ids).contains("m/1")
        assertThat(ids.size).isAtMost(6)
    }
}
