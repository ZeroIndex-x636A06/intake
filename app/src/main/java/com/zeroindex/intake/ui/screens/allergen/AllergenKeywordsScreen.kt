package com.zeroindex.intake.ui.screens.allergen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zeroindex.intake.data.db.entity.AllergenAliasEntity
import com.zeroindex.intake.domain.model.KeywordLevel
import com.zeroindex.intake.ui.components.ChoiceChip
import com.zeroindex.intake.ui.components.IntakeCard
import com.zeroindex.intake.ui.components.LabeledField
import com.zeroindex.intake.ui.components.SandNote
import com.zeroindex.intake.ui.components.ScreenHeading
import com.zeroindex.intake.ui.components.SectionLine
import com.zeroindex.intake.ui.components.SwitchRow
import com.zeroindex.intake.ui.components.Tag
import kotlinx.coroutines.delay

private fun KeywordLevel.title() = when (this) {
    KeywordLevel.CONTAINS -> "Contains"
    KeywordLevel.MAY_CONTAIN -> "May contain"
    KeywordLevel.CHECK_LABEL -> "Check the label"
}

private fun KeywordLevel.explain() = when (this) {
    KeywordLevel.CONTAINS -> "Seeing this word means the allergen is in the food. Intake blocks it until you choose to log it anyway."
    KeywordLevel.MAY_CONTAIN -> "Foods that are often made with it, but not always. Intake asks you to review once."
    KeywordLevel.CHECK_LABEL -> "Foods that tend to hide it. Nothing is blocked; you just get a reminder to read the package."
}

/** One allergen: its keywords grouped by how firmly they speak, with Add keyword. */
@Composable
fun AllergenKeywordsScreen(
    allergenKey: String,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    viewModel: MyAllergensViewModel = hiltViewModel(),
) {
    val flow = remember(allergenKey) { viewModel.stateFor(allergenKey) }
    val state by flow.collectAsStateWithLifecycle(initialValue = AllergenKeywordsState(key = allergenKey))
    val message by viewModel.message.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    var turningOff by remember { mutableStateOf<AllergenAliasEntity?>(null) }
    var removingAllergen by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(setOf(KeywordLevel.CONTAINS)) }
    var showCarriers by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        if (message != null) {
            delay(3_500)
            viewModel.consumeMessage()
        }
    }

    val q = query.trim().lowercase()
    val filtered = state.keywords.filter { q.isEmpty() || it.aliasTerm.contains(q) }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 12.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
            start = 16.dp,
            end = 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item(key = "heading") {
            Column {
                ScreenHeading(state.displayName.ifEmpty { "Allergen" })
                if (state.summary.isNotBlank()) {
                    Text(
                        state.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                message?.let { SandNote(it) }
            }
        }
        item(key = "avoid") {
            IntakeCard {
                SwitchRow(
                    title = "I avoid this",
                    subtitle = "Turn on to check foods, recipes and plans for it.",
                    on = state.avoided,
                    onToggle = { viewModel.setAvoided(state.key, !state.avoided) },
                    onLabel = "On",
                    offLabel = "Off",
                )
            }
        }
        item(key = "add") {
            Button(onClick = { adding = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text("Add keyword")
            }
        }
        item(key = "search") {
            LabeledField(label = "Find a keyword", value = query, onValueChange = { query = it }, placeholder = "Search ${state.keywords.size} keywords")
        }

        KeywordLevel.entries.forEach { level ->
            val rows = filtered.filter { it.level == level }
            val carriers = if (level == KeywordLevel.CHECK_LABEL) state.carriers.filter { q.isEmpty() || it.phrase.contains(q) } else emptyList()
            if (rows.isEmpty() && carriers.isEmpty()) return@forEach
            val open = level in expanded || q.isNotEmpty()
            item(key = "head-$level") {
                SectionLine(
                    title = level.title(),
                    note = "${rows.size + carriers.size}",
                    trailing = {
                        TextButton(
                            onClick = { expanded = if (level in expanded) expanded - level else expanded + level },
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) { Text(if (open) "Hide" else "Show") }
                    },
                )
            }
            if (open) {
                item(key = "explain-$level") {
                    Text(level.explain(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(rows, key = { it.id }) { row ->
                    KeywordRow(
                        row = row,
                        onToggle = { on -> if (on) viewModel.setKeywordEnabled(row.id, true) else turningOff = row },
                        onRemove = { viewModel.removeKeyword(row.id) },
                    )
                }
                if (carriers.isNotEmpty()) {
                    item(key = "carriers") {
                        Column {
                            TextButton(onClick = { showCarriers = !showCarriers }, modifier = Modifier.heightIn(min = 48.dp)) {
                                Text(if (showCarriers) "Hide built-in reminders" else "Show ${carriers.size} built-in reminders (always on)")
                            }
                            if (showCarriers) {
                                carriers.groupBy { it.reason }.forEach { (reason, terms) ->
                                    Text(reason, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
                                    Text(
                                        terms.joinToString(", ") { it.phrase.replace(" ... ", " ") },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (state.loaded && state.keywords.isEmpty()) {
            item(key = "empty") {
                SandNote("No keywords yet. Add the words that should flag ${state.displayName.lowercase()}, such as an ingredient name.")
            }
        }

        if (state.isCustom && state.loaded) {
            item(key = "remove") {
                OutlinedButton(onClick = { removingAllergen = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text("Remove this allergen")
                }
            }
        }
    }

    if (adding) {
        AddKeywordDialog(
            allergenName = state.displayName,
            onDismiss = { adding = false },
            onAdd = { term, level ->
                adding = false
                viewModel.addKeyword(state.key, term, level)
            },
        )
    }
    turningOff?.let { row ->
        AlertDialog(
            onDismissRequest = { turningOff = null },
            title = { Text("Turn off \"${row.aliasTerm}\"?") },
            text = {
                Text(
                    "Intake will stop warning you about this word, so ${state.displayName.lowercase()} could slip through " +
                        "when it appears. The word is not deleted; you can turn it back on any time.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.setKeywordEnabled(row.id, false); turningOff = null },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("Turn off", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { turningOff = null }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Keep it on") } },
        )
    }
    if (removingAllergen) {
        AlertDialog(
            onDismissRequest = { removingAllergen = false },
            title = { Text("Remove ${state.displayName}?") },
            text = { Text("It stops being checked and leaves your list. Your data is kept in backups, and you can add it again later.") },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.removeAllergen(state.key); removingAllergen = false },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("Remove", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { removingAllergen = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel") } },
        )
    }
}

@Composable
private fun KeywordRow(row: AllergenAliasEntity, onToggle: (Boolean) -> Unit, onRemove: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = row.aliasTerm,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (row.isEnabled) null else TextDecoration.LineThrough,
                color = if (row.isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Tag(if (row.isCustom) "Yours" else "Built in")
                if (!row.isEnabled) Tag("Off")
            }
        }
        if (row.isCustom) {
            TextButton(onClick = onRemove, modifier = Modifier.heightIn(min = 48.dp)) { Text("Remove") }
        }
        Switch(checked = row.isEnabled, onCheckedChange = onToggle, modifier = Modifier.heightIn(min = 48.dp))
    }
}

@Composable
private fun AddKeywordDialog(allergenName: String, onDismiss: () -> Unit, onAdd: (String, KeywordLevel) -> Unit) {
    var term by remember { mutableStateOf("") }
    var level by remember { mutableStateOf(KeywordLevel.CONTAINS) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a keyword to $allergenName") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LabeledField(label = "Word or phrase", value = term, onValueChange = { term = it }, placeholder = "e.g. sodium caseinate")
                Text("How firmly should it flag?", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    KeywordLevel.entries.forEach { l ->
                        ChoiceChip(label = l.title(), selected = level == l, onClick = { level = l })
                    }
                }
                Text(level.explain(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(term, level) }, enabled = term.isNotBlank(), modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Add", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel") } },
    )
}
