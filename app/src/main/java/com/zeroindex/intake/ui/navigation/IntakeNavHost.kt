package com.zeroindex.intake.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.toRoute
import com.zeroindex.intake.ui.motion.LocalReduceMotion
import com.zeroindex.intake.ui.motion.Motion
import com.zeroindex.intake.ui.onboarding.OnboardingScreen
import com.zeroindex.intake.ui.screens.ai.AiSettingsScreen
import com.zeroindex.intake.ui.screens.allergen.AllergenKeywordsScreen
import com.zeroindex.intake.ui.screens.allergen.AllergenReferenceScreen
import com.zeroindex.intake.ui.screens.allergen.MyAllergensScreen
import com.zeroindex.intake.ui.screens.backup.BackupScreen
import com.zeroindex.intake.ui.screens.body.BodyMetricsScreen
import com.zeroindex.intake.ui.screens.coach.CoachCheckInScreen
import com.zeroindex.intake.ui.screens.health.HealthConnectScreen
import com.zeroindex.intake.ui.screens.hub.AllergensScreen
import com.zeroindex.intake.ui.screens.hub.EatingOutScreen
import com.zeroindex.intake.ui.screens.fooddata.FoodDataScreen
import com.zeroindex.intake.ui.screens.fooddata.FoodDetailScreen
import com.zeroindex.intake.ui.screens.fooddata.MyFoodsScreen
import com.zeroindex.intake.ui.screens.hub.FoodHubScreen
import com.zeroindex.intake.ui.screens.hub.RecipesScreen
import com.zeroindex.intake.ui.screens.hub.TrainHubScreen
import com.zeroindex.intake.ui.screens.menu.MenuSourcesScreen
import com.zeroindex.intake.ui.screens.more.HelpScreen
import com.zeroindex.intake.ui.screens.more.MoreScreen
import com.zeroindex.intake.ui.screens.more.ScriptReferenceScreen
import com.zeroindex.intake.ui.screens.performance.PerformanceScreen
import com.zeroindex.intake.ui.screens.plan.CookModeScreen
import com.zeroindex.intake.ui.screens.plan.PlanScreen
import com.zeroindex.intake.ui.screens.plan.RecipeDetailScreen
import com.zeroindex.intake.ui.screens.plan.RecipeHunterScreen
import com.zeroindex.intake.ui.screens.planb.PlanBScreen
import com.zeroindex.intake.ui.screens.recipe.RecipeEditorScreen
import com.zeroindex.intake.ui.screens.recipe.RecipeImportScreen
import com.zeroindex.intake.ui.screens.progress.ProgressScreen
import com.zeroindex.intake.ui.screens.report.WeeklyReportScreen
import com.zeroindex.intake.ui.screens.settings.PrivacyScreen
import com.zeroindex.intake.ui.screens.settings.SettingsScreen
import com.zeroindex.intake.ui.screens.templates.MealTemplatesScreen
import com.zeroindex.intake.ui.screens.today.TodayScreen
import com.zeroindex.intake.ui.screens.train.ExerciseLibraryScreen
import com.zeroindex.intake.ui.screens.train.ProgramBuilderScreen
import com.zeroindex.intake.ui.screens.profile.ProfileScreen
import com.zeroindex.intake.ui.screens.profile.YourPlanScreen
import com.zeroindex.intake.ui.screens.train.ProgramGeneratorScreen
import com.zeroindex.intake.ui.screens.train.ProgramTemplatesScreen
import com.zeroindex.intake.ui.screens.train.TrainScreen
import com.zeroindex.intake.ui.screens.train.WorkoutHistoryScreen
import com.zeroindex.intake.ui.screens.workout.ActiveWorkoutScreen

/** The tab a destination belongs to, found through its nested graph; null before the graph is entered. */
fun tabOf(destination: NavDestination?): TopLevelDestination? =
    destination?.let { d ->
        TopLevelDestination.entries.firstOrNull { tab -> d.hierarchy.any { it.hasRoute(tab.graph::class) } }
    }

private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch(): Boolean =
    TabPolicy.isTabSwitch(tabOf(initialState.destination), tabOf(targetState.destination))

/**
 * One nested graph per tab, so each tab keeps its own back stack. Screens that several tabs open
 * (script reference, AI settings, settings) are registered in each graph that links to them; a
 * `navigate` finds the copy inside the current tab, so the pill keeps showing that tab.
 *
 * Feature agents: add new routes inside the graph of the tab they belong to, above that graph's
 * "add ... above this line" marker.
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
        // Moving between tabs fades through; pushing inside a tab is shared-axis X, reversed on back.
        enterTransition = {
            if (isTabSwitch()) Motion.fadeThroughIn(reduce) else Motion.sharedAxisEnter(forward = true, reduce = reduce)
        },
        exitTransition = {
            if (isTabSwitch()) Motion.fadeThroughOut(reduce) else Motion.sharedAxisExit(forward = true, reduce = reduce)
        },
        popEnterTransition = {
            if (isTabSwitch()) Motion.fadeThroughIn(reduce) else Motion.sharedAxisEnter(forward = false, reduce = reduce)
        },
        popExitTransition = {
            if (isTabSwitch()) Motion.fadeThroughOut(reduce) else Motion.sharedAxisExit(forward = false, reduce = reduce)
        },
    ) {
        val openScriptReference = { page: String, anchor: String? ->
            navController.navigate(Route.ScriptReference(page, anchor))
        }
        val openAiSettings = { navController.navigate(Route.AiSettings) }
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

        // ---- Welcome flow ----
        composable<Route.Onboarding> {
            OnboardingScreen(
                contentPadding = contentPadding,
                onFinished = {
                    if (navController.previousBackStackEntry != null) {
                        // Replayed from Help: go back to where it was opened.
                        navController.popBackStack()
                    } else {
                        navController.navigate(TabGraph.Today) { popUpTo<Route.Onboarding> { inclusive = true } }
                    }
                },
            )
        }

        // ---- Today ----
        navigation<TabGraph.Today>(startDestination = Route.Today) {
            composable<Route.Today> {
                TodayScreen(
                    contentPadding,
                    onOpenAiSettings = openAiSettings,
                    onOpenTrain = { openTab(TopLevelDestination.TRAIN) },
                    onResumeWorkout = { id -> navController.navigate(Route.ActiveWorkout(id)) },
                    onMakeProgram = { navController.navigate(Route.ProgramGenerator) },
                    onOpenProfile = { navController.navigate(Route.Profile) },
                    onBuildPlan = { navController.navigate(Route.YourPlan) },
                    onOpenCheckIn = { navController.navigate(Route.CoachCheckIn) },
                    onOpenBodyMetrics = { navController.navigate(Route.BodyMetrics) },
                )
            }
            aiSettings(contentPadding)
            // Screens a Today card opens, registered here too so the pill keeps showing Today
            // (NavGraphGuardTest checks that every navigate() in a graph has its target in that graph).
            activeWorkout(navController, contentPadding)
            programGenerator(navController, contentPadding)
            programBuilder(contentPadding, openScriptReference)
            profileRoutes(navController, contentPadding)
            coachCheckIn(contentPadding)
            bodyMetrics(navController, contentPadding)
            settings(navController, contentPadding, onGoToToday = { openTab(TopLevelDestination.TODAY) })
            composable<Route.Backup> { BackupScreen(contentPadding) }
            foodDataRoutes(navController, contentPadding)
            scriptReference(contentPadding)
            // Today is not restructured; add new Today routes above this line.
        }

        // ---- Food ----
        navigation<TabGraph.Food>(startDestination = Route.Food) {
            composable<Route.Food> {
                FoodHubScreen(
                    contentPadding = contentPadding,
                    onOpenMealTemplates = { navController.navigate(Route.MealTemplates) },
                    onOpenMealPlans = { navController.navigate(Route.MealPlans) },
                    onOpenRecipes = { navController.navigate(Route.Recipes) },
                    onOpenEatingOut = { navController.navigate(Route.EatingOut) },
                    onOpenAllergens = { navController.navigate(Route.Allergens) },
                    onOpenFoodScriptHelp = { openScriptReference("meal-plan", null) },
                    onOpenAiSettings = openAiSettings,
                    onOpenMyFoods = { navController.navigate(Route.MyFoods) },
                    onOpenFood = { id -> navController.navigate(Route.FoodDetail(id)) },
                    onOpenFoodData = { navController.navigate(Route.FoodData) },
                )
            }
            composable<Route.MealPlans> {
                PlanScreen(
                    contentPadding = contentPadding,
                    onOpenPlanB = { navController.navigate(Route.PlanB) },
                    onOpenCook = { id -> navController.navigate(Route.CookMode(id)) },
                    onOpenScriptReference = openScriptReference,
                )
            }
            composable<Route.Recipes> {
                RecipesScreen(
                    contentPadding = contentPadding,
                    onOpenRecipe = { id -> navController.navigate(Route.RecipeDetail(id)) },
                    onOpenHunter = { navController.navigate(Route.RecipeHunter) },
                    onOpenImport = { navController.navigate(Route.RecipeImport) },
                    onOpenNew = { navController.navigate(Route.RecipeEditor()) },
                )
            }
            composable<Route.RecipeHunter> {
                RecipeHunterScreen(
                    contentPadding,
                    onOpenAiSettings = openAiSettings,
                    onOpenImport = { navController.navigate(Route.RecipeImport) },
                )
            }
            composable<Route.RecipeImport> {
                RecipeImportScreen(
                    contentPadding = contentPadding,
                    onSaved = { id ->
                        navController.navigate(Route.RecipeDetail(id)) { popUpTo<Route.RecipeImport> { inclusive = true } }
                    },
                )
            }
            composable<Route.RecipeEditor> { entry ->
                RecipeEditorScreen(
                    recipeId = entry.toRoute<Route.RecipeEditor>().recipeId,
                    contentPadding = contentPadding,
                    onDone = { id ->
                        navController.navigate(Route.RecipeDetail(id)) { popUpTo<Route.RecipeEditor> { inclusive = true } }
                    },
                )
            }
            composable<Route.RecipeDetail> { entry ->
                RecipeDetailScreen(
                    recipeId = entry.toRoute<Route.RecipeDetail>().recipeId,
                    contentPadding = contentPadding,
                    onDeleted = { navController.popBackStack() },
                    onEdit = { id -> navController.navigate(Route.RecipeEditor(id)) },
                )
            }
            composable<Route.CookMode> { entry ->
                CookModeScreen(
                    recipeId = entry.toRoute<Route.CookMode>().recipeId,
                    contentPadding = contentPadding,
                    onClose = { navController.popBackStack() },
                )
            }
            composable<Route.EatingOut> {
                EatingOutScreen(
                    contentPadding = contentPadding,
                    onOpenPlanB = { navController.navigate(Route.PlanB) },
                    onOpenMenuImport = { navController.navigate(Route.MenuSources) },
                )
            }
            composable<Route.PlanB> { PlanBScreen(contentPadding, onOpenAiSettings = openAiSettings) }
            composable<Route.MenuSources> { MenuSourcesScreen(contentPadding) }
            composable<Route.MealTemplates> { MealTemplatesScreen(contentPadding) }
            composable<Route.Allergens> {
                AllergensScreen(
                    contentPadding = contentPadding,
                    onOpenMine = { navController.navigate(Route.MyAllergens) },
                    onOpenReference = { navController.navigate(Route.AllergenReference) },
                )
            }
            composable<Route.AllergenReference> { AllergenReferenceScreen(contentPadding) }
            scriptReference(contentPadding)
            aiSettings(contentPadding)
            settings(navController, contentPadding, onGoToToday = { openTab(TopLevelDestination.TODAY) })
            composable<Route.Backup> { BackupScreen(contentPadding) }
            foodDataRoutes(navController, contentPadding)
            composable<Route.MyFoods> {
                MyFoodsScreen(contentPadding, onOpenFood = { id -> navController.navigate(Route.FoodDetail(id)) })
            }
            composable<Route.FoodDetail> { entry ->
                FoodDetailScreen(
                    foodId = entry.toRoute<Route.FoodDetail>().foodId,
                    contentPadding = contentPadding,
                    onBack = { navController.popBackStack() },
                    onOpenFoodData = { navController.navigate(Route.FoodData) },
                )
            }
            // Add new Food routes above this line.
        }

        // ---- Train ----
        navigation<TabGraph.Train>(startDestination = Route.Train) {
            composable<Route.Train> {
                TrainHubScreen(
                    contentPadding = contentPadding,
                    onOpenSession = { id -> navController.navigate(Route.ActiveWorkout(id)) },
                    onOpenPrograms = { navController.navigate(Route.Programs) },
                    onOpenBuilder = { navController.navigate(Route.ProgramBuilder()) },
                    onOpenGenerator = { navController.navigate(Route.ProgramGenerator) },
                    onOpenTemplates = { navController.navigate(Route.ProgramTemplates) },
                    onOpenExercises = { navController.navigate(Route.ExerciseLibrary) },
                    onOpenPerformance = { navController.navigate(Route.Performance) },
                    onOpenProgramScriptHelp = { openScriptReference("program", null) },
                )
            }
            composable<Route.Programs> {
                TrainScreen(
                    contentPadding = contentPadding,
                    onOpenSession = { id -> navController.navigate(Route.ActiveWorkout(id)) },
                    onOpenBuilder = { id -> navController.navigate(Route.ProgramBuilder(id)) },
                    onOpenGenerator = { navController.navigate(Route.ProgramGenerator) },
                    onOpenTemplates = { navController.navigate(Route.ProgramTemplates) },
                    onOpenScriptReference = openScriptReference,
                )
            }
            programBuilder(contentPadding, openScriptReference)
            composable<Route.ProgramTemplates> {
                ProgramTemplatesScreen(
                    contentPadding = contentPadding,
                    onCreated = { id ->
                        navController.navigate(Route.ProgramBuilder(id)) { popUpTo<Route.ProgramTemplates> { inclusive = true } }
                    },
                )
            }
            programGenerator(navController, contentPadding)
            composable<Route.ExerciseLibrary> { ExerciseLibraryScreen(contentPadding) }
            composable<Route.Performance> { PerformanceScreen(contentPadding = contentPadding, onSaved = {}) }
            activeWorkout(navController, contentPadding)
            scriptReference(contentPadding)
            // Add new Train routes above this line.
        }

        // ---- Progress ----
        navigation<TabGraph.Progress>(startDestination = Route.Progress) {
            composable<Route.Progress> {
                ProgressScreen(
                    contentPadding = contentPadding,
                    onAddMeasurement = { navController.navigate(Route.BodyMetrics) },
                    onAddJumpTest = { navController.navigate(Route.Performance) },
                    onOpenTrainingStats = { navController.navigate(Route.WorkoutHistory) },
                    onOpenWeeklyReport = { navController.navigate(Route.WeeklyReport) },
                    onOpenHealthConnect = { navController.navigate(Route.HealthConnect) },
                    onOpenCoach = { navController.navigate(Route.CoachCheckIn) },
                )
            }
            bodyMetrics(navController, contentPadding)
            composable<Route.Performance> { PerformanceScreen(contentPadding = contentPadding, onSaved = {}) }
            composable<Route.WorkoutHistory> { WorkoutHistoryScreen(contentPadding) }
            composable<Route.WeeklyReport> { WeeklyReportScreen(contentPadding) }
            composable<Route.HealthConnect> { HealthConnectScreen(contentPadding) }
            coachCheckIn(contentPadding)
            // Add new Progress routes above this line.
        }

        // ---- More ----
        navigation<TabGraph.More>(startDestination = Route.More) {
            composable<Route.More> {
                MoreScreen(contentPadding = contentPadding, onNavigate = { route -> navController.navigate(route) })
            }
            settings(navController, contentPadding, onGoToToday = { openTab(TopLevelDestination.TODAY) })
            composable<Route.Backup> { BackupScreen(contentPadding) }
            aiSettings(contentPadding)
            composable<Route.Privacy> { PrivacyScreen(contentPadding) }
            composable<Route.Help> {
                HelpScreen(
                    contentPadding = contentPadding,
                    onOpenScriptDocs = { page -> openScriptReference(page, null) },
                    onGoToToday = { openTab(TopLevelDestination.TODAY) },
                    onOpenWelcome = { navController.navigate(Route.Onboarding) },
                )
            }
            scriptReference(contentPadding)
            profileRoutes(navController, contentPadding)
            programGenerator(navController, contentPadding)
            programBuilder(contentPadding, openScriptReference)
            foodDataRoutes(navController, contentPadding)
            // Add new More routes above this line.
        }
    }
}

private fun NavGraphBuilder.scriptReference(contentPadding: PaddingValues) {
    composable<Route.ScriptReference> { entry ->
        val args = entry.toRoute<Route.ScriptReference>()
        ScriptReferenceScreen(args.page, args.anchor, contentPadding)
    }
}

private fun NavGraphBuilder.activeWorkout(navController: NavHostController, contentPadding: PaddingValues) {
    composable<Route.ActiveWorkout> {
        ActiveWorkoutScreen(contentPadding = contentPadding, onFinished = { navController.popBackStack() })
    }
}

private fun NavGraphBuilder.programBuilder(
    contentPadding: PaddingValues,
    openScriptReference: (String, String?) -> Unit,
) {
    composable<Route.ProgramBuilder> { ProgramBuilderScreen(contentPadding, onOpenScriptReference = openScriptReference) }
}

private fun NavGraphBuilder.programGenerator(navController: NavHostController, contentPadding: PaddingValues) {
    composable<Route.ProgramGenerator> {
        ProgramGeneratorScreen(
            contentPadding = contentPadding,
            onSaved = { id ->
                navController.navigate(Route.ProgramBuilder(id)) { popUpTo<Route.ProgramGenerator> { inclusive = true } }
            },
        )
    }
}

private fun NavGraphBuilder.profileRoutes(navController: NavHostController, contentPadding: PaddingValues) {
    composable<Route.Profile> {
        ProfileScreen(
            contentPadding = contentPadding,
            onBuildPlan = { navController.navigate(Route.YourPlan) },
            onOpenTargetSettings = { navController.navigate(Route.Settings(section = "targets")) },
        )
    }
    composable<Route.YourPlan> {
        YourPlanScreen(
            contentPadding = contentPadding,
            onOpenProfile = { navController.navigate(Route.Profile) },
            onOpenGenerator = { navController.navigate(Route.ProgramGenerator) },
            onOpenProgram = { id -> navController.navigate(Route.ProgramBuilder(id)) },
        )
    }
}

private fun NavGraphBuilder.coachCheckIn(contentPadding: PaddingValues) {
    composable<Route.CoachCheckIn> { CoachCheckInScreen(contentPadding = contentPadding) }
}

private fun NavGraphBuilder.bodyMetrics(navController: NavHostController, contentPadding: PaddingValues) {
    composable<Route.BodyMetrics> {
        BodyMetricsScreen(contentPadding = contentPadding, onSaved = { navController.popBackStack() })
    }
}

/** Food data and the allergen screens Settings links to; registered in every graph that has Settings. */
private fun NavGraphBuilder.foodDataRoutes(navController: NavHostController, contentPadding: PaddingValues) {
    composable<Route.FoodData> { FoodDataScreen(contentPadding) }
    composable<Route.MyAllergens> {
        MyAllergensScreen(
            contentPadding,
            onOpenAllergen = { navController.navigate(Route.AllergenKeywords(it)) },
        )
    }
    composable<Route.AllergenKeywords> { entry ->
        AllergenKeywordsScreen(entry.toRoute<Route.AllergenKeywords>().allergenKey, contentPadding)
    }
}

private fun NavGraphBuilder.aiSettings(contentPadding: PaddingValues) {
    composable<Route.AiSettings> { AiSettingsScreen(contentPadding) }
}

private fun NavGraphBuilder.settings(navController: NavHostController, contentPadding: PaddingValues, onGoToToday: () -> Unit) {
    composable<Route.Settings> { entry ->
        SettingsScreen(
            contentPadding,
            onOpenAi = { navController.navigate(Route.AiSettings) },
            onOpenBackup = { navController.navigate(Route.Backup) },
            onOpenFoodData = { navController.navigate(Route.FoodData) },
            onOpenMyAllergens = { navController.navigate(Route.MyAllergens) },
            onGoToToday = onGoToToday,
            section = entry.toRoute<Route.Settings>().section,
        )
    }
}
