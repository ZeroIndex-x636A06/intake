package com.zeroindex.intake.domain.ai.cost

import com.zeroindex.intake.domain.ai.AiFeature
import com.zeroindex.intake.domain.ai.AiTask
import com.zeroindex.intake.domain.ai.ModelInfo

/** Average request size measured from the person's own usage log. */
data class MeasuredUsage(val calls: Int, val avgPromptTokens: Double, val avgCompletionTokens: Double)

/** Where a feature's per-call figure came from, best first. */
enum class CostSource(val label: String) {
    MEASURED("Measured from your usage"),
    BENCHMARK("Benchmark cost"),
    ESTIMATE("Built-in estimate"),
}

data class FeatureCostInput(
    val feature: AiFeature,
    /** False for an opt-in feature whose switch is off: it counts as zero uses. */
    val enabled: Boolean,
    val usesPerDay: Double,
    val model: ModelInfo?,
    val measured: MeasuredUsage? = null,
    /** The model's measured cost per request in the benchmark, if it was benchmarked. */
    val benchmarkUsdPerCall: Double? = null,
)

data class FeatureCost(
    val feature: AiFeature,
    val model: ModelInfo?,
    val source: CostSource,
    val inputTokens: Int,
    val outputTokens: Int,
    /** Null when there is no model or no price to work from. */
    val perCallUsd: Double?,
    /** Uses per day after the on/off switch: zero when the feature is off. */
    val usesPerDay: Double,
    val perDayUsd: Double,
    val perMonthUsd: Double,
    val enabled: Boolean,
) {
    val priced: Boolean get() = perCallUsd != null
}

data class CostSummary(
    val rows: List<FeatureCost>,
    val perDayUsd: Double,
    val perMonthUsd: Double,
    /** Features that are on and used but could not be priced (no model chosen or no catalog yet). */
    val unpriced: List<AiFeature>,
)

/**
 * The cost estimate, the same arithmetic as `docs/AI-COSTS.md`:
 *
 *     per call  = input tokens x input price + output tokens x output price
 *                 + per-request fee + images x image fee + searches x search fee
 *     per day   = uses per day x per call          (0 when the feature is off)
 *     per month = per day x 30
 *
 * Tokens come from the person's own usage log once a feature has [MIN_MEASURED_CALLS] successful calls
 * with token counts; failing that, the benchmark's measured cost per request for the chosen model; failing
 * that, the built-in typical request size. A measured figure always beats a default.
 */
object AiCostModel {
    const val MIN_MEASURED_CALLS = 3
    const val DAYS_PER_MONTH = 30

    fun estimate(inputs: List<FeatureCostInput>): CostSummary {
        val rows = inputs.filter { it.feature.inEstimate }.map(::feature)
        return CostSummary(
            rows = rows,
            perDayUsd = AiMoney.round(rows.sumOf { it.perDayUsd }),
            perMonthUsd = AiMoney.round(rows.sumOf { it.perMonthUsd }),
            unpriced = rows.filter { it.enabled && it.usesPerDay > 0 && !it.priced }.map { it.feature },
        )
    }

    fun feature(input: FeatureCostInput): FeatureCost {
        val profile = input.feature.profile
        val model = input.model
        val measured = input.measured?.takeIf { it.calls >= MIN_MEASURED_CALLS }
        val benchmark = input.benchmarkUsdPerCall?.takeIf { it >= 0 }

        val source = when {
            measured != null -> CostSource.MEASURED
            model != null && benchmark != null -> CostSource.BENCHMARK
            else -> CostSource.ESTIMATE
        }
        val inTokens = if (measured != null) Math.round(measured.avgPromptTokens).toInt() else profile.inputTokens
        val outTokens = if (measured != null) Math.round(measured.avgCompletionTokens).toInt() else profile.outputTokens

        val perCall: Double? = when {
            model == null -> null
            // The benchmark sends plain requests, so the web searches a feature adds are billed on top of it.
            source == CostSource.BENCHMARK -> benchmark!! + profile.searches * AiTask.WEB_SEARCH_USD
            else -> AiSpend.callUsd(model, inTokens, outTokens, profile)
        }?.let(AiMoney::round)

        val uses = if (input.enabled) input.usesPerDay.coerceAtLeast(0.0) else 0.0
        val perDay = AiMoney.round((perCall ?: 0.0) * uses)
        return FeatureCost(
            feature = input.feature,
            model = model,
            source = source,
            inputTokens = inTokens,
            outputTokens = outTokens,
            perCallUsd = perCall,
            usesPerDay = uses,
            perDayUsd = perDay,
            perMonthUsd = AiMoney.round(perDay * DAYS_PER_MONTH),
            enabled = input.enabled,
        )
    }
}
