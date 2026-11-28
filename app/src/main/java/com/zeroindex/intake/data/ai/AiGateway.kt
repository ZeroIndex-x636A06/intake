package com.zeroindex.intake.data.ai

import com.zeroindex.intake.domain.ai.AiFailureKind
import com.zeroindex.intake.domain.ai.AiFeature
import com.zeroindex.intake.domain.ai.AiGuard
import com.zeroindex.intake.domain.ai.AiTask
import com.zeroindex.intake.domain.ai.AiTrigger
import com.zeroindex.intake.domain.ai.GuardRefusal
import com.zeroindex.intake.domain.ai.ModelInfo
import com.zeroindex.intake.domain.ai.ReplyDecode
import com.zeroindex.intake.domain.ai.ReplyRepair
import com.zeroindex.intake.domain.ai.cost.AiBudget
import com.zeroindex.intake.domain.ai.cost.AiSpend
import com.zeroindex.intake.domain.ai.cost.AiUsageRecord
import com.zeroindex.intake.domain.ai.cost.UsagePeriods
import com.zeroindex.intake.domain.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** A parsed answer plus what it cost and which model gave it. */
data class AiOutcome<T>(val value: T, val response: AiResponse)

/**
 * The only way a feature talks to a model. It owns the decisions so no feature can get them wrong on its own:
 *
 * - **Whether** (the guard, [authorize]): every call names an [AiTrigger] and a feature; the master switch
 *   must be on, a key must exist, the trigger must be allowed for that feature (a schedule only for features
 *   the person opted in), and this month's spend must be under the budget cap. Otherwise nothing is sent.
 *   The rest of the app never needs any of this (SPEC §3.3).
 * - **Which model**: the user's choice for that task, else the suggestion.
 * - **How private**: the privacy setting is applied to every request, not left to each caller to remember.
 * - **How patient**: each feature has its own timeout (photos and web search wait longer than text).
 * - **How strict**: a model with strict structured output is held to the schema; one with only JSON mode gets
 *   the schema in the prompt.
 * - **What it cost, and why it failed**: every call that goes out is logged (feature, model, tokens, cost,
 *   success, and the kind of failure; never the prompt or the key) for the usage meter and the budget.
 *
 * Nothing else in the app may call [OpenRouterClient.complete]; `AiAuditTest` fails if something tries.
 */
@Singleton
class AiGateway @Inject constructor(
    private val client: OpenRouterClient,
    private val env: AiEnvironment,
    private val json: Json,
    private val clock: Clock,
) {
    /** Whether AI can be offered right now: a key exists and the master switch is on. */
    fun isConfigured(): Boolean = unavailableReason() == null

    /** Why AI is not on offer (switch off, then no key), or null when it is. A hint for screens, not the gate. */
    fun unavailableReason(): AiError? = when {
        !env.enabledNow() -> AiError.Off
        env.openRouterKey() == null -> AiError.NoKey
        else -> null
    }

    /**
     * The guard. Returns the key when a call by [feature] for [trigger] may go out, else throws and nothing
     * is sent. A missing or disallowed trigger is a programming error: it throws [IllegalStateException] in
     * debug builds so it is found at once, and is refused with [AiError.NotAllowed] in release builds.
     */
    suspend fun authorize(feature: AiFeature, trigger: AiTrigger?): String {
        val policy = env.policy()
        val key = env.openRouterKey()
        val now = clock.nowMillis()
        val budget = AiBudget.status(
            capUsd = policy.monthlyBudgetUsd,
            spentUsd = env.spentSince(UsagePeriods.monthStart(now, clock.zone())),
        )
        val refusal = AiGuard.check(
            feature = feature,
            trigger = trigger,
            enabled = policy.enabled,
            hasKey = key != null,
            optedInSchedules = policy.scheduleOptIns,
            budget = budget,
        )
        when (refusal) {
            null -> Unit
            GuardRefusal.NO_TRIGGER, GuardRefusal.TRIGGER_NOT_ALLOWED -> {
                val detail = "AI call for ${feature.name} with trigger $trigger"
                if (env.strict) throw IllegalStateException("$detail: ${refusal.name}")
                throw AiError.NotAllowed(detail)
            }
            GuardRefusal.SWITCH_OFF -> throw AiError.Off
            GuardRefusal.NOT_OPTED_IN -> throw AiError.NotAllowed("${feature.name} is not switched on")
            GuardRefusal.NO_KEY -> throw AiError.NoKey
            GuardRefusal.BUDGET -> throw AiError.BudgetReached(budget.capUsd, budget.spentUsd)
        }
        return key!!
    }

    /**
     * Runs [feature] and decodes the reply into [T].
     *
     * The reply is read leniently (fences, prose around the object and trailing commas are tolerated, see
     * [ReplyRepair]) and checked against [serializer]. If it still does not fit, the model gets one repair
     * attempt in the same user action: its own reply and the error go back with "Your previous reply did not
     * match the schema: ... Reply with JSON only." A second failure is reported as a plain [AiError.BadResponse];
     * the model's text is never shown. Each request is guarded, billed to the budget and logged on its own, so
     * the repair attempt counts toward spend like any other call.
     */
    suspend fun <T> structured(
        feature: AiFeature,
        trigger: AiTrigger?,
        serializer: KSerializer<T>,
        schema: JsonObject,
        system: String,
        parts: List<AiPart>,
        tools: List<ServerTool> = emptyList(),
        maxTokens: Int? = null,
    ): AiOutcome<T> {
        authorize(feature, trigger)
        val task = feature.task ?: throw IllegalArgumentException("${feature.name} has no model task")
        val model = env.modelFor(task)
        val request = AiRequest(
            model = model,
            parts = parts,
            system = system,
            schema = schema,
            schemaName = task.name.lowercase(),
            tools = tools,
            maxTokens = maxTokens ?: (task.typicalOutputTokens * 2),
            privacy = env.policy().privacy,
            timeout = feature.timeout,
            // Unknown model (an override missing from the cached list) is tried strictly, as before.
            strictSchema = env.priceOf(model)?.structuredOutputs ?: true,
        )
        val (first, usedRequest) = sendWithFallback(feature, trigger, request, task)
        val firstDecode = ReplyRepair.decode(json, serializer, first.content)
        if (firstDecode is ReplyDecode.Ok) {
            settle(feature, trigger, usedRequest, first, success = true)
            return AiOutcome(firstDecode.value, first)
        }
        settle(feature, trigger, usedRequest, first, success = false, failure = AiFailureKind.INVALID_OUTPUT)

        val error = (firstDecode as ReplyDecode.Invalid).error
        val retryRequest = usedRequest.copy(
            parts = parts + AiPart.Text(repairPrompt(first.content, error)),
            // The answer already exists and only needs reshaping: do not pay for a second web search.
            tools = emptyList(),
        )
        val retry = send(feature, trigger, retryRequest)
        val retryDecode = ReplyRepair.decode(json, serializer, retry.content)
        if (retryDecode is ReplyDecode.Invalid) {
            settle(feature, trigger, retryRequest, retry, success = false, failure = AiFailureKind.INVALID_OUTPUT)
            throw AiError.BadResponse(retryDecode.error, raw = retry.content)
        }
        settle(feature, trigger, retryRequest, retry, success = true)
        return AiOutcome((retryDecode as ReplyDecode.Ok).value, retry.copy(costUsd = sum(first.costUsd, retry.costUsd)))
    }

    /**
     * One guarded, logged request with a caller-built [AiRequest] and no decoding: the benchmark, which
     * grades replies itself. The guard and the usage log apply exactly as for [structured].
     */
    suspend fun complete(feature: AiFeature, trigger: AiTrigger?, request: AiRequest): AiResponse {
        val response = send(feature, trigger, request)
        settle(feature, trigger, request, response, success = true)
        return response
    }

    /**
     * [send], and when the chosen model has no provider that meets the privacy setting or accepts what the task
     * sends (the "no endpoints" answer), the next models that do, from [AiEnvironment.fallbackModels]: the
     * same privacy setting and the same request, never relaxed. At most [MAX_MODEL_ATTEMPTS] models are tried;
     * each attempt is logged by [send] like any other. Any other failure is reported at once. Returns the
     * reply and the request that got it, so a repair turn goes to the model that answered.
     */
    private suspend fun sendWithFallback(
        feature: AiFeature,
        trigger: AiTrigger?,
        request: AiRequest,
        task: AiTask,
    ): Pair<AiResponse, AiRequest> {
        var current = request
        val tried = mutableSetOf(request.model)
        var alternatives: List<String>? = null
        while (true) {
            try {
                return send(feature, trigger, current) to current
            } catch (e: AiError) {
                if (e !is AiError.NoEndpointForPrivacy && e !is AiError.NoEndpointForParameters) throw e
                if (tried.size >= MAX_MODEL_ATTEMPTS) throw e
                if (alternatives == null) {
                    alternatives = try {
                        env.fallbackModels(task, tried.toSet())
                    } catch (c: CancellationException) {
                        throw c
                    } catch (_: Exception) {
                        emptyList()
                    }
                }
                val options = alternatives.orEmpty()
                val next = options.firstOrNull { it !in tried } ?: throw e
                tried += next
                current = current.copy(model = next, strictSchema = env.priceOf(next)?.structuredOutputs ?: true)
            }
        }
    }

    suspend fun testKey(key: String): KeyData = client.keyInfo(key)

    /** Guards, sends, and on a failed send logs the attempt. A reply is logged by [settle] once its outcome is known. */
    private suspend fun send(feature: AiFeature, trigger: AiTrigger?, wanted: AiRequest): AiResponse {
        val key = authorize(feature, trigger)
        // Every request sets require_parameters, so one the model's providers do not list is not sent at all.
        val request = if (!env.priceOf(wanted.model).acceptsTemperature()) wanted.copy(temperature = null) else wanted
        try {
            return client.complete(key, request)
        } catch (e: CancellationException) {
            // The person pressed Cancel: the OkHttp call was cancelled with the coroutine. It may still have been billed.
            logFailed(feature, trigger, request, AiFailureKind.CANCELLED)
            throw e
        } catch (e: AiError) {
            logFailed(feature, trigger, request, e.failureKind)
            throw e
        }
    }

    private fun ModelInfo?.acceptsTemperature() = this?.acceptsTemperature ?: true

    private suspend fun logFailed(feature: AiFeature, trigger: AiTrigger?, request: AiRequest, kind: AiFailureKind) {
        withContext(NonCancellable) {
            log(
                AiUsageRecord(
                    feature = feature, model = request.model, trigger = trigger!!, promptTokens = null,
                    completionTokens = null, costUsd = 0.0, costReported = false, success = false,
                    at = clock.nowMillis(), failure = kind,
                ),
            )
        }
    }

    private suspend fun settle(
        feature: AiFeature,
        trigger: AiTrigger?,
        request: AiRequest,
        response: AiResponse,
        success: Boolean,
        failure: AiFailureKind? = null,
    ) {
        val model = env.priceOf(response.model) ?: env.priceOf(request.model)
        val cost = AiSpend.resolve(response.costUsd, response.promptTokens, response.completionTokens, model, feature.profile)
        log(
            AiUsageRecord(
                feature = feature,
                model = response.model.ifBlank { request.model },
                trigger = trigger!!,
                promptTokens = response.promptTokens,
                completionTokens = response.completionTokens,
                costUsd = cost.usd,
                costReported = cost.reported,
                success = success,
                at = clock.nowMillis(),
                failure = failure,
            ),
        )
    }

    /** The meter must never break a feature: a failed write is dropped, a cancellation is passed on. */
    private suspend fun log(record: AiUsageRecord) {
        try {
            env.record(record, clock.nowMillis())
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Usage logging is best effort.
        }
    }

    private fun sum(a: Double?, b: Double?): Double? = if (a == null && b == null) null else (a ?: 0.0) + (b ?: 0.0)

    companion object {
        private const val MAX_ECHO_CHARS = 12_000

        /** The chosen model plus at most two others, when a model has no provider for the privacy setting. */
        const val MAX_MODEL_ATTEMPTS = 3

        /** The repair turn: the model's own reply, then the error, then the instruction. */
        fun repairPrompt(previousReply: String, error: String): String =
            "Here is your previous reply:\n${previousReply.take(MAX_ECHO_CHARS)}\n\n" +
                "Your previous reply did not match the schema: $error. Reply with JSON only."

        /**
         * Decodes a reply into [T], or null if it does not match. Shared with
         * the benchmark so "parsed" means the same thing in both.
         */
        fun <T> decodeReply(json: Json, serializer: KSerializer<T>, content: String): T? =
            (ReplyRepair.decode(json, serializer, content) as? ReplyDecode.Ok)?.value

        /** Pulls the JSON object out of a reply that may be fenced or prefixed with prose. */
        fun extractJson(content: String): String? = ReplyRepair.extractJson(content)
    }
}
