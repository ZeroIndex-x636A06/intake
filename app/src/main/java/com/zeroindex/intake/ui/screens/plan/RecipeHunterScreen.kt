@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.zeroindex.intake.ui.screens.plan

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zeroindex.intake.data.ai.recipes.HuntedRecipe
import com.zeroindex.intake.domain.ai.RecipeVerdict
import com.zeroindex.intake.ui.components.AccentNote
import com.zeroindex.intake.ui.components.ChoiceChip
import com.zeroindex.intake.ui.components.Eyebrow
import com.zeroindex.intake.ui.components.Flag
import com.zeroindex.intake.ui.components.IntakeCard
import com.zeroindex.intake.ui.components.SectionLine
import com.zeroindex.intake.ui.components.SubHead
import com.zeroindex.intake.ui.components.Tag
import com.zeroindex.intake.ui.screens.ai.usd
import com.zeroindex.intake.ui.theme.IntakeShape
import kotlin.math.roundToInt

private val CUISINES = listOf("Mediterranean", "Mexican", "East Asian", "Indian", "Middle Eastern", "American", "Thai")

@Composable
fun RecipeHunterScreen(
    contentPadding: PaddingValues,
    onOpenAiSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecipeHunterViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf<HuntedRecipe?>(null) }
    val pad = PaddingValues(
        top = contentPadding.calculateTopPadding(),
        bottom = contentPadding.calculateBottomPadding() + 24.dp,
        start = 16.dp,
        end = 16.dp,
    )

    when (val stage = state.stage) {
        HuntStage.NeedsKey -> Column(modifier.padding(pad)) {
            SubHead("Recipe Hunter", "Searches the web, so it needs an OpenRouter key.")
            Button(onClick = onOpenAiSettings, shape = IntakeShape.Button, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("Set up AI tools")
            }
        }

        HuntStage.Form -> Column(
            modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(pad),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SubHead("Recipe Hunter", "Macro goals are per serving.")
            val q = state.query
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Num("Protein at least (g)", q.minProteinPerServing, Modifier.weight(1f)) { v ->
                    viewModel.updateQuery { it.copy(minProteinPerServing = v) }
                }
                Num("Calories at most", q.maxKcalPerServing, Modifier.weight(1f)) { v ->
                    viewModel.updateQuery { it.copy(maxKcalPerServing = v) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Num("Servings", q.servings, Modifier.weight(1f)) { v -> viewModel.updateQuery { it.copy(servings = v ?: 4) } }
                Num("Max minutes", q.maxMinutes, Modifier.weight(1f)) { v -> viewModel.updateQuery { it.copy(maxMinutes = v) } }
            }
            Eyebrow("Cuisine")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                CUISINES.forEach { c ->
                    ChoiceChip(
                        label = c,
                        selected = c in q.cuisines,
                        onClick = {
                            viewModel.updateQuery { it.copy(cuisines = if (c in it.cuisines) it.cuisines - c else it.cuisines + c) }
                        },
                    )
                }
            }
            if (q.avoidedAllergens.isNotEmpty()) {
                Eyebrow("Always excluded")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Locked: these come from the allergen profile and every
                    // result is checked against them here, not by the model.
                    q.avoidedAllergens.forEach { Tag("${it.replaceFirstChar(Char::uppercase)}-free · locked") }
                }
            }
            OutlinedTextField(
                value = q.notes,
                onValueChange = { n -> viewModel.updateQuery { it.copy(notes = n) } },
                label = { Text("Anything else (optional)") },
                shape = IntakeShape.Field,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = viewModel::hunt, shape = IntakeShape.Button, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("Find recipes")
            }
            Text(
                "Uses web search through OpenRouter; usually a few cents per search. Only the goals above " +
                    "leave the phone.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        is HuntStage.Running -> Column(modifier.padding(pad), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SubHead("Recipe Hunter", "Working - this can take a minute.")
            stage.steps.forEachIndexed { i, step ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (i == stage.steps.lastIndex) {
                        CircularProgressIndicator(modifier = Modifier.padding(end = 10.dp).height(18.dp))
                    } else {
                        Text("✓  ", color = MaterialTheme.colorScheme.primary)
                    }
                    Text(step.message, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        is HuntStage.Failed -> Column(modifier.padding(pad), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SubHead("Recipe Hunter", null)
            AccentNote(
                lead = "The search did not finish",
                body = stage.message,
                container = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            OutlinedButton(onClick = viewModel::backToForm, shape = IntakeShape.Button, modifier = Modifier.fillMaxWidth()) {
                Text("Back")
            }
        }

        is HuntStage.Done -> LazyColumn(modifier.fillMaxWidth(), contentPadding = pad, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item(key = "head") {
                SubHead(
                    "Recipe Hunter",
                    "${stage.result.usable.size} found · ${stage.result.results.size - stage.result.usable.size} removed " +
                        "· ${stage.result.rounds} round${if (stage.result.rounds == 1) "" else "s"} · ${usd(stage.result.costUsd)}",
                )
            }
            if (stage.result.usable.isEmpty()) {
                item(key = "none") {
                    AccentNote(
                        lead = "Nothing passed every check",
                        body = "Try a lower protein goal, a higher calorie cap, or more time.",
                        container = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            items(stage.result.results, key = { it.recipe.sourceUrl + it.recipe.title }) { hunted ->
                HuntedCard(hunted, saved = hunted.recipe.sourceUrl in stage.saved, onClick = { open = hunted })
            }
            item(key = "again") {
                OutlinedButton(onClick = viewModel::backToForm, shape = IntakeShape.Button, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                    Text("Change the search")
                }
            }
        }
    }

    open?.let { hunted ->
        val saved = (state.stage as? HuntStage.Done)?.saved?.contains(hunted.recipe.sourceUrl) == true
        HuntedDetailSheet(hunted, saved, onSave = { viewModel.save(hunted) }, onDismiss = { open = null })
    }
}

@Composable
private fun HuntedCard(hunted: HuntedRecipe, saved: Boolean, onClick: () -> Unit) {
    val r = hunted.recipe
    val rejected = hunted.verdict as? RecipeVerdict.Rejected
    IntakeCard(modifier = Modifier.alpha(if (rejected != null) 0.55f else 1f).clickable(onClick = onClick)) {
        Text(r.title, style = MaterialTheme.typography.titleSmall)
        Text(
            "${r.kcalPerServing.roundToInt()} kcal · ${r.proteinPerServing.roundToInt()} g protein per serving · ${r.sourceName}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            when (val v = hunted.verdict) {
                RecipeVerdict.Passed -> Tag("Passed allergen check")
                is RecipeVerdict.NeedsReview -> Flag("Check allergens")
                is RecipeVerdict.Rejected -> Flag("Removed: ${v.reason}")
            }
            if (!r.macrosPublished) Tag("Macros estimated")
            if (saved) Tag("Saved")
        }
    }
}

@Composable
private fun HuntedDetailSheet(hunted: HuntedRecipe, saved: Boolean, onSave: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val r = hunted.recipe
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(r.title, style = MaterialTheme.typography.headlineSmall)
            Text(
                "${r.servings} servings · ${r.totalMinutes?.roundToInt()?.let { "$it min" } ?: "time not stated"} · ${r.sourceName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when (val v = hunted.verdict) {
                is RecipeVerdict.Rejected -> AccentNote(
                    lead = "Removed by your checks", body = v.reason,
                    container = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                is RecipeVerdict.NeedsReview -> AccentNote(
                    lead = "Check before cooking", body = v.reason,
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                RecipeVerdict.Passed -> Unit
            }
            SectionLine("Per serving")
            Text(
                "${r.kcalPerServing.roundToInt()} kcal · ${r.proteinPerServing.roundToInt()} g protein · " +
                    "${r.carbsPerServing.roundToInt()} g carbs · ${r.fatPerServing.roundToInt()} g fat" +
                    if (r.macrosPublished) "" else " (estimated from the ingredients)",
                style = MaterialTheme.typography.bodySmall,
            )
            SectionLine("Ingredients")
            r.ingredients.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
            SectionLine("Steps")
            r.steps.forEachIndexed { i, s -> Text("${i + 1}. $s", style = MaterialTheme.typography.bodySmall) }
            Spacer(Modifier.height(6.dp))
            if (hunted.verdict !is RecipeVerdict.Rejected) {
                Button(onClick = onSave, enabled = !saved, shape = IntakeShape.Button, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text(if (saved) "Saved to My recipes" else "Save to My recipes")
                }
            }
            OutlinedButton(
                onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(r.sourceUrl))) } },
                shape = IntakeShape.Button,
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) { Text("Open the original page") }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Num(label: String, value: Int?, modifier: Modifier, onChange: (Int?) -> Unit) {
    OutlinedTextField(
        value = value?.toString().orEmpty(),
        onValueChange = { onChange(it.filter(Char::isDigit).toIntOrNull()) },
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        singleLine = true,
        shape = IntakeShape.Field,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}
