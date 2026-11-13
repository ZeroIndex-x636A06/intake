package com.zeroindex.intake.domain.recipe

/**
 * One ingredient line read for the nutrition estimate: how much, in what unit, and what of.
 * [value] is null for a line with no amount ("fresh basil", "salt to taste").
 */
data class IngredientAmount(
    val value: Double?,
    /** Canonical unit: cup, tbsp, tsp, floz, ml, l, oz, lb, g, kg, pinch, dash, or a counting word such as clove or can. */
    val unit: String?,
    /** small, medium or large, when the line said so. */
    val size: String?,
    /** The weight the line itself states in grams: "(220g)", or "(14 oz)" times the number of cans. It wins over any unit. */
    val statedGrams: Double?,
    /** What the line is of: no amount, no brackets, nothing after the first comma. */
    val name: String,
    /** Everything after the amount, lower case, brackets removed: the text the matching rules read. */
    val description: String,
    val optional: Boolean,
    /** "to taste", "for serving", "for garnish": no amount by design. */
    val toTaste: Boolean,
)

/** Reads quantities, units, fractions and stated weights off an ingredient line. Pure and total. */
object IngredientAmounts {
    private const val FRAC = """\d+\s+\d+/\d+|\d+/\d+|\d+(?:\.\d+)?"""
    private val LEADING = Regex("""^($FRAC)(?:\s*(?:-|–|to)\s*($FRAC))?\s*""")
    private val PAREN_WEIGHT = Regex(
        """\(\s*(?:about\s+|approx\.?\s*|~\s*)?(\d+(?:\.\d+)?)\s*(g|gr|grams?|kg|oz|ounces?|lbs?|pounds?)\s*(each)?\s*\)""",
    )
    private val PAREN = Regex("""\([^)]*\)""")
    private val SIZES = setOf("small", "medium", "large", "med", "lg", "sm")
    private val TO_TASTE = Regex("""\b(to taste|for serving|for garnish|for topping|as needed|for dusting|for greasing)\b""")

    /** Longest first, so "fl oz" is read before "oz". */
    private val UNITS: List<Pair<String, String>> = listOf(
        "fluid ounces" to "floz", "fluid ounce" to "floz", "fl. oz." to "floz", "fl oz" to "floz", "fl. oz" to "floz",
        "tablespoons" to "tbsp", "tablespoon" to "tbsp", "tbsps" to "tbsp", "tbsp" to "tbsp", "tbs" to "tbsp",
        "teaspoons" to "tsp", "teaspoon" to "tsp", "tsps" to "tsp", "tsp" to "tsp",
        "milliliters" to "ml", "millilitres" to "ml", "milliliter" to "ml", "millilitre" to "ml", "ml" to "ml",
        "kilograms" to "kg", "kilogram" to "kg", "kgs" to "kg", "kg" to "kg",
        "liters" to "l", "litres" to "l", "liter" to "l", "litre" to "l",
        "ounces" to "oz", "ounce" to "oz", "oz" to "oz",
        "pounds" to "lb", "pound" to "lb", "lbs" to "lb", "lb" to "lb",
        "grams" to "g", "gram" to "g", "gr" to "g", "g" to "g",
        "cups" to "cup", "cup" to "cup", "c" to "cup",
        "pinches" to "pinch", "pinch" to "pinch", "dashes" to "dash", "dash" to "dash",
        "cloves" to "clove", "clove" to "clove", "slices" to "slice", "slice" to "slice",
        "cans" to "can", "can" to "can", "jars" to "jar", "jar" to "jar",
        "packages" to "package", "package" to "package", "packets" to "packet", "packet" to "packet", "pkg" to "package",
        "sticks" to "stick", "stick" to "stick", "bunches" to "bunch", "bunch" to "bunch",
        "sprigs" to "sprig", "sprig" to "sprig", "heads" to "head", "head" to "head",
        "pieces" to "piece", "piece" to "piece", "stalks" to "stalk", "stalk" to "stalk",
        "leaves" to "leaf", "leaf" to "leaf", "fillets" to "fillet", "fillet" to "fillet",
        "cubes" to "cube", "cube" to "cube",
    )

    /** Units that hold a stated weight each: "2 (14 oz) cans" is two cans of 14 oz. */
    private val CONTAINERS = setOf("can", "jar", "package", "packet", "stick", "bunch", "head", "piece", "fillet", "cube")

    /** True for units that hold a stated weight each ("2 (14 oz) cans"): the bracket is per can, not the total. */
    fun isContainerUnit(unit: String?): Boolean = unit != null && unit in CONTAINERS

    fun parse(line: String): IngredientAmount {
        val text = IngredientLineParser.normalise(IngredientCleaner.clean(line)).lowercase()
        val optional = Regex("""\boptional\b""").containsMatchIn(text)
        val toTaste = TO_TASTE.containsMatchIn(text)
        val weightMatch = PAREN_WEIGHT.find(text)
        val body = PAREN.replace(text, " ").replace(Regex("""\s+"""), " ").trim()

        var rest = body
        var value: Double? = null
        LEADING.find(rest)?.let { m ->
            val a = fraction(m.groupValues[1])
            val b = m.groupValues[2].takeIf { it.isNotEmpty() }?.let(::fraction)
            if (a != null) {
                value = if (b != null) (a + b) / 2.0 else a
                rest = rest.substring(m.range.last + 1).trim()
            }
        }

        var size: String? = null
        var unit: String? = null
        if (value == null) {
            // "pinch of salt", "dash of hot sauce" carry a unit and no number.
            Regex("""^(pinch|dash)(?:es)?\b""").find(rest)?.let { m ->
                unit = m.groupValues[1]
                value = 1.0
                rest = rest.substring(m.range.last + 1).trim()
            }
        } else {
            val sizeMatch = Regex("""^(extra[ -]large|small|medium|large|med|lg|sm)\b\.?\s*""").find(rest)
            if (sizeMatch != null) {
                size = canonicalSize(sizeMatch.groupValues[1])
                rest = rest.substring(sizeMatch.range.last + 1).trim()
            }
            for ((word, canon) in UNITS) {
                val m = Regex("""^${Regex.escape(word)}(?![a-z])\.?\s*""").find(rest) ?: continue
                unit = canon
                rest = rest.substring(m.range.last + 1).trim()
                break
            }
            if (size == null) {
                Regex("""^(extra[ -]large|small|medium|large|med|lg|sm)\b\.?\s*""").find(rest)?.let { m ->
                    size = canonicalSize(m.groupValues[1])
                    rest = rest.substring(m.range.last + 1).trim()
                }
            }
        }
        rest = rest.removePrefix("of ").trim()
        val description = rest.trim().trim(',').trim()
        val name = description.substringBefore(',').trim()

        var statedGrams: Double? = null
        if (weightMatch != null) {
            val w = weightMatch.groupValues[1].toDoubleOrNull()
            val per = weightMatch.groupValues[2].let(::gramsPerWeightUnit)
            if (w != null && per != null) {
                val each = weightMatch.groupValues[3].isNotEmpty() || unit in CONTAINERS || (unit == null && value != null && value!! > 1.0 && size == null)
                // "8 oz (220g) pasta" restates its own amount and the bracket wins; "2 (14 oz) cans" is two of them.
                statedGrams = if (each && value != null) w * per * value!! else w * per
            }
        }
        return IngredientAmount(value, unit, size, statedGrams, name, description, optional, toTaste)
    }

    private fun canonicalSize(s: String) = when (s) {
        "small", "sm" -> "small"
        "large", "lg", "extra large", "extra-large" -> "large"
        else -> "medium"
    }

    private fun gramsPerWeightUnit(u: String): Double? = when (u) {
        "g", "gr", "gram", "grams" -> 1.0
        "kg" -> 1000.0
        "oz", "ounce", "ounces" -> 28.349523125
        "lb", "lbs", "pound", "pounds" -> 453.59237
        else -> null
    }

    /** "1 1/2", "3/4", "2", "0.5" to a number; null for nonsense such as "5/0". */
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
