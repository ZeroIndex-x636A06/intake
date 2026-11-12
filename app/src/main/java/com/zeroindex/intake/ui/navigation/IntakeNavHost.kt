package com.zeroindex.intake.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.compose.navigation
import androidx.navigation.toRoute
import com.zeroindex.intake.ui.motion.LocalReduceMotion
import com.zeroindex.intake.ui.motion.Motion
import com.zeroindex.intake.ui.onboarding.OnboardingScreen
import com.zeroindex.intake.ui.screens.allergen.AllergenKeywordsScreen
import com.zeroindex.intake.ui.screens.allergen.AllergensScreen
import com.zeroindex.intake.ui.screens.body.BodyMetricsScreen
import com.zeroindex.intake.ui.screens.checkin.WeeklyCheckInScreen
import com.zeroindex.intake.ui.screens.drinks.DrinksScreen
import com.zeroindex.intake.ui.screens.fooddata.FoodDetailScreen
import com.zeroindex.intake.domain.model.MealSlot
import com.zeroindex.intake.ui.addfood.AddFoodTab
import com.zeroindex.intake.ui.addfood.LogFoodScreen
import com.zeroindex.intake.ui.screens.hub.EatingOutScreen
import com.zeroindex.intake.ui.screens.hub.defaultMealSlot
import com.zeroindex.intake.ui.screens.nutrition.NutritionActions
import com.zeroindex.intake.ui.screens.nutrition.NutritionMoreScreen
import com.zeroindex.intake.ui.screens.nutrition.NutritionHomeScreen
import com.zeroindex.intake.ui.screens.hub.RecipesScreen
import com.zeroindex.intake.ui.screens.menu.MenuSourcesScreen
import com.zeroindex.intake.ui.screens.more.ScriptReferenceScreen
import com.zeroindex.intake.ui.screens.performance.PerformanceScreen
import com.zeroindex.intake.ui.screens.photos.PhotoCompareScreen
import com.zeroindex.intake.ui.screens.photos.ProgressPhotosScreen
import com.zeroindex.intake.ui.screens.plan.CookModeScreen
import com.zeroindex.intake.ui.screens.plan.PlanScreen
import com.zeroindex.intake.ui.screens.plan.RecipeDetailScreen
import com.zeroindex.intake.ui.screens.plan.RecipeHunterScreen
import com.zeroindex.intake.ui.screens.profile.ProfileScreen
import com.zeroindex.intake.ui.screens.profile.YourPlanScreen
import com.zeroindex.intake.ui.screens.progress.ProgressScreen
import com.zeroindex.intake.ui.screens.recipe.RecipeEditorScreen
import com.zeroindex.intake.ui.screens.recipe.RecipeImportScreen
import com.zeroindex.intake.ui.screens.settings.PrivacyScreen
import com.zeroindex.intake.ui.screens.settings.SettingsGroupScreen
import com.zeroindex.intake.ui.screens.settings.SettingsHomeScreen
import com.zeroindex.intake.ui.screens.swap.SwapSheet
import com.zeroindex.intake.ui.screens.templates.MealTemplatesScreen
import com.zeroindex.intake.ui.screens.today.TodayScreen
import com.zeroindex.intake.ui.screens.train.ProgramBuilderScreen
import com.zeroindex.intake.ui.screens.train.ProgramGeneratorScreen
import com.zeroindex.intake.ui.screens.train.ProgramTemplatesScreen
import com.zeroindex.intake.ui.screens.train.WorkoutHistoryScreen
import com.zeroindex.intake.ui.screens.train.ProgramsScreen
import com.zeroindex.intake.ui.screens.train.TrainActions
import com.zeroindex.intake.ui.screens.train.TrainRootScreen
import com.zeroindex.intake.ui.screens.videos.MakeWorkoutScreen
import com.zeroindex.intake.ui.screens.videos.VideoDetailScreen
import com.zeroindex.intake.ui.screens.workout.WorkoutSummaryScreen
import com.zeroindex.intake.ui.screens.workout.ActiveWorkoutScreen
import com.zeroindex.intake.ui.screens.you.YouScreen

/** The tab a destination belongs to, found through its nested graph; null before the graph is entered or at the root. */
fun tabOf(destination: NavDestination?): TopLevelDestination? =
    destination?.let { d ->
        TopLevelDestination.entries.firstOrNull { tab -> d.hierarchy.any { it.hasRoute(tab.graph::class) } }
    }

/** The tab root a destination is, or null when it is anything else (a pushed screen, a sheet). */
fun tabRootOf(destination: NavDestination?): TopLevelDestination? =
    destination?.let { d -> TopLevelDestination.entries.firstOrNull { d.hasRoute(it.route::class) } }

private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch(): Boolean =
    TabPolicy.isTabSwitch(tabOf(initialState.destination), tabOf(targetState.destination)) ||
        (tabRootOf(initialState.destination) != null && tabRootOf(targetState.destination) != null)

/**
 * One nested graph per tab (each keeps its own back stack), plus the **shared screens registered once at the
 * root**: You, Settings, Allergens, Drinks, the workout flow and so on. A screen two tabs open lives there, never
 * in two graphs, so the pill never jumps tabs. See [Route] for the rule feature agents follow.
 *
 * Every pushed screen is wrapped in [PushedScreen] (top bar with a back arrow, no pill) and every tab root in
 * [TabRootScreen]. Motion: pushes slide horizontally, tabs cross-fade ([Motion]).
 */
@Composable
fun IntakeNavHost(
    navController: NavHostController,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    /** True on a first run: the welcome flow is the start destination, the tabs follow it. */
    startOnboarding: Boolean = false,
) {
    val reduce = LocalReduceMotion.current
    val start: Any = if (startOnboarding) Route.Onboarding else TabGraph.Today
    NavHost(
        navController = navController,
        startDestination = start,
        modifier = modifier,
        // Moving between tabs fades through; pushing is a horizontal slide, reversed on back.
        enterTransition = { if (isTabSwitch()) Motion.fadeThroughIn(reduce) else Motion.pushEnter(reduce) },
        exitTransition = { if (isTabSwitch()) Motion.fadeThroughOut(reduce) else Motion.pushExit(reduce) },
        popEnterTransition = { if (isTabSwitch()) Motion.fadeThroughIn(reduce) else Motion.popEnter(reduce) },
        popExitTransition = { if (isTabSwitch()) Motion.fadeThroughOut(reduce) else Motion.popExit(reduce) },
    ) {
        val back: () -> Unit = { navController.popBackStack() }
        val openScriptReference = { page: String, anchor: String? ->
            navController.navigate(Route.ScriptReference(page, anchor))
        }
        val openSettings = { group: String, focus: String? ->
            navController.navigate(Route.SettingsGroup(group, focus))
        }
        val openAi = { openSettings("ai", null) }
        // Another tab's root, with that tab's own back stack restored (the same move the pill makes).
        val openTab = { tab: TopLevelDestination ->
            navController.navigate(tab.graph) {
                // Pop to Today (not the graph's start, which is the welcome flow on a first run).
                popUpTo<Route.Today> { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
            Unit
        }
        val goToToday = { openTab(TopLevelDestination.TODAY) }

        // ---- Welcome flow ----
        composable<Route.Onboarding> {
            FullScreenTask {
                OnboardingScreen(
                    contentPadding = it,
                    onFinished = {
                        if (navController.previousBackStackEntry != null) {
                            // Replayed from Settings: go back to where it was opened.
                            navController.popBackStack()
                        } else {
                            navController.navigate(TabGraph.Today) { popUpTo<Route.Onboarding> { inclusive = true } }
                        }
                    },
                )
            }
        }

        // ---- Today ----
        navigation<TabGraph.Today>(startDestination = Route.Today) {
            composable<Route.Today> {
                TodayScreen(
                    onGoToToday = goToToday,
                    onOpenAiSettings = openAi,
                    onOpenTrain = { openTab(TopLevelDestination.TRAIN) },
                    onResumeWorkout = { id -> navController.navigate(Route.ActiveWorkout(id)) },
                    onMakeProgram = { navController.navigate(Route.ProgramGenerator) },
                    onOpenProfile = { navController.navigate(Route.You) },
                    onBuildPlan = { navController.navigate(Route.YourPlan) },
                    onOpenCheckIn = { navController.navigate(Route.WeeklyCheckIn) },
                    onOpenBodyMetrics = { navController.navigate(Route.BodyMetrics) },
                    onOpenDrinks = { navController.navigate(Route.Drinks) },
                    onSwap = { slot -> navController.navigate(Route.SwapSheet(slot)) },
                    onOpenPlan = { openTab(TopLevelDestination.NUTRITION) },
                    onOpenSettings = { g, f -> openSettings(g, f) },
                    onLogFood = { s, at, mode -> navController.navigate(Route.LogFood(s, at, mode)) },
                )
            }
            // Today has no pushed screens of its own: add new Today routes above this line.
        }

        // ---- Nutrition ----
        navigation<TabGraph.Nutrition>(startDestination = Route.Nutrition) {
            val nutritionActions = NutritionActions(
                onOpenAllergens = { navController.navigate(Route.Allergens) },
                onLogFood = { mode, slot -> navController.navigate(Route.LogFood(slot = slot, mode = mode)) },
                onSwap = { slot -> navController.navigate(Route.SwapSheet(slot)) },
                onOpenDrinks = { navController.navigate(Route.Drinks) },
                onOpenMealPlans = { navController.navigate(Route.MealPlans) },
                onOpenRecipes = { navController.navigate(Route.Recipes) },
                onOpenMealTemplates = { navController.navigate(Route.MealTemplates) },
                onOpenEatingOut = { navController.navigate(Route.EatingOut) },
                onOpenMore = { navController.navigate(Route.NutritionMore) },
            )
            composable<Route.Nutrition> {
                TabRootScreen(TopLevelDestination.NUTRITION, "Nutrition", goToToday) { pad ->
                    NutritionHomeScreen(contentPadding = pad, actions = nutritionActions)
                }
            }
            composable<Route.NutritionMore> {
                PushedScreen("More", back) { pad -> NutritionMoreScreen(contentPadding = pad, actions = nutritionActions) }
            }
            composable<Route.MealPlans> {
                PushedScreen("Meal plans", back) { pad ->
                    PlanScreen(
                        contentPadding = pad,
                        onOpenSwap = { navController.navigate(Route.SwapSheet(null)) },
                        onOpenCook = { id -> navController.navigate(Route.CookMode(id)) },
                        onOpenScriptReference = openScriptReference,
                    )
                }
            }
            composable<Route.Recipes> {
                PushedScreen("Recipes", back) { pad ->
                    RecipesScreen(
                        contentPadding = pad,
                        onOpenRecipe = { id -> navController.navigate(Route.RecipeDetail(id)) },
                        onOpenHunter = { navController.navigate(Route.RecipeHunter) },
                        onOpenImport = { navController.navigate(Route.RecipeImport) },
                        onOpenNew = { navController.navigate(Route.RecipeEditor()) },
                    )
                }
            }
            composable<Route.RecipeHunter> {
                PushedScreen("Recipe Hunter", back) { pad ->
                    RecipeHunterScreen(
                        pad,
                        onOpenAiSettings = openAi,
                        onOpenImport = { navController.navigate(Route.RecipeImport) },
                    )
                }
            }
            composable<Route.RecipeImport> {
                PushedScreen("Import a recipe", back) { pad ->
                    RecipeImportScreen(
                        contentPadding = pad,
                        onSaved = { id ->
                            navController.navigate(Route.RecipeDetail(id)) { popUpTo<Route.RecipeImport> { inclusive = true } }
                        },
                    )
                }
            }
            composable<Route.RecipeEditor> { entry ->
                PushedScreen("Recipe", back) { pad ->
                    RecipeEditorScreen(
                        recipeId = entry.toRoute<Route.RecipeEditor>().recipeId,
                        contentPadding = pad,
                        onDone = { id ->
                            navController.navigate(Route.RecipeDetail(id)) { popUpTo<Route.RecipeEditor> { inclusive = true } }
                        },
                    )
                }
            }
            composable<Route.RecipeDetail> { entry ->
                PushedScreen("Recipe", back) { pad ->
                    RecipeDetailScreen(
                        recipeId = entry.toRoute<Route.RecipeDetail>().recipeId,
                        contentPadding = pad,
                        onDeleted = { navController.popBackStack() },
                        onEdit = { id -> navController.navigate(Route.RecipeEditor(id)) },
                    )
                }
            }
            composable<Route.CookMode> { entry ->
                // Cook mode is full screen with its own chrome: oversized targets, no top bar.
                FullScreenTask {
                    CookModeScreen(
                        recipeId = entry.toRoute<Route.CookMode>().recipeId,
                        contentPadding = it,
                        onClose = { navController.popBackStack() },
                    )
                }
            }
            composable<Route.EatingOut> {
                PushedScreen("Eating out", back) { pad ->
                    EatingOutScreen(
                        contentPadding = pad,
                        onOpenMenuImport = { navController.navigate(Route.MenuSources) },
                    )
                }
            }
            composable<Route.MenuSources> { PushedScreen("Menu import", back) { pad -> MenuSourcesScreen(pad) } }
            composable<Route.MealTemplates> { PushedScreen("Meal templates", back) { pad -> MealTemplatesScreen(pad) } }
            // Add new Nutrition routes above this line.
        }

        // ---- Train ----
        navigation<TabGraph.Train>(startDestination = Route.Train) {
            composable<Route.Train> {
                TabRootScreen(
                    TopLevelDestination.TRAIN,
                    "Train",
                    goToToday,
                    actions = {
                        IconButton(onClick = { navController.navigate(Route.Programs) }) {
                            Icon(Icons.AutoMirrored.Filled.ListAlt, contentDescription = "Programs")
                        }
                    },
                ) { pad ->
                    TrainRootScreen(
                        contentPadding = pad,
                        actions = TrainActions(
                            onOpenSession = { id -> navController.navigate(Route.ActiveWorkout(id)) },
                            onOpenSummary = { id -> navController.navigate(Route.WorkoutSummary(id)) },
                            onOpenHistory = { navController.navigate(Route.WorkoutHistory) },
                            onOpenPrograms = { navController.navigate(Route.Programs) },
                            onOpenGenerator = { navController.navigate(Route.ProgramGenerator) },
                            onOpenVideo = { id -> navController.navigate(Route.VideoDetail(id)) },
                            onMakeWorkout = { id -> navController.navigate(Route.MakeWorkout(id)) },
                            onOpenKeySettings = { openSettings("keys", null) },
                        ),
                    )
                }
            }
            composable<Route.Programs> {
                PushedScreen("Programs", back) { pad ->
                    ProgramsScreen(
                        contentPadding = pad,
                        onOpenSession = { id -> navController.navigate(Route.ActiveWorkout(id)) },
                        onOpenBuilder = { id -> navController.navigate(Route.ProgramBuilder(id)) },
                        onOpenGenerator = { navController.navigate(Route.ProgramGenerator) },
                        onOpenTemplates = { navController.navigate(Route.ProgramTemplates) },
                        onOpenVideo = { id -> navController.navigate(Route.VideoDetail(id)) },
                        onOpenScriptReference = openScriptReference,
                    )
                }
            }
            composable<Route.VideoDetail> {
                PushedScreen("Video", back) { pad ->
                    VideoDetailScreen(
                        contentPadding = pad,
                        onMakeWorkout = { id -> navController.navigate(Route.MakeWorkout(id)) },
                        onOpenWorkout = { id -> navController.navigate(Route.ProgramBuilder(id)) },
                    )
                }
            }
            composable<Route.MakeWorkout> {
                PushedScreen("Make this a workout", back) { pad ->
                    MakeWorkoutScreen(
                        contentPadding = pad,
                        onSaved = { _, sessionId ->
                            if (sessionId != null) {
                                navController.navigate(Route.ActiveWorkout(sessionId)) { popUpTo<Route.MakeWorkout> { inclusive = true } }
                            } else {
                                navController.popBackStack()
                            }
                        },
                        onAddByHand = { id ->
                            navController.navigate(Route.ProgramBuilder(id)) { popUpTo<Route.MakeWorkout> { inclusive = true } }
                        },
                    )
                }
            }
            composable<Route.ProgramTemplates> {
                PushedScreen("Program templates", back) { pad ->
                    ProgramTemplatesScreen(
                        contentPadding = pad,
                        onCreated = { id ->
                            navController.navigate(Route.ProgramBuilder(id)) { popUpTo<Route.ProgramTemplates> { inclusive = true } }
                        },
                    )
                }
            }
            // Add new Train routes above this line.
        }

        // ---- Progress ----
        navigation<TabGraph.Progress>(startDestination = Route.Progress) {
            composable<Route.Progress> {
                TabRootScreen(TopLevelDestination.PROGRESS, "Progress", goToToday) { pad ->
                    ProgressScreen(
                        contentPadding = pad,
                        onAddMeasurement = { navController.navigate(Route.BodyMetrics) },
                        onAddJumpTest = { navController.navigate(Route.Performance) },
                        onOpenTrainingStats = { navController.navigate(Route.WorkoutHistory) },
                        onOpenCheckIn = { navController.navigate(Route.WeeklyCheckIn) },
                    )
                }
            }
            // Add new Progress routes above this line.
        }

        // ---- Shared screens: registered ONCE, here at the root. Add new shared routes above the last line. ----
        composable<Route.You> {
            PushedScreen(
                "You",
                back,
                actions = {
                    IconButton(onClick = { navController.navigate(Route.Settings) }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            ) { pad ->
                YouScreen(
                    contentPadding = pad,
                    onEditProfile = { navController.navigate(Route.Profile) },
                    onOpenPlan = { navController.navigate(Route.YourPlan) },
                    onChangeSetting = { group, focus -> openSettings(group, focus) },
                    onOpenPhotos = { navController.navigate(Route.ProgressPhotos) },
                    onOpenCompare = { navController.navigate(Route.PhotoCompare) },
                    onOpenHelp = { openSettings("about", null) },
                )
            }
        }
        composable<Route.Settings> {
            PushedScreen("Settings", back) { pad ->
                SettingsHomeScreen(pad, onOpenGroup = { group, focus -> openSettings(group, focus) })
            }
        }
        composable<Route.SettingsGroup> { entry ->
            val args = entry.toRoute<Route.SettingsGroup>()
            SettingsGroupScreen(
                groupId = args.group,
                focus = args.focus,
                onBack = back,
                onOpenScriptReference = openScriptReference,
                onOpenPrivacy = { navController.navigate(Route.Privacy) },
                onOpenWelcome = { navController.navigate(Route.Onboarding) },
                onGoToToday = goToToday,
            )
        }
        composable<Route.Privacy> { PushedScreen("What leaves this phone", back) { pad -> PrivacyScreen(pad) } }
        composable<Route.Profile> {
            PushedScreen("About you", back) { pad ->
                ProfileScreen(
                    contentPadding = pad,
                    onBuildPlan = { navController.navigate(Route.YourPlan) },
                    onOpenTargetSettings = { openSettings("nutrition", null) },
                )
            }
        }
        composable<Route.YourPlan> {
            PushedScreen("Your plan", back) { pad ->
                YourPlanScreen(
                    contentPadding = pad,
                    onOpenProfile = { navController.navigate(Route.Profile) },
                    onOpenGenerator = { navController.navigate(Route.ProgramGenerator) },
                    onOpenProgram = { id -> navController.navigate(Route.ProgramBuilder(id)) },
                )
            }
        }
        composable<Route.ProgressPhotos> { PushedScreen("Progress photos", back) { pad -> ProgressPhotosScreen(pad, onCompare = { navController.navigate(Route.PhotoCompare) }) } }
        composable<Route.PhotoCompare> { PushedScreen("Compare photos", back) { pad -> PhotoCompareScreen(pad) } }
        composable<Route.ScriptReference> { entry ->
            val args = entry.toRoute<Route.ScriptReference>()
            PushedScreen("Script language", back) { pad -> ScriptReferenceScreen(args.page, args.anchor, pad) }
        }
        composable<Route.Allergens> {
            PushedScreen("Allergens", back) { pad ->
                AllergensScreen(pad, onOpenAllergen = { navController.navigate(Route.AllergenKeywords(it)) })
            }
        }
        composable<Route.AllergenKeywords> { entry ->
            PushedScreen("Keywords", back) { pad ->
                AllergenKeywordsScreen(entry.toRoute<Route.AllergenKeywords>().allergenKey, pad)
            }
        }
        composable<Route.LogFood> { entry ->
            PushedScreen("Log food", back) { pad ->
                val args = entry.toRoute<Route.LogFood>()
                val slot = args.slot?.let { n -> MealSlot.entries.firstOrNull { it.name == n } } ?: defaultMealSlot(java.time.LocalTime.now().hour)
                // The time to log at: the one the caller passed (Today's selected day), else now, fixed for this visit.
                val at = remember(entry) { if (args.atMillis > 0) args.atMillis else System.currentTimeMillis() }
                LogFoodScreen(
                    slot = slot,
                    loggedAtMillis = at,
                    initialTab = AddFoodTab.fromName(args.mode),
                    contentPadding = pad,
                    onBack = back,
                    onOpenFood = { id, chosen -> navController.navigate(Route.FoodDetail(id, chosen.name, at)) },
                    onOpenDrinks = { q -> navController.navigate(if (q.isBlank()) Route.Drinks else Route.DrinksSearch(q)) },
                    onOpenAiSettings = openAi,
                )
            }
        }
        composable<Route.FoodDetail> { entry ->
            val args = entry.toRoute<Route.FoodDetail>()
            PushedScreen("Food", back) { pad ->
                FoodDetailScreen(
                    foodId = args.foodId,
                    slot = args.slot,
                    atMillis = args.atMillis,
                    contentPadding = pad,
                    onBack = back,
                    // Logged from Log food: leave both, back to where it was opened. From Drinks or Mine: just this page.
                    onLogged = { if (!navController.popBackStack<Route.LogFood>(inclusive = true)) navController.popBackStack() },
                    onOpenFoodData = { openSettings("fooddb", null) },
                )
            }
        }
        composable<Route.Drinks> {
            PushedScreen("Drinks", back) { pad ->
                DrinksScreen(pad, onOpenFood = { id -> navController.navigate(Route.FoodDetail(id)) })
            }
        }
        composable<Route.DrinksSearch> { entry ->
            val typed = entry.toRoute<Route.DrinksSearch>().query
            PushedScreen("Drinks", back) { pad ->
                DrinksScreen(pad, onOpenFood = { id -> navController.navigate(Route.FoodDetail(id)) }, initialQuery = typed)
            }
        }
        dialog<Route.SwapSheet> { entry ->
            SwapSheet(
                slot = entry.toRoute<Route.SwapSheet>().slot,
                onDismiss = back,
                onOpenAiSettings = { back(); openAi() },
                onOpenRadiusSetting = { back(); openSettings("nutrition", "eatOutRadius") },
            )
        }
        composable<Route.ActiveWorkout> {
            FullScreenTask {
                ActiveWorkoutScreen(
                    contentPadding = it,
                    // The back arrow minimises: the session keeps running and the session bar brings it back.
                    onMinimise = back,
                    onFinished = { id ->
                        navController.navigate(Route.WorkoutSummary(id)) { popUpTo<Route.ActiveWorkout> { inclusive = true } }
                    },
                )
            }
        }
        composable<Route.WorkoutSummary> {
            PushedScreen("Workout summary", back) { pad -> WorkoutSummaryScreen(contentPadding = pad, onDone = back) }
        }
        composable<Route.WorkoutHistory> {
            PushedScreen("Training log", back) { pad ->
                WorkoutHistoryScreen(pad, onOpenSession = { id -> navController.navigate(Route.WorkoutSummary(id)) })
            }
        }
        composable<Route.ProgramGenerator> {
            PushedScreen("Make me a program", back) { pad ->
                ProgramGeneratorScreen(
                    contentPadding = pad,
                    onSaved = { id ->
                        navController.navigate(Route.ProgramBuilder(id)) { popUpTo<Route.ProgramGenerator> { inclusive = true } }
                    },
                )
            }
        }
        composable<Route.ProgramBuilder> {
            PushedScreen("Program", back) { pad -> ProgramBuilderScreen(pad, onOpenScriptReference = openScriptReference) }
        }
        composable<Route.Performance> {
            PushedScreen("Jump tests", back) { pad -> PerformanceScreen(contentPadding = pad, onSaved = {}) }
        }
        composable<Route.BodyMetrics> {
            PushedScreen("Log weight", back) { pad ->
                BodyMetricsScreen(contentPadding = pad, onSaved = { navController.popBackStack() })
            }
        }
        composable<Route.WeeklyCheckIn> {
            PushedScreen("Weekly check-in", back) { pad -> WeeklyCheckInScreen(contentPadding = pad) }
        }
        // Add new shared routes above this line.
    }
}
