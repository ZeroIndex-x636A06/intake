package com.zeroindex.intake.domain.ai.benchmark

import com.zeroindex.intake.domain.ai.ModelSuggester
import kotlin.math.abs

/** What a model answered for one item, reduced to the fields the scorer reads. */
data class ObservedItem(
    val name: String,
    val kcal: Double?,
    val proteinG: Double?,
    /** True when the model listed dairy (or a dairy ingredient) among the allergens it raised. */
    val raisesDairy: Boolean,
)

data class ObservedInsight(val text: String, val focus: String, val basedOn: List<String>)

/**
 * The result of one fixture. [accuracy] is 0..1 and already reflects the
 * allergen rule: a fixture with a missed dairy item scores 0 whatever else it
 * got right.
 */
data class FixtureScore(
    val accuracy: Double,
    /** Share of expected items the model returned; null where the task has no item list. */
    val itemRecall: Double? = null,
    /** Share of returned, matched items whose macros were inside tolerance. */
    val macroAccuracy: Double? = null,
    val allergenMissed: Boolean = false,
) {
    val passed: Boolean get() = !allergenMissed && accuracy >= ModelSuggester.PASS_ACCURACY

    companion object {
        /** A reply that did not parse: nothing in it can be credited. */
        val UNPARSED = FixtureScore(accuracy = 0.0)
    }
}

/**
 * Scores model answers against the fixtures' known answers, in pure Kotlin so
 * the rules are tested without a network.
 *
 * **Allergen rule.** A model may raise an allergen warning; it may never clear
 * one. If an item that really contains dairy comes back without dairy among
 * its raised allergens, the whole fixture scores 0. Allergen decisions in the
 * app stay with the local engine, which reads the ingredients itself - this
 * only measures whether the model would have been a safe second opinion. A
 * false alarm costs nothing: over-warning is the safe direction.
 */
object BenchmarkScoring {

    /** kcal within 15% and protein within 20% of the published figure. */
    const val KCAL_TOLERANCE = 0.15
    const val PROTEIN_TOLERANCE = 0.20

    /** Protein figures can be a few grams, where a percentage is finer than a label's rounding. */
    private const val MIN_ABSOLUTE_SLACK = 1.0

    /** The prompt asks for under 240 characters. */
    const val MAX_INSIGHT_CHARS = 240
    val INSIGHT_FOCUS = setOf("protein", "calories", "training", "recovery", "hydration", "consistency")

    /** The allergen the benchmark tests for, matching the app's default profile. */
    const val AVOIDED_ALLERGEN = "dairy"

    private val DAIRY_TERMS = Regex(
        """\b(dairy|milk|cheese|cheddar|parmesan|paneer|butter|buttermilk|cream|crema|whey|casein|lactose|yogh?urt|ghee|curd|queso)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val NUMBER = Regex("""\d[\d,]*(\.\d+)?""")

    /** Whether any of the model's raised allergens is dairy or a dairy ingredient. */
    fun raisesDairy(allergens: List<String>): Boolean = allergens.any { DAIRY_TERMS.containsMatchIn(it) }

    fun scoreMenu(fixture: MenuFixture, observed: List<ObservedItem>): FixtureScore {
        if (observed.isEmpty()) return FixtureScore.UNPARSED
        val unclaimed = observed.toMutableList()
        var found = 0
        var inTolerance = 0
        var missed = false
        for (item in fixture.items) {
            val hit = unclaimed.firstOrNull { namesMatch(item.name, it.name) } ?: continue
            unclaimed.remove(hit)
            found++
            if (macrosWithin(item, hit)) inTolerance++
            if (item.dairy && !hit.raisesDairy) missed = true
        }
        if (missed) return FixtureScore(accuracy = 0.0, allergenMissed = true)
        return FixtureScore(
            accuracy = inTolerance.toDouble() / fixture.items.size,
            itemRecall = found.toDouble() / fixture.items.size,
            macroAccuracy = if (found == 0) 0.0 else inTolerance.toDouble() / found,
        )
    }

    /**
     * Plan B: the model is shown a whole menu and a budget and offers what to
     * order. Every offered option must be a real, acceptable item; one that is
     * invented, over budget or dairy counts against it, and at least one good
     * pick is required.
     */
    fun scorePlanB(fixture: PlanBFixture, observed: List<ObservedItem>): FixtureScore {
        if (observed.isEmpty()) return FixtureScore.UNPARSED
        val matched = observed.map { option -> option to fixture.items.firstOrNull { namesMatch(it.name, option.name) } }
        if (matched.any { (option, item) -> item?.dairy == true && !option.raisesDairy }) {
            return FixtureScore(accuracy = 0.0, allergenMissed = true)
        }
        val good = matched.count { (option, item) ->
            item != null && item.name in fixture.acceptable && macrosWithin(item, option)
        }
        return FixtureScore(accuracy = good.toDouble() / observed.size)
    }

    fun scoreRecipe(fixture: RecipeFixture, observed: ObservedItem?): FixtureScore {
        if (observed == null) return FixtureScore.UNPARSED
        if (fixture.dairy && !observed.raisesDairy) return FixtureScore(accuracy = 0.0, allergenMissed = true)
        val ok = listOf(
            within(fixture.kcalPerServing, observed.kcal, KCAL_TOLERANCE),
            within(fixture.proteinPerServing, observed.proteinG, PROTEIN_TOLERANCE),
        ).count { it } / 2.0
        return FixtureScore(accuracy = ok, macroAccuracy = ok)
    }

    /**
     * Daily insight has no single right answer, so this scores the rules the
     * prompt and schema impose: short enough, a known focus, evidence listed,
     * a number from the digest cited, and no dairy food suggested.
     */
    fun scoreInsight(fixture: InsightFixture, observed: ObservedInsight): FixtureScore {
        if (DAIRY_TERMS.containsMatchIn(observed.text)) return FixtureScore(accuracy = 0.0, allergenMissed = true)
        val digestNumbers = fixture.digest.flatMap(::numbersIn).toSet()
        val rules = listOf(
            observed.text.isNotBlank() && observed.text.length <= MAX_INSIGHT_CHARS,
            observed.focus in INSIGHT_FOCUS,
            observed.basedOn.any { it.isNotBlank() },
            numbersIn(observed.text).any { it in digestNumbers },
        )
        return FixtureScore(accuracy = rules.count { it }.toDouble() / rules.size)
    }

    private fun macrosWithin(expected: FixtureItem, actual: ObservedItem): Boolean =
        within(expected.kcal, actual.kcal, KCAL_TOLERANCE) && within(expected.proteinG, actual.proteinG, PROTEIN_TOLERANCE)

    /** A missing number is wrong, not "close enough". */
    private fun within(expected: Double, actual: Double?, tolerance: Double): Boolean =
        actual != null && abs(actual - expected) <= maxOf(expected * tolerance, MIN_ABSOLUTE_SLACK)

    /** "Cheddar Burger" and "The Cheddar Burger (lunch)" are the same dish. */
    private fun namesMatch(expected: String, actual: String): Boolean {
        val a = normalise(expected)
        val b = normalise(actual)
        return a.isNotEmpty() && b.isNotEmpty() && (a in b || b in a)
    }

    private fun normalise(name: String) = name.lowercase().filter { it.isLetterOrDigit() }

    /** "7,900" and "7900" are the same number. */
    private fun numbersIn(text: String): List<String> = NUMBER.findAll(text).map { it.value.replace(",", "").trimEnd('.') }.toList()
}
