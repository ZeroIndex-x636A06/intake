package com.zeroindex.intake.domain.allergen

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.zeroindex.intake.domain.model.AllergenMatchKind
import com.zeroindex.intake.domain.model.KeywordLevel
import org.junit.Test

/**
 * The false alarms in the v3 brief, rebuilt with our own wording, plus the hard cases that must keep
 * blocking and the safety rule: panels and tags are never negation-downgraded.
 */
class NegationAwareMatchingTest {

    private val aliases = AllergenCatalog.aliases()

    private fun scan(
        text: String,
        allergen: String = "dairy",
        freeText: Boolean = true,
        ingredients: String? = null,
        panel: String? = null,
        inIngredients: Boolean = false,
        contains: List<String> = emptyList(),
        traces: List<String> = emptyList(),
        extra: List<AllergenAlias> = emptyList(),
    ) = AllergenMatcher.scan(
        aliases = aliases + extra,
        avoidedAllergens = setOf(allergen),
        name = text,
        ingredients = ingredients,
        structuredContains = contains,
        structuredTraces = traces,
        checkLabelTerms = HiddenCarriers.forAllergen(allergen),
        checkLabelInIngredients = inIngredients,
        freeText = freeText,
        panelText = panel,
    )

    private fun AllergenScanResult.worst(): AllergenMatchKind? = matches.maxByOrNull { it.kind.rank }?.kind

    private fun assertMentionedOnly(r: AllergenScanResult) {
        assertThat(r.blocks).isFalse()
        assertThat(r.hasAnything).isFalse()
        assertThat(r.mentionedAvoided).isNotEmpty()
    }

    // ---- the cases from the brief ------------------------------------------------------------

    @Test fun `made with oil, not butter`() = assertMentionedOnly(scan("sauce made with oil, not butter"))

    @Test fun `checking the bun for milk is mentioned and the bun is already being checked`() {
        val r = scan("chili dogs, checking the bun for milk")
        assertMentionedOnly(r)
        assertThat(r.checkLabel).isEmpty()
    }

    @Test fun `frito pie without the cheese is never blocked`() {
        val r = scan("Frito pie without the cheese")
        assertThat(r.blocks).isFalse()
        assertThat(r.hasAnything).isFalse()
        assertThat(r.mentionedAvoided.map { it.matchedTerm }).contains("cheese")
    }

    @Test fun `skip the cheese and sour cream negates both`() {
        val r = scan("Chili, plain, skip the cheese and sour cream")
        assertMentionedOnly(r)
        assertThat(r.mentionedAvoided.map { it.matchedTerm }).containsAtLeast("cheese", "sour cream")
        assertThat(r.matches.none { it.kind == AllergenMatchKind.CONTAINS }).isTrue()
    }

    @Test fun `self hit - the word dairy in dairy-free is not a hit`() {
        assertThat(scan("Dairy-free mac and beef").matches).isEmpty()
        assertThat(scan("Dairy free mac and beef").matches).isEmpty()
    }

    @Test fun `dairy-free provolone is check the label with the casein reason`() {
        val r = scan("dairy-free provolone")
        assertThat(r.hasAnything).isFalse()
        assertThat(r.checkLabel.single().note).isEqualTo("Some dairy-free cheeses still contain casein")
    }

    @Test fun `dairy-free cheese slice, shreds and butter are check the label`() {
        assertThat(scan("dairy-free cheese slice").checkLabel).isNotEmpty()
        assertThat(scan("dairy-free cheese slice").hasAnything).isFalse()
        val shreds = scan("dairy-free shreds")
        assertThat(shreds.checkLabel.single().reason).isEqualTo("Some dairy-free cheeses still contain casein")
        val butter = scan("dairy-free butter")
        assertThat(butter.checkLabel).isNotEmpty()
        assertThat(butter.hasAnything).isFalse()
    }

    @Test fun `oat milk and other plant milks are check the label, never may contain`() {
        listOf(
            "oat milk", "almond milk", "soy milk", "coconut milk", "rice milk", "cashew milk", "pea milk", "hemp milk",
            "vegan butter", "plant butter", "plant-based butter",
        ).forEach { food ->
            val r = scan(food)
            assertThat(r.worst()).isEqualTo(AllergenMatchKind.CHECK_LABEL)
            assertThat(r.mayContain).isEmpty()
            assertThat(r.checkLabel.first().reason).isNotEmpty()
        }
    }

    @Test fun `soy milk is still soy for a soy allergy`() {
        val r = scan("soy milk", allergen = "soy")
        assertThat(r.blocks).isTrue()
    }

    @Test fun `no cheese at Subway is mentioned as avoided`() {
        val r = scan("Rotisserie chicken, turkey, roast beef, or ham, doubled, no cheese, oil and vinegar or mustard")
        assertThat(r.worst()).isEqualTo(AllergenMatchKind.MENTIONED_AVOIDED)
    }

    @Test fun `none of the brief cases block`() {
        listOf(
            "sauce made with oil, not butter", "checking the bun for milk", "Frito pie without the cheese",
            "Chili, plain, skip the cheese and sour cream", "Dairy-free mac and beef", "dairy-free provolone", "oat milk",
            "dairy-free cheese slice", "no cheese",
        ).forEach { assertThat(scan(it).blocks).isFalse() }
    }

    // ---- hard cases still block --------------------------------------------------------------

    @Test fun `hard cases still block`() {
        listOf(
            "Grilled cheese sandwich", "cheese pizza", "lactose-free milk", "butter chicken", "whey protein",
            "buttermilk pancakes", "cream of mushroom soup",
        ).forEach { assertWithMessage(it).that(scan(it).blocks).isTrue() }
    }

    @Test fun `lactose-free never negates`() {
        val r = scan("lactose-free milk")
        assertThat(r.blocks).isTrue()
        assertThat(scan("lactose-free yogurt").blocks).isTrue()
        assertThat(scan("lactose-free").blocks).isTrue()
    }

    @Test fun `non-dairy is check the label`() {
        val r = scan("non-dairy whipped topping")
        assertThat(r.hasAnything).isFalse()
        assertThat(r.checkLabel).isNotEmpty()
        assertThat(scan("nondairy creamer").hasAnything).isFalse()
    }

    @Test fun `a negated mention elsewhere does not rescue a real one in the same text`() {
        val r = scan("Cheeseburger with extra cheese, no cheese on the side")
        assertThat(r.blocks).isTrue()
        val r2 = scan("grilled cheese, skip the butter")
        assertThat(r2.blocks).isTrue()
        assertThat(r2.mentionedAvoided.map { it.matchedTerm }).contains("butter")
    }

    // ---- clause boundaries -------------------------------------------------------------------

    @Test fun `a comma ends the scope`() {
        assertThat(scan("no onions, cheese on top").blocks).isTrue()
    }

    @Test fun `and followed by a new verb starts a new clause`() {
        assertThat(scan("no cheese and add butter").blocks).isTrue()
        assertThat(scan("skip the sour cream and melt cheese on top").blocks).isTrue()
    }

    @Test fun `and followed by a noun stays in the clause`() {
        assertThat(scan("without cheese and butter").hasAnything).isFalse()
        assertThat(scan("without cheese and butter").mentionedAvoided).hasSize(2)
    }

    @Test fun `a period or parenthesis ends the scope`() {
        assertThat(scan("no onions. Cheese on top").blocks).isTrue()
        assertThat(scan("no onions (cheese optional)").blocks).isTrue()
    }

    @Test fun `negation does not reach backwards`() {
        assertThat(scan("cheese, no salt").blocks).isTrue()
        assertThat(scan("cheese no salt").blocks).isTrue()
    }

    @Test fun `a negator reaches only a few words`() {
        assertThat(scan("no added salt chicken breast roasted in butter").blocks).isTrue()
    }

    @Test fun `no-bake is not a negation`() {
        assertThat(scan("no-bake cheesecake").hasAnything).isTrue()
    }

    @Test fun `hold the, minus, free of and without all negate`() {
        listOf("burger, hold the cheese", "salad minus cheese", "bowl free of cheese", "tacos without cheese").forEach {
            assertWithMessage(it).that(scan(it).hasAnything).isFalse()
            assertWithMessage(it).that(scan(it).mentionedAvoided).isNotEmpty()
        }
    }

    // ---- the safety rule ---------------------------------------------------------------------

    @Test fun `ingredient panels are never negation downgraded`() {
        val r = scan("Crackers", freeText = true, panel = "wheat flour, no milk, contains: milk")
        assertThat(r.blocks).isTrue()
        assertThat(scan("Crackers", freeText = false, ingredients = "contains: milk").blocks).isTrue()
        assertThat(scan("x", freeText = true, panel = "may contain milk").blocks).isTrue()
    }

    @Test fun `negation off by default for names too`() {
        assertThat(scan("Pizza without cheese", freeText = false).blocks).isTrue()
    }

    @Test fun `ingredient text is only free text when the caller says so`() {
        val panelLike = scan("Bowl", ingredients = "rice, no cheese", inIngredients = false)
        assertThat(panelLike.blocks).isTrue()
        val lines = scan("Bowl", ingredients = "rice\nno cheese", inIngredients = true)
        assertThat(lines.blocks).isFalse()
    }

    @Test fun `open food facts tags are never downgraded`() {
        assertThat(scan("Dairy-free bar, no cheese", contains = listOf("en:milk")).blocks).isTrue()
        assertThat(scan("Bar", traces = listOf("en:milk")).mayContain).isNotEmpty()
    }

    @Test fun `a model can raise a warning but never clear one`() {
        val local = scan("Chili, skip the cheese")
        val verdict = ModelAllergenPolicy.merge(
            local = listOf(local),
            claims = listOf(ModelAllergenClaim("Chili, skip the cheese", listOf("dairy"), "chili is often topped with cheese")),
            avoided = setOf("dairy"),
        )
        assertThat(verdict.perItem[0]).isEqualTo(AllergenMatchKind.MAY_CONTAIN)
        val hard = ModelAllergenPolicy.merge(
            local = listOf(scan("cheese pizza")),
            claims = listOf(ModelAllergenClaim("cheese pizza", emptyList(), "")),
            avoided = setOf("dairy"),
        )
        assertThat(hard.perItem[0]).isEqualTo(AllergenMatchKind.CONTAINS)
        assertThat(hard.scan.blocks).isTrue()
    }

    @Test fun `mentioned as avoided is not a per item flag`() {
        val verdict = ModelAllergenPolicy.merge(listOf(scan("no cheese")), emptyList(), setOf("dairy"))
        assertThat(verdict.perItem).isEmpty()
        assertThat(verdict.scan.mentionedAvoided).isNotEmpty()
    }

    // ---- exclusions and the other allergens --------------------------------------------------

    @Test fun `peanut butter and cocoa butter are not dairy butter`() {
        assertThat(scan("Apples with peanut butter").matches).isEmpty()
        assertThat(scan("cocoa butter").matches).isEmpty()
        assertThat(scan("cream of tartar").matches).isEmpty()
    }

    @Test fun `almond flour is not wheat flour`() {
        assertThat(scan("almond flour pancakes", allergen = "wheat").blocks).isFalse()
        assertThat(scan("wheat flour", allergen = "wheat").blocks).isTrue()
    }

    @Test fun `gluten-free is a self hit and a substitute`() {
        val r = scan("gluten-free bread", allergen = "gluten")
        assertThat(r.blocks).isFalse()
        assertThat(r.mayContain).isEmpty()
        assertThat(r.checkLabel).isNotEmpty()
    }

    @Test fun `negation works for other allergens`() {
        val r = scan("Pad thai, no peanuts", allergen = "peanuts")
        assertThat(r.blocks).isFalse()
        assertThat(r.mentionedAvoided).isNotEmpty()
        assertThat(scan("Pad thai with peanuts", allergen = "peanuts").blocks).isTrue()
    }

    @Test fun `a custom keyword at each level behaves at that level`() {
        val extra = listOf(
            AllergenAlias("nightshades", "tomato", KeywordLevel.CONTAINS),
            AllergenAlias("nightshades", "paprika", KeywordLevel.MAY_CONTAIN),
            AllergenAlias("nightshades", "spice blend", KeywordLevel.CHECK_LABEL),
        )
        assertThat(scan("tomato soup", allergen = "nightshades", extra = extra).blocks).isTrue()
        assertThat(scan("paprika chicken", allergen = "nightshades", extra = extra).mayContain).isNotEmpty()
        assertThat(scan("taco spice blend", allergen = "nightshades", extra = extra).checkLabel).isNotEmpty()
        assertThat(scan("pasta, no tomato", allergen = "nightshades", extra = extra).mentionedAvoided).isNotEmpty()
        assertThat(scan("nightshade-free pasta", allergen = "nightshades", extra = extra).matches).isEmpty()
    }

    // ---- the catalog -------------------------------------------------------------------------

    @Test fun `all fifteen allergens ship, each with a deep list`() {
        assertThat(AllergenCatalog.ALL.map { it.key }).containsExactly(
            "dairy", "egg", "fish", "shellfish", "tree nuts", "peanuts", "wheat", "soy", "sesame",
            "gluten", "celery", "mustard", "lupin", "molluscs", "sulphites",
        )
        AllergenCatalog.ALL.forEach { a ->
            assertWithMessage(a.key).that(a.keywords.size).isAtLeast(20)
            assertWithMessage(a.key).that(a.keywords.map { it.term }).containsNoDuplicates()
            assertWithMessage("carriers ${a.key}").that(HiddenCarriers.forAllergen(a.key)).isNotEmpty()
        }
    }

    @Test fun `every original dairy alias is still there at contains`() {
        val dairy = AllergenCatalog.find("dairy")!!.keywords.associate { it.term to it.level }
        AllergenCatalog.DAIRY_CORE.forEach { assertThat(dairy[it]).isEqualTo(KeywordLevel.CONTAINS) }
    }

    @Test fun `spot checks across the list`() {
        fun blocks(allergen: String, text: String) = scan(text, allergen = allergen, freeText = false).blocks
        assertThat(blocks("egg", "mayonnaise")).isTrue()
        assertThat(blocks("fish", "anchovies")).isTrue()
        assertThat(blocks("shellfish", "shrimp scampi")).isTrue()
        assertThat(blocks("molluscs", "oyster sauce")).isTrue()
        assertThat(blocks("molluscs", "oyster mushrooms")).isFalse()
        assertThat(blocks("tree nuts", "walnut brownie")).isTrue()
        assertThat(blocks("tree nuts", "water chestnuts")).isFalse()
        assertThat(blocks("peanuts", "groundnut oil")).isTrue()
        assertThat(blocks("soy", "tofu stir fry")).isTrue()
        assertThat(blocks("sesame", "tahini dressing")).isTrue()
        assertThat(blocks("celery", "celery salt")).isTrue()
        assertThat(blocks("mustard", "dijon vinaigrette")).isTrue()
        assertThat(blocks("lupin", "lupin flour")).isTrue()
        assertThat(blocks("sulphites", "sodium metabisulphite")).isTrue()
        assertThat(blocks("gluten", "barley malt")).isTrue()
        assertThat(blocks("wheat", "semolina")).isTrue()
    }

    @Test fun `plural and hyphen forms match`() {
        assertThat(scan("Cheeses", freeText = false).blocks).isTrue()
        assertThat(scan("half-and-half", freeText = false).blocks).isTrue()
        assertThat(scan("Crème fraîche", freeText = false).blocks).isTrue()
    }

    @Test fun `summary line is plain language`() {
        assertThat(com.zeroindex.intake.data.plan.ImportSummary.line(2, 2, 3, 3))
            .isEqualTo("2 need a decision · 5 worth a look · 3 mentioned as avoided")
        assertThat(com.zeroindex.intake.data.plan.ImportSummary.line(0, 0, 0, 0)).isEqualTo("Nothing flagged")
    }
}
