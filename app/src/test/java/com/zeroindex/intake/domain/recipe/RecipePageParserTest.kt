package com.zeroindex.intake.domain.recipe

import com.google.common.truth.Truth.assertThat
import kotlin.random.Random
import org.junit.Test

class RecipePageParserTest {

    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResource("/recipes/$name")) { "missing fixture $name" }.readText()

    private fun found(name: String, url: String? = "https://example.test/page"): RecipeDraft =
        (RecipePageParser.parse(fixture(name), url) as PageParse.Found).recipe

    @Test fun `json ld graph with a multi typed recipe`() {
        val r = found("graph.html")
        assertThat(r.source).isEqualTo(RecipeSource.JSON_LD)
        assertThat(r.name).isEqualTo("Lemon Chicken Bowls & Rice")
        assertThat(r.servings).isEqualTo(4)
        assertThat(r.totalMinutes).isEqualTo(80)
        assertThat(r.imageUrl).isEqualTo("https://example.test/img/lemon.jpg")
        assertThat(r.url).isEqualTo("https://example.test/lemon-chicken")
        assertThat(r.ingredients).hasSize(5)
        assertThat(r.ingredients[0]).isEqualTo("1 ½ lb chicken thighs")
        assertThat(r.ingredients[2]).isEqualTo("1–2 lemons, juiced")
        assertThat(r.steps).containsExactly("Season the chicken.", "Roast for 35 minutes.", "Serve over rice.").inOrder()
        val n = r.nutrition!!
        assertThat(n.kcal).isEqualTo(520.0)
        assertThat(n.proteinG).isEqualTo(42.0)
        assertThat(n.carbsG).isEqualTo(48.5)
        assertThat(n.fatG).isEqualTo(16.0)
    }

    @Test fun `json ld top level array, string ingredients and numbered string instructions`() {
        val r = found("array.html")
        assertThat(r.name).isEqualTo("Plain Pancakes")
        assertThat(r.servings).isEqualTo(12)
        assertThat(r.totalMinutes).isEqualTo(25)
        assertThat(r.ingredients).containsExactly("2 cups flour", "1 tbsp sugar", "2 eggs").inOrder()
        assertThat(r.steps).containsExactly("Mix everything.", "Fry in a pan.", "Serve warm.").inOrder()
        assertThat(r.imageUrl).isEqualTo("https://example.test/a.jpg")
        assertThat(r.nutrition).isNull()
        // No url in the data: the page address is kept.
        assertThat(r.url).isEqualTo("https://example.test/page")
    }

    @Test fun `how to sections, numeric yield, summed prep and cook time, and a cdata wrapper`() {
        val r = found("sections.html")
        assertThat(r.name).isEqualTo("Tacos")
        assertThat(r.servings).isEqualTo(6)
        assertThat(r.totalMinutes).isEqualTo(40)
        assertThat(r.steps).containsExactly("Meat: Brown the beef.", "Meat: Add spices.", "Assemble: Fill the shells.").inOrder()
        assertThat(r.ingredients).hasSize(3)
    }

    @Test fun `microdata is the fallback`() {
        val r = found("microdata.html")
        assertThat(r.source).isEqualTo(RecipeSource.MICRODATA)
        // The author's name is inside a nested item and is not the recipe's name.
        assertThat(r.name).isEqualTo("Oat Porridge")
        assertThat(r.servings).isEqualTo(2)
        assertThat(r.totalMinutes).isEqualTo(15)
        assertThat(r.imageUrl).isEqualTo("https://example.test/oats.jpg")
        assertThat(r.ingredients).containsExactly("1 cup rolled oats", "2 cups water", "A pinch of salt").inOrder()
        assertThat(r.steps).containsExactly("Boil the water.", "Stir in the oats and cook 8 minutes.").inOrder()
        assertThat(r.nutrition).isEqualTo(PageNutrition(310.0, 11.0, 54.0, 6.0))
    }

    @Test fun `a page with no recipe data keeps its visible text for the optional ai step`() {
        val p = RecipePageParser.parse(fixture("nodata.html"), null) as PageParse.NoRecipe
        assertThat(p.pageText).contains("My trip to Italy")
        assertThat(p.pageText).contains("pasta & gelato")
        assertThat(p.pageText).doesNotContain("var x")
        assertThat(p.pageText).doesNotContain("color:red")
        assertThat(p.pageText).doesNotContain("Home")
    }

    @Test fun `malformed json and unclosed markup never throw`() {
        val p = RecipePageParser.parse(fixture("malformed.html"), null)
        // Nothing usable from the broken script blocks; whatever microdata survives is not the broken JSON recipe.
        if (p is PageParse.Found) assertThat(p.recipe.name).isNotEqualTo("Broken")
    }

    @Test fun `truncated json that the raw newline repair can read`() {
        val html = "<script type=\"application/ld+json\">{\"@type\":\"Recipe\",\"name\":\"Line\nBreak\",\"recipeIngredient\":[\"1 egg\"]}</script>"
        val r = (RecipePageParser.parse(html) as PageParse.Found).recipe
        assertThat(r.name).isEqualTo("Line Break")
    }

    @Test fun `recipe with no ingredients or steps is not a recipe`() {
        val html = "<script type=\"application/ld+json\">{\"@type\":\"Recipe\",\"name\":\"Empty\"}</script>"
        assertThat(RecipePageParser.parse(html)).isInstanceOf(PageParse.NoRecipe::class.java)
    }

    @Test fun `kilojoules are converted and units are tolerated`() {
        val n = RecipePageParser.nutritionOf("1046 kJ", "10,5 g", null, "5g")!!
        assertThat(n.kcal).isWithin(0.5).of(250.0)
        assertThat(n.proteinG).isEqualTo(10.5)
        assertThat(n.carbsG).isNull()
        assertThat(n.fatG).isEqualTo(5.0)
        assertThat(RecipePageParser.nutritionOf(null, null, null, null)).isNull()
    }

    @Test fun `yields`() {
        fun yield(v: String) = (RecipePageParser.parse(
            "<script type=\"application/ld+json\">{\"@type\":\"Recipe\",\"name\":\"x\",\"recipeYield\":$v,\"recipeIngredient\":[\"a\"]}</script>",
        ) as PageParse.Found).recipe.servings
        assertThat(yield("\"4 to 6 servings\"")).isEqualTo(6)
        assertThat(yield("\"Serves 8\"")).isEqualTo(8)
        assertThat(yield("[\"2\",\"2 people\"]")).isEqualTo(2)
        assertThat(yield("\"a loaf\"")).isNull()
        assertThat(yield("0")).isNull()
    }

    @Test fun `random and hostile input never throws`() {
        val rnd = Random(7)
        val pieces = listOf("<script type=\"application/ld+json\">", "</script>", "{", "}", "[", "]", "\"@type\":\"Recipe\"", ",", ":", "itemscope",
            "itemtype=\"https://schema.org/Recipe\"", "<div ", ">", "itemprop=\"name\"", "<li itemprop=\"recipeIngredient\">", "</li>", "\"", "'", "<", "&#x110000;", "&amp", "\u0000")
        repeat(2000) {
            val s = buildString { repeat(rnd.nextInt(1, 40)) { append(pieces[rnd.nextInt(pieces.size)]) } }
            RecipePageParser.parse(s, null)
        }
        RecipePageParser.parse("", null)
        RecipePageParser.parse("<script type=\"application/ld+json\">" + "[".repeat(100_000) + "</script>", null)
    }

    @Test fun `html entities`() {
        assertThat(HtmlText.decode("Tom &amp; Jerry &#39;s &#x41; &bogus; &#0;")).isEqualTo("Tom & Jerry 's A &bogus; &#0;")
    }
}

class IsoDurationTest {
    @Test fun iso() {
        assertThat(IsoDuration.minutes("PT30M")).isEqualTo(30)
        assertThat(IsoDuration.minutes("PT1H30M")).isEqualTo(90)
        assertThat(IsoDuration.minutes("P0DT45M")).isEqualTo(45)
        assertThat(IsoDuration.minutes("P1DT2H")).isEqualTo(26 * 60)
        assertThat(IsoDuration.minutes("PT90S")).isEqualTo(2)
        assertThat(IsoDuration.minutes("pt20m")).isEqualTo(20)
        assertThat(IsoDuration.minutes("PT1.5H")).isEqualTo(90)
    }

    @Test fun `not durations`() {
        assertThat(IsoDuration.minutes(null)).isNull()
        assertThat(IsoDuration.minutes("")).isNull()
        assertThat(IsoDuration.minutes("P")).isNull()
        assertThat(IsoDuration.minutes("PT")).isNull()
        assertThat(IsoDuration.minutes("PT0M")).isNull()
        assertThat(IsoDuration.minutes("soon")).isNull()
    }

    @Test fun `plain minutes and words`() {
        assertThat(IsoDuration.minutes("45 min")).isEqualTo(45)
        assertThat(IsoDuration.minutes("20")).isEqualTo(20)
    }
}

class IngredientLineParserTest {
    private fun p(s: String) = IngredientLineParser.parse(s)

    @Test fun `quantity unit and name`() {
        val a = p("2 cups milk")
        assertThat(a.quantity).isEqualTo(2.0)
        assertThat(a.unit).isEqualTo("cups")
        assertThat(a.name).isEqualTo("milk")
        assertThat(a.grams).isNull()
    }

    @Test fun `fractions`() {
        assertThat(p("1 ½ cups rice").quantity).isEqualTo(1.5)
        assertThat(p("1½ cups rice").quantity).isEqualTo(1.5)
        assertThat(p("1 1/2 cups rice").quantity).isEqualTo(1.5)
        assertThat(p("¾ tsp salt").quantity).isEqualTo(0.75)
        assertThat(p("⅓ cup oil").name).isEqualTo("oil")
        assertThat(p("1 ½ cups rice").quantityText).isEqualTo("1 1/2 cups")
    }

    @Test fun `ranges use the upper value`() {
        val r = p("1-2 tbsp olive oil")
        assertThat(r.quantity).isEqualTo(2.0)
        assertThat(r.name).isEqualTo("olive oil")
        assertThat(p("1–2 lemons").quantity).isEqualTo(2.0)
    }

    @Test fun `weights give grams and volumes do not`() {
        assertThat(p("200 g chicken breast").grams).isEqualTo(200.0)
        assertThat(p("1 lb ground beef").grams).isWithin(0.1).of(453.6)
        assertThat(p("8 oz cheddar").grams).isWithin(0.1).of(226.8)
        assertThat(p("1 cup flour").grams).isNull()
    }

    @Test fun `no leading amount is all name`() {
        val s = p("Salt to taste")
        assertThat(s.quantity).isNull()
        assertThat(s.name).isEqualTo("Salt to taste")
        assertThat(p("").name).isEqualTo("")
        assertThat(p("3").quantity).isNull() // an amount with no name is just text
    }

    @Test fun `a plain count`() {
        val e = p("3 eggs")
        assertThat(e.quantity).isEqualTo(3.0)
        assertThat(e.unit).isNull()
        assertThat(e.name).isEqualTo("eggs")
    }
}
