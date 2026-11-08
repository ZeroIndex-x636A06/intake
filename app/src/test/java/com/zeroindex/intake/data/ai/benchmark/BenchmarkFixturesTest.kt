package com.zeroindex.intake.data.ai.benchmark

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.domain.ai.ModelSuggester
import com.zeroindex.intake.domain.ai.benchmark.BenchmarkScoring
import com.zeroindex.intake.domain.ai.benchmark.InsightFixtureSet
import com.zeroindex.intake.domain.ai.benchmark.MenuFixtureSet
import com.zeroindex.intake.domain.ai.benchmark.PlanBFixtureSet
import com.zeroindex.intake.domain.ai.benchmark.RecipeFixtureSet
import java.io.File
import kotlinx.serialization.json.Json
import org.junit.Test

/**
 * The bundled fixtures are the benchmark's ground truth, so they are checked
 * against themselves: a wrong "expected" value would unfairly fail every model.
 */
class BenchmarkFixturesTest {

    private val json = Json { ignoreUnknownKeys = true }
    private fun read(name: String) = File("src/main/assets/benchmark/$name").readText()

    private val menus = json.decodeFromString(MenuFixtureSet.serializer(), read("menu-ingestion.json")).fixtures
    private val recipes = json.decodeFromString(RecipeFixtureSet.serializer(), read("recipe-hunter.json")).fixtures
    private val planBs = json.decodeFromString(PlanBFixtureSet.serializer(), read("plan-b.json")).fixtures
    private val insights = json.decodeFromString(InsightFixtureSet.serializer(), read("daily-insight.json")).fixtures

    @Test
    fun `each task has enough fixtures for a model to be able to pass`() {
        listOf(menus.size, recipes.size, planBs.size, insights.size).forEach {
            assertThat(it).isAtLeast(ModelSuggester.MIN_FIXTURES)
            assertThat(it).isAtMost(ModelSuggester.MIN_FIXTURES)
        }
    }

    @Test
    fun `fixture ids are unique within a task`() {
        assertThat(menus.map { it.id }.toSet()).hasSize(menus.size)
        assertThat(recipes.map { it.id }.toSet()).hasSize(recipes.size)
        assertThat(planBs.map { it.id }.toSet()).hasSize(planBs.size)
        assertThat(insights.map { it.id }.toSet()).hasSize(insights.size)
    }

    @Test
    fun `every expected menu item and its published macros appear in the menu text`() {
        menus.forEach { menu ->
            menu.items.forEach { item ->
                assertThat(menu.text).contains(item.name)
                assertThat(menu.text).contains("${item.kcal.toInt()} cal, ${item.proteinG.toInt()}g protein")
            }
        }
    }

    @Test
    fun `menu dairy flags agree with the item text except for the two deliberate traps`() {
        // Tandoori Chicken says "yogurt-free" and Tom Kha is made with coconut milk: neither is dairy.
        val traps = setOf("Tandoori Chicken", "Creamy Tom Kha Soup")
        menus.forEach { menu ->
            val paragraphs = menu.text.split("\n\n")
            menu.items.forEach { item ->
                val paragraph = paragraphs.first { it.contains(item.name) }
                val mentionsDairy = BenchmarkScoring.raisesDairy(listOf(paragraph.substringAfter(item.name)))
                if (item.name in traps) {
                    assertThat(item.dairy).isFalse()
                    assertThat(mentionsDairy).isTrue()
                } else {
                    assertThat(mentionsDairy).isEqualTo(item.dairy)
                }
            }
        }
    }

    @Test
    fun `recipe pages state the servings and a recipe's dairy flag matches its ingredients`() {
        recipes.forEach { recipe ->
            assertThat(recipe.pageText).contains("Makes ${recipe.servings} servings")
            val ingredients = recipe.pageText.substringAfter("Ingredients\n").substringBefore("\n\nMethod").lines()
            assertThat(BenchmarkScoring.raisesDairy(ingredients)).isEqualTo(recipe.dairy)
        }
    }

    @Test
    fun `a recipe that publishes only whole-recipe nutrition divides to the expected per-serving figure`() {
        val chili = recipes.single { "whole recipe" in it.pageText }
        assertThat(chili.pageText).contains("3,200 calories")
        assertThat(3_200.0 / chili.servings).isEqualTo(chili.kcalPerServing)
        assertThat(256.0 / chili.servings).isEqualTo(chili.proteinPerServing)
    }

    @Test
    fun `plan b picks exist, fit the budget and are dairy free`() {
        planBs.forEach { fixture ->
            assertThat(fixture.acceptable).isNotEmpty()
            fixture.items.forEach { assertThat(fixture.menuText).contains(it.name) }
            fixture.acceptable.forEach { name ->
                val item = fixture.items.single { it.name == name }
                assertThat(item.dairy).isFalse()
                assertThat(item.kcal).isAtMost(fixture.kcalLeft.toDouble())
            }
            assertThat(fixture.items.any { it.dairy }).isTrue()
        }
    }

    @Test
    fun `insight digests are numbers only`() {
        insights.forEach { fixture ->
            assertThat(fixture.digest).isNotEmpty()
            assertThat(BenchmarkScoring.raisesDairy(fixture.digest)).isFalse()
        }
    }
}
