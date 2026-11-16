package com.zeroindex.intake.domain.ai

import com.zeroindex.intake.domain.allergen.ModelAllergenClaim

/** A short question the photo model asked because it was not sure, with the answers it offered. */
data class PhotoQuestion(val text: String, val choices: List<String>)

/** A question as the model returned it, before [PhotoQuestions.toAsk] checks it. */
data class RawPhotoQuestion(val question: String, val choices: List<String>)

/**
 * "Ask instead of guessing wrong": when the photo model is not confident it may return up to three short
 * questions with two to four choices each. The person taps answers and one follow-up call turns them into the
 * final numbers. Everything here is pure so the rules are tested rather than trusted.
 */
object PhotoQuestions {
    const val MAX_QUESTIONS = 3
    const val MIN_CHOICES = 2
    const val MAX_CHOICES = 4
    private const val MAX_QUESTION_CHARS = 120
    private const val MAX_CHOICE_CHARS = 40

    /**
     * The questions worth showing. None when the model says it is confident (whatever it returned), none when it
     * returned nothing usable. A question needs text and at least two different choices; extra choices past four
     * are dropped, and at most three questions are kept.
     */
    fun toAsk(overallConfidence: String, raw: List<RawPhotoQuestion>): List<PhotoQuestion> {
        if (overallConfidence.trim().equals("high", ignoreCase = true)) return emptyList()
        return raw.mapNotNull { q ->
            val text = clean(q.question, MAX_QUESTION_CHARS)
            val choices = q.choices.map { clean(it, MAX_CHOICE_CHARS) }.filter { it.isNotEmpty() }
                .distinctBy { it.lowercase() }.take(MAX_CHOICES)
            if (text.isEmpty() || choices.size < MIN_CHOICES) null else PhotoQuestion(text, choices)
        }.distinctBy { it.text.lowercase() }.take(MAX_QUESTIONS)
    }

    /** The answers that name a real choice of a real question, in question order. Anything else is ignored. */
    fun answered(questions: List<PhotoQuestion>, answers: Map<Int, String>): List<Pair<PhotoQuestion, String>> =
        questions.mapIndexedNotNull { i, q -> answers[i]?.takeIf { it in q.choices }?.let { q to it } }

    /** Taps a choice, or taps it again to take the answer back. */
    fun toggle(answers: Map<Int, String>, question: Int, choice: String): Map<Int, String> =
        if (answers[question] == choice) answers - question else answers + (question to choice)

    private fun clean(text: String, max: Int) =
        text.map { if (it.isISOControl()) ' ' else it }.joinToString("").replace(Regex("\\s+"), " ").trim().take(max)
}

/** One food of the first estimate, as the follow-up prompt describes it to the model. */
data class FirstLookItem(val name: String, val grams: Double, val kcal: Double, val protein: Double, val carbs: Double, val fat: Double)

/** The single follow-up call that turns the person's answers into the final numbers. */
object PhotoFollowUp {

    /**
     * The text of the follow-up request. The photo is not sent again: the model gets its own first look and the
     * answers, which is cheaper and keeps the picture on the phone after the first call.
     */
    fun prompt(
        items: List<FirstLookItem>,
        portionAssumption: String,
        answers: List<Pair<PhotoQuestion, String>>,
        note: String?,
    ): String = buildString {
        appendLine("This is the follow-up to your first look at a meal photo. The photo is not attached again.")
        appendLine("Your first look:")
        items.forEach {
            appendLine(
                "- ${it.name}: ${it.grams.toInt()} g, ${it.kcal.toInt()} kcal " +
                    "(protein ${it.protein.toInt()} g, carbs ${it.carbs.toInt()} g, fat ${it.fat.toInt()} g)",
            )
        }
        if (portionAssumption.isNotBlank()) appendLine("Portion assumption: $portionAssumption")
        appendLine("The person answered your questions:")
        answers.forEach { (q, a) -> appendLine("- ${q.text} -> $a") }
        note?.let { appendLine("Their note about the photo: $it") }
        appendLine(
            "Give the final estimate for the same meal in the same format. Trust the answers over your first guess: " +
                "rename or replace a food an answer corrects, and use the cooking method they gave for fat and milk. " +
                "Add butter, oil, milk or a sauce only if an answer or the note says it is there; do not add it by default. " +
                "Keep every allergen you flagged. Return questions as an empty list: do not ask again.",
        )
    }

    /**
     * Allergen claims from the first look survive the follow-up. The follow-up may rename or split foods, so each
     * first-look claim is attached to the follow-up food whose name shares the most words with it (the first food
     * when none does), and is only ever added: nothing the second answer says removes a warning the first raised.
     * The person can still untick a food, which is the one way a warning goes away.
     */
    fun carryAllergens(first: List<ModelAllergenClaim>, second: List<ModelAllergenClaim>): List<ModelAllergenClaim> {
        if (second.isEmpty()) return second
        val merged = second.map { it.allergens.toMutableList() to it.reasoning }.toMutableList()
        first.filter { it.allergens.isNotEmpty() }.forEach { claim ->
            val target = bestMatch(claim.itemName, second.map { it.itemName })
            val (have, _) = merged[target]
            val missing = claim.allergens.filter { a -> have.none { it.equals(a, ignoreCase = true) } }
            if (missing.isNotEmpty()) {
                have += missing
                val why = claim.reasoning.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()
                val carried = "From the first look at this photo (${claim.itemName})$why"
                merged[target] = have to listOf(merged[target].second, carried).filter { it.isNotBlank() }.joinToString(" ")
            }
        }
        return second.mapIndexed { i, c -> c.copy(allergens = merged[i].first, reasoning = merged[i].second) }
    }

    private fun bestMatch(name: String, candidates: List<String>): Int {
        val words = words(name)
        val scores = candidates.map { c -> words.intersect(words(c)).size }
        val best = scores.max()
        return if (best == 0) 0 else scores.indexOf(best)
    }

    private fun words(text: String): Set<String> =
        Regex("[a-z]{3,}").findAll(text.lowercase()).map { it.value }.toSet()
}

/** Hidden-calorie guesses (butter, milk, oil) count only when the photo or the person says they are there. */
object HiddenCalorieRules {
    /** The `basis` values the schema allows, in the words the model is given. */
    val BASES = listOf("seen_in_photo", "your_note", "your_answer", "guess")

    /** A guess with nothing behind it is not counted or shown as counted. */
    fun isCounted(basis: String): Boolean = !basis.trim().equals("guess", ignoreCase = true)
}

/** A packaged product named by the photo model, turned into something to search for. */
object PackagedProduct {
    private const val MAX_CHARS = 80
    private val NONE = setOf("null", "none", "n/a", "no", "unknown", "not packaged")

    /** The words to put in the food search, or null when the model did not name a product. */
    fun searchQuery(raw: String?): String? {
        val text = raw?.map { if (it.isISOControl()) ' ' else it }?.joinToString("")
            ?.replace(Regex("\\s+"), " ")?.trim()?.take(MAX_CHARS).orEmpty()
        return text.takeIf { it.isNotEmpty() && it.lowercase() !in NONE }
    }
}
