package com.zeroindex.intake.domain.hydration

/**
 * One line that says what the day's target is made of, e.g. "Base 103.1 fl oz + workout +47.3 + heat +10.1". Null when
 * nothing was added to the base. [format] writes the base with its unit; [formatNumber] writes each adjustment bare, so
 * the line stays short. Beyond two adjustments the rest are counted ("+ 1 more"); the card's full list names them all.
 */
fun TargetBreakdown.summaryLine(format: (Double) -> String, formatNumber: (Double) -> String): String? {
    if (adjustments.isEmpty()) return null
    val shown = adjustments.take(2)
    val parts = mutableListOf("Base ${format(baseMl)}")
    shown.forEach { parts += "${it.short} +${formatNumber(it.ml)}" }
    val more = adjustments.size - shown.size
    if (more > 0) parts += "$more more"
    return parts.joinToString(" + ")
}
