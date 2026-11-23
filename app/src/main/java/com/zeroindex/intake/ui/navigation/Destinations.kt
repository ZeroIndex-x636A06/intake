package com.zeroindex.intake.ui.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import com.zeroindex.intake.R
import com.zeroindex.intake.ui.theme.IntakeIcons
import kotlinx.serialization.Serializable

/**
 * Type-safe routes (Navigation Compose reads these through kotlinx.serialization).
 * IA v6: the dock is Today, Food (route Nutrition), a centre + (Quick log), Train, Insights (route Progress). There is no More tab: the
 * avatar in every tab root's top bar opens [You], and Settings is the gear on You.
 *
 * Feature agents: a screen only one tab uses goes in that tab's graph, above its "add ... above this line"
 * marker. A screen that several tabs open (Settings, You, Drinks, Allergens, ...) is registered ONCE at the
 * root of the NavHost, in the "shared screens" block, never in two graphs. Every pushed screen is wrapped in
 * `PushedScreen(...)` (top bar with back arrow, no pill); tab roots use `TabRootScreen(...)`.
 */
sealed interface Route {
    // Tab roots.
    @Serializable data object Today : Route
    /** The Food tab root (labelled Food since v6, internal name Nutrition): Diary / Plan / Recipes / Library segments. */
    @Serializable data object Nutrition : Route
    @Serializable data object Train : Route
    @Serializable data object Progress : Route

    /** The welcome flow, shown before the tabs on a first run and from Help, "Show the welcome again". */
    @Serializable data object Onboarding : Route

    // Nutrition tab. Hubs first, then the screens they open.
    @Serializable data object MealPlans : Route
    @Serializable data object Recipes : Route
    @Serializable data object EatingOut : Route
    /** The one Allergens screen (status, your allergens, keywords, check the label, hidden-ingredient guide, flagged). */
    @Serializable data object Allergens : Route
    @Serializable data class AllergenKeywords(val allergenKey: String) : Route
    @Serializable data object RecipeHunter : Route
    @Serializable data object RecipeImport : Route
    @Serializable data class RecipeEditor(val recipeId: String? = null) : Route
    @Serializable data class RecipeDetail(val recipeId: String) : Route
    @Serializable data class CookMode(val recipeId: String) : Route
    @Serializable data object MenuSources : Route
    @Serializable data object MealTemplates : Route
    /** Make or edit a meal template: name, then foods and recipes with amounts. [templateId] null starts a new one. */
    @Serializable data class MealTemplateEditor(val templateId: String? = null) : Route
    /** A shopping list built from chosen recipes and meal templates. Needs no meal plan. */
    @Serializable data object ShoppingList : Route
    /**
     * One meal of one day (opened from a meal row on the Nutrition tab): its totals, what is logged in it, and the ways to add
     * more. [slot] is a `MealSlot` name, [epochDay] the local calendar day (`LocalDate.toEpochDay`, never later than today).
     */
    @Serializable data class NutritionMeal(val slot: String, val epochDay: Long) : Route
    /** The old "More" list. Kept registered but unreachable since v6: the Library segment replaces it. */
    @Serializable data object NutritionMore : Route
    /**
     * Log food: a full-screen task with Search, Scan, Photo, Mine and Quick add. Shared (Today and Nutrition open it),
     * so it is registered once at the root. [slot] is a `MealSlot` name (null picks by the clock), [atMillis] the time
     * to log at (0 means now) and [mode] an `AddFoodTab` name.
     */
    @Serializable data class LogFood(val slot: String? = null, val atMillis: Long = 0L, val mode: String = "SEARCH") : Route
    /**
     * Food detail: picture, portion stepper, nutrition label, allergens, Log, Swap for another, edit and contribute.
     * Shared (Log food, Drinks, Mine open it). [slot] and [atMillis] are what the log goes to (0 means now).
     */
    @Serializable data class FoodDetail(val foodId: String, val slot: String? = null, val atMillis: Long = 0L) : Route
    /**
     * The Drinks page (the drinks page, from Log food): category chips, search over the food database, quick sizes from Settings,
     * today's drinks with caffeine and sugar. Pushed from Today, Nutrition and Log food. Its settings stay in Settings.
     */
    @Serializable data object Drinks : Route
    /** Drinks opened from Log food with the words typed there, so its search starts with them. */
    @Serializable data class DrinksSearch(val query: String) : Route
    /** Customise a drink (add-ins with amounts), or edit a saved "My drink"; [foodId] is that drink's food row. Opened from Drinks. */
    @Serializable data class CustomDrink(val foodId: String) : Route
    /**
     * Swap the planned meal: a bottom sheet (dialog destination) with Plan & recipes, Saved, Saved eat-out options and the
     * optional "Find healthy options nearby" (AI, on tap). It replaces Plan B as a destination.
     */
    @Serializable data class SwapSheet(val slot: String? = null) : Route

    // Train tab.
    @Serializable data object Programs : Route
    /** [programId] null opens the builder at its program list / "new program" step. */
    @Serializable data class ProgramBuilder(val programId: String? = null) : Route
    @Serializable data object ProgramTemplates : Route
    @Serializable data object ProgramGenerator : Route
    /** Train > Videos: one video (the player, chapters, save, Make this a workout). Inside the Train graph. */
    @Serializable data class VideoDetail(val videoId: String) : Route
    /** "Make this a workout": review what was read from a video, then save it as a My workout. Inside the Train graph. */
    @Serializable data class MakeWorkout(val videoId: String) : Route
    @Serializable data object Performance : Route
    @Serializable data class ActiveWorkout(val sessionId: String) : Route
    /** The post-workout summary, also the read-only view of an older session (opened from Train and from the training log). */
    @Serializable data class WorkoutSummary(val sessionId: String) : Route

    // Progress tab.
    @Serializable data object WorkoutHistory : Route
    /** Progress > Training stats: estimated 1RM, recent records and this weeks sets. */
    @Serializable data object TrainingStats : Route
    /** Calculators: estimated 1RM, percentage table and plate loader. Opened from Train and from Training stats, so it is a shared route. */
    @Serializable data object TrainTools : Route
    /**
     * The one weekly check-in (summary, suggestions with Apply / Dismiss, week chips, Export / Print PDF). It
     * replaces the old WeeklyReport and CoachCheckIn routes.
     */
    @Serializable data object WeeklyCheckIn : Route
    @Serializable data object BodyMetrics : Route

    // You (the avatar) and Settings. Shared: registered once at the NavHost root.
    /** The profile screen behind the avatar: header, goals and plan, progress photos, help. */
    @Serializable data object You : Route
    /** Settings home: search and one row per group. Holds only settings. */
    @Serializable data object Settings : Route
    /** One Settings group page. [group] is a `SettingsGroup.id`; [focus] a `SettingId.name` to scroll to, or null. */
    @Serializable data class SettingsGroup(val group: String, val focus: String? = null) : Route
    /** "What leaves this phone": the privacy statement, opened from Settings > About and help. */
    @Serializable data object Privacy : Route
    /** About you and goal inputs (sex, birth year, height, build, phase, pace, baseline week). Opened from You. */
    @Serializable data object Profile : Route
    /** "Your plan": how the plan was worked out, with Apply. Opened from You > Goals and plan. */
    @Serializable data object YourPlan : Route
    /** Progress photos timeline and add, from You. */
    @Serializable data object ProgressPhotos : Route
    /** Compare two progress photo dates side by side with a divider slider. */
    @Serializable data object PhotoCompare : Route

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
    @Serializable data object Nutrition : TabGraph
    @Serializable data object Train : TabGraph
    @Serializable data object Progress : TabGraph
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
    TODAY(Route.Today, TabGraph.Today, R.string.nav_today, IntakeIcons.Calendar),
    NUTRITION(Route.Nutrition, TabGraph.Nutrition, R.string.nav_nutrition, IntakeIcons.ForkKnife),
    TRAIN(Route.Train, TabGraph.Train, R.string.nav_train, IntakeIcons.Dumbbell),
    PROGRESS(Route.Progress, TabGraph.Progress, R.string.nav_progress, IntakeIcons.LineChart),
}
