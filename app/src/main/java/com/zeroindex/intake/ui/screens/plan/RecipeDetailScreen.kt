package com.zeroindex.intake.ui.screens.plan

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zeroindex.intake.data.db.entity.AllergenStatus
import com.zeroindex.intake.data.repository.IngredientView
import com.zeroindex.intake.domain.recipe.IngredientSections
import com.zeroindex.intake.domain.recipe.LineAllergenTag
import com.zeroindex.intake.domain.recipe.RecipeNutritionLabels
import com.zeroindex.intake.domain.recipe.ResolvedRecipeNutrition
import com.zeroindex.intake.domain.recipe.RecipeTimes
import com.zeroindex.intake.ui.allergen.CheckLabelChip
import com.zeroindex.intake.ui.components.IntakeCard
import com.zeroindex.intake.ui.components.MacroTiles
import com.zeroindex.intake.ui.components.PillTone
import com.zeroindex.intake.ui.components.SandNote
import com.zeroindex.intake.ui.components.SectionLine
import com.zeroindex.intake.ui.components.StatusPill
import com.zeroindex.intake.ui.components.SubHead
import com.zeroindex.intake.ui.images.RecipePhoto
import com.zeroindex.intake.ui.screens.today.SlotPickerSheet
import com.zeroindex.intake.ui.theme.CarbAccent
import com.zeroindex.intake.ui.theme.FatAccent
import com.zeroindex.intake.ui.theme.IntakeShape
import com.zeroindex.intake.ui.theme.ProteinAccent
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * One recipe. "Log a serving" is the only primary button; the original page, adding to the week, editing and
 * deleting live in the menu at the top right.
 */
@Composable
fun RecipeDetailScreen(
    recipeId: String,
    contentPadding: PaddingValues,
    onDeleted: () -> Unit,
    onEdit: (String) -> Unit,
    /** Opens cook mode for this recipe (it works with no meal plan). */
    onCook: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecipeLibraryViewModel = hiltViewModel(),
) {
    val recipes by viewModel.recipes.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val recipe = recipes.firstOrNull { it.id == recipeId } ?: return
    val view by remember(recipeId) { viewModel.viewOf(recipeId) }.collectAsStateWithLifecycle(null)
    val estimate by remember(recipeId) { viewModel.estimateOf(recipeId) }.collectAsStateWithLifecycle(null)
    val nutrition = resolveNutrition(recipe, estimate?.perServing)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var picking by remember { mutableStateOf(false) }
    var logged by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var highlight by remember { mutableStateOf(false) }
    val firstFlagged = remember { BringIntoViewRequester() }

    val ingredients = view?.ingredients.orEmpty()
    val flagged = ingredients.count { it.tag != null }

    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(contentPadding).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val source = recipe.sourceUrl?.let { runCatching { Uri.parse(it).host?.removePrefix("www.") }.getOrNull() }
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                SubHead(
                    recipe.name,
                    listOfNotNull(
                        source,
                        "${recipe.servings} servings",
                        RecipeTimes.label(recipe.prepMinutes, recipe.cookMinutes, recipe.totalTimeMinutes),
                    ).joinToString(" · "),
                )
            }
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Recipe options")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    recipe.sourceUrl?.let { url ->
                        DropdownMenuItem(
                            text = { Text("Open the original page") },
                            onClick = {
                                menu = false
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                            },
                        )
                    }
                    DropdownMenuItem(text = { Text("Add to this week") }, onClick = { menu = false; viewModel.addToThisWeek(recipe) })
                    DropdownMenuItem(text = { Text("Edit recipe") }, onClick = { menu = false; onEdit(recipe.id) })
                    DropdownMenuItem(
                        text = { Text("Delete recipe", color = MaterialTheme.colorScheme.error) },
                        onClick = { menu = false; confirmDelete = true },
                    )
                }
            }
        }
        RecipePhoto(recipe.imageUrl)
        if (nutrition.hasAnything) {
            MacroTiles(
                listOf(
                    Triple(ResolvedRecipeNutrition.text(nutrition.protein, "g"), "Protein", ProteinAccent),
                    Triple(ResolvedRecipeNutrition.text(nutrition.carbs, "g"), "Carbs", CarbAccent),
                    Triple(nutrition.kcal?.let { (if (it.estimated) "~" else "") + it.value.roundToInt() } ?: ResolvedRecipeNutrition.NOT_LISTED, "kcal", FatAccent),
                ),
            )
            Text(
                "Per serving · fat ${ResolvedRecipeNutrition.text(nutrition.fat, "g")}" + if (nutrition.anyEstimated) " · ~ is estimated from ingredients" else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                "Nutrition: not listed. The page gave none and the ingredients could not be matched.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        estimate?.takeIf { nutrition.anyEstimated }?.let { est ->
            RecipeEstimateCard(est, nutrition, onPick = { i, f -> viewModel.pickFood(recipeId, i, f) })
        }
        val extras = RecipeNutritionLabels.extras(
            recipe.fiberPerServing, recipe.sugarPerServing, recipe.saturatedFatPerServing, recipe.sodiumMgPerServing,
        )
        if (extras.isNotEmpty()) {
            IntakeCard {
                Text("Also per serving", style = MaterialTheme.typography.titleSmall)
                extras.forEach { (label, value) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(
                    "As the recipe page published them.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (ingredients.isNotEmpty() || recipe.ingredientsText.isNotBlank()) {
            SectionLine("Ingredients") {
                // Tapping the summary jumps to the flagged lines and tints them.
                Box(
                    Modifier
                        .clickable(enabled = flagged > 0) {
                            highlight = !highlight
                            if (highlight) scope.launch { firstFlagged.bringIntoView() }
                        }
                        .semantics { if (flagged > 0) contentDescription = "Show the $flagged flagged ingredient lines" },
                ) {
                    when (recipe.allergenStatus) {
                        AllergenStatus.MAY_CONTAIN -> StatusPill("Check allergens", tone = PillTone.Wait)
                        AllergenStatus.CHECK_LABEL -> CheckLabelChip(null)
                        AllergenStatus.CONTAINS -> StatusPill("Contains an allergen", tone = PillTone.Bad)
                        AllergenStatus.CLEAR -> StatusPill("Passed allergen check")
                        AllergenStatus.MENTIONED_AVOIDED -> StatusPill(MentionedAvoided.PILL)
                        else -> StatusPill("Allergens not checked", tone = PillTone.Wait)
                    }
                }
            }
            if (flagged > 0) {
                Text(
                    if (highlight) "$flagged flagged ${if (flagged == 1) "line" else "lines"} shown below. Tap the chip to hide."
                    else "$flagged ${if (flagged == 1) "line is" else "lines are"} tagged below. Tap the chip to find ${if (flagged == 1) "it" else "them"}.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (MentionedAvoided.applies(recipe.allergenStatus)) {
                SandNote(MentionedAvoided.note(emptyList()))
            }
            if (recipe.allergenStatus == AllergenStatus.MAY_CONTAIN) {
                SandNote("When this was saved, an ingredient looked like it might contain something you avoid. Check the labels before cooking.")
            }
            IntakeCard {
                if (ingredients.isEmpty()) {
                    // Until the tagged lines load, show the stored text.
                    recipe.ingredientsText.lines().filter(String::isNotBlank).forEach {
                        Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp))
                    }
                } else {
                    var seenFirst = false
                    IngredientSections.of(ingredients) { it.group }.forEach { section ->
                        section.label?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                            )
                        }
                        section.items.forEach { line ->
                            val mod = if (line.tag != null && !seenFirst) {
                                seenFirst = true
                                Modifier.bringIntoViewRequester(firstFlagged)
                            } else {
                                Modifier
                            }
                            IngredientLine(line, highlight && line.tag != null, mod)
                        }
                    }
                }
            }
        }
        recipe.notes?.takeIf { it.isNotBlank() }?.let { notes ->
            SectionLine("Notes")
            IntakeCard {
                notes.lines().filter(String::isNotBlank).forEach {
                    Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 3.dp))
                }
            }
        }
        if (recipe.instructions.isNotBlank()) {
            SectionLine("Steps")
            IntakeCard {
                recipe.instructions.lines().filter(String::isNotBlank).forEachIndexed { i, step ->
                    Text(
                        if (step.trimStart().firstOrNull()?.isDigit() == true) step else "${i + 1}. $step",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 3.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (recipe.instructions.isNotBlank()) {
                OutlinedButton(
                    onClick = { onCook(recipe.id) },
                    shape = IntakeShape.Button,
                    modifier = Modifier.weight(1f).height(52.dp),
                ) { Text("Cook") }
            }
            Button(
                onClick = { picking = true },
                enabled = nutrition.kcal != null,
                shape = IntakeShape.Button,
                modifier = Modifier.weight(if (recipe.instructions.isNotBlank()) 2f else 1f).height(52.dp),
            ) { Text(if (logged) "Logged · log another" else "Log a serving") }
        }
        notice?.let { SandNote(it) }
        Spacer(Modifier.height(24.dp))
    }

    if (picking) {
        SlotPickerSheet(
            title = "Log a serving to",
            onDismiss = { picking = false },
            onPick = { slot -> viewModel.logServing(recipe, slot, estimate) { logged = true; picking = false } },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this recipe?") },
            text = {
                Text(
                    "\"${recipe.name}\" leaves your recipe list and any plan it is in. Servings you already logged stay logged.",
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.delete(recipe); onDeleted() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep it") } },
        )
    }
}

/** "heavy whipping cream · contains dairy": the line, then a small tag when the allergen engine flagged it. */
@Composable
private fun IngredientLine(line: IngredientView, highlighted: Boolean, modifier: Modifier = Modifier) {
    val tag = line.tag
    val tagColor = when (tag?.severity) {
        LineAllergenTag.Severity.CONTAINS -> MaterialTheme.colorScheme.error
        LineAllergenTag.Severity.MAY_CONTAIN -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val text = buildAnnotatedString {
        append(line.text)
        if (tag != null) {
            append("  ·  ")
            withStyle(SpanStyle(color = tagColor)) { append(tag.text) }
        }
    }
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (highlighted) {
                    Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 4.dp)
                } else {
                    Modifier.padding(vertical = 2.dp)
                },
            ),
    )
}
