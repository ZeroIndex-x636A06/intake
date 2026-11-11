package com.zeroindex.intake.domain.ai

import com.zeroindex.intake.domain.ai.benchmark.BenchmarkCandidates
import com.zeroindex.intake.domain.ai.benchmark.EndpointKnowledge
import com.zeroindex.intake.domain.ai.benchmark.ExclusionReason

/**
 * The privacy setting as the endpoint check needs it: whether a zero-retention endpoint is required (both
 * "No data collection" and "Zero retention" are checked against the zero-retention list, the only public
 * per-endpoint data policy), and what is known about each model's providers. Unknown never excludes a model.
 */
data class PrivacyFit(val requireZdr: Boolean, val knowledge: EndpointKnowledge)

/**
 * What a request sends that a provider must accept, because every request sets `require_parameters`: a strict
 * schema (or JSON mode for a model without it), the sampling and length settings, and tools when the task
 * searches the web. A provider that lacks any of them is skipped by OpenRouter, and when none is left the
 * answer is the same 404 as a privacy failure.
 */
object ModelFit {

    /** The provider parameters a request for [task] to [model] sends. */
    fun requiredParameters(model: ModelInfo, task: AiTask): Set<String> = buildSet {
        add(BenchmarkCandidates.requiredParameter(model))
        add("max_tokens")
        add("temperature")
        if (task.needsTools) add("tools")
    }

    /** Null when some provider of [model] can serve [task] under [fit]; otherwise why not. */
    fun verdict(model: ModelInfo, task: AiTask, fit: PrivacyFit): ExclusionReason? =
        BenchmarkCandidates.verdict(
            model,
            fit.requireZdr,
            fit.knowledge,
            extraRequired = requiredParameters(model, task) - BenchmarkCandidates.requiredParameter(model),
        )

    /**
     * Models to try for [task], best first: the suggestion, then the other shortlisted models, then every other
     * capable model, all with a provider that meets [fit]. The same list serves the gateway's fallback, so a
     * model that failed is simply skipped by the caller.
     */
    fun ordered(
        task: AiTask,
        catalog: List<ModelInfo>,
        benchmarks: List<BenchmarkScore>,
        allowRetainingModels: Boolean,
        fit: PrivacyFit,
    ): List<ModelInfo> {
        val s = ModelSuggester.suggest(task, catalog, benchmarks, allowRetainingModels, fit)
        val usable = s.candidates
            .filter { it.model.id !in s.unavailable && (allowRetainingModels || !it.model.mayRetainPrompts) }
            .map { it.model }
        val head = s.suggested?.model
        val shortlisted = ModelSuggester.resolveShortlist(task, usable).toSet()
        val rest = usable.filter { it.id != head?.id }
        return listOfNotNull(head) + rest.filter { it.id in shortlisted } + rest.filter { it.id !in shortlisted }
    }

    /** Models worth a provider lookup for [task]: the shortlist first, then the head of the capable ranking. */
    fun lookupIds(task: AiTask, catalog: List<ModelInfo>, benchmarks: List<BenchmarkScore>): List<String> {
        val ranked = BenchmarkCandidates.ranked(task, catalog, benchmarks)
        return (ModelSuggester.resolveShortlist(task, ranked) + ranked.take(LOOKUP_RANKED).map { it.id }).distinct()
    }

    private const val LOOKUP_RANKED = 4
}
