package com.zeroindex.intake.domain.recipe

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RecipeSearchTest {
    private fun r(name: String, kcal: Double? = 400.0, protein: Double? = 30.0, last: Long? = null) =
        RecipeCandidate(name.lowercase().replace(' ', '-'), name, kcal, protein, last)

    @Test
    fun `whole name beats prefix beats word start beats fragment`() {
        val all = listOf(r("Spicy chicken curry"), r("Curry"), r("Curry leaf rice"), r("Chicken curry"), r("Scurry bars"))
        assertThat(RecipeSearch.rank("curry", all).map { it.name })
            .containsExactly("Curry", "Curry leaf rice", "Chicken curry", "Spicy chicken curry", "Scurry bars").inOrder()
    }

    @Test
    fun `every typed word must match`() {
        val all = listOf(r("Chicken rice bowl"), r("Chicken salad"), r("Rice pudding"))
        assertThat(RecipeSearch.rank("chick rice", all).map { it.name }).containsExactly("Chicken rice bowl")
        assertThat(RecipeSearch.rank("tofu", all)).isEmpty()
        assertThat(RecipeSearch.rank("  ", all)).isEmpty()
    }

    @Test
    fun `a name prefix match outranks a later word match`() {
        val all = listOf(r("Lemon chicken"), r("Chicken lemon soup"))
        assertThat(RecipeSearch.rank("chicken", all).map { it.name }).containsExactly("Chicken lemon soup", "Lemon chicken").inOrder()
    }

    @Test
    fun `ties go to the recently logged recipe, then the shorter name`() {
        val all = listOf(r("Beef chili", last = null), r("Bean chili", last = 500), r("Black bean chili bowl", last = 900))
        assertThat(RecipeSearch.rank("chili", all).map { it.name })
            .containsExactly("Black bean chili bowl", "Bean chili", "Beef chili").inOrder()
    }

    @Test
    fun `punctuation and case do not matter`() {
        assertThat(RecipeSearch.rank("MAC & cheese", listOf(r("Mac-and-cheese"), r("Mac n cheese"), r("Pasta"))).map { it.name })
            .containsExactly("Mac n cheese", "Mac-and-cheese").inOrder()
    }

    @Test
    fun `summary says not listed instead of zero`() {
        assertThat(r("A", 412.4, 31.6).summary).isEqualTo("412 kcal · 32 g protein")
        assertThat(r("B", 300.0, null).summary).isEqualTo("300 kcal · protein not listed")
        assertThat(r("C", null, null).summary).isEqualTo("kcal not listed · protein not listed")
        assertThat(r("C", null, 5.0).loggable).isFalse()
        assertThat(r("D", 0.0, null).loggable).isTrue()
    }

    @Test
    fun `recents are recipes whose name was logged, latest first, once each`() {
        val recipes = listOf(r("Chili"), r("Pancakes"), r("Soup"))
        val logged = listOf(
            RecipeSearch.LoggedName("chili ", 100), RecipeSearch.LoggedName("Pancakes", 300),
            RecipeSearch.LoggedName("Chili", 200), RecipeSearch.LoggedName("Banana", 999),
        )
        val out = RecipeSearch.recents(recipes, logged)
        assertThat(out.map { it.name }).containsExactly("Pancakes", "Chili").inOrder()
        assertThat(out.map { it.lastLoggedAt }).containsExactly(300L, 200L).inOrder()
        assertThat(RecipeSearch.recents(recipes, logged, limit = 1)).hasSize(1)
        assertThat(RecipeSearch.recents(recipes, emptyList())).isEmpty()
    }

    @Test
    fun `merge of recency into ranking`() {
        val recipes = RecipeSearch.withLastLogged(
            listOf(r("Tuna melt"), r("Tuna pasta")),
            listOf(RecipeSearch.LoggedName("Tuna pasta", 10)),
        )
        assertThat(RecipeSearch.rank("tuna", recipes).map { it.name }).containsExactly("Tuna pasta", "Tuna melt").inOrder()
    }
}
