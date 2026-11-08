package com.zeroindex.intake.ui.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.zeroindex.intake.ui.screens.more.MoreScreen
import com.zeroindex.intake.ui.screens.plan.PlanScreen
import com.zeroindex.intake.ui.screens.progress.ProgressScreen
import com.zeroindex.intake.ui.screens.today.TodayScreen
import com.zeroindex.intake.ui.screens.train.TrainScreen

@Composable
fun IntakeNavHost(
    navController: NavHostController,
    contentPadding: PaddingValues,
) {
    NavHost(navController = navController, startDestination = Route.Today) {
        composable<Route.Today> { TodayScreen(contentPadding) }
        composable<Route.Plan> {
            PlanScreen(
                contentPadding = contentPadding,
                onCookRecipe = { recipeId -> navController.navigate(Route.CookMode(recipeId)) },
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
            )
        }
        composable<Route.More> {
            MoreScreen(
                contentPadding = contentPadding,
                onNavigate = { route -> navController.navigate(route) },
            )
        }
    }
}
