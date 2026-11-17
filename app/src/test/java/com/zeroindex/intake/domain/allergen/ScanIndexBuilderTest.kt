package com.zeroindex.intake.domain.allergen

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The shared scan index (Q5b-1) must give the same answers as handing the matcher every keyword. */
class ScanIndexBuilderTest {

    private val every: List<AllergenAlias> =
        AllergenCatalog.ALL.flatMap { a -> a.keywords.map { AllergenAlias(a.key, it.term, it.level) } }

    private val foods = listOf(
        "Ranch dressing" to "buttermilk, soybean oil, egg yolk, salt",
        "Almond milk, unsweetened" to "almonds, water, sea salt",
        "Whey protein bar" to "whey protein isolate, peanuts, soy lecithin",
        "Peanut butter" to "peanuts, salt",
        "Chicken breast, grilled" to null,
        "Flour tortillas" to "wheat flour, water, shortening",
        "Shrimp fried rice" to "rice, shrimp, egg, soy sauce, sesame oil",
        "Dark chocolate" to "cocoa, sugar. May contain milk",
    )

    @Test
    fun `narrowed keywords give the same scan as every keyword`() {
        val sets = listOf(
            setOf("dairy"),
            setOf("dairy", "peanut"),
            setOf("egg", "soy", "sesame"),
            AllergenCatalog.ALL.map { it.key }.toSet(),
            emptySet(),
        )
        for (avoided in sets) {
            val narrowed = ScanIndexBuilder.relevantAliases(every, avoided)
            val terms = HiddenCarriers.forAllergens(avoided)
            for ((name, ingredients) in foods) {
                val full = AllergenMatcher.scan(every, avoided, name, ingredients = ingredients, checkLabelTerms = terms)
                val fast = AllergenMatcher.scan(narrowed, avoided, name, ingredients = ingredients, checkLabelTerms = terms)
                assertThat(fast).isEqualTo(full)
            }
        }
    }

    @Test
    fun `only the avoided allergens keywords are kept`() {
        val kept = ScanIndexBuilder.relevantAliases(every, setOf("dairy"))
        assertThat(kept).isNotEmpty()
        assertThat(kept.map { it.allergen.lowercase() }.toSet()).containsExactly("dairy")
        assertThat(ScanIndexBuilder.relevantAliases(every, emptySet())).isEmpty()
    }
}
