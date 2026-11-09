package com.zeroindex.intake.data.foodref

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FoodRefSearchTest {

    private fun food(id: Int, name: String, portions: String = "") = RefFoodEntity(
        id = id, name = name, category = "", kcal = 100.0, protein = 1.0, carbs = 1.0, fat = 1.0,
        fiber = 0.0, sugar = 0.0, sodium = 0.0, saturatedFat = 0.0, portions = portions, dataset = "SR",
    )

    @Test
    fun tokenizeSplitsOnPunctuationAndLowercases() {
        assertThat(FoodRefSearch.tokenize("Greek Yogurt, 2% (plain)")).containsExactly("greek", "yogurt", "2", "plain").inOrder()
    }

    @Test
    fun queryTokensDropSingleLettersButKeepALoneOne() {
        assertThat(FoodRefSearch.queryTokens("vitamin a")).containsExactly("vitamin")
        assertThat(FoodRefSearch.queryTokens("a")).containsExactly("a")
        assertThat(FoodRefSearch.queryTokens("one two three four five six")).hasSize(FoodRefSearch.MAX_TOKENS)
    }

    @Test
    fun wordStartMatchesBeatMidWordMatches() {
        val names = listOf(
            food(1, "Pineapple, raw"),
            food(2, "Apples, raw, with skin"),
            food(3, "Crabapple, raw"),
        )
        val ranked = FoodRefSearch.rank("apple", names).map { it.id }
        // "Apples..." starts with the token; the other two only contain it inside a word.
        assertThat(ranked.first()).isEqualTo(2)
        assertThat(ranked).containsExactly(2, 3, 1).inOrder() // then the shorter name
    }

    @Test
    fun nameStartingWithTheFirstTokenBeatsLaterWordStart() {
        val ranked = FoodRefSearch.rank(
            "cheese",
            listOf(food(1, "Sandwich, cheese"), food(2, "Cheese, cheddar")),
        ).map { it.id }
        assertThat(ranked).containsExactly(2, 1).inOrder()
    }

    @Test
    fun shorterNamesWinWithinATier() {
        val ranked = FoodRefSearch.rank(
            "cheese",
            listOf(
                food(1, "Cheese, cheddar, reduced fat, sliced"),
                food(2, "Cheese, brie"),
                food(3, "Cheese, cheddar"),
            ),
        ).map { it.id }
        assertThat(ranked).containsExactly(2, 3, 1).inOrder()
    }

    @Test
    fun everyTokenMustMatchWhateverTheOrder() {
        val ranked = FoodRefSearch.rank(
            "raw apple",
            listOf(food(1, "Apples, raw, with skin"), food(2, "Apples, canned"), food(3, "Beef, raw")),
        ).map { it.id }
        assertThat(ranked).containsExactly(1)
    }

    @Test
    fun resultsAreCapped() {
        val many = (1..100).map { food(it, "Rice $it") }
        assertThat(FoodRefSearch.rank("rice", many, limit = 30)).hasSize(30)
    }

    @Test
    fun portionsParseAndSkipMalformedPieces() {
        val portions = FoodRefSearch.parsePortions("1 cup, diced|132;broken;1 slice (1 oz)|28;zero|0;|5")
        assertThat(portions).containsExactly(RefPortion("1 cup, diced", 132.0), RefPortion("1 slice (1 oz)", 28.0)).inOrder()
        assertThat(FoodRefSearch.parsePortions("")).isEmpty()
    }

    @Test
    fun defaultPortionFallsBackToHundredGrams() {
        val ref = with(FoodRefSearch) { food(1, "Salt").toRefFood() }
        assertThat(ref.defaultPortion).isEqualTo(RefPortion("100 g", 100.0))
    }
}
