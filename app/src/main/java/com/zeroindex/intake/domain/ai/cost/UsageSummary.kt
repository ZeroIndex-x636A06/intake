package com.zeroindex.intake.domain.ai.cost

import com.zeroindex.intake.domain.ai.AiFeature
import com.zeroindex.intake.domain.ai.AiTrigger
import java.time.ZoneId

/** One model call as the usage log keeps it. Never holds a prompt, a reply or a key. */
data class AiUsageRecord(
    val feature: AiFeature,
    val model: String,
    val trigger: AiTrigger,
    val promptTokens: Int?,
    val completionTokens: Int?,
    val costUsd: Double,
    /** True when OpenRouter reported the cost; false when it was worked out from catalog prices. */
    val costReported: Boolean,
    val success: Boolean,
    val at: Long,
)

data class PeriodTotals(val calls: Int, val usd: Double) {
    operator fun plus(o: PeriodTotals) = PeriodTotals(calls + o.calls, AiMoney.round(usd + o.usd))

    companion object {
        val ZERO = PeriodTotals(0, 0.0)
    }
}

data class FeatureUsage(val feature: AiFeature, val today: PeriodTotals, val week: PeriodTotals, val month: PeriodTotals)

data class UsageSummary(
    val features: List<FeatureUsage>,
    val today: PeriodTotals,
    val week: PeriodTotals,
    val month: PeriodTotals,
)

/** The "AI usage" meter's arithmetic. */
object UsageSummarizer {

    /**
     * Per-feature totals for today, this week and this month. Calls logged before [resetAt] are left out:
     * "Reset counters" only moves that line, it never removes a row, so history stays on the phone.
     * Features with no calls in the month are omitted.
     */
    fun summarize(records: List<AiUsageRecord>, nowMillis: Long, zone: ZoneId, resetAt: Long = 0L): UsageSummary {
        val day = UsagePeriods.dayStart(nowMillis, zone)
        val week = UsagePeriods.weekStart(nowMillis, zone)
        val month = UsagePeriods.monthStart(nowMillis, zone)
        val live = records.filter { it.at >= resetAt && it.at <= nowMillis }

        fun totals(rows: List<AiUsageRecord>, from: Long) =
            rows.filter { it.at >= from }.let { PeriodTotals(it.size, AiMoney.round(it.sumOf { r -> r.costUsd })) }

        val perFeature = AiFeature.entries.mapNotNull { f ->
            val rows = live.filter { it.feature == f }
            val m = totals(rows, month)
            if (m.calls == 0) null else FeatureUsage(f, totals(rows, day), totals(rows, week), m)
        }
        return UsageSummary(
            features = perFeature,
            today = totals(live, day),
            week = totals(live, week),
            month = totals(live, month),
        )
    }

    /**
     * What counts against the budget: everything logged this calendar month, whatever "Reset counters" did.
     * Resetting the display must not be a way around the cap.
     */
    fun monthSpend(records: List<AiUsageRecord>, nowMillis: Long, zone: ZoneId): Double {
        val month = UsagePeriods.monthStart(nowMillis, zone)
        return AiMoney.round(records.filter { it.at in month..nowMillis }.sumOf { it.costUsd })
    }

    /** Average request size of a feature over its successful calls that reported tokens, or null with none. */
    fun measured(records: List<AiUsageRecord>, feature: AiFeature): MeasuredUsage? {
        val rows = records.filter { it.feature == feature && it.success && it.promptTokens != null && it.completionTokens != null }
        if (rows.isEmpty()) return null
        return MeasuredUsage(
            calls = rows.size,
            avgPromptTokens = rows.sumOf { it.promptTokens!! }.toDouble() / rows.size,
            avgCompletionTokens = rows.sumOf { it.completionTokens!! }.toDouble() / rows.size,
        )
    }
}
