package com.zeroindex.intake.domain.food

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * A food's serving in words, for the food page: "1 packet (43 g)", "1 serving (43 g)", "1.5 servings (64.5 g)".
 * Pure. The grams always come from the food's serving size, never from a bracket in the label.
 *
 * - A label with a count and a unit keeps its unit: "3 oz (85 g)" stays "3 oz (85 g)".
 * - A bare weight ("43g", "43 g") or no label at all becomes "1 serving (43 g)".
 * - Other counts are pluralised: "2 packets (86 g)", "1.5 servings (64.5 g)".
 */
object ServingText {

    /** [count] is the words for the servings ("1 packet", "1.5 servings"); [grams] is the weight under them. */
    data class Phrase(val count: String, val grams: Double) {
        /** Words and weight together: "1 packet (43 g)". */
        val full: String get() = "$count (${number(grams, 1)} g)"
    }

    private val PARENTHESES = Regex("""\([^)]*\)""")
    private val BARE_WEIGHT = Regex("""^\d+(?:[.,]\d+)?\s*(?:g|gr|grams?|ml)$""", RegexOption.IGNORE_CASE)
    private val COUNTED = Regex("""^(\d+(?:\.\d+)?|\d+/\d+)\s+(.+)$""")

    /** Units written the same in the singular and the plural. */
    private val UNCHANGING = setOf("g", "ml", "oz", "kg", "mg", "lb", "tbsp", "tsp", "cm", "kcal")

    /**
     * The phrase for [servings] of a food whose one serving weighs [servingGrams]. [label] is the food's serving label;
     * null or blank means "serving".
     */
    fun of(label: String?, servingGrams: Double, servings: Double = 1.0): Phrase {
        val grams = servingGrams.finiteOrZero().coerceAtLeast(0.0)
        val count = servings.finiteOrZero().coerceAtLeast(0.0)
        val (unitCount, unit) = unitOf(label)
        val quantity = unitCount * count
        val words = "${number(quantity, 2)} ${unitWords(unit, quantity)}"
        return Phrase(count = words, grams = grams * count)
    }

    /** The count and the unit of one serving, from the label with its bracketed weight removed. */
    private fun unitOf(label: String?): Pair<Double, String> {
        val stripped = label.orEmpty().replace(PARENTHESES, " ").replace(Regex("""\s+"""), " ").trim()
        if (stripped.isEmpty() || BARE_WEIGHT.matches(stripped)) return 1.0 to "serving"
        val m = COUNTED.matchEntire(stripped)
        if (m == null) return 1.0 to stripped.lowercase()
        val count = parseCount(m.groupValues[1]) ?: return 1.0 to stripped.lowercase()
        return count to m.groupValues[2].trim().lowercase()
    }

    private fun parseCount(raw: String): Double? {
        if ('/' in raw) {
            val (a, b) = raw.split('/').map { it.toDoubleOrNull() ?: return null }
            return if (b > 0) a / b else null
        }
        return raw.toDoubleOrNull()?.takeIf { it > 0 }
    }

    /** The unit in the form its quantity needs: "serving" for 1, "servings" otherwise; abbreviations stay as they are. */
    private fun unitWords(unit: String, quantity: Double): String {
        val singular = singular(unit)
        if (abs(quantity - 1.0) < 0.005) return singular
        return plural(singular)
    }

    private fun singular(unit: String): String {
        val last = unit.substringAfterLast(' ')
        val head = unit.substringBeforeLast(' ', missingDelimiterValue = "")
        val fixed = when {
            last in UNCHANGING -> last
            last.endsWith("ies") && last.length > 3 -> last.dropLast(3) + "y"
            last.endsWith("sses") || last.endsWith("ches") || last.endsWith("shes") || last.endsWith("xes") -> last.dropLast(2)
            last.endsWith("s") && !last.endsWith("ss") && last.length > 1 -> last.dropLast(1)
            else -> last
        }
        return if (head.isEmpty()) fixed else "$head $fixed"
    }

    private fun plural(unit: String): String {
        val last = unit.substringAfterLast(' ')
        val head = unit.substringBeforeLast(' ', missingDelimiterValue = "")
        val fixed = when {
            last in UNCHANGING -> last
            last.endsWith("s") || last.endsWith("x") || last.endsWith("z") || last.endsWith("ch") || last.endsWith("sh") -> last + "es"
            last.length > 1 && last.endsWith("y") && last[last.length - 2] !in "aeiou" -> last.dropLast(1) + "ies"
            else -> last + "s"
        }
        return if (head.isEmpty()) fixed else "$head $fixed"
    }

    private fun Double.finiteOrZero(): Double = if (isFinite()) this else 0.0

    /** Up to [places] decimals, without trailing zeros: 2.0 is "2", 1.5 is "1.5". */
    private fun number(x: Double, places: Int): String {
        val scale = 10.0.pow(places)
        val r = (x * scale).roundToLong() / scale
        return if (r == floor(r)) r.toLong().toString() else r.toString()
    }
}
