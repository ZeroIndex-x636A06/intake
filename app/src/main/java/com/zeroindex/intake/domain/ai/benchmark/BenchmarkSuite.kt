package com.zeroindex.intake.domain.ai.benchmark

import com.zeroindex.intake.domain.ai.AiTask
import com.zeroindex.intake.domain.ai.BenchmarkScore
import com.zeroindex.intake.domain.ai.ModelInfo
import com.zeroindex.intake.domain.ai.ModelSuggester
import kotlin.math.ceil

/** One request of a benchmark run, as measured. */
data class RequestOutcome(
    val score: FixtureScore,
    /** The first reply decoded against the schema, with no retry. */
    val parsedFirstTry: Boolean,
    val latencyMs: Long,
    /** From the response's usage block; null when the provider did not report one. */
    val costUsd: Double?,
)

/**
 * What the benchmark covers and how a model's requests become one
 * [BenchmarkScore]. Vision is absent on purpose: it needs photos with known
 * answers, none are bundled, and a result that was not measured is not shown.
 */
object BenchmarkSuite {

    /** Default hard spend limit for one run, in USD. */
    const val DEFAULT_CAP_USD = 0.25

    /** The task's suggested model plus this many of the cheapest others. */
    const val EXTRA_CHEAP_MODELS = 3

    val tasks: List<AiTask> = listOf(
        AiTask.MENU_INGESTION,
        AiTask.RECIPE_HUNTER,
        AiTask.PLAN_B,
        AiTask.DAILY_INSIGHT,
    )

    /**
     * The reply budget per request. Generous, because a model that spends it
     * thinking should not be failed for being cut off, and it is also the
     * worst case the up-front cost estimate assumes.
     */
    fun maxOutputTokens(task: AiTask): Int = when (task) {
        AiTask.DAILY_INSIGHT -> 1_000
        else -> 3_000
    }

    /**
     * The suggestion for [task] plus the cheapest other models able to do it,
     * all honouring the retention setting the picker already applies.
     */
    fun defaultCandidates(
        task: AiTask,
        catalog: List<ModelInfo>,
        benchmarks: List<BenchmarkScore>,
        allowRetainingModels: Boolean,
    ): List<ModelInfo> {
        val suggestion = ModelSuggester.suggest(task, catalog, benchmarks, allowRetainingModels)
        val suggested = suggestion.suggested?.model
        val cheapest = suggestion.candidates
            .filter { allowRetainingModels || !it.model.mayRetainPrompts }
            .map { it.model }
            .filter { it.id != suggested?.id }
            .take(EXTRA_CHEAP_MODELS)
        return listOfNotNull(suggested) + cheapest
    }

    /** One stored row's numbers from a model's finished requests. */
    fun summarize(task: AiTask, modelId: String, outcomes: List<RequestOutcome>): BenchmarkScore {
        require(outcomes.isNotEmpty()) { "A score needs at least one request" }
        val latencies = outcomes.map { it.latencyMs }.sorted()
        val mid = latencies.size / 2
        val median = if (latencies.size % 2 == 1) latencies[mid] else (latencies[mid - 1] + latencies[mid]) / 2
        return BenchmarkScore(
            task = task,
            modelId = modelId,
            accuracy = outcomes.map { it.score.accuracy }.average(),
            jsonValidity = outcomes.count { it.parsedFirstTry }.toDouble() / outcomes.size,
            fixtures = outcomes.size,
            medianLatencyMs = median,
            costUsdPerRequest = outcomes.mapNotNull { it.costUsd }.takeIf { it.isNotEmpty() }?.average(),
        )
    }
}

/**
 * The up-front price of a run. Deliberately a ceiling: the whole reply budget
 * is assumed used, and text is counted at three characters a token (English
 * runs nearer four), so the figure shown before the user confirms is one the
 * run will not exceed unless the catalog's prices are wrong.
 */
object BenchmarkCost {
    private const val CHARS_PER_TOKEN = 3.0

    fun inputTokens(chars: Int): Int = ceil(chars / CHARS_PER_TOKEN).toInt()

    fun requestUsd(model: ModelInfo, inputTokens: Int, maxOutputTokens: Int): Double =
        (inputTokens * model.promptPerMillion + maxOutputTokens * model.completionPerMillion) / 1e6 + model.perRequest
}
