package com.zeroindex.intake.ui.screens.allergen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zeroindex.intake.data.db.entity.AllergenStatus
import com.zeroindex.intake.data.repository.AllergenListItem
import com.zeroindex.intake.domain.allergen.AllergenCatalog
import com.zeroindex.intake.domain.model.AllergenSeverity
import com.zeroindex.intake.ui.components.ChipTone
import com.zeroindex.intake.ui.components.ChoiceChip
import com.zeroindex.intake.ui.components.Eyebrow
import com.zeroindex.intake.ui.components.HeroCard
import com.zeroindex.intake.ui.components.IconTile
import com.zeroindex.intake.ui.components.IconTileStyle
import com.zeroindex.intake.ui.components.IntakeCard
import com.zeroindex.intake.ui.components.IntakeChip
import com.zeroindex.intake.ui.components.IntakeSwitch
import com.zeroindex.intake.ui.components.LabeledField
import com.zeroindex.intake.ui.components.LineButton
import com.zeroindex.intake.ui.components.ListCard
import com.zeroindex.intake.ui.components.ListDivider
import com.zeroindex.intake.ui.components.SandNote
import com.zeroindex.intake.ui.components.SectionLine
import com.zeroindex.intake.ui.components.heroMuted
import com.zeroindex.intake.ui.motion.MoreDetail
import com.zeroindex.intake.ui.theme.IconSize
import com.zeroindex.intake.ui.theme.IntakeIcons
import com.zeroindex.intake.ui.theme.intake
import kotlinx.coroutines.delay

/**
 * The one Allergens screen (IA v5 3g and round 3, R3-3), in this order:
 *  1. status ("Dairy · strict") and the Avoid / Strict choice, on a dark hero;
 *  2. your allergens, with the switch for each;
 *  3. keywords (tap an allergen to manage its keywords);
 *  4. check-the-label foods;
 *  5. the hidden-ingredient reference, collapsed per allergen;
 *  6. recently flagged items.
 *
 * It merges the old My allergens and Allergen reference. The warning sheet inside Log food is unchanged, and a model or
 * an import can only raise a warning, never clear one. Allergens is the one stated exception to "settings live in
 * Settings": it is safety data and stays where food is chosen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AllergensScreen(
    contentPadding: PaddingValues,
    onOpenAllergen: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MyAllergensViewModel = hiltViewModel(),
    reference: AllergenReferenceViewModel = hiltViewModel(),
) {
    val overview by viewModel.overview.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val ref by reference.uiState.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        if (message != null) {
            delay(3_500)
            viewModel.consumeMessage()
        }
    }

    val all = overview?.items.orEmpty()
    val avoided = all.filter { it.avoided }
    val others = all.filter { !it.avoided && (query.isBlank() || it.displayName.contains(query.trim(), true) || it.key.contains(query.trim(), true)) }
    val severity = overview?.severity ?: AllergenSeverity.AVOID

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 4.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
            start = 16.dp,
            end = 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 1. Status and the Avoid / Strict choice, on the dark hero
        item(key = "status") {
            val line = if (avoided.isEmpty()) "No allergens set" else
                avoided.joinToString(", ") { it.displayName } + " · " + if (severity == AllergenSeverity.STRICT) "strict" else "avoid"
            HeroCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconTile(IntakeIcons.ShieldCheck, style = IconTileStyle.Lime)
                    Column(Modifier.weight(1f)) {
                        Text(line, style = MaterialTheme.typography.headlineSmall)
                        Text(
                            text = if (avoided.isEmpty()) {
                                "Nothing is switched on, so nothing will be flagged. Turn on the ones you avoid."
                            } else {
                                "Intake checks every food, recipe and plan against these, on this phone. A model or an imported file can raise a warning, never clear one."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = heroMuted(),
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Eyebrow("How careful should Intake be?", color = heroMuted())
                Text(
                    text = if (severity == AllergenSeverity.STRICT) {
                        "Strict: for a serious allergy. Treat every possible trace as a reason to stop and read."
                    } else {
                        "Avoid: warn me when something I avoid shows up."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = heroMuted(),
                    modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
                )
                HeroSegmented(
                    options = listOf("Avoid", "Strict"),
                    selectedIndex = if (severity == AllergenSeverity.STRICT) 1 else 0,
                    onSelect = { viewModel.setSeverity(if (it == 1) AllergenSeverity.STRICT else AllergenSeverity.AVOID) },
                )
            }
        }
        message?.let { m -> item(key = "message") { SandNote(m) } }

        // 2. Your allergens: the switched-on ones, then the search and the rest
        item(key = "mine-head") { SectionLine("Your allergens", note = "${avoided.size}") }
        if (avoided.isNotEmpty()) {
            item(key = "mine") {
                ListCard {
                    avoided.forEachIndexed { index, item ->
                        if (index > 0) ListDivider()
                        AllergenRow(item, onOpen = { onOpenAllergen(item.key) }, onToggle = { viewModel.setAvoided(item.key, it) })
                    }
                }
            }
        }
        item(key = "find") {
            Column {
                LabeledField(label = "Add an allergen", value = query, onValueChange = { query = it }, placeholder = "Search the list")
                LineButton(
                    onClick = { adding = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 8.dp),
                ) {
                    Icon(IntakeIcons.Plus, contentDescription = null, modifier = Modifier.size(IconSize.Small))
                    Spacer(Modifier.size(6.dp))
                    Text("Add your own allergen")
                }
            }
        }
        val shownOthers = others.take(if (query.isBlank()) 6 else 40)
        if (shownOthers.isNotEmpty()) {
            item(key = "others") {
                ListCard {
                    shownOthers.forEachIndexed { index, item ->
                        if (index > 0) ListDivider()
                        AllergenRow(item, onOpen = { onOpenAllergen(item.key) }, onToggle = { viewModel.setAvoided(item.key, it) })
                    }
                }
            }
        }
        if (query.isBlank() && others.size > 6) {
            item(key = "more") { Text("Search to find the other ${others.size - 6}.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }

        // 3. Keywords
        item(key = "keywords-head") { SectionLine("Keywords", note = "Tap an allergen to see and edit the words that flag it") }
        item(key = "keywords") {
            val withKeywords = all.filter { it.avoided || it.isCustom }
            IntakeCard {
                if (withKeywords.isEmpty()) {
                    Text("Turn on an allergen to see its keywords.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        withKeywords.forEach { a ->
                            ChoiceChip("${a.displayName} · ${a.keywordCount}", selected = false, onClick = { onOpenAllergen(a.key) })
                        }
                    }
                }
            }
        }

        // 4. Check the label
        if (ref.checkLabelByReason.isNotEmpty()) {
            item(key = "label-head") { SectionLine("Check the label", note = "Nothing is blocked, you are reminded to read the pack") }
            item(key = "label") {
                IntakeCard {
                    ref.checkLabelByReason.forEach { (reason, terms) ->
                        Text(reason, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
                        Text(terms.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // 5. Hidden-ingredient reference
        item(key = "ref-head") { SectionLine("Hidden-ingredient reference", note = "Every term means it is present, whatever the front of the pack says") }
        ref.aliasesByAllergen.forEach { (allergen, terms) ->
            item(key = "ref-$allergen") {
                MoreDetail(title = "${allergen.replaceFirstChar { it.uppercase() }} · ${terms.size} terms") {
                    Text(
                        terms.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }
        }

        // 6. Recently flagged
        item(key = "flagged-head") { SectionLine("Recently flagged") }
        if (ref.flagged.isEmpty()) {
            item(key = "flagged-none") {
                Text("Nothing flagged in your recipes or saved eat-out options.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            item(key = "flagged") {
                ListCard {
                    ref.flagged.forEachIndexed { index, f ->
                        if (index > 0) ListDivider()
                        val contains = f.status == AllergenStatus.CONTAINS
                        Row(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 18.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(f.name, style = MaterialTheme.typography.titleMedium)
                                Text(f.kind, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IntakeChip(
                                text = if (contains) "Contains" else "May contain",
                                tone = if (contains) ChipTone.Bad else ChipTone.Warn,
                            )
                        }
                    }
                }
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

/** One allergen row: name, its line, the On chip when switched on, and the switch. The row opens its keywords. */
@Composable
private fun AllergenRow(item: AllergenListItem, onOpen: () -> Unit, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = "Manage ${item.displayName} keywords", onClick = onOpen)
            .heightIn(min = 56.dp)
            .padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(item.displayName, style = MaterialTheme.typography.titleMedium)
            Text(
                text = "${item.keywordCount} keywords" + if (item.avoided) " · you avoid this" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (item.avoided) IntakeChip("On", tone = ChipTone.Ok, modifier = Modifier.padding(end = 8.dp))
        IntakeSwitch(checked = item.avoided, onCheckedChange = onToggle)
    }
}

/** The Avoid / Strict choice on the dark hero: a translucent track with the chosen item lifted in the hero's light text. */
@Composable
private fun HeroSegmented(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    val c = MaterialTheme.intake
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.onHero.copy(alpha = 0.12f), CircleShape)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .background(if (selected) c.onHero else Color.Transparent, CircleShape)
                    .clickable(role = Role.RadioButton, onClickLabel = option, onClick = { onSelect(index) })
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = option,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (selected) c.hero else c.onHero,
                    maxLines = 1,
                )
            }
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
                Text("For example Nightshades or Red dye 40. You can add the words that should flag it on the next screen.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.padding(top = 8.dp))
                LabeledField(label = "Name", value = name, onValueChange = { name = it })
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(name) }, enabled = name.isNotBlank(), modifier = Modifier.heightIn(min = 48.dp)) { Text("Add", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel") } },
    )
}
