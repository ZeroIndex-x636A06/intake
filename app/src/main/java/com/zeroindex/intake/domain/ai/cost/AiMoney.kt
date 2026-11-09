package com.zeroindex.intake.domain.ai.cost

import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor

/** Rounding and display for the small dollar amounts AI calls cost. */
object AiMoney {

    /** Costs are kept to a millionth of a dollar, which removes floating point noise without losing real cents. */
    fun round(usd: Double): Double = floor(usd * 1_000_000.0 + 0.5) / 1_000_000.0

    /**
     * How to show an amount: cents once it is ten cents or more, then more places as it gets smaller, so a
     * four-hundredth of a cent photo does not read as "$0.00". Anything above zero but under $0.0001 says so.
     */
    fun format(usd: Double): String {
        val v = round(usd)
        val a = abs(v)
        val sign = if (v < 0) "-" else ""
        return when {
            a == 0.0 -> "\$0.00"
            a >= 0.1 -> sign + "\$" + String.format(Locale.US, "%.2f", a)
            a >= 0.01 -> sign + "\$" + String.format(Locale.US, "%.3f", a)
            a >= 0.0001 -> sign + "\$" + String.format(Locale.US, "%.4f", a)
            else -> "<\$0.0001"
        }
    }
}
