package com.zeroindex.intake.domain.recipe

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.data.foodref.FoodRefSearch
import com.zeroindex.intake.data.foodref.RefFoodEntity
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import kotlinx.coroutines.runBlocking
import org.junit.Test

/** Runs the estimator against the real bundled USDA file, the way the app searches it. */
class IngredientEstimatorTest {

    private val asset = listOf("src/main/assets/food_ref_v1.db", "app/src/main/assets/food_ref_v1.db").map(::File).first { it.exists() }

    private fun <T> withLookup(block: suspend (suspend (String) -> List<ReferenceFood>) -> T): T =
        DriverManager.getConnection("jdbc:sqlite:${asset.absolutePath}").use { c -> runBlocking { block { q -> search(c, q) } } }

    private fun search(c: Connection, query: String): List<ReferenceFood> {
        val tokens = FoodRefSearch.queryTokens(query)
        if (tokens.isEmpty()) return emptyList()
        val p = tokens + List(FoodRefSearch.MAX_TOKENS - tokens.size) { "" }
        val sql = """SELECT * FROM ref_food WHERE name LIKE '%' || ? || '%' AND (? = '' OR name LIKE '%' || ? || '%')
            AND (? = '' OR name LIKE '%' || ? || '%') AND (? = '' OR name LIKE '%' || ? || '%')
            ORDER BY LENGTH(name), name LIMIT ${FoodRefSearch.CANDIDATE_LIMIT}"""
        val rows = mutableListOf<RefFoodEntity>()
        c.prepareStatement(sql).use { st ->
            st.setString(1, p[0]); st.setString(2, p[1]); st.setString(3, p[1])
            st.setString(4, p[2]); st.setString(5, p[2]); st.setString(6, p[3]); st.setString(7, p[3])
            st.executeQuery().use { rs ->
                while (rs.next()) {
                    rows += RefFoodEntity(
                        rs.getInt("id"), rs.getString("name"), rs.getString("category"), rs.getDouble("kcal"), rs.getDouble("protein"),
                        rs.getDouble("carbs"), rs.getDouble("fat"), rs.getDouble("fiber"), rs.getDouble("sugar"), rs.getDouble("sodium"),
                        rs.getDouble("saturatedFat"), rs.getString("portions"), rs.getString("dataset"),
                    )
                }
            }
        }
        return FoodRefSearch.rank(query, rows, FoodRefSearch.RESULT_LIMIT).map {
            ReferenceFood(
                it.id, it.name, it.kcal, it.protein, it.carbs, it.fat, it.fiber, it.sugar, it.sodium, it.saturatedFat,
                FoodRefSearch.parsePortions(it.portions).map { p -> ReferencePortion(p.label, p.grams) },
            )
        }
    }

    private val tomatoZucchiniPasta = listOf(
        "2 medium zucchinis",
        "2 cups cherry tomatoes",
        "8 oz (220g) pasta (we used fusilli)",
        "2 tablespoons olive oil",
        "1 teaspoon garlic powder",
        "1 teaspoon black pepper",
        "1 teaspoon fennel seeds",
        "1/4 cup vegetable stock",
        "1/2 teaspoon Cayenne pepper, optional",
        "1 cup grated parmesan",
        "Fresh basil or Parsley",
    )

    @Test fun `the tomato zucchini pasta comes out in a plausible range per serving`() = withLookup { lookup ->
        val e = IngredientEstimator.estimate(tomatoZucchiniPasta, 4, lookup)
        e.lines.forEach { println("${it.line} -> ${it.food?.name} ${it.grams} g ${it.notCounted ?: ""}") }
        val per = e.perServing!!
        println("per serving: ${per.kcal.toInt()} kcal, ${per.protein} g protein, ${per.carbs} g carbs, ${per.fat} g fat")
        assertThat(per.kcal).isWithin(100.0).of(450.0)
        assertThat(per.kcal).isGreaterThan(350.0)
        assertThat(per.kcal).isLessThan(550.0)
        assertThat(per.protein).isGreaterThan(12.0)
        assertThat(per.protein).isLessThan(25.0)
        // Not zero for the macros the page left out.
        assertThat(per.carbs).isGreaterThan(30.0)
        assertThat(per.fat).isGreaterThan(5.0)
    }

    @Test fun `each line matches the sensible food and the stated weight wins`() = withLookup { lookup ->
        val e = IngredientEstimator.estimate(tomatoZucchiniPasta, 4, lookup)
        fun food(i: Int) = e.lines[i].food!!.name
        assertThat(food(0)).contains("zucchini")
        assertThat(e.lines[0].grams).isWithin(1.0).of(392.0)
        assertThat(food(1)).contains("Tomatoes")
        assertThat(e.lines[1].grams).isWithin(10.0).of(298.0)
        assertThat(food(2)).startsWith("Pasta, dry")
        // "8 oz (220g)": 220 g, not 226.8 g.
        assertThat(e.lines[2].grams).isEqualTo(220.0)
        assertThat(food(3)).startsWith("Oil, olive")
        assertThat(e.lines[3].grams).isWithin(1.0).of(27.0)
        assertThat(food(4)).isEqualTo("Spices, garlic powder")
        assertThat(food(5)).isEqualTo("Spices, pepper, black")
        assertThat(food(6)).isEqualTo("Spices, fennel seed")
        assertThat(food(7).lowercase()).contains("vegetable broth")
        assertThat(food(9)).startsWith("Cheese, parmesan")
        assertThat(e.lines[9].grams).isWithin(15.0).of(110.0)
    }

    @Test fun `optional and unquantified lines are listed as not counted`() = withLookup { lookup ->
        val e = IngredientEstimator.estimate(tomatoZucchiniPasta, 4, lookup)
        assertThat(e.lines[8].counted).isFalse()
        assertThat(e.lines[8].notCounted).isEqualTo("optional")
        assertThat(e.lines[10].counted).isFalse()
        assertThat(e.lines[10].notCounted).isEqualTo("no amount given")
        assertThat(e.notCountedLines.map { it.line }).containsExactly("1/2 teaspoon Cayenne pepper, optional", "Fresh basil or Parsley")
        assertThat(e.countedLines).isEqualTo(9)
    }

    @Test fun `choosing a different food for a line changes the total and is marked`() = withLookup { lookup ->
        val base = IngredientEstimator.estimate(tomatoZucchiniPasta, 4, lookup)
        val whole = lookup("pasta whole-wheat dry").first()
        val changed = IngredientEstimator.estimate(tomatoZucchiniPasta, 4, lookup, mapOf(2 to whole))
        assertThat(changed.lines[2].chosen).isTrue()
        assertThat(changed.lines[2].food!!.id).isEqualTo(whole.id)
        assertThat(changed.perServing!!.kcal).isNotEqualTo(base.perServing!!.kcal)
        assertThat(changed.lines[2].alternatives.map { it.id }).doesNotContain(whole.id)
    }

    @Test fun `nothing matched gives no estimate rather than zeros`() = withLookup { lookup ->
        val e = IngredientEstimator.estimate(listOf("2 cups qzxwv", "salt to taste"), 2, lookup)
        assertThat(e.perServing).isNull()
        assertThat(e.countedLines).isEqualTo(0)
    }

    @Test fun `every rule's query finds a food in the bundled file`() = withLookup { lookup ->
        for (rule in IngredientRules.ALL) {
            assertThat(lookup(rule.query)).isNotEmpty()
        }
    }

    // ---- the quantity reader -----------------------------------------------------------------

    @Test fun `quantities, fractions, units and stated weights`() {
        IngredientAmounts.parse("1/4 cup vegetable stock").let {
            assertThat(it.value).isEqualTo(0.25); assertThat(it.unit).isEqualTo("cup"); assertThat(it.name).isEqualTo("vegetable stock")
        }
        IngredientAmounts.parse("1 1/2 tbsp olive oil").let { assertThat(it.value).isEqualTo(1.5); assertThat(it.unit).isEqualTo("tbsp") }
        IngredientAmounts.parse("1½ cups milk").let { assertThat(it.value).isEqualTo(1.5) }
        IngredientAmounts.parse("2 medium zucchinis").let {
            assertThat(it.value).isEqualTo(2.0); assertThat(it.size).isEqualTo("medium"); assertThat(it.unit).isNull(); assertThat(it.name).isEqualTo("zucchinis")
        }
        IngredientAmounts.parse("8 oz (220g) pasta (we used fusilli)").let {
            assertThat(it.statedGrams).isEqualTo(220.0); assertThat(it.name).isEqualTo("pasta")
        }
        IngredientAmounts.parse("2 (14 oz) cans tomatoes").let { assertThat(it.statedGrams).isWithin(0.1).of(793.8) }
        IngredientAmounts.parse("1-2 tbsp honey").let { assertThat(it.value).isEqualTo(1.5) }
        IngredientAmounts.parse("Fresh basil or Parsley").let { assertThat(it.value).isNull() }
        IngredientAmounts.parse("salt, to taste").let { assertThat(it.toTaste).isTrue(); assertThat(it.value).isNull() }
        IngredientAmounts.parse("2 cloves garlic, minced").let { assertThat(it.unit).isEqualTo("clove"); assertThat(it.name).isEqualTo("garlic") }
        IngredientAmounts.parse("3 fl oz cream").let { assertThat(it.unit).isEqualTo("floz") }
        IngredientAmounts.parse("a pinch of salt").let { assertThat(it.name).isNotEmpty() }
    }
}
