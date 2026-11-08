package com.zeroindex.intake.domain.ai.benchmark

import kotlinx.serialization.Serializable

/**
 * The hand-written fixture sets under `assets/benchmark/`, one file per task.
 * Every expected value is stated in the fixture text itself (published macros,
 * ingredient lines), so a correct model can read the answer rather than guess
 * it, and a wrong answer is the model's fault and not the fixture's.
 *
 * All restaurants, recipes and numbers are made up.
 */
@Serializable
data class FixtureItem(val name: String, val kcal: Double, val proteinG: Double, val dairy: Boolean)

@Serializable
data class MenuFixture(val id: String, val restaurant: String, val text: String, val items: List<FixtureItem>)

@Serializable
data class RecipeFixture(
    val id: String,
    /** The page as the real feature would have read it, source URL included. */
    val pageText: String,
    val servings: Int,
    val kcalPerServing: Double,
    val proteinPerServing: Double,
    val dairy: Boolean,
)

@Serializable
data class PlanBFixture(
    val id: String,
    val menuText: String,
    val kcalLeft: Int,
    val proteinLeft: Int,
    val items: List<FixtureItem>,
    /** Names of the items that fit the budget and contain no dairy. */
    val acceptable: List<String>,
)

@Serializable
data class InsightFixture(val id: String, val digest: List<String>)

@Serializable
data class MenuFixtureSet(val fixtures: List<MenuFixture>)

@Serializable
data class RecipeFixtureSet(val fixtures: List<RecipeFixture>)

@Serializable
data class PlanBFixtureSet(val fixtures: List<PlanBFixture>)

@Serializable
data class InsightFixtureSet(val fixtures: List<InsightFixture>)
