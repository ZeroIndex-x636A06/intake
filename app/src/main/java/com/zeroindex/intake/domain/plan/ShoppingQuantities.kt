package com.zeroindex.intake.domain.plan

import com.zeroindex.intake.domain.recipe.IngredientAmounts
import com.zeroindex.intake.domain.recipe.IngredientRules
import com.zeroindex.intake.domain.units.WeightUnit
import java.util.Locale

/** One ingredient line from one recipe in the plan. [scale] is planned servings over the recipe's servings. */
data class ShoppingContribution(
    val line: String,
    /** Which recipe or meal it came from, for "where is this used". */
    val source: String? = null,
    val group: String? = null,
    val scale: Double = 1.0,
)

/** The total for one shopping item. [quantity] is the stored form (see [ShoppingQuantities]); empty when no line gave an amount. */
data class ShoppingTotal(
    val key: String,
    val name: String,
    val quantity: String,
    val sources: List<String>,
    val groups: List<String>,
)

/**
 * Adds up ingredient amounts across the plan's recipes using the same line reader as the nutrition estimate
 * ([IngredientAmounts]). Amounts are kept in a metric, machine-readable stored form so the display can follow the
 * user's unit setting later:
 *
 *  - weight in grams: "450 g";  volume in millilitres: "250 ml";  counts as written: "2 medium", "3 clove", "1 bunch"
 *  - parts that cannot be added are joined: "2 medium + 200 g"
 *  - "≈" in front marks a total that used a known average weight (a medium zucchini is about 196 g)
 *
 * A line with no amount adds nothing, so an item only ever mentioned that way has an empty quantity.
 */
object ShoppingQuantities {
    private const val ML_PER_CUP = 236.588
    private val ML = mapOf(
        "cup" to ML_PER_CUP, "tbsp" to ML_PER_CUP / 16.0, "tsp" to ML_PER_CUP / 48.0, "floz" to ML_PER_CUP / 8.0,
        "ml" to 1.0, "l" to 1000.0, "pinch" to ML_PER_CUP / 48.0 / 16.0, "dash" to ML_PER_CUP / 48.0 / 8.0,
    )
    private val GRAMS = mapOf("g" to 1.0, "kg" to 1000.0, "oz" to 28.349523125, "lb" to 453.59237)

    /** The key an item is grouped by: lower case, no brackets or prep notes, singular words ("2 medium zucchinis" and "zucchini" meet). */
    fun itemKey(name: String): String {
        val words = name.lowercase().replace(Regex("""\([^)]*\)"""), " ").substringBefore(',')
            .split(Regex("""[^a-z]+""")).filter { it.isNotEmpty() && it !in NOISE }
        return words.joinToString(" ") { singular(it) }
    }

    private val NOISE = setOf("fresh", "freshly", "chopped", "diced", "minced", "sliced", "large", "small", "medium", "of", "the", "a", "optional", "finely", "roughly")

    private fun singular(w: String) = when {
        w.length > 4 && w.endsWith("ies") -> w.dropLast(3) + "y"
        w.length > 4 && w.endsWith("oes") -> w.dropLast(2)
        w.length > 3 && w.endsWith("ches") -> w.dropLast(2)
        w.length > 3 && w.endsWith("s") && !w.endsWith("ss") && !w.endsWith("us") -> w.dropLast(1)
        else -> w
    }

    fun aggregate(lines: List<ShoppingContribution>): List<ShoppingTotal> =
        lines.filter { it.line.isNotBlank() }
            .map { it to IngredientAmounts.parse(it.line) }
            .filter { it.second.name.isNotBlank() }
            .groupBy { itemKey(it.second.name).ifEmpty { it.second.name.lowercase() } }
            .map { (key, group) ->
                var grams = 0.0
                var ml = 0.0
                val counts = LinkedHashMap<String, Double>()
                for ((c, a) in group) {
                    val factor = c.scale.takeIf { it > 0 } ?: 1.0
                    val v = a.value
                    when {
                        a.statedGrams != null -> grams += a.statedGrams!! * factor
                        v == null -> Unit
                        a.unit != null && a.unit in GRAMS -> grams += v * GRAMS.getValue(a.unit!!) * factor
                        a.unit != null && a.unit in ML -> ml += v * ML.getValue(a.unit!!) * factor
                        else -> {
                            val label = listOfNotNull(a.size, a.unit).joinToString(" ")
                            counts[label] = (counts[label] ?: 0.0) + v * factor
                        }
                    }
                }
                val rule = IngredientRules.find(group.first().second.description.ifEmpty { group.first().second.name })
                var approx = false
                // Counts with a known average weight fold into grams when there is a weight to add them to.
                if (grams > 0) {
                    val it = counts.iterator()
                    while (it.hasNext()) {
                        val (label, n) = it.next()
                        val each = rule?.each?.let { e -> e[label] ?: e[label.substringBefore(' ')] }
                        if (each != null) { grams += n * each; approx = true; it.remove() }
                    }
                }
                val parts = buildList {
                    if (grams > 0) add("${number(grams, 1)} g")
                    if (ml > 0) add("${number(ml, 1)} ml")
                    counts.forEach { (label, n) -> add(if (label.isEmpty()) number(n) else "${number(n)} $label") }
                }
                val quantity = parts.joinToString(" + ").let { if (approx && it.isNotEmpty()) "≈ $it" else it }
                ShoppingTotal(
                    key = key,
                    name = group.first().second.name.replaceFirstChar { it.uppercase() },
                    quantity = quantity,
                    sources = group.mapNotNull { it.first.source?.takeIf(String::isNotBlank) }.distinct(),
                    groups = group.mapNotNull { it.first.group?.takeIf(String::isNotBlank) }.distinct(),
                )
            }
            .sortedBy { it.key }

    // ---- display -----------------------------------------------------------------------------

    private val SEGMENT = Regex("""^(≈\s*)?(\d+(?:\.\d+)?)(?:\s+(.+))?$""")

    /**
     * The stored quantity in the user's units. [unit] POUNDS means imperial: ounces and pounds, teaspoons,
     * tablespoons and cups. Anything this does not recognise ("1 bunch") passes through unchanged.
     */
    fun display(quantity: String, unit: WeightUnit): String {
        val q = quantity.trim()
        if (q.isEmpty()) return quantity
        val approx = q.startsWith("≈")
        val segments = q.removePrefix("≈").trim().split(" + ").map { it.trim() }
        val shown = segments.map { seg ->
            val m = SEGMENT.matchEntire(seg) ?: return quantity
            val n = m.groupValues[2].toDoubleOrNull() ?: return quantity
            val label = m.groupValues[3]
            when (label) {
                "g" -> grams(n, unit)
                "ml" -> millilitres(n, unit)
                "" -> number(n)
                else -> "${number(n)} ${pluralise(label, n)}"
            }
        }
        return (if (approx) "≈ " else "") + shown.joinToString(" + ")
    }

    private val COUNT_WORDS = setOf("clove", "slice", "can", "jar", "package", "packet", "stick", "bunch", "sprig", "head", "piece", "stalk", "leaf", "fillet", "cube")

    private fun pluralise(label: String, n: Double): String {
        val last = label.substringAfterLast(' ')
        // Only the counting words this file writes are pluralised; free text such as "lemons" is left as it is.
        if (n <= 1.0 || last !in COUNT_WORDS) return label
        val plural = when {
            last.endsWith("ch") || last.endsWith("s") -> last + "es"
            last == "leaf" -> "leaves"
            else -> last + "s"
        }
        return label.dropLast(last.length) + plural
    }

    private fun grams(g: Double, unit: WeightUnit): String = when (unit) {
        WeightUnit.KILOGRAMS -> if (g >= 1000) "${number(g / 1000.0, 2)} kg" else "${number(g)} g"
        WeightUnit.POUNDS -> if (g >= 453.59237) "${number(g / 453.59237, 2)} lb" else "${number(g / 28.349523125, 1)} oz"
    }

    private fun millilitres(ml: Double, unit: WeightUnit): String = when (unit) {
        WeightUnit.KILOGRAMS -> if (ml >= 1000) "${number(ml / 1000.0, 2)} l" else "${number(ml)} ml"
        WeightUnit.POUNDS -> when {
            ml >= ML_PER_CUP / 4.0 -> "${number(ml / ML_PER_CUP, 2)} ${if (ml / ML_PER_CUP > 1.0) "cups" else "cup"}"
            ml >= ML_PER_CUP / 16.0 * 0.95 -> "${number(ml / (ML_PER_CUP / 16.0), 1)} tbsp"
            else -> "${number(ml / (ML_PER_CUP / 48.0), 1)} tsp"
        }
    }

    /** 592, 2.5, 0.3: whole numbers from 10 up, otherwise up to two decimals; [decimals] fixes the precision. */
    private fun number(v: Double, decimals: Int = -1): String {
        val d = if (decimals >= 0) decimals else if (v >= 10 || v == Math.rint(v)) 0 else 2
        return String.format(Locale.US, "%.${d}f", v).let { if (d > 0) it.trimEnd('0').trimEnd('.') else it }
    }
}
