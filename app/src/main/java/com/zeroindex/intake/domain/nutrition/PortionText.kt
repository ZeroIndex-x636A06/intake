package com.zeroindex.intake.domain.nutrition

/** Words for a food portion that never say the same amount twice. */
object PortionText {

    /**
     * "1 cup (240 g)", but just "100 g" when the portion's own label already is the amount ("100 g (100 g)" read as a mistake).
     * [amountText] is the portion's weight written in the person's unit. A label that already carries a bracket is kept as it is.
     */
    fun withAmount(label: String, amountText: String): String {
        fun norm(s: String) = s.filterNot { it.isWhitespace() }.lowercase()
        val l = label.trim()
        if (l.isEmpty()) return amountText
        if (norm(l) == norm(amountText) || l.contains("(")) return l
        return "$l ($amountText)"
    }
}
