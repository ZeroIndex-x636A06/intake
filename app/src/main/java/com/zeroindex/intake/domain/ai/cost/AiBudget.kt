package com.zeroindex.intake.domain.ai.cost

enum class BudgetLevel { OK, NOTICE, BLOCKED }

/** Where this month's spend stands against the cap the person set. */
data class BudgetStatus(val capUsd: Double, val spentUsd: Double) {
    val level: BudgetLevel
        get() = when {
            capUsd <= 0.0 || spentUsd >= capUsd -> BudgetLevel.BLOCKED
            spentUsd >= capUsd * AiBudget.NOTICE_AT -> BudgetLevel.NOTICE
            else -> BudgetLevel.OK
        }

    /** 0..1 for a progress bar; a cap of zero or less reads as full. */
    val fraction: Double get() = if (capUsd <= 0.0) 1.0 else (spentUsd / capUsd).coerceIn(0.0, 1.0)

    val remainingUsd: Double get() = (capUsd - spentUsd).coerceAtLeast(0.0)

    val blocked: Boolean get() = level == BudgetLevel.BLOCKED
}

/**
 * The monthly cap: calls are allowed until the month's spend reaches it, a notice shows from 80%, and the
 * next calendar month starts again from zero. The person can raise the cap at any time.
 */
object AiBudget {
    const val DEFAULT_CAP_USD = 2.0
    const val NOTICE_AT = 0.8

    fun status(capUsd: Double, spentUsd: Double): BudgetStatus = BudgetStatus(capUsd, spentUsd.coerceAtLeast(0.0))

    /** A typed cap, or null when it is not a positive amount. */
    fun parseCap(text: String): Double? =
        text.trim().removePrefix("$").trim().toDoubleOrNull()?.takeIf { it > 0 && it.isFinite() }?.let { AiMoney.round(it) }
}
