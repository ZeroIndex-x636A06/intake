package com.zeroindex.intake.ui.screens.nutrition

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zeroindex.intake.ui.theme.IntakeShape

/**
 * The pushed "More" list behind the Nutrition tab's last row: Plan, Recipes, Meals (saved templates), Eating out and
 * Drinks, each with the one-line summary the old section blocks had, each opening the screen it opened before.
 */
@Composable
fun NutritionMoreScreen(
    contentPadding: PaddingValues,
    actions: NutritionActions,
    modifier: Modifier = Modifier,
    viewModel: NutritionHomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val s = state.sections
    val day = state.hydration
    val unit = state.settings.volumeUnit
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
            start = 16.dp,
            end = 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            NavCard(
                title = s.planName ?: "Meal plan",
                body = if (s.planName == null) {
                    "No plan yet. Import one, write one or start from a template. Plans of any length, shopping lists, import and export."
                } else {
                    listOfNotNull(s.weekLine, s.weekDish?.let { "this week: $it" }, "shopping list, import and export").joinToString(" · ")
                },
                onClick = actions.onOpenMealPlans,
            )
        }
        item {
            NavCard(
                title = "Recipes",
                body = if (s.recipeCount == 0) {
                    "No recipes yet. Add one, import one from a link or let Recipe Hunter find one."
                } else {
                    "${s.recipeCount} saved · ${s.recipeNames.joinToString(", ")}" + if (s.recipeCount > 3) " and more" else ""
                },
                onClick = actions.onOpenRecipes,
            )
        }
        item {
            NavCard(
                title = "Meals",
                body = if (s.templateCount == 0) {
                    "No saved meals yet. Save a meal you eat often and log it in one tap, or from an NFC tag."
                } else {
                    "${s.templateCount} saved · ${s.templateNames.joinToString(", ")}" + if (s.templateCount > 3) " and more" else ""
                },
                onClick = actions.onOpenMealTemplates,
            )
        }
        item {
            NavCard(
                title = "Eating out",
                body = (if (s.eatOutCount == 0) "No saved options yet. " else "${s.eatOutCount} saved option${if (s.eatOutCount == 1) "" else "s"}. ") +
                    "Saved options and restaurant menus. Nearby search is in Swap.",
                onClick = actions.onOpenEatingOut,
            )
        }
        item {
            NavCard(
                title = "Drinks",
                body = if (day == null) {
                    "Log a drink, with sizes and categories"
                } else {
                    "${unit.formatNumber(day.consumedMl)} of ${unit.format(day.target.totalMl)} today" +
                        (state.drinkNames.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "")
                },
                onClick = actions.onOpenDrinks,
            )
        }
    }
}

@Composable
private fun NavCard(title: String, body: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface,
        shape = IntakeShape.Card,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        }
    }
}
