package com.zeroindex.intake.ui.screens.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zeroindex.intake.data.db.entity.RecipeEntity
import com.zeroindex.intake.domain.recipe.EstimateTotals
import com.zeroindex.intake.domain.recipe.EstimatedLine
import com.zeroindex.intake.domain.recipe.RecipeEstimate
import com.zeroindex.intake.domain.recipe.ReferenceFood
import com.zeroindex.intake.domain.recipe.ResolvedRecipeNutrition
import com.zeroindex.intake.ui.components.IntakeCard
import kotlin.math.roundToInt

/** The recipe's per-serving numbers as the page (or the saved recipe) gave them, with an estimate filling any gap. */
internal fun resolveNutrition(recipe: RecipeEntity, estimate: EstimateTotals?): ResolvedRecipeNutrition =
    ResolvedRecipeNutrition.resolve(
        kcal = recipe.kcalPerServing, protein = recipe.proteinPerServing, carbs = recipe.carbsPerServing, fat = recipe.fatPerServing,
        fiber = recipe.fiberPerServing, sugar = recipe.sugarPerServing, sodiumMg = recipe.sodiumMgPerServing,
        saturatedFat = recipe.saturatedFatPerServing,
        estimate = estimate,
    )

/**
 * "Estimated from ingredients": what was worked out, from which lines, and how. Tapping the header shows each line's
 * matched food, the grams used and its calories, with a way to pick a different food for a line, and lists
 * the lines that were not counted.
 */
@Composable
internal fun RecipeEstimateCard(
    estimate: RecipeEstimate,
    resolved: ResolvedRecipeNutrition,
    onPick: (lineIndex: Int, food: ReferenceFood) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    IntakeCard(modifier) {
        Text("Estimated from ingredients", style = MaterialTheme.typography.titleSmall)
        val parts = buildList {
            if (resolved.kcal?.estimated == false) add("The calories are the page's own.")
            val filled = listOfNotNull(
                "protein".takeIf { resolved.protein?.estimated == true },
                "carbs".takeIf { resolved.carbs?.estimated == true },
                "fat".takeIf { resolved.fat?.estimated == true },
                "calories".takeIf { resolved.kcal?.estimated == true },
            )
            if (filled.isNotEmpty()) add("The ${filled.joinToString(", ")} ${if (filled.size == 1) "is" else "are"} an estimate, marked with ~.")
            add("Worked out here from ${estimate.countedLines} of ${estimate.lines.size} ingredient lines against the offline USDA food list, divided by ${estimate.servings} servings. Not the site's numbers.")
        }
        Text(parts.joinToString(" "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { open = !open }) { Text(if (open) "Hide the ingredient matches" else "Show how each line was matched") }
        if (open) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                estimate.lines.forEachIndexed { i, line -> if (line.counted || line.food != null) MatchedLine(i, line, onPick) }
                val skipped = estimate.lines.withIndex().filter { !it.value.counted }
                if (skipped.isNotEmpty()) {
                    Text("Not counted", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp))
                    skipped.forEach { (i, line) ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text(
                                "${line.line} (${line.notCounted ?: "not counted"})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            if (line.notCounted != "optional" && line.alternatives.isNotEmpty()) PickFood(i, line, onPick, label = "Pick food")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MatchedLine(index: Int, line: EstimatedLine, onPick: (Int, ReferenceFood) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(line.line, style = MaterialTheme.typography.bodySmall)
            val food = line.food
            val detail = if (food == null) {
                ""
            } else if (line.counted) {
                "${food.name} · ${line.grams!!.roundToInt()} g · ${line.kcal!!.roundToInt()} kcal" + if (line.chosen) " · your pick" else ""
            } else {
                "${food.name} · ${line.notCounted}"
            }
            Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (line.alternatives.isNotEmpty() || line.chosen) PickFood(index, line, onPick, label = "Change")
    }
}

@Composable
private fun PickFood(index: Int, line: EstimatedLine, onPick: (Int, ReferenceFood) -> Unit, label: String) {
    var menu by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { menu = true }) { Text(label) }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            line.alternatives.forEach { f ->
                DropdownMenuItem(text = { Text(f.name, style = MaterialTheme.typography.bodySmall) }, onClick = { menu = false; onPick(index, f) })
            }
        }
    }
}
