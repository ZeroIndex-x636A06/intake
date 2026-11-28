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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.zeroindex.intake.domain.model.MealSlot
import com.zeroindex.intake.domain.recipe.IngredientSections
import com.zeroindex.intake.domain.recipe.LineAllergenTag
import com.zeroindex.intake.domain.recipe.RecipeNutritionLabels
import com.zeroindex.intake.domain.recipe.ResolvedRecipeNutrition
import com.zeroindex.intake.domain.recipe.RecipeTimes
import com.zeroindex.intake.domain.recipe.ServingsStep
import com.zeroindex.intake.ui.allergen.CheckLabelChip
import com.zeroindex.intake.ui.components.IntakeCard
import com.zeroindex.intake.ui.components.MacroTiles
import com.zeroindex.intake.ui.components.SandNote
import com.zeroindex.intake.ui.components.SectionLine
import com.zeroindex.intake.ui.images.RecipePhoto
import com.zeroindex.intake.ui.screens.today.SlotPickerSheet
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import com.zeroindex.intake.ui.components.ChipTone
import com.zeroindex.intake.ui.components.GhostButton
import com.zeroindex.intake.ui.components.IconCircleButton
import com.zeroindex.intake.ui.components.IntakeChip
import com.zeroindex.intake.ui.components.SoftButton
import com.zeroindex.intake.ui.theme.IntakeIcons
import com.zeroindex.intake.ui.theme.IntakeShape
import com.zeroindex.intake.ui.theme.IntakeSpacing
import com.zeroindex.intake.ui.theme.NumberSmall
import com.zeroindex.intake.ui.theme.intake
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
    val pendingServing by viewModel.pendingLog.collectAsStateWithLifecycle()
    val recipe = recipes.firstOrNull { it.id == recipeId } ?: return
    val view by remember(recipeId) { viewModel.viewOf(recipeId) }.collectAsStateWithLifecycle(null)
    val estimate by remember(recipeId) { viewModel.estimateOf(recipeId) }.collectAsStateWithLifecycle(null)
    val avoids by viewModel.avoidsAnything.collectAsStateWithLifecycle()
    val liveVerdict by remember(recipeId) { viewModel.verdictOf(recipeId) }.collectAsStateWithLifecycle(null)
    val nutrition = resolveNutrition(recipe, estimate?.perServing)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var picking by remember { mutableStateOf(false) }
    // Servings to log, in half steps; the recipe's own amount is one serving.
    var servings by rememberSaveable(recipeId) { mutableStateOf(1.0) }
    var logged by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var highlight by remember { mutableStateOf(false) }
    val firstFlagged = remember { BringIntoViewRequester() }

    val ingredients = view?.ingredients.orEmpty()
    val flagged = ingredients.count { it.tag != null }

    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(contentPadding).padding(horizontal = IntakeSpacing.ScreenGutter),
        verticalArrangement = Arrangement.spacedBy(IntakeSpacing.StackGap),
    ) {
        val source = recipe.sourceUrl?.let { runCatching { Uri.parse(it).host?.removePrefix("www.") }.getOrNull() }
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f).padding(top = 8.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(recipe.name, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    listOfNotNull(
                        source,
                        "${recipe.servings} servings",
                        RecipeTimes.label(recipe.prepMinutes, recipe.cookMinutes, recipe.totalTimeMinutes),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconCircleButton(IntakeIcons.More, "Recipe options", onClick = { menu = true })
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
                        text = { Text("Delete recipe", color = MaterialTheme.intake.bad) },
                        onClick = { menu = false; confirmDelete = true },
                    )
                }
            }
        }
        RecipePhoto(recipe.imageUrl)
        IntakeCard {
            Column(verticalArrangement = Arrangement.spacedBy(IntakeSpacing.StackGap)) {
        if (nutrition.hasAnything) {
            MacroTiles(
                listOf(
                    Triple(nutrition.kcal?.let { (if (it.estimated) "~" else "") + it.value.roundToInt() } ?: ResolvedRecipeNutrition.NOT_LISTED, "kcal", MaterialTheme.colorScheme.onSurface),
                    Triple(ResolvedRecipeNutrition.text(nutrition.protein, "g"), "Protein", MaterialTheme.intake.protein),
                    Triple(ResolvedRecipeNutrition.text(nutrition.carbs, "g"), "Carbs", MaterialTheme.intake.carb),
                    Triple(ResolvedRecipeNutrition.text(nutrition.fat, "g"), "Fat", MaterialTheme.intake.fat),
                ),
            )
            Text(
                "Per serving" + if (nutrition.anyEstimated) " · ~ is estimated from ingredients" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                "Nutrition: not listed. The page gave none and the ingredients could not be matched.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // The stepper and "Log a serving" sit with the numbers they log, as in the mockup; "Cook" is the quieter partner below.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(IntakeSpacing.RowGap), verticalAlignment = Alignment.CenterVertically) {
            ServingsStepper(
                servings = servings,
                onChange = { up -> servings = ServingsStep.next(servings, up) },
            )
            Button(
                onClick = { picking = true },
                enabled = nutrition.kcal != null,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    when {
                        logged && servings == 1.0 -> "Logged · log another"
                        logged -> "Logged · log ${ServingsStep.label(servings)}"
                        servings == 1.0 -> "Log a serving"
                        else -> "Log ${ServingsStep.label(servings)}"
                    },
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        if (recipe.instructions.isNotBlank()) {
            SoftButton(onClick = { onCook(recipe.id) }, small = true, modifier = Modifier.fillMaxWidth()) { Text("Cook") }
        }
        if (nutrition.kcal == null) {
            // The button is off for a reason the person can fix: say what is missing and where to add it.
            Text(
                "Log a serving needs a calorie figure, and this recipe has none. Add the ingredients' nutrition in the recipe to turn it on.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            GhostButton(onClick = { onEdit(recipe.id) }) { Text("Edit recipe to add nutrition") }
        }
            }
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
                        .semantics { if (flagged > 0) contentDescription = flaggedLinesDescription(flagged) },
                ) {
                    // The same single verdict as the Recipes list and Log food > Mine (RecipeLogger.rowAllergen).
                    // The stored status is only a fallback while that verdict loads or when nothing is avoided, and a
                    // stored "clear" is never shown as a pass: only a live read of every ingredient line can be green.
                    val live = liveVerdict ?: com.zeroindex.intake.ui.addfood.RowAllergen.Checking.takeIf { avoids }
                    if (live != null) {
                        com.zeroindex.intake.ui.addfood.RowAllergenChip(live)
                    } else {
                        when (recipe.allergenStatus) {
                            AllergenStatus.MAY_CONTAIN -> IntakeChip("Check allergens", tone = ChipTone.Warn)
                            AllergenStatus.CHECK_LABEL -> CheckLabelChip(null)
                            AllergenStatus.CONTAINS -> IntakeChip("Contains an allergen", tone = ChipTone.Bad)
                            AllergenStatus.MENTIONED_AVOIDED -> IntakeChip(MentionedAvoided.PILL)
                            else -> IntakeChip(com.zeroindex.intake.domain.allergen.AllergenEvidence.ClearBasis.NAME_ONLY.chipText)
                        }
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
        if (recipe.instructions.isNotBlank()) {
            SectionLine("Steps")
            IntakeCard {
                recipe.instructions.lines().filter(String::isNotBlank).forEachIndexed { i, step ->
                    // A step that already starts with its own number keeps it as text; the others get the numbered ink dot.
                    val numbered = step.trimStart().firstOrNull()?.isDigit() == true
                    Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (!numbered) {
                            Surface(color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary, shape = CircleShape, modifier = Modifier.size(26.dp)) {
                                Box(contentAlignment = Alignment.Center) { Text("${i + 1}", style = MaterialTheme.typography.labelSmall) }
                            }
                        }
                        Text(step, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        estimate?.takeIf { nutrition.anyEstimated }?.let { est ->
            RecipeEstimateCard(est, nutrition, onPick = { i, f -> viewModel.pickFood(recipeId, i, f) })
        }
        recipe.notes?.takeIf { it.isNotBlank() }?.let { notes ->
            SectionLine("Notes")
            IntakeCard {
                notes.lines().filter(String::isNotBlank).forEach {
                    Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 3.dp))
                }
            }
        }
        notice?.let { SandNote(it) }
        Spacer(Modifier.height(24.dp))
    }

    if (picking) {
        SlotPickerSheet(
            title = "Log ${ServingsStep.label(servings)} to",
            onDismiss = { picking = false },
            onPick = { slot -> picking = false; viewModel.logServing(recipe, slot, estimate, servings) { logged = true } },
            slots = MealSlot.MEALS,
        )
    }

    pendingServing?.let { p ->
        com.zeroindex.intake.ui.allergen.AllergenWarningSheet(
            onFindSwap = com.zeroindex.intake.ui.allergen.rememberFindSwap(p.slot.name),
            foodName = p.pending.name,
            scan = p.pending.scan,
            ingredientText = p.pending.ingredientText,
            onDismiss = viewModel::dismissPendingServing,
            onLogAnyway = { viewModel.confirmPendingServing { logged = true } },
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
                    Text("Delete", color = MaterialTheme.intake.bad)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep it") } },
        )
    }
}

/**
 * The servings to log, with minus and plus in half steps (`.stepper`: a `soft` pill with two round `card` buttons and the value
 * between). Same look as the checklist's stepper in cook mode.
 */
@Composable
private fun ServingsStepper(servings: Double, onChange: (up: Boolean) -> Unit) {
    Surface(color = MaterialTheme.intake.soft, shape = IntakeShape.Pill) {
        Row(Modifier.padding(3.dp), verticalAlignment = Alignment.CenterVertically) {
            IconCircleButton(
                IntakeIcons.Minus, "Fewer servings to log",
                onClick = { onChange(false) }, enabled = servings > ServingsStep.MIN,
            )
            Text(
                ServingsStep.label(servings),
                style = NumberSmall,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.widthIn(min = 44.dp).padding(horizontal = 4.dp),
            )
            IconCircleButton(IntakeIcons.Plus, "More servings to log", onClick = { onChange(true) })
        }
    }
}

/** The chip's spoken label for the flagged ingredient lines, singular for one. */
internal fun flaggedLinesDescription(count: Int): String =
    "Show the $count flagged ${if (count == 1) "ingredient line" else "ingredient lines"}"

/** "heavy whipping cream · contains dairy": the line, then a small tag when the allergen engine flagged it. */
@Composable
private fun IngredientLine(line: IngredientView, highlighted: Boolean, modifier: Modifier = Modifier) {
    val tag = line.tag
    val tagColor = when (tag?.severity) {
        LineAllergenTag.Severity.CONTAINS -> MaterialTheme.intake.bad
        LineAllergenTag.Severity.MAY_CONTAIN -> MaterialTheme.intake.warn
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
                    Modifier.background(MaterialTheme.intake.warnBg, IntakeShape.IconSmall).padding(horizontal = 6.dp, vertical = 4.dp)
                } else {
                    Modifier.padding(vertical = 2.dp)
                },
            ),
    )
}
