package com.zeroindex.intake.data.ai.benchmark

import com.zeroindex.intake.data.ai.AiError
import com.zeroindex.intake.data.ai.AiPart
import com.zeroindex.intake.data.ai.AiPrivacy
import com.zeroindex.intake.data.ai.AiRequest
import com.zeroindex.intake.data.ai.AiGateway
import com.zeroindex.intake.data.db.dao.AiInsightDao
import com.zeroindex.intake.data.db.entity.ModelBenchmarkEntity
import com.zeroindex.intake.data.db.entity.seedId
import com.zeroindex.intake.domain.ai.AiFeature
import com.zeroindex.intake.domain.ai.AiTask
import com.zeroindex.intake.domain.ai.AiTrigger
import com.zeroindex.intake.domain.ai.BenchmarkScore
import com.zeroindex.intake.domain.ai.ModelInfo
import com.zeroindex.intake.domain.ai.benchmark.BenchmarkCost
import com.zeroindex.intake.domain.ai.benchmark.BenchmarkSuite
import com.zeroindex.intake.domain.ai.benchmark.FixtureScore
import com.zeroindex.intake.domain.ai.benchmark.RequestOutcome
import com.zeroindex.intake.domain.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The models to measure on one task. */
data class BenchmarkSelection(val task: AiTask, val models: List<ModelInfo>)

data class BenchmarkProgress(
    val task: AiTask,
    val modelId: String,
    /** Requests finished for this model, and how many it has in total. */
    val done: Int,
    val total: Int,
    val spentUsd: Double,
)

/** What became of one (task, model) pair. */
sealed interface ModelResult {
    val task: AiTask
    val modelId: String

    /** Measured in full and stored; replaces any earlier row for the pair. */
    data class Saved(val score: BenchmarkScore) : ModelResult {
        override val task: AiTask get() = score.task
        override val modelId: String get() = score.modelId
    }

    /** Not stored, because a partial or unmeasured result would mislead the picker. */
    data class Skipped(override val task: AiTask, override val modelId: String, val reason: String) : ModelResult
}

data class BenchmarkReport(
    val results: List<ModelResult>,
    val spentUsd: Double,
    val capUsd: Double,
    val stoppedByCap: Boolean,
    /** Set when the run ended early for a reason that affects every model (bad key, no credit, offline). */
    val abortReason: String?,
)

/**
 * Amendment §1: runs the fixtures against candidate models on the user's own
 * OpenRouter key, so the "not benchmarked" labels can be replaced with
 * measured numbers.
 *
 * It goes through [AiGateway] with the same system prompts, schemas and
 * privacy setting the features use, one request at a time, so the latency it
 * records is not inflated by the run's own parallelism and the spend cap is
 * checked between every pair of requests. The gateway's guard applies: the
 * master switch, the monthly budget and the usage log (feature Benchmark).
 *
 * The key never leaves the gateway and client, which put it in the
 * Authorization header. Nothing here formats an error from anything but
 * [AiError.message], which is fixed text, so the key cannot end up in a
 * message even if a server echoes it back.
 */
@Singleton
class BenchmarkRunner @Inject constructor(
    private val gateway: AiGateway,
    private val cases: BenchmarkCases,
    private val dao: AiInsightDao,
    private val clock: Clock,
) {
    /** A ceiling on what [selections] will cost; see [BenchmarkCost]. */
    suspend fun estimateUsd(selections: List<BenchmarkSelection>): Double = withContext(Dispatchers.IO) {
        selections.sumOf { selection ->
            val requests = cases.forTask(selection.task)
            selection.models.sumOf { model -> requests.sumOf { requestEstimate(model, selection.task, it) } }
        }
    }

    /**
     * Runs every selection in order and stores one row per fully measured
     * (task, model). Stops once spend reaches [capUsd]; a model cut off by the
     * cap is reported, not stored. Cancelling keeps the rows already stored.
     */
    suspend fun run(
        selections: List<BenchmarkSelection>,
        capUsd: Double,
        privacy: AiPrivacy,
        onProgress: (BenchmarkProgress) -> Unit,
    ): BenchmarkReport {
        require(capUsd > 0) { "The spend cap must be above zero" }
        val results = mutableListOf<ModelResult>()
        var spent = 0.0
        var stoppedByCap = false
        var abortReason: String? = null

        selections@ for (selection in selections) {
            val requests = withContext(Dispatchers.IO) { cases.forTask(selection.task) }
            for (model in selection.models) {
                if (spent >= capUsd) {
                    stoppedByCap = true
                    break@selections
                }
                val outcomes = mutableListOf<RequestOutcome>()
                var skipReason: String? = null
                for (case in requests) {
                    if (spent >= capUsd) {
                        stoppedByCap = true
                        skipReason = "Stopped at the spend cap before it finished"
                        break
                    }
                    onProgress(BenchmarkProgress(selection.task, model.id, outcomes.size, requests.size, spent))
                    val response = try {
                        gateway.complete(AiFeature.BENCHMARK, AiTrigger.USER_ACTION, request(selection.task, model, case, privacy))
                    } catch (e: AiError) {
                        if (e.endsRun()) abortReason = e.message else skipReason = e.message
                        break
                    }
                    // A provider that reports no cost must not be able to slip past the cap.
                    spent += response.costUsd ?: requestEstimate(model, selection.task, case)
                    val score = case.grade(response.content)
                    outcomes += RequestOutcome(
                        score = score ?: FixtureScore.UNPARSED,
                        parsedFirstTry = score != null,
                        latencyMs = response.latencyMs,
                        costUsd = response.costUsd,
                    )
                }
                results += finish(selection.task, model, requests.size, outcomes, skipReason ?: abortReason)
                if (abortReason != null) break@selections
            }
        }
        return BenchmarkReport(results, spent, capUsd, stoppedByCap, abortReason)
    }

    private suspend fun finish(
        task: AiTask,
        model: ModelInfo,
        expected: Int,
        outcomes: List<RequestOutcome>,
        stopReason: String?,
    ): ModelResult = when {
        stopReason != null -> ModelResult.Skipped(task, model.id, stopReason)
        outcomes.none { it.costUsd != null } ->
            ModelResult.Skipped(task, model.id, "OpenRouter did not report a cost for this model")
        outcomes.size < expected -> ModelResult.Skipped(task, model.id, "Not every fixture ran")
        else -> {
            val score = BenchmarkSuite.summarize(task, model.id, outcomes)
            dao.upsertBenchmarks(listOf(toEntity(score, clock.nowMillis())))
            ModelResult.Saved(score)
        }
    }

    private fun request(task: AiTask, model: ModelInfo, case: BenchmarkCase, privacy: AiPrivacy) = AiRequest(
        model = model.id,
        parts = listOf(AiPart.Text(case.user)),
        system = case.system,
        schema = case.schema,
        schemaName = task.name.lowercase(),
        maxTokens = BenchmarkSuite.maxOutputTokens(task),
        privacy = privacy,
    )

    private fun requestEstimate(model: ModelInfo, task: AiTask, case: BenchmarkCase): Double =
        BenchmarkCost.requestUsd(model, BenchmarkCost.inputTokens(case.inputChars), BenchmarkSuite.maxOutputTokens(task))

    /** Problems with the account or the connection hit every model alike; model-specific ones do not. */
    private fun AiError.endsRun(): Boolean = when (this) {
        AiError.NoKey, AiError.Off, is AiError.BudgetReached, is AiError.NotAllowed,
            AiError.Unauthorized, AiError.InsufficientCredits, AiError.RateLimited, is AiError.Network -> true
        AiError.NoEndpointForPrivacy, is AiError.Server, is AiError.BadResponse -> false
    }

    companion object {
        /**
         * One row per (task, model), with an id derived from the pair so a
         * rerun replaces the old result - on this phone and, via sync, on the
         * others - instead of piling up beside it.
         */
        fun toEntity(score: BenchmarkScore, now: Long) = ModelBenchmarkEntity(
            id = seedId("model_benchmark", "${score.task.name}:${score.modelId}"),
            task = score.task.name,
            modelId = score.modelId,
            runAt = now,
            fixtures = score.fixtures,
            accuracy = score.accuracy,
            jsonValidity = score.jsonValidity,
            medianLatencyMs = requireNotNull(score.medianLatencyMs),
            costUsdPerRequest = requireNotNull(score.costUsdPerRequest),
            createdAt = now,
            updatedAt = now,
        )
    }
}
