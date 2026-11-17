package com.zeroindex.intake.data.ai.benchmark

import com.zeroindex.intake.data.ai.AiError
import com.zeroindex.intake.data.ai.AiPart
import com.zeroindex.intake.data.ai.AiPrivacy
import com.zeroindex.intake.data.ai.AiRequest
import com.zeroindex.intake.data.ai.AiResponse
import com.zeroindex.intake.data.ai.AiGateway
import com.zeroindex.intake.data.db.dao.AiInsightDao
import com.zeroindex.intake.data.db.entity.ModelBenchmarkEntity
import com.zeroindex.intake.data.db.entity.seedId
import com.zeroindex.intake.domain.ai.AiFailureKind
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
import kotlinx.coroutines.delay
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
    /** Set while the run is waiting, for example "Rate limited, waiting 15 s". */
    val note: String? = null,
)

/**
 * How the run paces itself. One request at a time with a small gap keeps a provider's rate limit from being
 * hit in the first place; a 429 that still gets through is waited out, [rateLimitWaitsMs] long each time (or
 * the provider's own `Retry-After`), a few times, before only that model is skipped.
 */
data class BenchmarkPacing(
    val gapMs: Long = 1_000,
    val rateLimitWaitsMs: List<Long> = listOf(5_000, 15_000, 45_000),
    val cooldownAfterSkipMs: Long = 10_000,
    val sleep: suspend (Long) -> Unit = { delay(it) },
) {
    companion object {
        /** The longest we will wait on one `Retry-After`. */
        const val MAX_WAIT_MS = 60_000L
        val NONE = BenchmarkPacing(gapMs = 0, rateLimitWaitsMs = listOf(0, 0, 0), cooldownAfterSkipMs = 0, sleep = {})
    }
}

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
    data class Skipped(
        override val task: AiTask,
        override val modelId: String,
        val reason: String,
        /** Why, in the usage meter's terms, when it was a failed call; null for "stopped at the cap" and the like. */
        val kind: AiFailureKind? = null,
    ) : ModelResult
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
     *
     * A rate limit (HTTP 429) is transient and belongs to one model: it is waited out per [pacing], and only
     * after that does the model get skipped, with the run carrying on to the next. Only a rejected key, no
     * credit, the master switch or being offline ends the whole run.
     */
    suspend fun run(
        selections: List<BenchmarkSelection>,
        capUsd: Double,
        privacy: AiPrivacy,
        onProgress: (BenchmarkProgress) -> Unit,
        pacing: BenchmarkPacing = BenchmarkPacing(),
    ): BenchmarkReport {
        require(capUsd > 0) { "The spend cap must be above zero" }
        val results = mutableListOf<ModelResult>()
        var spent = 0.0
        var stoppedByCap = false
        var abortReason: String? = null
        var first = true

        selections@ for (selection in selections) {
            val requests = withContext(Dispatchers.IO) { cases.forTask(selection.task) }
            for (model in selection.models) {
                if (spent >= capUsd) {
                    stoppedByCap = true
                    break@selections
                }
                val outcomes = mutableListOf<RequestOutcome>()
                var skip: Skip? = null
                for (case in requests) {
                    if (spent >= capUsd) {
                        stoppedByCap = true
                        skip = Skip("Stopped at the spend cap before it finished", null)
                        break
                    }
                    if (!first) pacing.sleep(pacing.gapMs)
                    first = false
                    onProgress(BenchmarkProgress(selection.task, model.id, outcomes.size, requests.size, spent))
                    val attempt = sendWithPatience(selection, model, case, privacy, pacing) { note ->
                        onProgress(BenchmarkProgress(selection.task, model.id, outcomes.size, requests.size, spent, note))
                    }
                    val response = attempt.response
                    if (response == null) {
                        val error = attempt.error!!
                        if (error.endsRun()) abortReason = error.message else skip = Skip(attempt.reason!!, error.failureKind)
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
                results += finish(
                    selection.task, model, requests.size, outcomes,
                    skip ?: abortReason?.let { Skip(it, null) },
                )
                if (abortReason != null) break@selections
                // A model that was rate limited out leaves the provider needing a moment before the next one.
                if (skip?.kind == AiFailureKind.RATE_LIMITED) pacing.sleep(pacing.cooldownAfterSkipMs)
            }
        }
        return BenchmarkReport(results, spent, capUsd, stoppedByCap, abortReason)
    }

    private class Skip(val reason: String, val kind: AiFailureKind?)

    /** The outcome of one request: a response, or the error and the words to report it with. */
    private class Attempt(val response: AiResponse?, val error: AiError?, val reason: String?)

    /** Sends one request, waiting out a rate limit a few times before giving up on this model. */
    private suspend fun sendWithPatience(
        selection: BenchmarkSelection,
        model: ModelInfo,
        case: BenchmarkCase,
        privacy: AiPrivacy,
        pacing: BenchmarkPacing,
        onNote: (String) -> Unit,
    ): Attempt {
        var waits = 0
        while (true) {
            try {
                val response = gateway.complete(AiFeature.BENCHMARK, AiTrigger.USER_ACTION, request(selection.task, model, case, privacy))
                return Attempt(response, null, null)
            } catch (e: AiError.RateLimited) {
                if (waits >= pacing.rateLimitWaitsMs.size) {
                    return Attempt(null, e, "Rate limited by the provider, even after waiting")
                }
                val wait = (e.retryAfterMs ?: pacing.rateLimitWaitsMs[waits]).coerceAtMost(BenchmarkPacing.MAX_WAIT_MS)
                onNote("Rate limited, waiting ${(wait + 999) / 1000} s")
                pacing.sleep(wait)
                waits++
            } catch (e: AiError) {
                return Attempt(null, e, e.message)
            }
        }
    }

    private suspend fun finish(
        task: AiTask,
        model: ModelInfo,
        expected: Int,
        outcomes: List<RequestOutcome>,
        stop: Skip?,
    ): ModelResult = when {
        stop != null -> ModelResult.Skipped(task, model.id, stop.reason, stop.kind)
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
        timeout = AiFeature.BENCHMARK.timeout,
        strictSchema = model.structuredOutputs,
    )

    private fun requestEstimate(model: ModelInfo, task: AiTask, case: BenchmarkCase): Double =
        BenchmarkCost.requestUsd(model, BenchmarkCost.inputTokens(case.inputChars), BenchmarkSuite.maxOutputTokens(task))

    /**
     * Problems with the account or the connection hit every model alike; model-specific ones do not. A rate
     * limit is the model's provider being busy, not the account, so it is never in this list.
     */
    private fun AiError.endsRun(): Boolean = when (this) {
        AiError.NoKey, AiError.Off, is AiError.BudgetReached, is AiError.NotAllowed,
            AiError.Unauthorized, AiError.InsufficientCredits, is AiError.Network -> true
        is AiError.RateLimited, AiError.Timeout, is AiError.Refused, is AiError.NoModel,
            AiError.NoEndpointForPrivacy, AiError.NoEndpointForParameters, is AiError.Server, is AiError.BadResponse -> false
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
