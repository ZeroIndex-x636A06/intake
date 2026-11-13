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
import androidx.compose.foundation.layout.size
import com.zeroindex.intake.domain.ai.SavedHunt
import com.zeroindex.intake.domain.ai.SavedHuntQuery
import com.zeroindex.intake.ui.components.SwitchRow
import java.text.DateFormat
import java.util.Date
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.zeroindex.intake.domain.ai.HuntKeys
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
import com.zeroindex.intake.data.ai.recipes.RecipeHunter
import com.zeroindex.intake.ui.allergen.CheckLabelChip
import com.zeroindex.intake.domain.ai.HuntPolicy
import com.zeroindex.intake.domain.ai.RecipeVerdict
import com.zeroindex.intake.ui.components.AccentNote
import com.zeroindex.intake.ui.components.ChoiceChip
import com.zeroindex.intake.ui.components.Eyebrow
import com.zeroindex.intake.ui.components.IntakeCard
import com.zeroindex.intake.ui.components.SectionLine
import com.zeroindex.intake.ui.screens.ai.usd
import com.zeroindex.intake.ui.theme.IntakeShape
import kotlin.math.roundToInt

/** "Any" first: it means no preference and clears the others; picking a specific cuisine clears it. */
private const val ANY_CUISINE = "Any"
private val CUISINES = listOf(
    ANY_CUISINE, "American", "BBQ", "Cajun/Southern", "Caribbean", "Chinese", "East Asian", "Filipino", "French", "Greek",
    "Indian", "Italian", "Japanese", "Korean", "Latin American", "Mediterranean", "Mexican", "Middle Eastern", "Spanish",
    "Thai", "Vietnamese", "African",
)

private fun toggleCuisine(current: List<String>, c: String): List<String> = when {
    c == ANY_CUISINE -> if (ANY_CUISINE in current) emptyList() else listOf(ANY_CUISINE)
    c in current -> current - c
    else -> (current - ANY_CUISINE) + c
}

@Composable
fun RecipeHunterScreen(
    contentPadding: PaddingValues,
    onOpenAiSettings: () -> Unit,
    onOpenImport: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecipeHunterViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf<HuntedRecipe?>(null) }
    var showRemoved by remember { mutableStateOf(false) }
    val pad = PaddingValues(
        top = contentPadding.calculateTopPadding(),
        bottom = contentPadding.calculateBottomPadding() + 24.dp,
        start = 16.dp,
        end = 16.dp,
    )

    when (val stage = state.stage) {
        HuntStage.NeedsKey -> Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(pad)) {
            HuntSubtitle("Searches the web with AI, so it needs “Use AI features” on and an OpenRouter key (Settings → AI).")
            OutlinedButton(onClick = onOpenImport, shape = IntakeShape.Button, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("Have a link? Import it with no AI")
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = onOpenAiSettings, shape = IntakeShape.Button, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("Set up AI tools")
            }
            RecentSearches(state.recent, canRunAgain = false, onOpen = viewModel::openRecent, onRunAgain = {}, onDelete = viewModel::deleteRecent)
        }

        HuntStage.Form -> Column(
            modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(pad),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val q = state.query
            HuntSubtitle(
                if (q.avoidedAllergens.isEmpty()) "Find recipes that fit your macros." else
                    "Find recipes that fit your macros and your ${q.avoidedAllergens.joinToString(" and ")} allergy.",
            )
            OutlinedButton(onClick = onOpenImport, shape = IntakeShape.Button, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("Have a link? Import it with no AI")
            }
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
                                viewModel.updateQuery { it.copy(cuisines = toggleCuisine(it.cuisines, c)) }
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
                SwitchRow(
                    title = "Use AI to read pages the phone can't",
                    subtitle = "Off by default. Sends the text of a page this phone cannot read to OpenRouter to copy out its recipe, " +
                        "about ${usd(RecipeHunter.ASSUMED_AI_READ_USD)} a page and at most ${RecipeHunter.MAX_AI_READS_PER_RUN} pages a search. " +
                        "It counts in the cost and cap below. Those recipes are labelled “Read by AI” and still need a look at the original page.",
                    on = q.aiReadPages,
                    onToggle = { viewModel.updateQuery { it.copy(aiReadPages = !it.aiReadPages) } },
                    onLabel = "On",
                    offLabel = "Off",
                    modifier = Modifier.padding(top = 4.dp),
                )
                Button(
                    onClick = viewModel::hunt,
                    shape = IntakeShape.Button,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(52.dp),
                ) { Text("Search recipes") }
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    PrivacyTag(if (q.aiReadPages) "Sends goals and some page text to OpenRouter" else "Sends goals to OpenRouter")
                }
                Text(
                    "About ${usd(HuntPolicy().capUsd)} at most per search" +
                        (if (q.aiReadPages) ", AI page reading included" else "") +
                        ", and you see the running cost. Ingredients are checked on this phone.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            RecentSearches(state.recent, canRunAgain = true, onOpen = viewModel::openRecent, onRunAgain = viewModel::runAgain, onDelete = viewModel::deleteRecent)
        }

        is HuntStage.Running -> Column(modifier.padding(pad), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            HuntSubtitle("Working - this can take a minute or two.")
            val policy = HuntPolicy()
            val last = stage.steps.lastOrNull()
            LoadingState(
                steps = stage.steps.mapIndexed { i, step ->
                    if (i == stage.steps.lastIndex) "◌ ${step.message}" else "✓ ${step.message}"
                } + (last?.let {
                    "Round ${it.round} of up to ${policy.maxRounds} · ${it.kept} kept · ${usd(it.costUsd)} of ${usd(policy.capUsd)} cap"
                } ?: "Starting") + "Ingredients are checked against your allergens on this phone",
            )
        }

        is HuntStage.Failed -> Column(modifier.padding(pad), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
            val onCard: (HuntedRecipe) -> Unit = { open = it }
            fun isSaved(h: HuntedRecipe) = HuntKeys.urlKey(h.recipe.sourceUrl) in stage.saved
            item(key = "head") {
                HuntSubtitle(
                    "${result.rounds} round${if (result.rounds == 1) "" else "s"} · ${usd(result.costUsd)} spent · " +
                        "${result.candidatesSeen} pages read · ${result.stop.label.replaceFirstChar(Char::lowercase)}" +
                        (if (result.aiReads > 0) " · ${result.aiReads} read by AI" else ""),
                )
            }
            item(key = "legend") {
                Text(
                    "Check the label: an ingredient may carry the allergen, so read the pack. " +
                        "Check allergens: the recipe may contain it, so read the ingredients first. " +
                        "Both come from this phone's own check; the AI cannot clear either.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            item(key = "results-head") {
                HuntSection("Results · ${result.kept.size} kept", note = "of ${result.results.size} reviewed")
            }
            if (result.kept.isEmpty()) {
                item(key = "none") {
                    EmptyState(
                        title = if (result.close.isEmpty()) "Nothing passed every check" else "Nothing met every limit",
                        body = if (result.close.isEmpty()) "Try a lower protein goal, a higher calorie cap, or more time, or tap Find more."
                        else "The close matches below are within 10% of a limit. Tap Find more to keep looking.",
                    )
                }
            }
            items(result.kept, key = { "k" + it.recipe.sourceUrl + it.recipe.title }) { hunted ->
                HuntedCard(hunted, saved = isSaved(hunted), onClick = { onCard(hunted) }, onSave = { viewModel.save(hunted) })
            }
            if (result.close.isNotEmpty()) {
                item(key = "close-head") { HuntSection("Close matches · ${result.close.size}", note = "within 10% of a limit") }
                items(result.close, key = { "c" + it.recipe.sourceUrl + it.recipe.title }) { hunted ->
                    HuntedCard(hunted, saved = isSaved(hunted), onClick = { onCard(hunted) }, onSave = { viewModel.save(hunted) })
                }
            }
            if (result.adaptable.isNotEmpty()) {
                item(key = "adapt-head") {
                    HuntSection(
                        "Could be adapted · ${result.adaptable.size}",
                        note = "contain something you avoid, but only as a swappable part",
                    )
                }
                item(key = "adapt-legend") {
                    Text(
                        "These recipes still carry their allergen warning. The swaps are a suggestion: saving makes a separate " +
                            "copy with the swapped ingredients, checked again on this phone. Check every swapped product's label.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                items(result.adaptable, key = { "a" + it.recipe.sourceUrl + it.recipe.title }) { hunted ->
                    HuntedCard(hunted, saved = isSaved(hunted), onClick = { onCard(hunted) }, onSave = { viewModel.save(hunted) })
                }
            }
            if (result.unverified.isNotEmpty()) {
                item(key = "unverified-head") { HuntSection("Could not check · ${result.unverified.size}", note = "page unreadable, not saved") }
                items(result.unverified, key = { "u" + it.recipe.sourceUrl + it.recipe.title }) { hunted ->
                    HuntedCard(hunted, saved = false, onClick = { onCard(hunted) }, onSave = {})
                }
            }
            if (result.removed.isNotEmpty()) {
                item(key = "removed-head") {
                    HuntSection(
                        "Removed · ${result.removed.size}",
                        note = "allergen or well outside a limit",
                        trailing = { TextButton(onClick = { showRemoved = !showRemoved }) { Text(if (showRemoved) "Hide" else "Show") } },
                    )
                }
                if (showRemoved) {
                    items(result.removed, key = { "r" + it.recipe.sourceUrl + it.recipe.title }) { hunted ->
                        HuntedCard(hunted, saved = false, onClick = { onCard(hunted) }, onSave = {})
                    }
                }
            }
            item(key = "more") {
                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    stage.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    val working = stage.working
                    if (working != null) {
                        Text(
                            "${working.message} Round ${working.round} · ${usd(working.costUsd)} of ${usd(result.policy.capUsd)} cap",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        Button(
                            onClick = viewModel::findMore,
                            shape = IntakeShape.Button,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                        ) { Text("Find more") }
                        Text(
                            "Keeps the same search and skips what you have seen. Up to ${usd(result.policy.capUsd)} more, " +
                                "only when you tap. ${usd(result.costUsd)} spent so far.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(
                        onClick = viewModel::backToForm,
                        shape = IntakeShape.Button,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                    ) { Text("Change the search") }
                }
            }
        }
    }

    open?.let { hunted ->
        val saved = (state.stage as? HuntStage.Done)?.saved?.contains(HuntKeys.urlKey(hunted.recipe.sourceUrl)) == true
        HuntedDetailSheet(hunted, saved, onSave = { viewModel.save(hunted) }, onDismiss = { open = null })
    }
}

/**
 * A section heading with its explanation on its own line below, so a long note can never squeeze the title into a
 * column of single letters. [trailing] (a Show/Hide button) sits at the end of the title line.
 */
@Composable
private fun HuntSection(title: String, note: String? = null, trailing: @Composable (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(top = 13.dp, bottom = 8.dp, start = 1.dp, end = 1.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            trailing?.invoke()
        }
        if (note != null) {
            Text(note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The bottom of the start screen: earlier searches, newest first. Opening one spends nothing; "Run again" searches anew. */
@Composable
private fun RecentSearches(
    recent: List<SavedHunt>,
    canRunAgain: Boolean,
    onOpen: (String) -> Unit,
    onRunAgain: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    if (recent.isEmpty()) return
    val stamp = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        HuntSection(
            "Recent searches",
            note = "Open one to see its results again with nothing spent. Run again searches the web anew and costs the usual amount.",
        )
        recent.forEach { h ->
            val c = h.counts
            ResultCard(modifier = Modifier.padding(vertical = 4.dp)) {
                Text(recentTitle(h.query), style = MaterialTheme.typography.titleSmall)
                Text(
                    stamp.format(Date(h.createdAt)) + " · ${usd(h.costUsd)}" + if (h.aiReads > 0) " · ${h.aiReads} read by AI" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    listOfNotNull(
                        "${c.kept} kept",
                        if (c.close > 0) "${c.close} close" else null,
                        if (c.adaptable > 0) "${c.adaptable} adaptable" else null,
                        if (c.unverified > 0) "${c.unverified} not checked" else null,
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
                val titles = h.results.take(3).joinToString(", ") { it.title }
                if (titles.isNotEmpty()) {
                    Text(
                        titles,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    TextButton(onClick = { onOpen(h.id) }, modifier = Modifier.semantics { contentDescription = "Open results: ${recentTitle(h.query)}" }) {
                        Text("Open results")
                    }
                    if (canRunAgain) {
                        TextButton(onClick = { onRunAgain(h.id) }, modifier = Modifier.semantics { contentDescription = "Run again: ${recentTitle(h.query)}" }) {
                            Text("Run again")
                        }
                    }
                    TextButton(onClick = { onDelete(h.id) }, modifier = Modifier.semantics { contentDescription = "Delete saved search: ${recentTitle(h.query)}" }) {
                        Text("Delete")
                    }
                }
            }
        }
    }
}

private fun recentTitle(q: SavedHuntQuery): String {
    val cuisines = q.cuisines.filterNot { it == ANY_CUISINE }
    val what = if (cuisines.isEmpty()) "Any cuisine" else cuisines.joinToString(", ")
    return what + (q.minProteinPerServing?.let { " · ≥ $it g protein" } ?: "")
}

@Composable
private fun HuntSubtitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, bottom = 10.dp),
    )
}

@Composable
private fun HuntedCard(hunted: HuntedRecipe, saved: Boolean, onClick: () -> Unit, onSave: () -> Unit) {
    val context = LocalContext.current
    val r = hunted.recipe
    val adaptable = hunted.adaptable
    val rejected = (hunted.verdict as? RecipeVerdict.Rejected)?.takeIf { adaptable == null }
    val unverified = hunted.verdict as? RecipeVerdict.Unverified
    ResultCard(dimmed = rejected != null || unverified != null, modifier = Modifier.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            // Space is reserved only for a picture that exists and loads; a missing or broken one leaves no gap.
            var imageFailed by remember(r.imageUrl) { mutableStateOf(false) }
            r.imageUrl?.takeIf { !imageFailed }?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    onError = { imageFailed = true },
                    modifier = Modifier.padding(end = 10.dp).size(76.dp).clip(RoundedCornerShape(12.dp)),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(r.title, style = MaterialTheme.typography.titleSmall)
                FlowRow(
                    modifier = Modifier.padding(top = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (saved) StatusPill("\u2713 Saved")
                    if (r.aiRead) StatusPill("Read by AI", tone = PillTone.Wait)
                    when (val v = hunted.verdict) {
                        RecipeVerdict.Passed ->
                            if (hunted.scan.hasCheckLabel) CheckLabelChip(hunted.scan.checkLabel.first().note, showReason = false)
                            else StatusPill("Passed allergen check")
                        is RecipeVerdict.NeedsReview -> StatusPill("Check allergens", tone = PillTone.Wait)
                        // Short chips only, one per count against the recipe; the full reasons are in the text below.
                        is RecipeVerdict.Rejected -> hunted.reasonChips.forEach { chip ->
                            StatusPill(if (adaptable != null) "Original: ${chip.replaceFirstChar(Char::lowercase)}" else chip, tone = PillTone.Bad)
                        }
                        is RecipeVerdict.Unverified -> StatusPill("Not checked", tone = PillTone.Wait)
                    }
                }
            }
        }
        if (unverified == null) {
            Text(
                listOfNotNull(
                    r.proteinPerServing?.let { "${it.roundToInt()} g protein" },
                    r.kcalPerServing?.let { "${it.roundToInt()} kcal" },
                    if (!r.nutritionStated) "Nutrition not stated on the page" else null,
                    r.totalMinutes?.let { "$it min" },
                ).joinToString(" \u00b7 "),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 5.dp),
            )
        }
        if (adaptable != null) {
            Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                adaptable.swaps.forEach { swap -> Text("\u2022 ${swap.summary}", style = MaterialTheme.typography.bodySmall) }
            }
        }
        Text(
            when {
                rejected != null -> "Removed because: ${rejected.reasons.joinToString("; ")}. This result is excluded from saving."
                unverified != null -> "${unverified.reason}. Allergens were not checked, so it cannot be saved. Source: ${r.sourceName}."
                adaptable != null -> listOfNotNull(
                    adaptable.confidence.label,
                    "The original keeps its warning",
                    "Source: ${r.sourceName}",
                ).joinToString(" \u00b7 ")
                else -> listOfNotNull(
                    if (hunted.isClose) "Close: ${hunted.fitReason}" else null,
                    "Source: ${r.sourceName}",
                    "Ingredients checked locally",
                ).joinToString(" \u00b7 ")
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 5.dp),
        )
        if (rejected == null && unverified == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = "View recipe: ${r.title}" }) {
                    Text("View recipe")
                }
                if (adaptable != null && !saved) {
                    TextButton(onClick = onSave, modifier = Modifier.semantics { contentDescription = "Save adapted copy: ${r.title}" }) {
                        Text("Save adapted copy")
                    }
                }
            }
        }
        if (unverified != null) {
            TextButton(
                onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(r.sourceUrl))) } },
                modifier = Modifier.semantics { contentDescription = "Open the page: ${r.title}" },
            ) { Text("Open the page") }
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
                "${r.servings} servings · ${r.totalMinutes?.let { "$it min" } ?: "time not stated"} · ${r.sourceName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val adaptable = hunted.adaptable
            if (adaptable != null) {
                AccentNote(
                    lead = "Could be adapted",
                    body = "The original recipe says: ${(hunted.verdict as RecipeVerdict.Rejected).reason}. It keeps that warning. " +
                        "Saving makes a separate copy with these swaps, checked again on this phone:\n" +
                        adaptable.swaps.joinToString("\n") { "\u2022 ${it.summary}" + (it.note?.let { n -> " ($n)" } ?: "") },
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            when (val v = hunted.verdict) {
                is RecipeVerdict.Rejected -> if (adaptable == null) AccentNote(
                    lead = "Removed by your checks", body = v.reason,
                    container = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                is RecipeVerdict.NeedsReview -> AccentNote(
                    lead = "Check before cooking", body = v.reason,
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                is RecipeVerdict.Unverified, RecipeVerdict.Passed -> Unit
            }
            if (hunted.isClose) {
                AccentNote(
                    lead = "Close match", body = hunted.fitReason.orEmpty(),
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            SectionLine("Per serving")
            Text(
                if (!r.nutritionStated) "Nutrition not stated on the page." else
                    listOfNotNull(
                        r.kcalPerServing?.let { "${it.roundToInt()} kcal" },
                        r.proteinPerServing?.let { "${it.roundToInt()} g protein" },
                        r.carbsPerServing?.let { "${it.roundToInt()} g carbs" },
                        r.fatPerServing?.let { "${it.roundToInt()} g fat" },
                    ).joinToString(" · ") + " (as published on the page)",
                style = MaterialTheme.typography.bodySmall,
            )
            SectionLine(if (adaptable != null) "Ingredients (original)" else "Ingredients")
            r.ingredients.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
            if (adaptable != null) {
                SectionLine("Ingredients (adapted copy)")
                adaptable.adaptedIngredients.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
            }
            SectionLine("Steps")
            r.steps.forEachIndexed { i, s -> Text("${i + 1}. $s", style = MaterialTheme.typography.bodySmall) }
            Spacer(Modifier.height(6.dp))
            if (hunted.isSavable) {
                Button(onClick = onSave, enabled = !saved, shape = IntakeShape.Button, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text(
                        when {
                            saved -> "\u2713 Saved to My recipes"
                            adaptable != null -> "Save adapted copy"
                            else -> "Save to My recipes"
                        },
                    )
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
