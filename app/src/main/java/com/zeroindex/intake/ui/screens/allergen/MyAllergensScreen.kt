package com.zeroindex.intake.ui.screens.allergen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zeroindex.intake.data.repository.AllergenListItem
import com.zeroindex.intake.domain.allergen.AllergenCatalog
import com.zeroindex.intake.domain.model.AllergenSeverity
import com.zeroindex.intake.ui.components.IntakeCard
import com.zeroindex.intake.ui.components.LabeledField
import com.zeroindex.intake.ui.components.PillTone
import com.zeroindex.intake.ui.components.SandNote
import com.zeroindex.intake.ui.components.ScreenHeading
import com.zeroindex.intake.ui.components.SectionLine
import com.zeroindex.intake.ui.components.Segmented
import com.zeroindex.intake.ui.components.StatusPill
import kotlinx.coroutines.delay

/**
 * Food, Allergens, My allergens: pick what you avoid from the full list, add your own, and tap one to
 * manage its keywords. Everything is stored on this phone.
 */
@Composable
fun MyAllergensScreen(
    contentPadding: PaddingValues,
    onOpenAllergen: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MyAllergensViewModel = hiltViewModel(),
) {
    val overview by viewModel.overview.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        if (message != null) {
            delay(3_500)
            viewModel.consumeMessage()
        }
    }

    val all = overview?.items.orEmpty()
    val shown = all.filter { query.isBlank() || it.displayName.contains(query.trim(), ignoreCase = true) || it.key.contains(query.trim(), ignoreCase = true) }
    val avoidedCount = all.count { it.avoided }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 12.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
            start = 16.dp,
            end = 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "heading") {
            Column {
                ScreenHeading("My allergens")
                Text(
                    text = if (avoidedCount == 0) {
                        "Nothing is switched on, so nothing will be flagged. Turn on the ones you avoid."
                    } else {
                        "You avoid $avoidedCount. Intake checks every food, recipe and plan against these."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
                message?.let { SandNote(it) }
            }
        }

        overview?.let { o ->
            item(key = "severity") {
                IntakeCard {
                    Text("How careful should Intake be?", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = if (o.severity == AllergenSeverity.STRICT) {
                            "Strict: for a serious allergy. Treat every possible trace as a reason to stop and read."
                        } else {
                            "Avoid: warn me when something I avoid shows up."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                    Segmented(
                        options = listOf("Avoid", "Strict"),
                        selectedIndex = if (o.severity == AllergenSeverity.STRICT) 1 else 0,
                        onSelect = { viewModel.setSeverity(if (it == 1) AllergenSeverity.STRICT else AllergenSeverity.AVOID) },
                    )
                }
            }
        }

        item(key = "search") {
            LabeledField(label = "Find an allergen", value = query, onValueChange = { query = it }, placeholder = "Search the list")
        }

        item(key = "add") {
            Button(
                onClick = { adding = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) { Text("Add your own allergen") }
        }

        val groups = listOf("Yours", "US top 9", "EU additions")
        groups.forEach { group ->
            val rows = shown.filter { groupOf(it) == group }
            if (rows.isNotEmpty()) {
                item(key = "group-$group") {
                    SectionLine(
                        title = if (group == "Yours") "Allergens you added" else group,
                        note = "${rows.size}",
                    )
                }
                items(rows, key = { "allergen-${it.key}" }) { item ->
                    AllergenRow(
                        item = item,
                        onOpen = { onOpenAllergen(item.key) },
                        onToggle = { viewModel.setAvoided(item.key, it) },
                    )
                }
            }
        }

        if (shown.isEmpty() && all.isNotEmpty()) {
            item(key = "none") {
                SandNote("Nothing matches \"${query.trim()}\". You can add it as your own allergen.")
            }
        }
    }

    if (adding) {
        AddAllergenDialog(
            initial = query.trim(),
            onDismiss = { adding = false },
            onAdd = { name ->
                adding = false
                viewModel.createAllergen(name) { onOpenAllergen(it) }
            },
        )
    }
}

private fun groupOf(item: AllergenListItem): String =
    if (item.isCustom) "Yours" else AllergenCatalog.find(item.key)?.group ?: "Yours"

@Composable
private fun AllergenRow(item: AllergenListItem, onOpen: () -> Unit, onToggle: (Boolean) -> Unit) {
    IntakeCard(modifier = Modifier.clickable(role = Role.Button, onClickLabel = "Manage ${item.displayName} keywords", onClick = onOpen)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 56.dp)) {
            Column(Modifier.weight(1f)) {
                Text(item.displayName, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "${item.keywordCount} keywords" + if (item.avoided) " - you avoid this" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (item.avoided) {
                StatusPill("On", tone = PillTone.Ok, modifier = Modifier.padding(end = 8.dp))
            }
            Switch(
                checked = item.avoided,
                onCheckedChange = onToggle,
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
    }
}

@Composable
private fun AddAllergenDialog(initial: String, onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add your own allergen") },
        text = {
            Column {
                Text(
                    "For example Nightshades or Red dye 40. You can add the words that should flag it on the next screen.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.padding(top = 8.dp))
                LabeledField(label = "Name", value = name, onValueChange = { name = it })
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(name) }, enabled = name.isNotBlank(), modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Add", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel") } },
    )
}
