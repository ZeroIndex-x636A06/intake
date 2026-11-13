package com.zeroindex.intake.ui.screens.templates

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zeroindex.intake.data.foodref.RefFood
import com.zeroindex.intake.domain.template.MealTemplateRules
import com.zeroindex.intake.domain.template.PickItem
import com.zeroindex.intake.ui.components.EmptyState
import com.zeroindex.intake.ui.components.Eyebrow
import com.zeroindex.intake.ui.components.IntakeCard
import com.zeroindex.intake.ui.components.SandNote
import com.zeroindex.intake.ui.components.SectionLine
import com.zeroindex.intake.ui.components.SubHead
import com.zeroindex.intake.ui.theme.IntakeShape
import kotlin.math.roundToInt

/**
 * Make or edit a meal template (feedback round 4, item 11): a name, then foods and recipes with amounts. It needs no
 * meal plan. "Add a food or recipe" opens a search over the foods on this phone, the offline USDA database and saved
 * recipes. Saving needs a name, at least one item and sensible amounts; the allergen check runs each time the template
 * is logged, against the profile as it is then.
 */
@Composable
fun MealTemplateEditorScreen(
    templateId: String?,
    contentPadding: PaddingValues,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MealTemplateEditorViewModel = hiltViewModel(),
) {
    LaunchedEffect(templateId) { viewModel.start(templateId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val hits by viewModel.hits.collectAsStateWithLifecycle()
    var adding by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }

    if (state.missing) {
        Column(modifier.padding(contentPadding).padding(16.dp)) {
            EmptyState(title = "That template is gone", body = "It was removed. Go back to your meal templates.")
        }
        return
    }

    if (adding) {
        AddPicker(
            query = query,
            onQuery = { query = it; viewModel.setQuery(it) },
            hits = hits,
            contentPadding = contentPadding,
            onPick = { viewModel.addPicked(it); adding = false },
            onRef = { viewModel.addRef(it); adding = false },
            onCancel = { adding = false },
            modifier = modifier,
        )
        return
    }

    val lines = viewModel.linesOf(state)
    val totals = MealTemplateRules.totals(lines.filter { MealTemplateRules.portionOk(it.grams) })

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
        item(key = "head") {
            SubHead(
                if (state.isNew) "New meal template" else "Edit meal template",
                "A meal you eat often, saved so it logs in one tap. No meal plan needed.",
            )
        }
        item(key = "name") {
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::setName,
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item(key = "items-head") {
            SectionLine("Foods and recipes", note = "${state.items.size} added")
        }
        if (state.items.isEmpty()) {
            item(key = "empty") {
                EmptyState(body = "Nothing added yet. Search your foods, the offline food database or your recipes.")
            }
        } else {
            item(key = "items") {
                IntakeCard {
                    Column {
                        state.items.forEachIndexed { i, item ->
                            ItemRow(item, state.unit.label, viewModel)
                            if (i != state.items.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
            item(key = "totals") {
                Text(
                    "Total · ${totals.kcal.roundToInt()} kcal · ${totals.proteinText}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item(key = "add") {
            OutlinedButton(
                onClick = { adding = true },
                shape = IntakeShape.Button,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) { Text("Add a food or recipe") }
        }
        state.problem?.let { p -> item(key = "problem") { SandNote(p) } }
        item(key = "save") {
            Button(
                onClick = { viewModel.save(onDone) },
                enabled = !state.saving,
                shape = IntakeShape.Button,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) { Text(if (state.isNew) "Save template" else "Save changes") }
        }
    }
}

@Composable
private fun ItemRow(item: EditorItem, unitLabel: String, viewModel: MealTemplateEditorViewModel) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.titleSmall, maxLines = 2)
            Text(
                (if (item.isRecipe) "Recipe · " else "") +
                    "${item.kcalPer100g.roundToInt()} kcal per ${if (item.isRecipe) "serving" else "100 g"}" +
                    if (item.proteinListed) "" else " · protein not listed",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedTextField(
            value = item.amountText,
            onValueChange = { viewModel.setAmount(item.key, it) },
            label = { Text(if (item.isRecipe) "servings" else unitLabel) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.width(104.dp),
        )
        IconButton(onClick = { viewModel.remove(item.key) }, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Filled.Close, contentDescription = "Remove ${item.name}")
        }
    }
}

@Composable
private fun AddPicker(
    query: String,
    onQuery: (String) -> Unit,
    hits: SearchHits,
    contentPadding: PaddingValues,
    onPick: (PickItem) -> Unit,
    onRef: (RefFood) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
            start = 16.dp,
            end = 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item(key = "q") {
            OutlinedTextField(
                value = query,
                onValueChange = onQuery,
                label = { Text("Search foods and recipes") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
        item(key = "cancel") {
            OutlinedButton(onClick = onCancel, shape = IntakeShape.Button, modifier = Modifier.heightIn(min = 48.dp)) { Text("Back to the template") }
        }
        if (hits.lists.recent.isNotEmpty()) {
            item(key = "h-recent") { Eyebrow("Recent", Modifier.padding(top = 8.dp)) }
            items(items = hits.lists.recent, key = { "recent-${it.key}" }) { p -> PickLine(p.name, p.detail, onClick = { onPick(p) }) }
        }
        if (hits.lists.allSaved.isNotEmpty()) {
            item(key = "h-all") { Eyebrow("All saved", Modifier.padding(top = 8.dp)) }
            items(items = hits.lists.allSaved, key = { "all-${it.key}" }) { p -> PickLine(p.name, p.detail, onClick = { onPick(p) }) }
        }
        if (query.isBlank() && hits.lists.allSaved.isEmpty()) {
            item(key = "hint") { SandNote("Nothing saved yet. Type a food name to search the offline food database.") }
        }
        if (hits.database.isNotEmpty()) {
            item(key = "h-db") { Eyebrow("Food database (USDA, works offline)", Modifier.padding(top = 8.dp)) }
            items(items = hits.database, key = { "d-${it.id}" }) { d ->
                PickLine(d.name, "${d.kcal.roundToInt()} kcal per 100 g", onClick = { onRef(d) })
            }
        }
        if (query.isNotBlank() && hits.lists.allSaved.isEmpty() && hits.lists.recent.isEmpty() && hits.database.isEmpty()) {
            item(key = "none") { EmptyState(body = "Nothing matches \"${query.trim()}\". Try another word.") }
        }
    }
}

@Composable
private fun PickLine(name: String, detail: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 56.dp).padding(vertical = 8.dp).semantics { contentDescription = "Add $name" }) {
        Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 2)
        Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider(Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
    }
}
