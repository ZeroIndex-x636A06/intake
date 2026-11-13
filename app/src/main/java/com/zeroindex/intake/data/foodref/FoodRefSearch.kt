package com.zeroindex.intake.data.foodref

/** A common portion with its weight, from USDA's food_portion table. */
data class RefPortion(val label: String, val grams: Double)

/** A bundled food with its parsed portions. Values are per 100 g (sodium in mg). */
data class RefFood(
    val id: Int,
    val name: String,
    val category: String,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val fiber: Double,
    val sugar: Double,
    val sodium: Double,
    val saturatedFat: Double,
    val portions: List<RefPortion>,
    val dataset: String,
) {
    /** The portion a result row quotes: the first USDA lists, or 100 g when there is none. */
    val defaultPortion: RefPortion get() = portions.firstOrNull() ?: RefPortion("100 g", 100.0)

    fun kcalFor(grams: Double) = kcal * grams / 100.0
    fun proteinFor(grams: Double) = protein * grams / 100.0
}

/**
 * Pure search logic for the offline database: tokenising the query, ranking the
 * candidates SQL returns, and parsing the portions column. No Android here, so
 * `FoodRefSearchTest` covers it directly.
 */
object FoodRefSearch {

    const val MAX_TOKENS = 4
    const val CANDIDATE_LIMIT = 500
    const val RESULT_LIMIT = 30

    /** Lower-cased letter and digit runs: "Greek yogurt, 2%" becomes [greek, yogurt, 2]. */
    fun tokenize(text: String): List<String> =
        text.lowercase().split(NOT_LETTER_OR_DIGIT).filter { it.isNotEmpty() }

    private val NOT_LETTER_OR_DIGIT = Regex("[^\\p{L}\\p{N}]+")

    /**
     * Query tokens, at most [MAX_TOKENS]. A one-letter token matches too much to be
     * worth a scan, so it is dropped unless it is all there is.
     */
    fun queryTokens(query: String): List<String> {
        val all = tokenize(query).distinct()
        val useful = all.filter { it.length > 1 }.ifEmpty { all }
        return useful.take(MAX_TOKENS)
    }

    /**
     * Ranking tiers, best first:
     *  0. every token starts a word and the name begins with the first token ("apple" -> "Apples, raw");
     *  1. every token starts a word somewhere in the name ("raw apple" -> "Apples, raw");
     *  2. at least one token only matches inside a word ("ple" -> "Apples").
     * Ties go to the shorter name, then alphabetical order.
     */
    fun tier(name: String, tokens: List<String>): Int {
        if (tokens.isEmpty()) return 2
        val words = tokenize(name)
        val allWordStart = tokens.all { token -> words.any { it.startsWith(token) } }
        if (!allWordStart) return 2
        return if (words.firstOrNull()?.startsWith(tokens.first()) == true) 0 else 1
    }

    fun rank(query: String, candidates: List<RefFoodEntity>, limit: Int = RESULT_LIMIT): List<RefFoodEntity> {
        val tokens = queryTokens(query)
        return candidates
            .filter { entity ->
                // SQL only checked the first four tokens, which is also all the ranker uses.
                val lower = entity.name.lowercase()
                tokens.all { lower.contains(it) }
            }
            // The tier is worked out once per food, not on every comparison of the sort.
            .map { Triple(tier(it.name, tokens), it.name.lowercase(), it) }
            .sortedWith(compareBy<Triple<Int, String, RefFoodEntity>> { it.first }.thenBy { it.second.length }.thenBy { it.second })
            .take(limit)
            .map { it.third }
    }

    /** "1 cup, diced|132;1 slice|28" -> two portions. Malformed pieces are skipped. */
    fun parsePortions(text: String): List<RefPortion> =
        text.split(';').mapNotNull { piece ->
            val at = piece.lastIndexOf('|')
            if (at <= 0) return@mapNotNull null
            val grams = piece.substring(at + 1).toDoubleOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
            RefPortion(piece.substring(0, at).trim(), grams).takeIf { it.label.isNotEmpty() }
        }

    fun RefFoodEntity.toRefFood() = RefFood(
        id = id,
        name = name,
        category = category,
        kcal = kcal,
        protein = protein,
        carbs = carbs,
        fat = fat,
        fiber = fiber,
        sugar = sugar,
        sodium = sodium,
        saturatedFat = saturatedFat,
        portions = parsePortions(portions),
        dataset = dataset,
    )
}
