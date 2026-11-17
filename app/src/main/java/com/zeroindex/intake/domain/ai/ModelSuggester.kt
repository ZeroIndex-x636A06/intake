package com.zeroindex.intake.domain.ai

import com.zeroindex.intake.domain.ai.benchmark.ExclusionReason

/** A finished benchmark score for one model on one task. */
data class BenchmarkScore(
    val task: AiTask,
    val modelId: String,
    val accuracy: Double,
    val jsonValidity: Double,
    val fixtures: Int,
    val medianLatencyMs: Long? = null,
    val costUsdPerRequest: Double? = null,
)

/** Why a model is the suggestion, in words the picker can show as-is. */
enum class SuggestionBasis(val explanation: String) {
    BENCHMARKED("Cheapest model that passed the benchmark for this task"),
    SHORTLIST("Cheapest capable model from the suggested shortlist · not benchmarked yet"),
    CHEAPEST_CAPABLE("Cheapest model that supports this task · not benchmarked yet"),
}

data class RankedModel(
    val model: ModelInfo,
    val estimatedUsdPerUse: Double,
    val benchmark: BenchmarkScore?,
)

data class Suggestion(
    val suggested: RankedModel?,
    val basis: SuggestionBasis?,
    /** Every capable model, most capable first and cheapest within a tier, for the picker list. */
    val candidates: List<RankedModel>,
    /** Capable models with no provider that meets the privacy setting and the task's parameters, with why. */
    val unavailable: Map<String, ExclusionReason> = emptyMap(),
)

/**
 * Amendment §1: "proactively suggest the optimal model for the task", as
 * cheap as possible while still doing the task correctly.
 *
 * "Correctly" can only come from a benchmark, so a benchmarked pass always
 * beats a guess. Without one, the suggestion is the cheapest capable model on
 * the amendment's shortlist, and it says plainly that it is unbenchmarked -
 * the picker never presents a guess as a tested result.
 *
 * Free models are excluded from suggestions unless [allowRetainingModels]:
 * a free model that keeps your food photos is not the "optimal" choice for a
 * personal health app, however cheap. They stay in the candidate list, marked.
 */
object ModelSuggester {

    const val PASS_ACCURACY = 0.85
    const val PASS_JSON_VALIDITY = 0.95
    const val MIN_FIXTURES = 5

    fun estimateUsd(model: ModelInfo, task: AiTask): Double =
        (task.typicalInputTokens * model.promptPerMillion + task.typicalOutputTokens * model.completionPerMillion) / 1e6 +
            task.typicalImages * model.perImage +
            model.perRequest +
            task.typicalSearches * AiTask.WEB_SEARCH_USD

    fun suggest(
        task: AiTask,
        catalog: List<ModelInfo>,
        benchmarks: List<BenchmarkScore>,
        allowRetainingModels: Boolean,
        /** When given, models with no provider meeting the privacy setting are never suggested. See [ModelFit]. */
        fit: PrivacyFit? = null,
    ): Suggestion {
        val scores = benchmarks.filter { it.task == task }.associateBy { it.modelId }
        val candidates = catalog
            .filter(task::canRun)
            .map { RankedModel(it, estimateUsd(it, task), scores[it.id]) }
            .sortedWith(
                compareByDescending<RankedModel> { ModelCapability.rank(it.model, task) }
                    .thenBy { it.estimatedUsdPerUse }
                    .thenBy { it.model.id },
            )
        val unavailable = if (fit == null) {
            emptyMap()
        } else {
            candidates.mapNotNull { c -> ModelFit.verdict(c.model, task, fit)?.let { c.model.id to it } }.toMap()
        }
        val eligible = candidates.filter { (allowRetainingModels || !it.model.mayRetainPrompts) && it.model.id !in unavailable }

        eligible.firstOrNull { it.benchmark?.passes() == true }?.let {
            return Suggestion(it, SuggestionBasis.BENCHMARKED, candidates, unavailable)
        }
        val shortlisted = resolveShortlist(task, eligible.map { it.model }).toSet()
        eligible.firstOrNull { it.model.id in shortlisted }?.let {
            return Suggestion(it, SuggestionBasis.SHORTLIST, candidates, unavailable)
        }
        eligible.firstOrNull()?.let { return Suggestion(it, SuggestionBasis.CHEAPEST_CAPABLE, candidates, unavailable) }
        return Suggestion(null, null, candidates, unavailable)
    }

    /**
     * Turns shortlist entries into concrete ids. `vendor/family-*-variant`
     * matches ids of that shape whose wildcard part is a version number, and
     * resolves to the newest by creation date - so "google/gemini-*-flash"
     * means the latest Gemini Flash, never a Flash Lite or an image model.
     */
    fun resolveShortlist(task: AiTask, available: List<ModelInfo>): List<String> =
        task.shortlist.mapNotNull { entry ->
            if ('*' !in entry) {
                entry.takeIf { id -> available.any { it.id == id } }
            } else {
                val (prefix, suffix) = entry.split('*', limit = 2)
                available
                    .filter { m ->
                        m.id.startsWith(prefix) && m.id.endsWith(suffix) &&
                            m.id.removePrefix(prefix).removeSuffix(suffix).matches(VERSION)
                    }
                    .maxByOrNull { it.createdAt }
                    ?.id
            }
        }

    private fun BenchmarkScore.passes() =
        fixtures >= MIN_FIXTURES && accuracy >= PASS_ACCURACY && jsonValidity >= PASS_JSON_VALIDITY

    private val VERSION = Regex("""\d+(\.\d+)*""")
}

/** How well a model suits a task, before price. Higher is better. */
object ModelCapability {
    /**
     * Strict structured output counts most (the reply is held to the schema, so fewer repairs), then headroom in
     * the context window. Vision and tool support are required by [AiTask.canRun], not ranked.
     */
    fun rank(model: ModelInfo, task: AiTask): Int =
        (if (model.structuredOutputs) 2 else 0) + (if (model.contextLength >= task.minContext * 2) 1 else 0)
}
