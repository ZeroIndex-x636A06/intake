package com.zeroindex.intake.domain.recipe

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * QA Q2-5: the estimate must pick the ordinary food for a line and a believable weight. One row per line: the food's
 * name must match [food] (and never [not]), and the grams must fall in [min]..[max]. Run against the real bundled file.
 */
class IngredientMatchTableTest {

    private class Row(val line: String, val food: String, val min: Double, val max: Double, val not: String? = null)

    private val table = listOf(
        // Milk and dairy: cow's milk, not sheep, goat or buffalo.
        Row("1 cup milk", "^Milk, (whole|reduced fat)", 225.0, 250.0, "sheep|goat|buffalo|human|dry|condensed|imitation"),
        Row("1 cup whole milk", "^Milk, whole", 225.0, 250.0),
        Row("1 cup 2% milk", "^Milk, reduced fat.*2%", 225.0, 250.0),
        Row("1 cup skim milk", "^Milk,.*nonfat", 225.0, 250.0),
        Row("1 cup almond milk", "almond milk, unsweetened", 230.0, 265.0),
        Row("1 cup sheep milk", "^Milk, sheep", 240.0, 250.0),
        Row("1 can coconut milk", "coconut milk, canned", 380.0, 420.0),
        Row("1/2 cup heavy cream", "^Cream, fluid, heavy", 115.0, 125.0),
        Row("1 cup sour cream", "^Cream, sour", 225.0, 235.0),
        Row("4 oz cream cheese", "^Cheese, cream", 110.0, 116.0),
        Row("1 cup plain yogurt", "^Yogurt, plain", 225.0, 250.0),
        Row("1 cup shredded cheddar cheese", "^Cheese, cheddar$", 100.0, 115.0),
        Row("1 cup shredded mozzarella", "^Cheese, mozzarella", 105.0, 120.0),
        Row("1 tbsp butter", "^Butter, salted", 13.0, 15.0, "peanut"),
        Row("2 tbsp peanut butter", "^Peanut butter", 30.0, 34.0),
        // Canned goods: a can has a believable weight, and a size on the line wins.
        Row("a can of tomatoes", "^Tomatoes, canned", 400.0, 415.0),
        Row("1 can diced tomatoes", "^Tomatoes, canned.*diced", 400.0, 415.0),
        Row("1 (14.5 oz) can diced tomatoes", "^Tomatoes, canned", 408.0, 414.0),
        Row("1 14.5 oz can diced tomatoes", "^Tomatoes, canned", 408.0, 414.0),
        Row("14.5 ounce can tomatoes", "^Tomatoes, canned", 408.0, 414.0),
        Row("1 (28 oz) can crushed tomatoes", "^Tomatoes, crushed", 790.0, 800.0),
        Row("2 cans tomatoes", "^Tomatoes, canned", 800.0, 830.0),
        Row("2 cups diced tomatoes", "^Tomatoes, red, ripe, raw", 280.0, 400.0),
        Row("1 can black beans", "^Beans, black.*canned", 400.0, 440.0),
        Row("1 can (15 oz) kidney beans", "^Beans, kidney.*canned", 420.0, 430.0),
        Row("1 can chickpeas", "^Chickpeas.*canned", 400.0, 450.0),
        Row("1 can tuna", "^Fish, tuna.*canned", 100.0, 175.0),
        Row("1 can corn", "^Corn, sweet.*canned", 280.0, 430.0),
        Row("1 tbsp tomato paste", "^Tomato products, canned, paste", 14.0, 18.0),
        // Stock and broth: the kind on the line decides; a bare "stock" is never beef.
        Row("4 cups vegetable stock", "vegetable broth", 850.0, 980.0, "beef|chicken"),
        Row("4 cups stock", "broth|stock", 850.0, 1000.0, "beef"),
        Row("2 cups beef broth", "beef", 400.0, 500.0, "chicken|vegetable"),
        Row("2 cups chicken stock", "^Soup, chicken broth", 440.0, 500.0, "beef"),
        Row("2 cups chicken broth", "^Soup, chicken broth", 440.0, 500.0),
        // Grains: dry unless the line says cooked.
        Row("1 cup white rice", "^Rice, white, long-grain, regular, raw", 180.0, 190.0),
        Row("1 cup cooked rice", "^Rice, white.*cooked", 150.0, 165.0),
        Row("2 cups cooked brown rice", "^Rice, brown.*cooked", 380.0, 420.0),
        Row("200 g spaghetti", "^Pasta, dry", 200.0, 200.0),
        Row("1 cup cooked pasta", "^Pasta, cooked", 110.0, 160.0),
        Row("1 cup rolled oats", "^Cereals, oats", 78.0, 100.0),
        Row("1 cup all-purpose flour", "^Flour, wheat, all-purpose|^Wheat flour, white, all-purpose", 115.0, 135.0),
        Row("1 cup sugar", "^Sugars, granulated", 185.0, 200.0),
        Row("1 cup brown sugar", "^Sugars, brown", 140.0, 225.0),
        // Everyday things with a count.
        Row("1 large egg", "^Egg, whole, raw", 48.0, 52.0),
        Row("2 eggs", "^Egg, whole, raw", 95.0, 105.0),
        Row("1 medium onion", "^Onions, raw", 100.0, 120.0),
        Row("2 cloves garlic", "^Garlic", 5.0, 7.0),
        Row("1 medium sweet potato", "^Sweet potato", 120.0, 140.0, "leaves|canned"),
        Row("1 banana", "^Bananas, raw", 110.0, 125.0),
        Row("1 medium apple", "^Apples, raw", 170.0, 195.0),
        Row("2 stalks celery", "^Celery", 70.0, 90.0),
        Row("1 cup chopped carrots", "^Carrots, raw", 110.0, 135.0),
        Row("1 bell pepper", "^Peppers, (sweet|bell)", 110.0, 130.0),
        Row("1 slice bread", "^Bread,", 20.0, 45.0),
        // Weights and oils.
        Row("1 lb chicken breast", "^Chicken, .*breast", 450.0, 456.0),
        Row("1 lb ground beef", "^Beef, ground, 85%", 450.0, 456.0),
        Row("1 lb ground turkey", "^Turkey, ground", 450.0, 456.0),
        Row("1 lb salmon fillet", "^Fish, salmon", 450.0, 456.0),
        Row("8 oz mushrooms", "^Mushrooms, white", 225.0, 229.0),
        Row("1 tbsp olive oil", "^Oil, olive", 13.0, 14.0),
        Row("1 tsp salt", "^Salt, table", 4.0, 7.0),
        Row("1 tbsp honey", "^Honey", 20.0, 22.0),
        Row("1 tbsp soy sauce", "^Soy sauce", 14.0, 19.0),
        Row("8 oz firm tofu", "^Tofu, raw", 225.0, 229.0, "yogurt"),
        Row("1 cup cooked lentils", "^Lentils, mature seeds, cooked", 190.0, 205.0),
    )

    @Test fun `the table has at least forty lines`() {
        assertThat(table.size).isAtLeast(40)
    }

    @Test fun `every line picks the ordinary food and a believable weight`() = RefDbLookup.withLookup { lookup ->
        val failures = mutableListOf<String>()
        for (row in table) {
            val e = IngredientEstimator.estimate(listOf(row.line), 1, lookup).lines.single()
            val name = e.food?.name
            val grams = e.grams
            val foodOk = name != null && Regex(row.food, RegexOption.IGNORE_CASE).containsMatchIn(name) &&
                (row.not == null || !Regex(row.not, RegexOption.IGNORE_CASE).containsMatchIn(name))
            val gramsOk = grams != null && grams >= row.min && grams <= row.max
            if (!foodOk || !gramsOk) failures += "${row.line} -> $name, $grams g (${e.notCounted ?: "counted"}); wanted /${row.food}/ ${row.min}..${row.max} g"
        }
        assertThat(failures).isEmpty()
    }

    @Test fun `a vegetable soup uses vegetable stock and counts every line it can`() = RefDbLookup.withLookup { lookup ->
        val e = IngredientEstimator.estimate(
            listOf("1 tbsp olive oil", "1 medium onion", "3 stalks celery", "2 medium carrots", "1 can diced tomatoes", "4 cups vegetable stock", "1 cup chopped spinach"),
            4, lookup,
        )
        assertThat(e.notCountedLines).isEmpty()
        assertThat(e.lines.map { it.food!!.name.lowercase() }.none { "beef" in it }).isTrue()
        // Plausible for a vegetable soup: well under 100 kcal a serving.
        assertThat(e.perServing!!.kcal).isWithin(30.0).of(60.0)
    }

    @Test fun `a pasta bake with milk is no longer inflated by sheep milk`() = RefDbLookup.withLookup { lookup ->
        val e = IngredientEstimator.estimate(listOf("200 g pasta", "100 g cheddar cheese", "1 cup milk"), 4, lookup)
        val milk = e.lines[2]
        assertThat(milk.food!!.name).doesNotContain("sheep")
        // Whole cow milk is about 150 kcal a cup, not 265.
        assertThat(milk.kcal!!).isWithin(25.0).of(148.0)
    }

    @Test fun `the picker still offers other foods for the line`() = RefDbLookup.withLookup { lookup ->
        val e = IngredientEstimator.estimate(listOf("1 cup milk"), 1, lookup).lines.single()
        assertThat(e.alternatives).isNotEmpty()
    }

    @Test fun `the reader takes a can size from the line in each common spelling`() {
        fun grams(line: String) = IngredientAmounts.parse(line).statedGrams
        assertThat(grams("14.5 oz can diced tomatoes")).isWithin(0.5).of(411.1)
        assertThat(grams("1 14.5-ounce can diced tomatoes")).isWithin(0.5).of(411.1)
        assertThat(grams("2 (15 oz) cans black beans")).isWithin(0.5).of(850.2)
        assertThat(grams("1 can (28 ounces) crushed tomatoes")).isWithin(0.5).of(793.8)
        assertThat(grams("a can of tomatoes")).isNull()
        IngredientAmounts.parse("a can of tomatoes").let {
            assertThat(it.value).isEqualTo(1.0); assertThat(it.unit).isEqualTo("can"); assertThat(it.name).isEqualTo("tomatoes")
        }
        IngredientAmounts.parse("an 8 oz jar salsa").let { assertThat(it.statedGrams).isWithin(0.5).of(226.8) }
        IngredientAmounts.parse("a pinch of salt").let { assertThat(it.unit).isEqualTo("pinch") }
    }
}
