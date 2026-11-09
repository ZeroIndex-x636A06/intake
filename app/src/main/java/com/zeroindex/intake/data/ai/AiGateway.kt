package com.zeroindex.intake.data.ai

import com.zeroindex.intake.domain.ai.AiFeature
import com.zeroindex.intake.domain.ai.AiGuard
import com.zeroindex.intake.domain.ai.AiTrigger
import com.zeroindex.intake.domain.ai.GuardRefusal
import com.zeroindex.intake.domain.ai.cost.AiBudget
import com.zeroindex.intake.domain.ai.cost.AiSpend
import com.zeroindex.intake.domain.ai.cost.AiUsageRecord
import com.zeroindex.intake.domain.ai.cost.UsagePeriods
import com.zeroindex.intake.domain.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
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
 * - **What it cost**: every call that goes out is logged (feature, model, tokens, cost, success; never the
 *   prompt or the key) for the usage meter and the budget.
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
     * Runs [feature] and decodes the reply into [T]. A reply that does not parse is retried once with the
     * parse error fed back - models occasionally wrap JSON in prose even under a schema - and a second
     * failure is reported rather than guessed at. Each request is guarded and logged on its own.
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
        val request = AiRequest(
            model = env.modelFor(task),
            parts = parts,
            system = system,
            schema = schema,
            schemaName = task.name.lowercase(),
            tools = tools,
            maxTokens = maxTokens ?: (task.typicalOutputTokens * 2),
            privacy = env.policy().privacy,
        )
        val first = send(feature, trigger, request)
        val firstValue = decodeReply(json, serializer, first.content)
        settle(feature, trigger, request, first, firstValue != null)
        if (firstValue != null) return AiOutcome(firstValue, first)

        val retryRequest = request.copy(
            parts = parts + AiPart.Text(
                "Your previous reply could not be parsed. Reply with only a JSON object matching the schema.",
            ),
            tools = emptyList(),
        )
        val retry = send(feature, trigger, retryRequest)
        val value = decodeReply(json, serializer, retry.content)
        settle(feature, trigger, retryRequest, retry, value != null)
        if (value == null) throw AiError.BadResponse("it did not match the expected format", raw = retry.content)
        return AiOutcome(value, retry.copy(costUsd = sum(first.costUsd, retry.costUsd)))
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

    suspend fun testKey(key: String): KeyData = client.keyInfo(key)

    /** Guards, sends, and on a failed send logs the attempt. A reply is logged by [settle] once its outcome is known. */
    private suspend fun send(feature: AiFeature, trigger: AiTrigger?, request: AiRequest): AiResponse {
        val key = authorize(feature, trigger)
        try {
            return client.complete(key, request)
        } catch (e: AiError) {
            log(
                AiUsageRecord(
                    feature = feature, model = request.model, trigger = trigger!!, promptTokens = null,
                    completionTokens = null, costUsd = 0.0, costReported = false, success = false, at = clock.nowMillis(),
                ),
            )
            throw e
        }
    }

    private suspend fun settle(feature: AiFeature, trigger: AiTrigger?, request: AiRequest, response: AiResponse, success: Boolean) {
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
        /**
         * Decodes a reply into [T], or null if it does not match. Shared with
         * the benchmark so "parsed" means the same thing in both.
         */
        fun <T> decodeReply(json: Json, serializer: KSerializer<T>, content: String): T? {
            val text = extractJson(content) ?: return null
            return try {
                json.decodeFromString(serializer, text)
            } catch (_: SerializationException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
        }

        /** Pulls the JSON object out of a reply that may be fenced or prefixed with prose. */
        fun extractJson(content: String): String? {
            val trimmed = content.trim()
            if (trimmed.startsWith("{")) return trimmed
            val start = trimmed.indexOf('{')
            val end = trimmed.lastIndexOf('}')
            return if (start >= 0 && end > start) trimmed.substring(start, end + 1) else null
        }
    }
}
