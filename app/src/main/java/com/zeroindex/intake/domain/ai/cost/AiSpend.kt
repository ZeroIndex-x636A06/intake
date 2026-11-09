package com.zeroindex.intake.domain.ai.cost

import com.zeroindex.intake.domain.ai.AiTask
import com.zeroindex.intake.domain.ai.ModelInfo
import com.zeroindex.intake.domain.ai.TokenProfile

data class ResolvedCost(val usd: Double, val reported: Boolean)

/** What a finished call cost: the provider's own figure when it gave one, else catalog prices times tokens. */
object AiSpend {
    fun resolve(
        reportedUsd: Double?,
        promptTokens: Int?,
        completionTokens: Int?,
        model: ModelInfo?,
        profile: TokenProfile,
    ): ResolvedCost {
        if (reportedUsd != null && reportedUsd >= 0.0 && reportedUsd.isFinite()) {
            return ResolvedCost(AiMoney.round(reportedUsd), reported = true)
        }
        // No price known (or a router with a variable one) and no reported cost: nothing can be said, so it
        // counts as zero and is marked estimated.
        if (model == null || !model.hasKnownPrice) return ResolvedCost(0.0, reported = false)
        val input = promptTokens ?: profile.inputTokens
        val output = completionTokens ?: profile.outputTokens
        return ResolvedCost(AiMoney.round(callUsd(model, input, output, profile)), reported = false)
    }

    /** Tokens at the model's rates, plus per-request, per-image and per-search charges. */
    fun callUsd(model: ModelInfo, inputTokens: Int, outputTokens: Int, profile: TokenProfile): Double =
        (inputTokens * model.promptPerMillion + outputTokens * model.completionPerMillion) / 1e6 +
            profile.images * model.perImage +
            model.perRequest +
            profile.searches * AiTask.WEB_SEARCH_USD
}
