package com.zeroindex.intake.domain.recipe

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Scales a recipe's ingredient lines to a number of servings, for cook mode's checklist. Only the amount at the start
 * of a line is changed ("1 1/2 cups flour" to "3 cups flour"); the rest of the text is kept as written. A line with no
 * amount ("salt to taste", "fresh basil") is returned as it is. Pure and total; no network, no model.
 */
object IngredientScaling {
    private const val FRAC = """\d+\s+\d+/\d+|\d+/\d+|\d+(?:\.\d+)?"""
    private val LEADING = Regex("""^($FRAC)(?:(\s*(?:-|–|—|to)\s*)($FRAC))?""")
    private val BULLET = Regex("""^\s*[-•*]\s+""")
    private val PAREN_WEIGHT = Regex(
        """\((\s*(?:about\s+|approx\.?\s*|~\s*)?)(\d+(?:\.\d+)?)(\s*(?:g|gr|grams?|kg|oz|ounces?|lbs?|pounds?)\s*)\)""",
        RegexOption.IGNORE_CASE,
    )

    /** Servings wanted over the servings the recipe makes; 1.0 when either is not a positive number. */
    fun factor(wanted: Int, recipeServings: Int): Double =
        if (wanted > 0 && recipeServings > 0) wanted.toDouble() / recipeServings else 1.0

    fun scaleLine(line: String, factor: Double): String {
        if (!factor.isFinite() || factor <= 0.0 || abs(factor - 1.0) < 1e-9) return line
        val normalised = IngredientLineParser.normalise(line)
        val bullet = BULLET.find(normalised)?.value.orEmpty()
        val body = normalised.removePrefix(bullet)
        val m = LEADING.find(body) ?: return line
        // "2% milk" starts with a number that is not an amount.
        if (body.getOrNull(m.range.last + 1) == '%') return line
        val a = fraction(m.groupValues[1]) ?: return line
        val hasRange = m.groupValues[3].isNotEmpty()
        val b = if (hasRange) fraction(m.groupValues[3]) ?: return line else null

        val amount = buildString {
            append(formatAmount(a * factor))
            if (b != null) {
                append(m.groupValues[2])
                append(formatAmount(b * factor))
            }
        }
        var rest = body.substring(m.range.last + 1)
        // "8 oz (220 g) pasta" restates its own amount, so the bracket scales; "2 (14 oz) cans" does not.
        val unit = IngredientAmounts.parse(line).unit
        if (!IngredientAmounts.isContainerUnit(unit)) {
            rest = PAREN_WEIGHT.replace(rest) { p ->
                val g = p.groupValues[2].toDoubleOrNull() ?: return@replace p.value
                "(" + p.groupValues[1] + formatAmount(g * factor) + p.groupValues[3] + ")"
            }
        }
        return bullet + amount + rest
    }

    fun scaleAll(lines: List<String>, factor: Double): List<String> = lines.map { scaleLine(it, factor) }

    private val STEPS = listOf(
        0.0 to "", 0.125 to "1/8", 0.25 to "1/4", 1.0 / 3 to "1/3", 0.375 to "3/8", 0.5 to "1/2",
        0.625 to "5/8", 2.0 / 3 to "2/3", 0.75 to "3/4", 0.875 to "7/8", 1.0 to "",
    )

    /** 1.5 gives "1 1/2", 0.333 gives "1/3", 2.0 gives "2", 7.43 gives "7.4". Kitchen fractions, else one decimal. */
    fun formatAmount(value: Double): String {
        if (!value.isFinite() || value < 0) return ""
        val whole = floor(value)
        val frac = value - whole
        val nearest = STEPS.minByOrNull { abs(it.first - frac) }!!
        if (abs(nearest.first - frac) < 0.02 && value >= 0.1) {
            val w = whole.toLong() + if (nearest.first == 1.0) 1 else 0
            val f = nearest.second
            return when {
                f.isEmpty() -> w.toString()
                w == 0L -> f
                else -> "$w $f"
            }
        }
        return if (value >= 10) {
            value.roundToInt().toString()
        } else {
            val t = (value * 10).roundToInt() / 10.0
            if (t == floor(t)) t.toInt().toString() else t.toString()
        }
    }

    private fun fraction(s: String): Double? {
        val t = s.trim()
        Regex("""(\d+)\s+(\d+)/(\d+)""").matchEntire(t)?.let {
            val d = it.groupValues[3].toDouble()
            return if (d == 0.0) null else it.groupValues[1].toDouble() + it.groupValues[2].toDouble() / d
        }
        Regex("""(\d+)/(\d+)""").matchEntire(t)?.let {
            val d = it.groupValues[2].toDouble()
            return if (d == 0.0) null else it.groupValues[1].toDouble() / d
        }
        return t.toDoubleOrNull()
    }
}
