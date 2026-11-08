package com.zeroindex.intake.ui.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.zeroindex.intake.ui.screens.ai.AiSettingsScreen
import com.zeroindex.intake.ui.screens.allergen.AllergenReferenceScreen
import com.zeroindex.intake.ui.screens.body.BodyMetricsScreen
import com.zeroindex.intake.ui.screens.more.MoreScreen
import com.zeroindex.intake.ui.screens.performance.PerformanceScreen
import com.zeroindex.intake.ui.screens.plan.PlanScreen
import com.zeroindex.intake.ui.screens.planb.PlanBScreen
import com.zeroindex.intake.ui.screens.plan.RecipeDetailScreen
import com.zeroindex.intake.ui.screens.plan.RecipeHunterScreen
import com.zeroindex.intake.ui.screens.progress.ProgressScreen
import com.zeroindex.intake.ui.screens.settings.PrivacyScreen
import com.zeroindex.intake.ui.screens.settings.SettingsScreen
import com.zeroindex.intake.ui.screens.today.TodayScreen
import com.zeroindex.intake.ui.screens.train.ExerciseLibraryScreen
import com.zeroindex.intake.ui.screens.train.ProgramBuilderScreen
import com.zeroindex.intake.ui.screens.train.TrainScreen
import com.zeroindex.intake.ui.screens.train.WorkoutHistoryScreen
import com.zeroindex.intake.ui.screens.workout.ActiveWorkoutScreen

@Composable
fun IntakeNavHost(
    navController: NavHostController,
    contentPadding: PaddingValues,
) {
    NavHost(navController = navController, startDestination = Route.Today) {
        composable<Route.Today> {
            TodayScreen(contentPadding, onOpenAiSettings = { navController.navigate(Route.AiSettings) })
        }
        composable<Route.Plan> {
            PlanScreen(
                contentPadding = contentPadding,
                onOpenHunter = { navController.navigate(Route.RecipeHunter) },
                onOpenPlanB = { navController.navigate(Route.PlanB) },
                onOpenRecipe = { id -> navController.navigate(Route.RecipeDetail(id)) },
            )
        }
        composable<Route.PlanB> {
            PlanBScreen(contentPadding, onOpenAiSettings = { navController.navigate(Route.AiSettings) })
        }
        composable<Route.RecipeHunter> {
            RecipeHunterScreen(contentPadding, onOpenAiSettings = { navController.navigate(Route.AiSettings) })
        }
        composable<Route.RecipeDetail> { entry ->
            RecipeDetailScreen(
                recipeId = entry.toRoute<Route.RecipeDetail>().recipeId,
                contentPadding = contentPadding,
                onDeleted = { navController.popBackStack() },
            )
        }
        composable<Route.Train> {
            TrainScreen(
                contentPadding = contentPadding,
                onOpenSession = { id -> navController.navigate(Route.ActiveWorkout(id)) },
                onOpenBuilder = { navController.navigate(Route.ProgramBuilder) },
                onOpenHistory = { navController.navigate(Route.WorkoutHistory) },
                onOpenExercises = { navController.navigate(Route.ExerciseLibrary) },
            )
        }
        composable<Route.Progress> {
            ProgressScreen(
                contentPadding = contentPadding,
                onAddMeasurement = { navController.navigate(Route.BodyMetrics) },
                onAddJumpTest = { navController.navigate(Route.Performance) },
            )
        }
        composable<Route.More> {
            MoreScreen(
                contentPadding = contentPadding,
                onNavigate = { route -> navController.navigate(route) },
            )
        }

        composable<Route.AllergenReference> { AllergenReferenceScreen(contentPadding) }
        composable<Route.Settings> {
            SettingsScreen(contentPadding, onOpenAi = { navController.navigate(Route.AiSettings) })
        }
        composable<Route.AiSettings> { AiSettingsScreen(contentPadding) }
        composable<Route.Privacy> { PrivacyScreen(contentPadding) }
        composable<Route.ExerciseLibrary> { ExerciseLibraryScreen(contentPadding) }
        composable<Route.WorkoutHistory> { WorkoutHistoryScreen(contentPadding) }
        composable<Route.ProgramBuilder> { ProgramBuilderScreen(contentPadding) }
        composable<Route.ActiveWorkout> {
            ActiveWorkoutScreen(
                contentPadding = contentPadding,
                onFinished = { navController.popBackStack() },
            )
        }
        composable<Route.Performance> {
            PerformanceScreen(contentPadding = contentPadding, onSaved = {})
        }
        composable<Route.BodyMetrics> {
            BodyMetricsScreen(
                contentPadding = contentPadding,
                onSaved = { navController.popBackStack() },
            )
        }
    }
}
