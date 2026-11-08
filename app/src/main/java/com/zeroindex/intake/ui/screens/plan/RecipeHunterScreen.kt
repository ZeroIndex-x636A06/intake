@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.zeroindex.intake.ui.screens.plan

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.zeroindex.intake.ui.components.EmptyState
import com.zeroindex.intake.ui.components.ErrorState
import com.zeroindex.intake.ui.components.FormRow
import com.zeroindex.intake.ui.components.LabeledField
import com.zeroindex.intake.ui.components.LoadingState
import com.zeroindex.intake.ui.components.LockChip
import com.zeroindex.intake.ui.components.PillTone
import com.zeroindex.intake.ui.components.PrivacyTag
import com.zeroindex.intake.ui.components.ResultCard
import com.zeroindex.intake.ui.components.StatusPill
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.zeroindex.intake.ui.components.IntakeCard
import com.zeroindex.intake.ui.components.SectionLine
import com.zeroindex.intake.ui.components.SubHead
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
            val q = state.query
            SubHead(
                "Recipe Hunter",
                if (q.avoidedAllergens.isEmpty()) "Find recipes that fit your macros." else
                    "Find recipes that fit your macros and your ${q.avoidedAllergens.joinToString(" and ")} allergy.",
            )
            IntakeCard {
                FormRow(
                    left = { m ->
                        Num("Protein per serving · at least (g)", q.minProteinPerServing, m) { v ->
                            viewModel.updateQuery { it.copy(minProteinPerServing = v) }
                        }
                    },
                    right = { m ->
                        Num("Calories · no more than", q.maxKcalPerServing, m) { v ->
                            viewModel.updateQuery { it.copy(maxKcalPerServing = v) }
                        }
                    },
                )
                FormRow(
                    left = { m -> Num("Servings", q.servings, m) { v -> viewModel.updateQuery { it.copy(servings = v ?: 4) } } },
                    right = { m -> Num("Max prep time · minutes", q.maxMinutes, m) { v -> viewModel.updateQuery { it.copy(maxMinutes = v) } } },
                )
                Spacer(Modifier.height(8.dp))
                Eyebrow("Cuisine")
                FlowRow(
                    modifier = Modifier.padding(top = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
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
                    Spacer(Modifier.height(8.dp))
                    Eyebrow("Required filter")
                    FlowRow(Modifier.padding(top = 5.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        // Locked: these come from the allergen profile and every
                        // result is checked against them here, not by the model.
                        q.avoidedAllergens.forEach { LockChip("${it.replaceFirstChar(Char::uppercase)}-free · always on") }
                    }
                }
                LabeledField(
                    label = "Anything else · optional",
                    value = q.notes,
                    onValueChange = { n -> viewModel.updateQuery { it.copy(notes = n) } },
                    placeholder = "One-pan, no fish, freezer-friendly…",
                    modifier = Modifier.padding(top = 4.dp),
                )
                Button(
                    onClick = viewModel::hunt,
                    shape = IntakeShape.Button,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(52.dp),
                ) { Text("Search recipes") }
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    PrivacyTag("Sends goals to OpenRouter")
                    Text(
                        "  Usually a few cents a search. Ingredients are checked on this phone.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        is HuntStage.Running -> Column(modifier.padding(pad), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SubHead("Recipe Hunter", "Working - this can take a minute.")
            LoadingState(
                steps = stage.steps.mapIndexed { i, step ->
                    if (i == stage.steps.lastIndex) "◌ ${step.message}" else "✓ ${step.message}"
                } + "Checking ingredients against your allergens happens on this phone",
            )
        }

        is HuntStage.Failed -> Column(modifier.padding(pad), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SubHead("Recipe Hunter", null)
            ErrorState(
                title = "The search did not finish",
                body = stage.message,
            )
            OutlinedButton(onClick = viewModel::backToForm, shape = IntakeShape.Button, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                Text("Back to the search")
            }
        }

        is HuntStage.Done -> LazyColumn(modifier.fillMaxWidth(), contentPadding = pad, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val result = stage.result
            item(key = "head") { SubHead("Recipe Hunter", "${result.rounds} round${if (result.rounds == 1) "" else "s"} · ${usd(result.costUsd)} · ${result.model}") }
            item(key = "results-head") {
                SectionLine(
                    "Results · ${result.usable.size} kept",
                    note = "of ${result.results.size} reviewed",
                )
            }
            if (result.usable.isEmpty()) {
                item(key = "none") {
                    EmptyState(
                        title = "Nothing passed every check",
                        body = "Try a lower protein goal, a higher calorie cap, or more time.",
                    )
                }
            }
            items(result.results.sortedBy { it.verdict is RecipeVerdict.Rejected }, key = { it.recipe.sourceUrl + it.recipe.title }) { hunted ->
                HuntedCard(hunted, saved = hunted.recipe.sourceUrl in stage.saved, onClick = { open = hunted })
            }
            item(key = "again") {
                OutlinedButton(
                    onClick = viewModel::backToForm,
                    shape = IntakeShape.Button,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(50.dp),
                ) { Text("Change the search") }
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
    ResultCard(dimmed = rejected != null, modifier = Modifier.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Text(r.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).padding(end = 6.dp))
            when (val v = hunted.verdict) {
                RecipeVerdict.Passed -> StatusPill("Passed allergen check")
                is RecipeVerdict.NeedsReview -> StatusPill("Check allergens", tone = PillTone.Wait)
                is RecipeVerdict.Rejected -> StatusPill("Removed: ${v.reason.replaceFirstChar(Char::lowercase).let { if (it.length > 28) it.take(27) + "…" else it }}", tone = PillTone.Bad)
            }
        }
        Text(
            listOfNotNull(
                "${r.proteinPerServing.roundToInt()} g protein",
                "${r.kcalPerServing.roundToInt()} kcal",
                r.totalMinutes?.roundToInt()?.let { "$it min" },
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 5.dp),
        )
        Text(
            when {
                rejected != null -> "${rejected.reason}. This result is excluded from saving."
                else -> listOfNotNull(
                    "Source: ${r.sourceName}",
                    "Ingredients checked locally",
                    if (!r.macrosPublished) "macros estimated" else null,
                    if (saved) "saved" else null,
                ).joinToString(" · ")
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 5.dp),
        )
        if (rejected == null) {
            TextButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = "View recipe: ${r.title}" }) {
                Text("View recipe")
            }
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
    LabeledField(
        label = label,
        value = value?.toString().orEmpty(),
        onValueChange = { onChange(it.filter(Char::isDigit).take(5).toIntOrNull()) },
        keyboardType = KeyboardType.Number,
        modifier = modifier,
    )
}
