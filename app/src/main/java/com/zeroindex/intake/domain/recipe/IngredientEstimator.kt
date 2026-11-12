package com.zeroindex.intake.domain.recipe

/** A weight USDA gives for a household measure of a food, e.g. "1 cup, chopped" at 124 g. */
data class ReferencePortion(val label: String, val grams: Double)

/** A food from the bundled offline reference database. All values are per 100 g; sodium is milligrams. */
data class ReferenceFood(
    val id: Int,
    val name: String,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val fiber: Double,
    val sugar: Double,
    val sodiumMg: Double,
    val saturatedFat: Double,
    val portions: List<ReferencePortion> = emptyList(),
)

/** What one ingredient line came to. [food] and [grams] are both set when the line is counted; otherwise [notCounted] says why. */
data class EstimatedLine(
    val line: String,
    val food: ReferenceFood?,
    val grams: Double?,
    val notCounted: String?,
    /** Other foods the line could have meant, best first, for the "wrong match" picker. */
    val alternatives: List<ReferenceFood> = emptyList(),
    /** True when the person picked [food] by hand. */
    val chosen: Boolean = false,
) {
    val counted: Boolean get() = food != null && grams != null && notCounted == null
    val kcal: Double? get() = if (counted) food!!.kcal * grams!! / 100.0 else null
}

/** Per-serving figures of an estimate. */
data class EstimateTotals(
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val fiber: Double,
    val sugar: Double,
    val sodiumMg: Double,
    val saturatedFat: Double,
)

/** A recipe's nutrition worked out from its ingredient lines. Not the page's numbers, and always labelled as an estimate. */
data class RecipeEstimate(
    val servings: Int,
    val lines: List<EstimatedLine>,
    /** Per serving, or null when no line could be counted. */
    val perServing: EstimateTotals?,
) {
    val countedLines: Int get() = lines.count { it.counted }
    val notCountedLines: List<EstimatedLine> get() = lines.filter { !it.counted }
}

/**
 * Estimates a recipe's nutrition from its ingredient lines, offline and deterministically: each line's amount is turned
 * into grams (a stated weight first, then weight units, then household measures using the matched food's own USDA
 * portions or a standard density), the line is matched to a bundled USDA food, and the grams are summed and divided by
 * the servings. A line that cannot be matched or weighed is listed as not counted instead of guessed.
 *
 * [lookup] searches the offline reference database (the app's own food search ranking); it is passed in so this
 * stays free of Android and is tested against the real bundled file.
 */
object IngredientEstimator {

    private const val GRAMS_PER_CUP_DEFAULT = 240.0
    private const val ALTERNATIVES = 6

    /**
     * [overrides] maps a line's index to a food the person chose for it; the line is then weighed against that food.
     */
    suspend fun estimate(
        lines: List<String>,
        servings: Int,
        lookup: suspend (query: String) -> List<ReferenceFood>,
        overrides: Map<Int, ReferenceFood> = emptyMap(),
    ): RecipeEstimate {
        val results = lines.mapIndexed { i, raw -> estimateLine(raw, lookup, overrides[i]) }
        val counted = results.filter { it.counted }
        val n = servings.coerceAtLeast(1).toDouble()
        val per = if (counted.isEmpty()) null else {
            fun sum(f: (ReferenceFood) -> Double) = counted.sumOf { f(it.food!!) * it.grams!! / 100.0 } / n
            EstimateTotals(
                kcal = sum { it.kcal }, protein = sum { it.protein }, carbs = sum { it.carbs }, fat = sum { it.fat },
                fiber = sum { it.fiber }, sugar = sum { it.sugar }, sodiumMg = sum { it.sodiumMg }, saturatedFat = sum { it.saturatedFat },
            )
        }
        return RecipeEstimate(servings.coerceAtLeast(1), results, per)
    }

    private suspend fun estimateLine(
        raw: String,
        lookup: suspend (String) -> List<ReferenceFood>,
        chosen: ReferenceFood?,
    ): EstimatedLine {
        val line = raw.trim()
        val amount = IngredientAmounts.parse(line)
        if (amount.name.isBlank()) return EstimatedLine(line, null, null, "nothing to match")
        if (amount.optional) return EstimatedLine(line, null, null, "optional")
        if (amount.value == null && amount.statedGrams == null) {
            return EstimatedLine(line, null, null, if (amount.toTaste) "to taste, no amount" else "no amount given")
        }

        val rule = IngredientRules.find(amount.description.ifEmpty { amount.name })
        val candidates = if (chosen != null) emptyList() else findCandidates(amount, rule, lookup)
        val food = chosen ?: candidates.firstOrNull()
            ?: return EstimatedLine(line, null, null, "no matching food found")
        val grams = gramsOf(amount, food, rule)
            ?: return EstimatedLine(line, food, null, "no weight known for ${describeMeasure(amount)}", candidates.drop(1).take(ALTERNATIVES), chosen != null)
        val alternatives = if (chosen != null) {
            // Keep the picker populated after a choice: the other foods for the same line, minus the chosen one.
            findCandidates(amount, rule, lookup).filter { it.id != chosen.id }.take(ALTERNATIVES)
        } else {
            candidates.drop(1).take(ALTERNATIVES)
        }
        return EstimatedLine(line, food, grams, null, alternatives, chosen != null)
    }

    private fun describeMeasure(a: IngredientAmount): String =
        listOfNotNull(a.size, a.unit).joinToString(" ").ifEmpty { "this amount" }.let { "\"$it\"" }

    private suspend fun findCandidates(
        amount: IngredientAmount,
        rule: IngredientRules.Rule?,
        lookup: suspend (String) -> List<ReferenceFood>,
    ): List<ReferenceFood> {
        val queries = buildList {
            rule?.let { add(it.query) }
            addAll(genericQueries(amount.name))
        }.distinct()
        for (q in queries) {
            val found = lookup(q).filter { !looksLikeBrand(it.name) }
            if (found.isNotEmpty()) return found
        }
        return emptyList()
    }

    /** Restaurant and brand entries ("OLIVE GARDEN, lasagna") are not what a recipe line means. */
    private fun looksLikeBrand(name: String): Boolean {
        val head = name.substringBefore(',').trim()
        return head.length > 2 && head.any(Char::isLetter) && head == head.uppercase()
    }

    private val DESCRIPTORS = setOf(
        "fresh", "freshly", "chopped", "diced", "minced", "sliced", "large", "small", "medium", "ripe", "finely", "roughly",
        "thinly", "boneless", "skinless", "extra", "virgin", "good", "quality", "organic", "whole", "optional", "of", "a", "the",
        "about", "heaping", "packed", "we", "used", "your", "favorite", "favourite", "any", "kind", "type", "like", "such", "as", "and",
    )

    /** Fallback searches for a line no rule knows: its words without descriptors, singular, then the last word alone. */
    fun genericQueries(name: String): List<String> {
        val firstChoice = name.lowercase().split(Regex("""\s+or\s+""")).first()
        val words = firstChoice.split(Regex("""[^a-z]+""")).filter { it.isNotEmpty() && it !in DESCRIPTORS }.map(::singular)
        if (words.isEmpty()) return emptyList()
        val full = words.take(3).joinToString(" ")
        return listOf(full, words.last()).distinct()
    }

    private fun singular(w: String): String = when {
        w.length > 4 && w.endsWith("ies") -> w.dropLast(3) + "y"
        w.length > 4 && w.endsWith("oes") -> w.dropLast(2)
        w.length > 3 && w.endsWith("ches") -> w.dropLast(2)
        w.length > 3 && w.endsWith("s") && !w.endsWith("ss") && !w.endsWith("us") -> w.dropLast(1)
        else -> w
    }

    // ---- grams ---------------------------------------------------------------------------------

    private fun gramsOf(a: IngredientAmount, food: ReferenceFood, rule: IngredientRules.Rule?): Double? {
        a.statedGrams?.let { return it }
        val v = a.value ?: return null
        when (a.unit) {
            "g" -> return v
            "kg" -> return v * 1000.0
            "oz" -> return v * 28.349523125
            "lb" -> return v * 453.59237
            "pinch" -> return v * 0.3
            "dash" -> return v * 0.6
        }
        VOLUME_CUPS[a.unit]?.let { cupsPerUnit ->
            val perCup = rule?.gramsPerCup ?: gramsPerCupFromPortions(food) ?: GRAMS_PER_CUP_DEFAULT
            return v * cupsPerUnit * perCup
        }
        // A count: "2 medium zucchinis", "3 cloves garlic", "1 slice bread".
        val keys = listOfNotNull(a.unit, a.size) + ""
        rule?.each?.let { each -> keys.firstNotNullOfOrNull { each[it] }?.let { return v * it } }
        val word = a.unit ?: a.size
        if (word != null) {
            food.portions.firstOrNull { it.label.lowercase().contains(word) }?.let { p ->
                val per = Regex("""^\s*(\d+(?:\.\d+)?)""").find(p.label)?.groupValues?.get(1)?.toDoubleOrNull() ?: 1.0
                if (per > 0.0) return v * p.grams / per
            }
        }
        return null
    }

    private val VOLUME_CUPS = mapOf(
        "cup" to 1.0, "tbsp" to 1.0 / 16.0, "tsp" to 1.0 / 48.0, "floz" to 1.0 / 8.0, "ml" to 1.0 / 236.588, "l" to 1000.0 / 236.588,
    )

    /** Grams in a cup, worked out from the first household volume USDA lists for the food. */
    fun gramsPerCupFromPortions(food: ReferenceFood): Double? {
        for (p in food.portions) {
            val m = Regex("""^\s*(\d+(?:\.\d+)?)\s*(cup|cups|tbsp|tablespoon|tablespoons|tsp|teaspoon|teaspoons|fl oz)\b""").find(p.label.lowercase()) ?: continue
            val n = m.groupValues[1].toDoubleOrNull()?.takeIf { it > 0 } ?: continue
            val cups = n * when (m.groupValues[2]) {
                "cup", "cups" -> 1.0
                "tbsp", "tablespoon", "tablespoons" -> 1.0 / 16.0
                "tsp", "teaspoon", "teaspoons" -> 1.0 / 48.0
                else -> 1.0 / 8.0
            }
            return p.grams / cups
        }
        return null
    }
}
