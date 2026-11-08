package com.zeroindex.intake.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Today
import androidx.compose.ui.graphics.vector.ImageVector
import com.zeroindex.intake.R
import kotlinx.serialization.Serializable

/**
 * Type-safe routes (Navigation Compose reads these through kotlinx.serialization).
 * SPEC §5: the bottom bar is Today, Plan, Train, Progress, More.
 */
sealed interface Route {
    @Serializable data object Today : Route
    @Serializable data object Plan : Route
    @Serializable data object Train : Route
    @Serializable data object Progress : Route
    @Serializable data object More : Route

    // Reached from More, two taps from anywhere (SPEC §7.6).
    @Serializable data object AllergenReference : Route
    @Serializable data object Settings : Route
    @Serializable data object Privacy : Route
    @Serializable data object BodyMetrics : Route
    @Serializable data object Performance : Route
    @Serializable data object AiSettings : Route
    @Serializable data object RecipeHunter : Route
    @Serializable data class RecipeDetail(val recipeId: String) : Route
    @Serializable data object PlanB : Route
    @Serializable data object MenuSources : Route
    @Serializable data object MealTemplates : Route
    @Serializable data object HealthConnect : Route
    @Serializable data object WeeklyReport : Route
    @Serializable data object ExerciseLibrary : Route
    @Serializable data object ProgramBuilder : Route
    @Serializable data object WorkoutHistory : Route
    @Serializable data class ActiveWorkout(val sessionId: String) : Route
    @Serializable data class CookMode(val recipeId: String) : Route
}

/**
 * A bottom-bar entry. [labelRes] doubles as the accessibility label so the icon
 * is never the only thing announced (SPEC §3.7).
 */
enum class TopLevelDestination(
    val route: Route,
    val labelRes: Int,
    val icon: ImageVector,
) {
    TODAY(Route.Today, R.string.nav_today, Icons.Filled.Today),
    PLAN(Route.Plan, R.string.nav_plan, Icons.Filled.CalendarMonth),
    TRAIN(Route.Train, R.string.nav_train, Icons.Filled.FitnessCenter),
    PROGRESS(Route.Progress, R.string.nav_progress, Icons.AutoMirrored.Filled.ShowChart),
    MORE(Route.More, R.string.nav_more, Icons.Filled.MoreHoriz),
}
