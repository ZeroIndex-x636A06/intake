package com.zeroindex.intake.domain.allergen

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.domain.model.AllergenMatchKind
import com.zeroindex.intake.domain.model.FoodSource
import org.junit.Test

/** QA pass 1 allergen findings: honest clear wording, traces, override notes, where a match was found, templates. */
class QaPass1AllergenTest {

    private val dairy = listOf("milk", "butter", "cheese", "whey", "cream").map { AllergenAlias("dairy", it) }

    private fun scan(
        name: String? = null,
        ingredients: String? = null,
        tags: List<String> = emptyList(),
    ) = AllergenMatcher.scan(
        aliases = dairy,
        avoidedAllergens = setOf("dairy"),
        name = name,
        ingredients = ingredients,
        structuredContains = tags,
        checkLabelTerms = HiddenCarriers.DAIRY_TERMS,
    )

    // ---- Q1-4: a clear result says what it rests on ------------------------------------------------------------------

    @Test
    fun `a clear result with no label data is never the green fits-your-allergens wording`() {
        for (source in FoodSource.entries) {
            for (verified in listOf(true, false)) {
                val basis = AllergenEvidence.clearBasis(source, verified, "")
                assertThat(basis).isNotEqualTo(AllergenEvidence.ClearBasis.LABEL_READ)
                assertThat(basis.isCaution).isTrue()
            }
        }
    }

    @Test
    fun `a generic food reads name only and a bare packaged product reads no ingredient list`() {
        assertThat(AllergenEvidence.clearBasis(FoodSource.DATABASE, true, "")).isEqualTo(AllergenEvidence.ClearBasis.NAME_ONLY)
        assertThat(AllergenEvidence.clearBasis(FoodSource.MANUAL, false, "")).isEqualTo(AllergenEvidence.ClearBasis.NAME_ONLY)
        assertThat(AllergenEvidence.clearBasis(FoodSource.OPEN_FOOD_FACTS, false, "")).isEqualTo(AllergenEvidence.ClearBasis.NO_LIST)
        assertThat(AllergenEvidence.ClearBasis.NO_LIST.chipText).contains("check the label")
    }

    @Test
    fun `an ingredient list or tags make the clear result a label read`() {
        assertThat(AllergenEvidence.clearBasis(FoodSource.OPEN_FOOD_FACTS, false, "en:gluten")).isEqualTo(AllergenEvidence.ClearBasis.LABEL_READ)
        assertThat(AllergenEvidence.clearBasis(FoodSource.MANUAL, false, "flour, water, salt")).isEqualTo(AllergenEvidence.ClearBasis.LABEL_READ)
        assertThat(AllergenEvidence.ClearBasis.LABEL_READ.chipText).isEqualTo("Fits your allergens")
    }

    // ---- Q1-14: "may contain" on a panel is worded as a trace, and is still a warning ---------------------------------

    @Test
    fun `may contain milk on a panel is a trace, not an ingredient, and is still a blocking warning`() {
        val r = scan(name = "Dark chocolate 70%", ingredients = "cocoa mass, sugar, cocoa butter | may contain milk")
        // The tier is not lowered (CLAUDE.md: a panel's "may contain milk" stays a hard warning).
        assertThat(r.blocks).isTrue()
        assertThat(r.hasAnything).isTrue()
        val m = r.contains.single { it.matchedTerm == "milk" }
        assertThat(m.trace).isTrue()
        assertThat(m.note).contains("possible trace")
        assertThat(r.containsOnlyTraces).isTrue()
    }

    @Test
    fun `traces of and facility statements are traces`() {
        assertThat(scan(ingredients = "oats, sugar. Traces of milk.").containsOnlyTraces).isTrue()
        assertThat(scan(ingredients = "oats; made in a facility that also processes whey").containsOnlyTraces).isTrue()
    }

    @Test
    fun `milk as a real ingredient is not a trace even when a trace line names it too`() {
        val r = scan(ingredients = "sugar, milk powder, cocoa. May contain milk.")
        assertThat(r.blocks).isTrue()
        assertThat(r.containsOnlyTraces).isFalse()
        assertThat(r.contains.filter { it.matchedTerm == "milk" }.none { it.trace }).isTrue()
    }

    @Test
    fun `a contains declaration after a may contain is a real ingredient hit`() {
        val r = scan(ingredients = "sugar, may contain cream, contains butter")
        assertThat(r.contains.single { it.matchedTerm == "butter" }.trace).isFalse()
        assertThat(r.containsOnlyTraces).isFalse()
    }

    @Test
    fun `product allergen tags are never worded as traces`() {
        val r = scan(name = "Chocolate", tags = listOf("en:milk"), ingredients = "cocoa, may contain milk")
        assertThat(r.contains.any { it.field == MatchField.STRUCTURED_TAG && !it.trace }).isTrue()
        assertThat(r.containsOnlyTraces).isFalse()
    }

    @Test
    fun `the sheet body says trace for a trace-only match`() {
        val r = scan(ingredients = "sugar | may contain milk")
        assertThat(MatchWording.blockedBody(r.contains)).contains("possible trace")
        assertThat(MatchWording.blockedBody(scan(name = "Milk").contains)).doesNotContain("trace")
    }

    @Test
    fun `text without a trace marker is split into nothing`() {
        val s = TraceText.split("flour, butter, salt")
        assertThat(s.main).isEqualTo("flour, butter, salt")
        assertThat(s.traces).isNull()
    }

    // ---- Q1-1: every override path records the note ------------------------------------------------------------------

    @Test
    fun `the override note names every allergen the scan raised`() {
        val r = AllergenMatcher.scan(
            aliases = dairy + AllergenAlias("egg", "egg") + AllergenAlias("soy", "soy", com.zeroindex.intake.domain.model.KeywordLevel.MAY_CONTAIN),
            avoidedAllergens = setOf("dairy", "egg", "soy"),
            name = "Egg and cheese soy bowl",
        )
        val note = AllergenOverride.noteFor(r)
        assertThat(note).startsWith("Logged despite: ")
        assertThat(note).contains("dairy")
        assertThat(note).contains("egg")
        assertThat(note).contains("soy")
    }

    @Test
    fun `annotating with the note puts it on the name and the serving label`() {
        val note = AllergenOverride.noteFor(scan(name = "Milk, canned"))
        val (name, label) = AllergenOverride.annotate("Milk, canned", "38 g", note)
        assertThat(name).contains("Logged despite: dairy")
        assertThat(label).contains("Logged despite: dairy")
    }

    // ---- Q1-24: where it matched -------------------------------------------------------------------------------------

    @Test
    fun `the warning says the name when the match was in the name`() {
        val r = scan(name = "Milk, canned, condensed")
        assertThat(MatchWording.where(r.contains)).isEqualTo("the name")
        assertThat(MatchWording.blockedBody(r.contains)).contains("matched the name")
        assertThat(MatchWording.blockedBody(r.contains)).doesNotContain("an ingredient")
    }

    @Test
    fun `the warning says the ingredient list when that is where it matched`() {
        val r = scan(name = "Soup", ingredients = "water, cream, salt")
        assertThat(MatchWording.where(r.contains)).isEqualTo("the ingredient list")
    }

    @Test
    fun `the warning names both places and tags`() {
        val r = scan(name = "Cheese soup", ingredients = "water, cream", tags = listOf("en:milk"))
        val where = MatchWording.where(r.contains)
        assertThat(where).contains("the name")
        assertThat(where).contains("the ingredient list")
        assertThat(where).contains("allergen tags")
        assertThat(MatchWording.foundIn(r.contains.first { it.field == MatchField.NAME })).isEqualTo("found in the name")
    }

    // ---- Q1-21: templates ----------------------------------------------------------------------------------------------

    @Test
    fun `a template verdict is the worst verdict of any food in it`() {
        val shake = scan(name = "Protein powder", ingredients = "whey protein")
        val milk = scan(name = "Almond milk")
        val banana = scan(name = "Banana")
        val merged = TemplateAllergens.merge(listOf(banana, milk, shake))
        assertThat(merged.blocks).isTrue()
        assertThat(merged.contains).isNotEmpty()
        assertThat(TemplateAllergens.merge(listOf(banana)).matches).isEmpty()
        assertThat(TemplateAllergens.merge(emptyList()).matches).isEmpty()
    }

    @Test
    fun `logging a template with almond milk carries a check-the-label note`() {
        val almond = scan(name = "Almond milk")
        assertThat(almond.hasCheckLabel).isTrue()
        val note = TemplateAllergens.logNote(listOf("Almond milk" to almond, "Banana" to scan(name = "Banana")))
        assertThat(note).isNotNull()
        assertThat(note!!).contains("Almond milk")
        assertThat(note).contains("check the label")
        assertThat(note).contains("dairy")
        assertThat(TemplateAllergens.logNote(listOf("Banana" to scan(name = "Banana")))).isNull()
    }
}
