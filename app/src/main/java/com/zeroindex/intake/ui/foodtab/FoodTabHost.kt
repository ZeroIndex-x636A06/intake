package com.zeroindex.intake.ui.foodtab

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zeroindex.intake.ui.components.Segmented
import com.zeroindex.intake.ui.screens.hub.RecipesScreen
import com.zeroindex.intake.ui.screens.nutrition.NutritionActions
import com.zeroindex.intake.ui.screens.nutrition.NutritionHomeScreen
import com.zeroindex.intake.ui.screens.plan.PlanScreen
import com.zeroindex.intake.ui.theme.IntakeSpacing

/** What the Plan and Recipes segments open (the same screens, with the same callbacks, as their pushed routes). */
class FoodTabActions(
    val diary: NutritionActions,
    val library: LibraryActions,
    val onOpenSwap: () -> Unit,
    val onOpenCook: (String) -> Unit,
    val onOpenScriptReference: (page: String, anchor: String?) -> Unit,
    val onOpenRecipe: (String) -> Unit,
    val onOpenHunter: () -> Unit,
    val onOpenImport: () -> Unit,
    val onOpenNewRecipe: () -> Unit,
)

/**
 * The body of the Food tab root: a segmented control (Diary / Plan / Recipes / Library) under the top bar, and the chosen
 * segment's screen below it. Diary is the old Nutrition home; Plan and Recipes are the existing screens, hosted here instead
 * of behind a "More" list; Library is the hub for everything else. The chosen segment survives rotation and leaving the tab
 * (it is saved with the tab's back stack entry).
 *
 * [contentPadding] is the tab root's (top bar height, room for the dock). The segmented control takes the top part; each
 * segment gets the bottom part only, so its own list does not add the top bar's height twice.
 */
@Composable
fun FoodTabHost(
    contentPadding: PaddingValues,
    actions: FoodTabActions,
    modifier: Modifier = Modifier,
) {
    var selected by rememberSaveable { mutableIntStateOf(FoodSegment.Default.ordinal) }
    val segment = FoodSegment.fromIndex(selected)
    val below = PaddingValues(bottom = contentPadding.calculateBottomPadding())
    Column(modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
        Segmented(
            options = FoodSegment.entries.map { stringResource(it.labelRes) },
            selectedIndex = segment.ordinal,
            onSelect = { selected = it },
            role = androidx.compose.ui.semantics.Role.Tab,
            modifier = Modifier.fillMaxWidth().padding(horizontal = IntakeSpacing.ScreenGutter, vertical = 4.dp),
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (segment) {
                FoodSegment.Diary -> NutritionHomeScreen(
                    contentPadding = below,
                    // "Open plan" on a planned meal now switches to the Plan segment instead of pushing a second copy of it.
                    actions = actions.diary.withSegments(
                        onPlan = { selected = FoodSegment.Plan.ordinal },
                        onRecipes = { selected = FoodSegment.Recipes.ordinal },
                    ),
                )
                FoodSegment.Plan -> PlanScreen(
                    contentPadding = below,
                    onOpenSwap = actions.onOpenSwap,
                    onOpenCook = actions.onOpenCook,
                    onOpenScriptReference = actions.onOpenScriptReference,
                )
                FoodSegment.Recipes -> RecipesScreen(
                    contentPadding = below,
                    onOpenRecipe = actions.onOpenRecipe,
                    onOpenHunter = actions.onOpenHunter,
                    onOpenImport = actions.onOpenImport,
                    onOpenNew = actions.onOpenNewRecipe,
                )
                FoodSegment.Library -> LibraryScreen(contentPadding = below, actions = actions.library)
            }
        }
    }
}

/** The Diary's actions with "open the plan" and "open recipes" turned into a segment switch. */
private fun NutritionActions.withSegments(onPlan: () -> Unit, onRecipes: () -> Unit): NutritionActions =
    NutritionActions(
        onOpenAllergens = onOpenAllergens,
        onLogFood = onLogFood,
        onOpenMeal = onOpenMeal,
        onSwap = onSwap,
        onOpenMealPlans = onPlan,
        onOpenRecipes = onRecipes,
        onOpenMealTemplates = onOpenMealTemplates,
        onOpenShopping = onOpenShopping,
        onOpenEatingOut = onOpenEatingOut,
        onOpenMore = onOpenMore,
    )
