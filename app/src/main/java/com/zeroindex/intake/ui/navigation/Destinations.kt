package com.zeroindex.intake.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Today
import androidx.compose.ui.graphics.vector.ImageVector
import com.zeroindex.intake.R
import kotlinx.serialization.Serializable

/**
 * Type-safe routes (Navigation Compose reads these through kotlinx.serialization).
 * SPEC-v3 "Information architecture": the pill is Today, Food, Train, Progress, More, and each of
 * the last four opens a short hub of named sections.
 *
 * Feature agents: append new routes inside the group they belong to (Food, Train, Progress, More);
 * register the matching `composable<...>` in the same group's graph in IntakeNavHost.
 */
sealed interface Route {
    // Tab roots.
    @Serializable data object Today : Route
    @Serializable data object Food : Route
    @Serializable data object Train : Route
    @Serializable data object Progress : Route
    @Serializable data object More : Route

    /** The welcome flow, shown before the tabs on a first run and from Help, "Show the welcome again". */
    @Serializable data object Onboarding : Route

    // Food tab. Hubs first, then the screens they open.
    @Serializable data object MealPlans : Route
    @Serializable data object Recipes : Route
    @Serializable data object EatingOut : Route
    @Serializable data object Allergens : Route
    @Serializable data object AllergenReference : Route
    @Serializable data object MyAllergens : Route
    @Serializable data class AllergenKeywords(val allergenKey: String) : Route
    @Serializable data object RecipeHunter : Route
    @Serializable data object RecipeImport : Route
    @Serializable data class RecipeEditor(val recipeId: String? = null) : Route
    @Serializable data class RecipeDetail(val recipeId: String) : Route
    @Serializable data class CookMode(val recipeId: String) : Route
    @Serializable data object PlanB : Route
    @Serializable data object MenuSources : Route
    @Serializable data object MealTemplates : Route
    /** Every food on the phone; tap one for [FoodDetail]. */
    @Serializable data object MyFoods : Route
    /** Food detail and edit, with "Contribute to Open Food Facts". */
    @Serializable data class FoodDetail(val foodId: String) : Route
    /** Open Food Facts search and contribution settings, plus data credits. */
    @Serializable data object FoodData : Route

    // Train tab.
    @Serializable data object Programs : Route
    /** [programId] null opens the builder at its program list / "new program" step. */
    @Serializable data class ProgramBuilder(val programId: String? = null) : Route
    @Serializable data object ProgramTemplates : Route
    @Serializable data object ProgramGenerator : Route
    @Serializable data object ExerciseLibrary : Route
    @Serializable data object Performance : Route
    @Serializable data class ActiveWorkout(val sessionId: String) : Route

    // Progress tab.
    @Serializable data object WorkoutHistory : Route
    @Serializable data object WeeklyReport : Route
    @Serializable data object HealthConnect : Route
    @Serializable data object BodyMetrics : Route

    // More tab.
    /** [section] scrolls the settings list to "targets" or "allergens"; null opens at the top. */
    @Serializable data class Settings(val section: String? = null) : Route
    @Serializable data object Privacy : Route
    @Serializable data object Backup : Route
    @Serializable data object AiSettings : Route
    @Serializable data object Help : Route
    /** Profile & goals: basics, build, phase, training availability, and the baseline week. */
    @Serializable data object Profile : Route
    /** "Your plan": calories, macros, water and training built from the profile, with a why for each. */
    @Serializable data object YourPlan : Route

    /**
     * The bundled scripting wiki. [page] is a docs/scripting page such as "README"; [anchor] a
     * heading slug. Food entry points open "meal-plan", Train entry points "program".
     */
    @Serializable data class ScriptReference(val page: String = "README", val anchor: String? = null) : Route
}

/**
 * One nested navigation graph per tab, so each tab keeps its own back stack and the pill can tell
 * which tab a pushed screen belongs to.
 */
sealed interface TabGraph {
    @Serializable data object Today : TabGraph
    @Serializable data object Food : TabGraph
    @Serializable data object Train : TabGraph
    @Serializable data object Progress : TabGraph
    @Serializable data object More : TabGraph
}

/**
 * A pill entry. [labelRes] doubles as the accessibility label so the icon is never the only thing
 * announced (SPEC §3.7). [route] is the tab's root screen, [graph] the nested graph around it.
 */
enum class TopLevelDestination(
    val route: Route,
    val graph: TabGraph,
    val labelRes: Int,
    val icon: ImageVector,
) {
    TODAY(Route.Today, TabGraph.Today, R.string.nav_today, Icons.Filled.Today),
    FOOD(Route.Food, TabGraph.Food, R.string.nav_food, Icons.Filled.Restaurant),
    TRAIN(Route.Train, TabGraph.Train, R.string.nav_train, Icons.Filled.FitnessCenter),
    PROGRESS(Route.Progress, TabGraph.Progress, R.string.nav_progress, Icons.AutoMirrored.Filled.ShowChart),
    MORE(Route.More, TabGraph.More, R.string.nav_more, Icons.Filled.MoreHoriz),
}
