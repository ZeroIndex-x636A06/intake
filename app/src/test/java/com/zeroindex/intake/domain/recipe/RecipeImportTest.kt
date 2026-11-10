package com.zeroindex.intake.domain.recipe

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.domain.allergen.AllergenAlias
import com.zeroindex.intake.domain.allergen.AllergenMatcher
import org.junit.Test

/** The recipe import improvements, tested against small hand-written pages shaped like WPRM and Tasty Recipes. */
class RecipeImportTest {

    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResource("/recipes/$name")) { "missing fixture $name" }.readText()

    private fun found(name: String): RecipeDraft =
        (RecipePageParser.parse(fixture(name), "https://example.test/page") as PageParse.Found).recipe

    private fun foundInline(jsonLd: String): RecipeDraft =
        (
            RecipePageParser.parse(
                """<html><script type="application/ld+json">$jsonLd</script></html>""",
                "https://example.test/p",
            ) as PageParse.Found
            ).recipe

    // ---- 1. notes ----

    @Test fun `WPRM notes are read from the card and not from a script string`() {
        val notes = found("wprm-groups.html").notes!!
        assertThat(notes.lines()).containsExactly(
            "Vegan option: swap the cream for coconut cream.",
            "Store leftovers up to 3 days. Reheat with a splash of milk – the sauce thickens.",
        ).inOrder()
        assertThat(notes).doesNotContain("not the notes")
    }

    @Test fun `Tasty Recipes notes keep the list item and drop the Notes heading`() {
        val notes = found("tasty-groups.html").notes!!
        assertThat(notes).contains("Freeze unbaked rolls for up to a month.")
        assertThat(notes).contains("Use oat milk for a dairy-free version.")
        assertThat(notes).doesNotContain("Notes")
    }

    @Test fun `a page with no notes block has no notes`() {
        assertThat(found("array.html").notes).isNull()
    }

    // ---- 2. full nutrition ----

    @Test fun `fibre, sugar, saturated fat and sodium are kept, sodium in milligrams`() {
        val n = found("wprm-groups.html").nutrition!!
        assertThat(n.kcal).isEqualTo(919.0)
        assertThat(n.fiberG).isEqualTo(2.0)
        assertThat(n.sugarG).isEqualTo(2.0)
        assertThat(n.saturatedFatG).isEqualTo(37.0)
        assertThat(n.sodiumMg).isEqualTo(1179.0)
    }

    @Test fun `sodium given in grams is converted to milligrams and a bare number is milligrams`() {
        assertThat(found("tasty-groups.html").nutrition!!.sodiumMg).isWithin(1e-9).of(900.0)
        val n = RecipePageParser.nutritionOf("100", null, null, null, sodium = "450", fiber = "2000 mg")!!
        assertThat(n.sodiumMg).isEqualTo(450.0)
        assertThat(n.fiberG).isEqualTo(2.0)
        assertThat(RecipePageParser.nutritionOf(null, null, null, null, sodium = "1.2 g")!!.sodiumMg).isWithin(1e-9).of(1200.0)
    }

    // ---- 3. photo ----

    @Test fun `image as an array of strings, an ImageObject, and a plain string`() {
        assertThat(found("wprm-groups.html").imageUrl).isEqualTo("https://cdn.example.test/photos/pasta-1200.jpg")
        assertThat(found("tasty-groups.html").imageUrl).isEqualTo("https://img.example.test/rolls.jpg")
        val plain = foundInline(
            """{"@type":"Recipe","name":"X","image":"https://i.example.test/x.jpg","recipeIngredient":["1 egg"]}""",
        )
        assertThat(plain.imageUrl).isEqualTo("https://i.example.test/x.jpg")
        val objects = foundInline(
            """{"@type":"Recipe","name":"X","image":[{"@type":"ImageObject","url":"https://i.example.test/y.jpg"}],"recipeIngredient":["1 egg"]}""",
        )
        assertThat(objects.imageUrl).isEqualTo("https://i.example.test/y.jpg")
    }

    // ---- 5. times ----

    @Test fun `prep, cook and total are all kept and read as one line`() {
        val r = found("wprm-groups.html")
        assertThat(r.prepMinutes).isEqualTo(15)
        assertThat(r.cookMinutes).isEqualTo(18)
        assertThat(r.totalMinutes).isEqualTo(33)
        assertThat(RecipeTimes.label(r.prepMinutes, r.cookMinutes, r.totalMinutes)).isEqualTo("Prep 15 · Cook 18 · Total 33 min")
    }

    @Test fun `a missing total is the sum, and a lone total reads as minutes`() {
        val r = found("tasty-groups.html")
        assertThat(r.totalMinutes).isEqualTo(45)
        assertThat(RecipeTimes.label(r.prepMinutes, r.cookMinutes, r.totalMinutes)).isEqualTo("Prep 20 · Cook 25 · Total 45 min")
        assertThat(RecipeTimes.label(null, null, 30)).isEqualTo("30 min")
        assertThat(RecipeTimes.label(10, null, null)).isEqualTo("Prep 10 · Total 10 min")
        assertThat(RecipeTimes.label(null, null, null)).isNull()
    }

    // ---- 6. groups ----

    @Test fun `WPRM group headers are matched to the flat list, even with the same ingredient twice`() {
        val r = found("wprm-groups.html")
        assertThat(r.ingredients).hasSize(8)
        assertThat(r.ingredientGroups).containsExactly(
            "For the Noodles",
            "For the Chicken", "For the Chicken",
            "For the Sauce", "For the Sauce", "For the Sauce", "For the Sauce", "For the Sauce",
        ).inOrder()
        // The two butters are told apart by their group.
        assertThat(r.ingredients[2]).isEqualTo("1 tablespoon butter")
        assertThat(r.ingredientGroups[2]).isEqualTo("For the Chicken")
        assertThat(r.ingredients[3]).startsWith("1/2 cup butter")
        assertThat(r.ingredientGroups[3]).isEqualTo("For the Sauce")
    }

    @Test fun `Tasty Recipes h4 headers become groups and the Ingredients title does not`() {
        val r = found("tasty-groups.html")
        assertThat(r.ingredients).hasSize(4)
        assertThat(r.ingredientGroups).containsExactly("For the Dough", "For the Dough", "For the Filling", "For the Filling").inOrder()
    }

    @Test fun `a page with no named groups has no group labels`() {
        assertThat(found("array.html").ingredientGroups).isEmpty()
        val one = listOf(HtmlIngredientGroup(null, listOf("1 egg")))
        assertThat(RecipeHtmlExtras.labelsFor(listOf("1 egg"), one)).containsExactly(null)
    }

    @Test fun `when the lists cannot be matched the groups are not trusted`() {
        val groups = listOf(HtmlIngredientGroup("A", listOf("1 egg", "2 cups milk")), HtmlIngredientGroup("B", listOf("salt")))
        // Same length, text different: positional.
        assertThat(RecipeHtmlExtras.labelsFor(listOf("1 egg", "2 cups whole milk", "pinch of salt"), groups))
            .containsExactly("A", "A", "B").inOrder()
        // Different length and text: none.
        assertThat(RecipeHtmlExtras.labelsFor(listOf("1 egg", "tofu"), groups)).containsExactly(null, null)
    }

    @Test fun `groups survive the edit text round trip with ## headers`() {
        val lines = listOf("1 egg", "2 cups milk", "salt")
        val groups = listOf("Batter", "Batter", null)
        val text = IngredientSections.toTextWithHeaders(lines, groups)
        assertThat(text.lines()).containsExactly("## Batter", "1 egg", "2 cups milk", "##", "salt").inOrder()
        assertThat(IngredientSections.parseWithHeaders(text)).containsExactly(
            "Batter" to "1 egg", "Batter" to "2 cups milk", null to "salt",
        ).inOrder()
        assertThat(IngredientSections.parseWithHeaders("1 egg\n\n## For the Sauce:\n2 cups milk", defaultGroup = "Base"))
            .containsExactly("Base" to "1 egg", "For the Sauce" to "2 cups milk").inOrder()
    }

    @Test fun `consecutive lines with the same group form one section`() {
        val items = listOf("a" to "X", "b" to "X", "c" to null, "d" to "Y", "e" to "X")
        val sections = IngredientSections.of(items) { it.second }
        assertThat(sections.map { it.label }).containsExactly("X", null, "Y", "X").inOrder()
        assertThat(sections[0].items.map { it.first }).containsExactly("a", "b").inOrder()
    }

    @Test fun `stored group labels line up with the stored text lines`() {
        val encoded = IngredientGroupsCodec.encode(listOf("Sauce", null, "Sauce  hot", null))
        assertThat(encoded).isEqualTo("Sauce\n\nSauce hot\n")
        assertThat(IngredientGroupsCodec.decode(encoded, 4)).containsExactly("Sauce", null, "Sauce hot", null).inOrder()
        // A shorter or missing store is padded, never a crash: v2 recipes have none.
        assertThat(IngredientGroupsCodec.decode(null, 2)).containsExactly(null, null)
        assertThat(IngredientGroupsCodec.decode("A", 3)).containsExactly("A", null, null).inOrder()
        assertThat(IngredientGroupsCodec.encode(listOf(null, " "))).isNull()
    }

    // ---- 7. clean-up ----

    @Test fun `the WPRM comma artefact is removed from the imported lines`() {
        val r = found("wprm-groups.html")
        assertThat(r.ingredients[3]).isEqualTo("1/2 cup butter (cut into large cubes or slices)")
        assertThat(r.ingredients[5]).isEqualTo("1 clove garlic (minced)")
        assertThat(r.ingredients.none { it.contains("(,") }).isTrue()
    }

    @Test fun `entities are unescaped, including double-escaped ones, and spaces are collapsed`() {
        val r = found("wprm-groups.html")
        assertThat(r.ingredients[6]).isEqualTo("2 tablespoons Dijon & whole-grain mustard")
        assertThat(r.ingredients[7]).isEqualTo("1 cup vegan butter")
    }

    @Test fun `ingredient cleaner handles the stray punctuation cases`() {
        assertThat(IngredientCleaner.clean("1 clove garlic (, minced)")).isEqualTo("1 clove garlic (minced)")
        assertThat(IngredientCleaner.clean("2 onions ( , diced , )")).isEqualTo("2 onions (diced)")
        assertThat(IngredientCleaner.clean("1 cup flour ()")).isEqualTo("1 cup flour")
        assertThat(IngredientCleaner.clean("salt ,pepper")).isEqualTo("salt, pepper")
        assertThat(IngredientCleaner.clean("1,000 g flour")).isEqualTo("1,000 g flour")
        assertThat(IngredientCleaner.clean(", minced garlic,")).isEqualTo("minced garlic")
        assertThat(IngredientCleaner.clean("1&nbsp;cup  sugar &amp;amp; spice")).isEqualTo("1 cup sugar & spice")
        assertThat(IngredientCleaner.clean("it&amp;#8217;s")).isEqualTo("it’s")
        assertThat(IngredientCleaner.clean("   ")).isEmpty()
        assertThat(IngredientCleaner.cleanLabel("  For the Sauce: ")).isEqualTo("For the Sauce")
        assertThat(IngredientCleaner.cleanLabel(" : ")).isNull()
    }

    // ---- 4. allergen tags per line ----

    private val dairy = listOf(
        AllergenAlias("dairy", "butter"),
        AllergenAlias("dairy", "cream"),
        AllergenAlias("dairy", "milk"),
        AllergenAlias("dairy", "parmesan"),
    )

    private fun tag(line: String) = LineAllergens.tagOf(
        AllergenMatcher.scan(
            aliases = dairy, avoidedAllergens = setOf("dairy"), name = null, ingredients = line,
            checkLabelInIngredients = true,
        ),
    )

    @Test fun `each line is tagged by the normal ingredient rules`() {
        assertThat(tag("2 cups heavy whipping cream")!!.text).isEqualTo("contains dairy")
        assertThat(tag("2 cups heavy whipping cream")!!.severity).isEqualTo(LineAllergenTag.Severity.CONTAINS)
        val vegan = tag("1 cup vegan butter")!!
        assertThat(vegan.text).isEqualTo("check the label")
        assertThat(vegan.severity).isEqualTo(LineAllergenTag.Severity.CHECK_LABEL)
        assertThat(tag("16 ounces dry fettuccine pasta")).isNull()
    }

    @Test fun `an ingredient line is not read as free-text negation`() {
        // "no" in a recipe line does not clear the warning: that softening is only for a plan's own prose.
        assertThat(tag("no butter, just cream")!!.severity).isEqualTo(LineAllergenTag.Severity.CONTAINS)
    }

    @Test fun `flagged indexes name the lines to scroll to`() {
        val lines = found("wprm-groups.html").ingredients
        val tags = lines.map(::tag)
        val flagged = LineAllergens.flaggedIndexes(tags)
        assertThat(flagged).contains(2) // butter
        assertThat(flagged).contains(4) // cream
        assertThat(flagged).contains(7) // vegan butter
        assertThat(flagged).doesNotContain(0)
        assertThat(tags[7]!!.text).isEqualTo("check the label")
    }

    @Test fun `nutrition extras list only what the page gave`() {
        assertThat(RecipeNutritionLabels.extras(2.0, 2.0, 37.0, 1179.0)).containsExactly(
            "Fibre" to "2 g", "Sugar" to "2 g", "Saturated fat" to "37 g", "Sodium" to "1,179 mg",
        ).inOrder()
        assertThat(RecipeNutritionLabels.extras(null, 1.5, null, null)).containsExactly("Sugar" to "1.5 g")
        assertThat(RecipeNutritionLabels.extras(null, null, null, null)).isEmpty()
    }
}
