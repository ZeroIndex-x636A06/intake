package com.zeroindex.intake.domain.ai.benchmark

import com.zeroindex.intake.domain.ai.AiTask
import com.zeroindex.intake.domain.ai.BenchmarkScore
import com.zeroindex.intake.domain.ai.ModelCapability
import com.zeroindex.intake.domain.ai.ModelInfo
import com.zeroindex.intake.domain.ai.ModelSuggester

/**
 * One provider's endpoint for a model, as OpenRouter lists it: which parameters it accepts and whether it is
 * healthy. [tag] identifies the endpoint, and is how the zero-data-retention list names it.
 */
data class EndpointInfo(
    val modelId: String,
    val tag: String,
    val supportedParameters: Set<String>,
    /** OpenRouter's endpoint status; 0 is healthy, negative is degraded, down or retired. */
    val status: Int = 0,
) {
    val healthy: Boolean get() = status >= 0
}

/**
 * What the app knows about where each model can be served, from OpenRouter's public endpoint data.
 *
 * - [zdr] is the list of endpoints that keep nothing after answering. It is the only public per-endpoint data
 *   policy there is, so it is how "No data collection" and "Zero retention" are checked before a run. null means
 *   the list could not be loaded (offline), in which case nothing is filtered on privacy.
 * - [perModel] is each model's own endpoint list, fetched for the likely candidates, used to confirm an endpoint
 *   accepts the parameters a request needs and is up.
 */
data class EndpointKnowledge(
    val zdr: List<EndpointInfo>? = null,
    val perModel: Map<String, List<EndpointInfo>> = emptyMap(),
)

/** Why a model is not offered for a run. */
enum class ExclusionReason(val label: String) {
    FREE_TIER("free tier (tiny rate limits, and often retained)"),
    PRIVACY("not available with your privacy setting"),
    NO_ENDPOINT("no healthy provider accepts structured output"),
}

data class ExcludedModel(val task: AiTask, val model: ModelInfo, val reason: ExclusionReason)

/** The models to measure for one task, and the ones left out with the reason. */
data class CandidatePlan(
    val task: AiTask,
    val selected: List<ModelInfo>,
    val excluded: List<ExcludedModel>,
)

/**
 * B7: which models a benchmark run should spend its slots on.
 *
 * 1. Only models able to do the task at all ([AiTask.canRun]).
 * 2. Never `:free` variants (their rate limits caused the 429s) or zero-priced ones.
 * 3. Only models with a provider that is up, accepts the schema parameter, and meets the privacy setting, when
 *    that can be told from the endpoint data. A model that would fail with "no provider meets your privacy
 *    setting" never takes a slot.
 * 4. Ranked by capability first (strict structured output, context headroom), then price.
 */
object BenchmarkCandidates {

    /** Models fetched and checked per task: the suggestion plus the next most capable and cheapest. */
    const val PER_TASK = 4

    /** How many ranked models per task get a per-model endpoint lookup before the final cut. */
    const val LOOKAHEAD = 8

    /** The parameter a request needs from its provider: strict schema when the model has it, else JSON mode. */
    fun requiredParameter(model: ModelInfo): String = if (model.structuredOutputs) "structured_outputs" else "response_format"

    /** Capable, paid models, most capable then cheapest, before any privacy check. */
    fun ranked(task: AiTask, catalog: List<ModelInfo>, benchmarks: List<BenchmarkScore>): List<ModelInfo> {
        val scores = benchmarks.filter { it.task == task }.associateBy { it.modelId }
        return catalog
            .filter(task::canRun)
            .filterNot(::isFreeTier)
            .sortedWith(
                compareByDescending<ModelInfo> { ModelCapability.rank(it, task) }
                    .thenBy { ModelSuggester.estimateUsd(it, task) }
                    // A model already measured to pass is surer than one never tried.
                    .thenByDescending { scores[it.id]?.accuracy ?: 0.0 }
                    .thenBy { it.id },
            )
    }

    fun isFreeTier(model: ModelInfo): Boolean = model.id.endsWith(":free") || model.isFree

    /** The models worth looking up endpoints for: the head of the ranking. */
    fun lookahead(task: AiTask, catalog: List<ModelInfo>, benchmarks: List<BenchmarkScore>): List<ModelInfo> =
        ranked(task, catalog, benchmarks).take(LOOKAHEAD)

    fun plan(
        task: AiTask,
        catalog: List<ModelInfo>,
        benchmarks: List<BenchmarkScore>,
        /** True for "No data collection" and "Zero retention": the model must have a zero-retention endpoint. */
        requireZdr: Boolean,
        knowledge: EndpointKnowledge,
    ): CandidatePlan {
        val excluded = mutableListOf<ExcludedModel>()
        catalog.filter(task::canRun).filter(::isFreeTier).forEach { excluded += ExcludedModel(task, it, ExclusionReason.FREE_TIER) }

        val selected = mutableListOf<ModelInfo>()
        for (model in ranked(task, catalog, benchmarks)) {
            if (selected.size >= PER_TASK) break
            val why = verdict(model, requireZdr, knowledge)
            if (why == null) selected += model else excluded += ExcludedModel(task, model, why)
        }
        return CandidatePlan(task, selected, excluded)
    }

    /** Null when some endpoint of [model] can serve the request under the privacy setting; otherwise why not. */
    fun verdict(model: ModelInfo, requireZdr: Boolean, knowledge: EndpointKnowledge): ExclusionReason? {
        val needed = requiredParameter(model)
        val zdrTags = knowledge.zdr?.filter { it.modelId == model.id }
        val own = knowledge.perModel[model.id]

        // Candidate endpoints: the model's own list when we have it, else what the zero-retention list says.
        val endpoints = own ?: zdrTags
        if (requireZdr && zdrTags != null && zdrTags.isEmpty()) return ExclusionReason.PRIVACY
        if (endpoints == null) return null // nothing known: do not guess a model out.

        val eligible = endpoints.filter { e ->
            e.healthy && needed in e.supportedParameters &&
                (!requireZdr || zdrTags == null || zdrTags.any { it.tag == e.tag })
        }
        if (eligible.isNotEmpty()) return null
        // Why none is eligible: the privacy filter, or the parameter/health filter?
        val ignoringPrivacy = endpoints.any { it.healthy && needed in it.supportedParameters }
        return if (requireZdr && ignoringPrivacy) ExclusionReason.PRIVACY else ExclusionReason.NO_ENDPOINT
    }
}
