package com.zeroindex.intake.ui.screens.plan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zeroindex.intake.data.db.entity.AllergenStatus
import com.zeroindex.intake.data.db.entity.RecipeEntity
import com.zeroindex.intake.data.db.entity.RecipeOrigin
import com.zeroindex.intake.ui.components.AccentNote
import com.zeroindex.intake.ui.components.Flag
import com.zeroindex.intake.ui.components.IntakeCard
import com.zeroindex.intake.ui.components.SectionLine
import com.zeroindex.intake.ui.components.SubHead
import com.zeroindex.intake.ui.components.Tag
import com.zeroindex.intake.ui.theme.IntakeShape
import kotlin.math.roundToInt

/**
 * The Plan tab: Recipe Hunter, saved recipes, and Plan B for when the plan
 * falls through.
 */
@Composable
fun PlanScreen(
    contentPadding: PaddingValues,
    onOpenHunter: () -> Unit,
    onOpenPlanB: () -> Unit,
    onOpenRecipe: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecipeLibraryViewModel = hiltViewModel(),
) {
    val recipes by viewModel.recipes.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
            start = 16.dp,
            end = 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "head") { SubHead("Plan", "Recipes that fit your targets, and a backup for when plans change.") }

        // The mockup's `.parking` button: the one thing someone stood in a car
        // park needs, at the top.
        item(key = "planb") {
            Surface(
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = IntakeShape.Banner,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .clickable(role = Role.Button, onClick = onOpenPlanB),
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text("Can't eat what I cooked?", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Find a safer option nearby · saved options work offline",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }

        item(key = "hunter") {
            IntakeCard(modifier = Modifier.clickable(role = Role.Button, onClick = onOpenHunter)) {
                Text("Recipe Hunter", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(3.dp))
                Text(
                    "Searches the web for meal-prep recipes that hit your macros, then checks every " +
                        "ingredient against your allergens on this phone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item(key = "mine-head") { SectionLine("My recipes", note = "${recipes.size} saved") }
        if (recipes.isEmpty()) {
            item(key = "empty") {
                AccentNote(
                    lead = "No recipes yet",
                    body = "Recipes you save from Recipe Hunter land here, ready to log a serving.",
                    container = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(recipes, key = { it.id }) { recipe -> RecipeRow(recipe, onClick = { onOpenRecipe(recipe.id) }) }
    }
}

@Composable
internal fun RecipeRow(recipe: RecipeEntity, onClick: () -> Unit) {
    IntakeCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(recipe.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = listOfNotNull(
                        recipe.kcalPerServing?.let { "${it.roundToInt()} kcal" },
                        recipe.proteinPerServing?.let { "${it.roundToInt()} g protein" },
                        "per serving".takeIf { recipe.kcalPerServing != null },
                    ).joinToString(" · ").ifBlank { "${recipe.servings} servings" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (recipe.origin == RecipeOrigin.HUNTER) Tag("From the web")
        }
        if (recipe.allergenStatus == AllergenStatus.MAY_CONTAIN) {
            Spacer(Modifier.height(6.dp))
            Flag("Check allergens")
        }
    }
}
