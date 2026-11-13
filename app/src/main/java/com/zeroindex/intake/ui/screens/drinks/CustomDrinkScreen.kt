package com.zeroindex.intake.ui.screens.drinks

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zeroindex.intake.domain.drinks.AddIn
import com.zeroindex.intake.domain.drinks.AddInCatalog
import com.zeroindex.intake.domain.drinks.AddInUnit
import com.zeroindex.intake.domain.drinks.DrinkSizes
import com.zeroindex.intake.ui.allergen.AllergenWarningSheet
import com.zeroindex.intake.ui.components.ChoiceChip
import com.zeroindex.intake.ui.components.EmptyState
import com.zeroindex.intake.ui.components.IntakeCard
import com.zeroindex.intake.ui.components.SectionLine
import com.zeroindex.intake.ui.theme.IntakeShape
import kotlin.math.roundToInt

/**
 * Customise a drink (or edit a saved "My drink"): the base drink and how much of it, the add-ins with amounts (a small
 * built-in list, or any food from the search), the nutrition the glass comes to, then Log it once or Save as My drink.
 * The allergen check on the finished drink runs before a log, exactly as for any drink.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomDrinkScreen(
    foodId: String,
    contentPadding: PaddingValues,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CustomDrinkViewModel = hiltViewModel(),
) {
    LaunchedEffect(foodId) { viewModel.load(foodId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val unit = state.volumeUnit
    val base = state.base
    var confirmDelete by remember { mutableStateOf(false) }

    if (state.loaded && (state.missing || base == null)) {
        Column(modifier.padding(contentPadding).padding(16.dp)) {
            EmptyState(title = "That drink is not available", body = "It, or the drink it was made from, was removed.")
        }
        return
    }
    if (base == null) return

    var volumeText by remember(base.id, state.editingId) { mutableStateOf(formatVolume(unit.fromMl(state.baseVolumeMl))) }
    val result = state.result

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
        item(key = "base") {
            IntakeCard {
                Text(if (state.editingId != null) "Editing a saved drink" else "Customise", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(base.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.size(8.dp))
                OutlinedTextField(
                    value = volumeText,
                    onValueChange = {
                        volumeText = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(7)
                        volumeText.replace(',', '.').toDoubleOrNull()?.takeIf { v -> v > 0 }?.let { v -> viewModel.setBaseVolumeMl(DrinkSizes.toMl(unit, v)) }
                    },
                    label = { Text("How much ${base.name.lowercase()} (${unit.label})") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = IntakeShape.Field,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.baseReplaced) {
                    Text(
                        "The numbers saved for ${base.name} are too high for plain brewed tea or coffee, so the USDA brewed values are used here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }

        item(key = "added-head") { SectionLine("WHAT GOES IN") }
        if (state.addIns.isEmpty()) {
            item(key = "added-none") { EmptyState(body = "Nothing added yet. Pick from the list below, or search for any food.") }
        } else {
            items(state.addIns.size, key = { "add-$it-${state.addIns[it].key}" }) { i ->
                AddInRow(state.addIns[i], onAmount = { a, u -> viewModel.setAmount(i, a, u) }, onRemove = { viewModel.remove(i) })
            }
        }

        item(key = "pick-head") { SectionLine("ADD") }
        item(key = "pick") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AddInCatalog.ALL.groupBy { it.group }.forEach { (group, options) ->
                    Text(group, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        options.forEach { o -> ChoiceChip(o.name, false, { viewModel.addBuiltIn(o.key) }) }
                    }
                }
            }
        }
        item(key = "search") {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::setQuery,
                label = { Text("Or search any food to add") },
                singleLine = true,
                shape = IntakeShape.Field,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        items(state.searchResults, key = { "r-${it.key}" }) { found ->
            TextButton(onClick = { viewModel.addFood(found) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(
                    "${found.food.name} · ${found.food.kcalPer100g.roundToInt()} kcal per 100",
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        item(key = "total-head") { SectionLine("THE FINISHED DRINK") }
        item(key = "total") {
            IntakeCard {
                OutlinedTextField(
                    value = state.typedName,
                    onValueChange = viewModel::setName,
                    label = { Text("Name") },
                    placeholder = { Text(state.shownName) },
                    singleLine = true,
                    shape = IntakeShape.Field,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (result != null) {
                    Spacer(Modifier.size(8.dp))
                    Text(unit.format(result.totalVolumeMl), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "${result.total.kcal.roundToInt()} kcal · ${result.total.sugar.roundToInt()} g sugar · ${result.total.protein.roundToInt()} g protein · " +
                            "${result.total.carbs.roundToInt()} g carbs · ${result.total.fat.roundToInt()} g fat",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (result.caffeineMg >= 1) {
                        Text("${result.caffeineMg.roundToInt()} mg caffeine", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        "Counts toward water like ${base.name.lowercase()}. Allergens are checked for the whole drink.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item(key = "actions") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.logOnce(onDone) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Log it once") }
                OutlinedButton(onClick = { viewModel.save(onDone) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text(if (state.editingId != null) "Save changes" else "Save as My drink")
                }
                if (state.editingId != null) {
                    TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Delete this drink") }
                }
            }
        }
    }

    if (confirmDelete) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${state.shownName}?") },
            text = { Text("It leaves My drinks. Drinks you already logged keep their numbers.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; viewModel.deleteSaved(onDone) }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }

    state.pending?.let { p ->
        AllergenWarningSheet(
            foodName = p.food.name,
            scan = p.scan,
            ingredientText = p.food.allergenTags.takeIf { it.isNotBlank() },
            foodDetail = "${unit.format(p.ml)} · ${(p.food.kcalPer100g * p.ml / 100.0).roundToInt()} kcal",
            onDismiss = viewModel::dismissPending,
            onLogAnyway = { viewModel.confirmPending(onDone) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddInRow(addIn: AddIn, onAmount: (Double, AddInUnit?) -> Unit, onRemove: () -> Unit) {
    val option = AddInCatalog.byKey(addIn.key)
    var text by remember(addIn.key) { mutableStateOf(formatVolume(addIn.amount)) }
    IntakeCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(addIn.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                val n = addIn.nutrition()
                Text(
                    "${n.kcal.roundToInt()} kcal · ${"%.1f".format(java.util.Locale.US, n.sugar).removeSuffix(".0")} g sugar",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(48.dp)) { Icon(Icons.Filled.Close, contentDescription = "Remove ${addIn.name}") }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(6)
                    text.replace(',', '.').toDoubleOrNull()?.takeIf { v -> v > 0 }?.let { v -> onAmount(v, null) }
                },
                label = { Text("Amount") },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = IntakeShape.Field,
                modifier = Modifier.weight(1f),
            )
            val units = option?.units?.keys?.toList() ?: listOf(AddInUnit.fromStored(addIn.unit))
            units.forEach { u ->
                ChoiceChip(u.label, AddInUnit.fromStored(addIn.unit) == u, { if (option != null) onAmount(text.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 } ?: addIn.amount, u) })
            }
        }
    }
}

private fun formatVolume(v: Double): String =
    if (v == v.roundToInt().toDouble()) v.roundToInt().toString() else "%.1f".format(java.util.Locale.US, v)
