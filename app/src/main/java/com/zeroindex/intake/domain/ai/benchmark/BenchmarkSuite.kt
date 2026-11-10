package com.zeroindex.intake.domain.ai.benchmark

import com.zeroindex.intake.domain.ai.AiTask
import com.zeroindex.intake.domain.ai.BenchmarkScore
import com.zeroindex.intake.domain.ai.ModelInfo
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
     * The models to measure for [task] when nothing is known about their endpoints (offline, or before the lookup
     * finishes): capable paid models, most capable then cheapest. Never a free variant. See [BenchmarkCandidates]
     * for the full plan with the privacy check.
     */
    fun defaultCandidates(
        task: AiTask,
        catalog: List<ModelInfo>,
        benchmarks: List<BenchmarkScore>,
        allowRetainingModels: Boolean,
    ): List<ModelInfo> =
        BenchmarkCandidates.plan(task, catalog.filter { allowRetainingModels || !it.mayRetainPrompts }, benchmarks, false, EndpointKnowledge())
            .selected

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
