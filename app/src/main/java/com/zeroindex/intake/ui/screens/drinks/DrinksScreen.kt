package com.zeroindex.intake.ui.screens.drinks

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zeroindex.intake.data.db.entity.FoodEntity
import com.zeroindex.intake.domain.drinks.DrinkCategory
import com.zeroindex.intake.domain.drinks.DrinkSize
import com.zeroindex.intake.domain.drinks.DrinkSizes
import com.zeroindex.intake.domain.units.VolumeUnit
import com.zeroindex.intake.ui.allergen.AllergenChip
import com.zeroindex.intake.ui.allergen.AllergenWarningSheet
import com.zeroindex.intake.ui.components.EmptyState
import com.zeroindex.intake.ui.components.IntakeCard
import com.zeroindex.intake.ui.components.SectionLine
import com.zeroindex.intake.ui.screens.today.components.icon
import com.zeroindex.intake.ui.theme.IntakeShape
import com.zeroindex.intake.ui.theme.WaterAccent
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private fun DrinkCategory.chipIcon(): ImageVector = fallbackType.icon()

/**
 * The Drinks page (IA 5d, Option A frame): today's water against the target with caffeine, sugar, calories and alcohol
 * from drinks; category chips (Soda lists every soda); a search over the same food database; the quick sizes from
 * Settings (and Custom); and today's drinks with a remove. A drink is a food: one tap on a size writes the water entry and
 * the food-log entry together, and the allergen check runs before it does. Settings for all of this (target, sizes,
 * drink values, units) live in Settings > Water and drinks, never here.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DrinksScreen(
    contentPadding: PaddingValues,
    onOpenFood: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DrinksViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val unit = state.volumeUnit
    var customOpen by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(query) { viewModel.setQuery(query) }

    val selected = state.selected
    val sizes = remember(unit, state.presets) { DrinkSizes.buttons(unit, state.presets.map { DrinkSize(unit.format(it.volumeMl), it.volumeMl) }) }
    val time = remember { DateTimeFormatter.ofPattern("h:mm a") }

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
        item(key = "summary") { SummaryCard(state, unit) }

        item(key = "search") {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search drinks") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, contentDescription = "Clear the search") } },
                singleLine = true,
                shape = IntakeShape.Field,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (query.isBlank()) {
            item(key = "categories") {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DrinkCategory.entries.forEach { c ->
                        CategoryChip(c.label, c.chipIcon(), c == state.category) { viewModel.selectCategory(c) }
                    }
                }
            }
        }
        item(key = "list-head") {
            SectionLine(if (query.isBlank()) state.category.label.uppercase() else "RESULTS FOR \"${query.trim().uppercase()}\"")
        }
        if (state.drinks.isEmpty()) {
            item(key = "none") {
                EmptyState(
                    title = if (query.isBlank()) "No ${state.category.label.lowercase()} drinks yet" else "No drink matches",
                    body = if (query.isBlank()) {
                        "Drinks you scan or search for in Log food land in their category here."
                    } else {
                        "Try another word, or search Log food for packaged drinks."
                    },
                )
            }
        } else {
            item(key = "drinks") {
                IntakeCard {
                    Column {
                        state.drinks.forEachIndexed { i, row ->
                            if (i > 0) androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            DrinkRow(
                                row = row,
                                selected = row.food.id == selected?.id,
                                onSelect = { viewModel.select(row.food.id) },
                                onInfo = { onOpenFood(row.food.id) },
                            )
                        }
                    }
                }
            }
        }

        if (selected != null) {
            item(key = "size-head") { SectionLine("${selected.name.uppercase()} · TAP A SIZE TO LOG IT") }
            item(key = "sizes") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), maxItemsInEachRow = 2) {
                    sizes.forEach { s ->
                        SizeTile(s.label, kcalLabel(selected, s.ml), Modifier.weight(1f)) {
                            viewModel.log(selected, s.ml)
                        }
                    }
                    SizeTile("+ Custom size", "type any amount", Modifier.weight(1f), dashed = true) { customOpen = true }
                }
            }
        }

        item(key = "today-head") { SectionLine("LOGGED TODAY", note = if (state.entries.isEmpty()) null else "${state.entries.size} drink${if (state.entries.size == 1) "" else "s"}") }
        if (state.entries.isEmpty()) {
            item(key = "today-none") { EmptyState(body = "Nothing logged yet today. Pick a drink and tap a size.") }
        } else {
            items(state.entries, key = { it.log.id }) { e ->
                IntakeCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(e.log.drinkType.icon(), contentDescription = null, tint = WaterAccent, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(e.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                buildString {
                                    append(unit.format(e.log.volumeMl))
                                    append(" · ").append(time.format(Instant.ofEpochMilli(e.log.loggedAt).atZone(ZoneId.systemDefault())))
                                    e.kcal?.let { append(" · ${it.roundToInt()} kcal") }
                                    if (e.log.caffeineMg >= 1) append(" · ${e.log.caffeineMg.roundToInt()} mg caffeine")
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { viewModel.remove(e.log.id) }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Filled.RemoveCircleOutline, contentDescription = "Remove ${e.name}, ${unit.format(e.log.volumeMl)}")
                        }
                    }
                }
            }
        }
    }

    if (customOpen && selected != null) {
        CustomSizeDialog(selected.name, unit, onDismiss = { customOpen = false }, onLog = { ml -> customOpen = false; viewModel.log(selected, ml) })
    }

    state.pending?.let { p ->
        AllergenWarningSheet(
            foodName = p.food.name,
            scan = p.scan,
            ingredientText = p.food.allergenTags.takeIf { it.isNotBlank() },
            foodDetail = "${unit.format(p.ml)} · ${(p.food.kcalPer100g * p.ml / 100.0).roundToInt()} kcal",
            onDismiss = viewModel::dismissPending,
            onLogAnyway = viewModel::confirmPending,
        )
    }
}

private fun kcalLabel(food: FoodEntity, ml: Double): String {
    val k = food.kcalPer100g * ml / 100.0
    return if (k < 1.0) "0 kcal" else "${k.roundToInt()} kcal"
}

@Composable
private fun SummaryCard(state: DrinksUiState, unit: VolumeUnit) {
    val day = state.day
    IntakeCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (day == null) {
                    Text("Reading today's drinks…", style = MaterialTheme.typography.titleMedium)
                } else {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(unit.formatNumber(day.consumedMl), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                        Text(" of ${unit.format(day.target.totalMl)}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 4.dp))
                    }
                    Text(
                        "Counts caffeine and alcohol too",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (day != null) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(64.dp)) {
                    CircularProgressIndicator(progress = { day.progress.coerceIn(0f, 1f) }, color = WaterAccent, trackColor = MaterialTheme.colorScheme.surfaceVariant, strokeWidth = 7.dp, modifier = Modifier.size(64.dp))
                    Text("${(day.progress * 100).roundToInt().coerceAtLeast(0)}%", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (day != null) {
            Spacer(Modifier.size(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("Caffeine", "${day.totals.caffeineMg.roundToInt()} mg", Modifier.weight(1f))
                Stat("Sugar", "${state.sugarG.roundToInt()} g", Modifier.weight(1f))
                Stat("Calories", "${state.kcal.roundToInt()}", Modifier.weight(1f))
                Stat("Alcohol", "%.1f".format(java.util.Locale.US, day.totals.standardDrinks).removeSuffix(".0") + " std", Modifier.weight(1f))
            }
            day.notes.forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp)) }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), shape = IntakeShape.Tile, modifier = modifier) {
        Column(Modifier.padding(vertical = 8.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun CategoryChip(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        color = if (selected) scheme.primaryContainer else scheme.surface,
        contentColor = if (selected) scheme.onPrimaryContainer else scheme.onSurface,
        shape = IntakeShape.Card,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) scheme.primary else scheme.outlineVariant),
        modifier = Modifier.heightIn(min = 72.dp),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp), maxLines = 1)
        }
    }
}

@Composable
private fun DrinkRow(row: DrinkRowUi, selected: Boolean, onSelect: () -> Unit, onInfo: () -> Unit) {
    val f = row.food
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(horizontal = 10.dp))
        Column(Modifier.weight(1f)) {
            Text(f.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            Text(
                buildString {
                    append("${f.kcalPer100g.roundToInt()} kcal / 100 ml")
                    if ((f.caffeineMgPer100ml ?: 0.0) > 0) append(" · ${f.caffeineMgPer100ml!!.roundToInt()} mg caffeine")
                    if (f.sugarPer100g >= 0.5) append(" · ${f.sugarPer100g.roundToInt()} g sugar")
                    if ((f.abvPercent ?: 0.0) > 0) append(" · ${f.abvPercent!!.roundToInt()}% alcohol")
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            row.allergen?.let { a -> Box(Modifier.padding(top = 4.dp)) { AllergenChip(a.verdict, a.detail) } }
        }
        IconButton(onClick = onInfo, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Filled.Info, contentDescription = "Nutrition label for ${f.name}", modifier = Modifier.size(20.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SizeTile(title: String, sub: String, modifier: Modifier, dashed: Boolean = false, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        color = if (dashed) scheme.surface else scheme.primaryContainer,
        contentColor = if (dashed) scheme.primary else scheme.onPrimaryContainer,
        shape = IntakeShape.Card,
        border = BorderStroke(1.dp, if (dashed) scheme.outline else scheme.primary),
        modifier = modifier.heightIn(min = 64.dp),
    ) {
        Column(Modifier.padding(12.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold)
            Text(sub, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun CustomSizeDialog(drinkName: String, unit: VolumeUnit, onDismiss: () -> Unit, onLog: (Double) -> Unit) {
    var text by remember { mutableStateOf("") }
    val typed = text.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 && it.isFinite() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom size") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(7) },
                label = { Text("Amount of $drinkName (${unit.label})") },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        },
        confirmButton = { Button(enabled = typed != null, onClick = { typed?.let { onLog(DrinkSizes.toMl(unit, it)) } }) { Text("Log it") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
