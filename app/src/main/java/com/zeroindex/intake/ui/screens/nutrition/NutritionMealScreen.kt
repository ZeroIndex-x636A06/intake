@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.zeroindex.intake.ui.screens.nutrition

import com.zeroindex.intake.domain.units.KcalFormat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zeroindex.intake.domain.model.MealSlot
import com.zeroindex.intake.domain.nutrition.MealTotals
import com.zeroindex.intake.ui.components.IntakeCard
import com.zeroindex.intake.ui.components.MacroSplitBar
import com.zeroindex.intake.ui.screens.today.LogEntryActionsSheet
import com.zeroindex.intake.ui.components.Eyebrow
import com.zeroindex.intake.ui.components.GhostButton
import com.zeroindex.intake.ui.components.IconCircleButton
import com.zeroindex.intake.ui.components.IconCircleStyle
import com.zeroindex.intake.ui.components.LineButton
import com.zeroindex.intake.ui.components.ListDivider
import com.zeroindex.intake.ui.theme.IconSize
import com.zeroindex.intake.ui.theme.IntakeIcons
import com.zeroindex.intake.ui.theme.IntakeShape
import com.zeroindex.intake.ui.theme.IntakeSpacing
import com.zeroindex.intake.ui.theme.NumberSmall
import com.zeroindex.intake.ui.theme.intake
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The per-meal screen (Nutrition tab, tap a meal): kinda Samsung Health's meal screen, kinda Log food, in Intake's own way.
 *
 * Top to bottom: the meal's name (a dropdown that switches the meal) with the day and an overflow menu (Clear meal, Save as
 * meal template); the time new entries get; the totals card (kcal, protein, carbs and fat with the split bar, an unknown
 * macro reads "not listed"); the logged items, each with − and + (portion steps), tap to edit or move, a details button,
 * swipe or long-press to remove (soft delete, with Undo) and an allergen chip when the local check found something; then
 * "Add food" (Log food for this meal and day, which returns here), "Quick add calories", "Save as meal template" and "Done".
 */
@Composable
fun NutritionMealScreen(
    contentPadding: PaddingValues,
    onDone: () -> Unit,
    /** Log food for a `MealSlot` name, stamped at the given millis. Comes back here when it is done. */
    onAddFood: (slot: String, atMillis: Long) -> Unit,
    /** Food detail for a food id, with the slot and time a Log from there goes to. */
    onOpenFood: (foodId: String, slot: String, atMillis: Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NutritionMealViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pendingEntry by viewModel.pendingEntry.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<MealItemUi?>(null) }
    var quickAdd by rememberSaveable { mutableStateOf(false) }
    var naming by rememberSaveable { mutableStateOf(false) }
    var timing by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 4.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
            start = IntakeSpacing.ScreenGutter,
            end = IntakeSpacing.ScreenGutter,
        ),
        verticalArrangement = Arrangement.spacedBy(IntakeSpacing.StackGap),
    ) {
        item(key = "header") {
            MealHeader(
                slot = state.slot,
                dateLabel = state.dateLabel,
                hasItems = state.items.isNotEmpty(),
                onSlot = viewModel::setSlot,
                onClear = viewModel::clearMeal,
                onSaveTemplate = { naming = true },
            )
        }
        item(key = "time") {
            TimePill(time = state.time, entered = state.timeEntered, onClick = { timing = true }, onReset = viewModel::resetTime)
        }
        item(key = "totals") { TotalsCard(state.totals) }
        item(key = "foods") {
            IntakeCard {
                Text("Foods", style = MaterialTheme.typography.titleLarge)
                if (state.ready && state.items.isEmpty()) {
                    Text(
                        "Nothing logged in ${NutritionMealViewModel.slotName(state.slot).lowercase()} yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp, bottom = 4.dp),
                    )
                }
                state.items.forEach { item ->
                    androidx.compose.runtime.key(item.entry.id) {
                        MealItemRow(
                            item = item,
                            onEdit = { editing = item },
                            onRemove = { viewModel.remove(item) },
                            onStep = { up -> viewModel.step(item, up) },
                            onDetails = item.entry.foodId?.let { id -> { onOpenFood(id, state.slot.name, viewModel.loggingMillis()) } },
                        )
                    }
                }
            }
        }
        item(key = "actions") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onAddFood(state.slot.name, viewModel.loggingMillis()) },
                    shape = IntakeShape.Button,
                    modifier = Modifier.fillMaxWidth().heightIn(min = IntakeSpacing.TouchTarget),
                ) {
                    Icon(IntakeIcons.Plus, contentDescription = null, modifier = Modifier.size(IconSize.Small))
                    Spacer(Modifier.width(8.dp))
                    Text("Add food")
                }
                LineButton(onClick = { quickAdd = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Quick add calories")
                }
                LineButton(
                    onClick = { naming = true },
                    enabled = state.items.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Save as meal template") }
                GhostButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                    Text("Done")
                }
            }
        }
    }

    editing?.let { item ->
        LogEntryActionsSheet(
            entry = item.entry,
            onDismiss = { editing = null },
            onDelete = { viewModel.remove(item); editing = null },
            onQuantityChange = { grams -> viewModel.updateGrams(item, grams); editing = null },
            onMoveToSlot = { slot -> viewModel.moveTo(item, slot); editing = null },
            onCopyToSlot = { slot -> viewModel.copyTo(item, slot); editing = null },
        )
    }

    pendingEntry?.let { p ->
        com.zeroindex.intake.ui.allergen.PendingEntryAllergenSheet(
            pending = p,
            onDismiss = viewModel::dismissPendingEntry,
            onLogAnyway = viewModel::confirmPendingEntry,
        )
    }

    if (quickAdd) {
        QuickCaloriesDialog(
            problemOf = viewModel::quickAddProblem,
            onDismiss = { quickAdd = false },
            onAdd = { name, kcal -> viewModel.quickAddCalories(name, kcal); quickAdd = false },
        )
    }

    if (naming) {
        NameMealDialog(
            suggested = "${NutritionMealViewModel.slotName(state.slot)} template",
            onDismiss = { naming = false },
            onSave = { name -> viewModel.saveAsTemplate(name); naming = false },
        )
    }

    if (timing) {
        TimeDialog(initial = state.time, onDismiss = { timing = false }, onSet = { viewModel.setTime(it); timing = false })
    }
}

// ---- Header, time and totals ----

@Composable
private fun MealHeader(
    slot: MealSlot,
    dateLabel: String,
    hasItems: Boolean,
    onSlot: (MealSlot) -> Unit,
    onClear: () -> Unit,
    onSaveTemplate: () -> Unit,
) {
    var slotMenu by remember { mutableStateOf(false) }
    var overflow by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Box {
                Row(
                    Modifier
                        .heightIn(min = 48.dp)
                        .combinedClickable(onClickLabel = "Choose the meal", onClick = { slotMenu = true })
                        .semantics { contentDescription = "${NutritionMealViewModel.slotName(slot)}. Choose the meal" },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(NutritionMealViewModel.slotName(slot), style = MaterialTheme.typography.headlineSmall)
                    Icon(IntakeIcons.ChevronDown, contentDescription = null, modifier = Modifier.padding(start = 6.dp).size(IconSize.Small))
                }
                DropdownMenu(expanded = slotMenu, onDismissRequest = { slotMenu = false }) {
                    MealSlot.entries.forEach { s ->
                        DropdownMenuItem(
                            text = { Text(NutritionMealViewModel.slotName(s), fontWeight = if (s == slot) FontWeight.SemiBold else FontWeight.Normal) },
                            onClick = { onSlot(s); slotMenu = false },
                        )
                    }
                }
            }
            Text(dateLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box {
            IconCircleButton(IntakeIcons.More, "More for this meal", { overflow = true })
            DropdownMenu(expanded = overflow, onDismissRequest = { overflow = false }) {
                DropdownMenuItem(
                    text = { Text("Clear meal") },
                    leadingIcon = { Icon(IntakeIcons.Trash, contentDescription = null, modifier = Modifier.size(IconSize.Small)) },
                    enabled = hasItems,
                    onClick = { overflow = false; onClear() },
                )
                DropdownMenuItem(
                    text = { Text("Save as meal template") },
                    enabled = hasItems,
                    onClick = { overflow = false; onSaveTemplate() },
                )
            }
        }
    }
}

@Composable
private fun TimePill(time: LocalTime, entered: Boolean, onClick: () -> Unit, onReset: () -> Unit) {
    val text = remember(time) { time.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            onClick = onClick,
            color = MaterialTheme.colorScheme.surface,
            shape = IntakeShape.Pill,
            modifier = Modifier.heightIn(min = IntakeSpacing.TouchTarget).semantics { contentDescription = "Time for new entries, $text. Change it" },
        ) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(IntakeIcons.Clock, contentDescription = null, modifier = Modifier.size(IconSize.Small))
                Text(text, style = MaterialTheme.typography.titleSmall)
            }
        }
        Text("Time for new entries", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        if (entered) TextButton(onClick = onReset) { Text("Reset") }
    }
}

@Composable
private fun TotalsCard(t: MealTotals) {
    IntakeCard {
        Eyebrow("Total calories")
        Text("${KcalFormat.format(t.kcal)} kcal", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.size(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MacroTile("Protein", t.protein.text, MaterialTheme.intake.protein, Modifier.weight(1f))
            MacroTile("Carbs", t.carbs.text, MaterialTheme.intake.carb, Modifier.weight(1f))
            MacroTile("Fat", t.fat.text, MaterialTheme.intake.fat, Modifier.weight(1f))
        }
        Spacer(Modifier.size(10.dp))
        MacroSplitBar(t.splitProtein, t.splitCarbs, t.splitFat, Modifier.fillMaxWidth())
    }
}

@Composable
private fun MacroTile(label: String, value: String, accent: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.intake.soft, shape = IntakeShape.InnerCard, modifier = modifier) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = NumberSmall.copy(color = accent))
        }
    }
}

// ---- Items ----

@Composable
private fun MealItemRow(
    item: MealItemUi,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    onStep: (up: Boolean) -> Unit,
    onDetails: (() -> Unit)?,
) {
    val haptics = LocalHapticFeedback.current
    val swipe = rememberSwipeToDismissBoxState(positionalThreshold = { distance -> distance * 0.45f })
    // The swipe removes the item (soft delete, with Undo in the snackbar); the row then leaves the list, so the state resets.
    LaunchedEffect(swipe.currentValue) {
        if (swipe.currentValue != SwipeToDismissBoxValue.Settled) {
            onRemove()
            swipe.reset()
        }
    }
    SwipeToDismissBox(
        state = swipe,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Row(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                Icon(IntakeIcons.Trash, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            }
        },
    ) {
        Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
            Column(
                Modifier
                    .combinedClickable(
                        onClickLabel = "Edit ${item.name}",
                        onClick = onEdit,
                        onLongClickLabel = "Remove ${item.name}",
                        onLongClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onRemove()
                        },
                    )
                    .padding(vertical = 8.dp, horizontal = 4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(item.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${KcalFormat.format(item.kcal)} kcal · ${item.portion}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (onDetails != null) {
                        IconCircleButton(IntakeIcons.Info, "Food details for ${item.name}", onDetails, style = IconCircleStyle.Clear)
                    }
                    if (item.canStep) {
                        IconCircleButton(IntakeIcons.Minus, "Less ${item.name}", { onStep(false) }, style = IconCircleStyle.Soft)
                        Spacer(Modifier.width(4.dp))
                        IconCircleButton(IntakeIcons.Plus, "More ${item.name}", { onStep(true) }, style = IconCircleStyle.Soft)
                    }
                }
                item.allergen?.let { verdict ->
                    Box(Modifier.padding(top = 4.dp)) { com.zeroindex.intake.ui.addfood.RowAllergenChip(com.zeroindex.intake.ui.addfood.RowAllergen(verdict, item.allergenDetail, traceLabel = item.allergenTrace)) }
                }
                ListDivider(Modifier.padding(top = 8.dp))
            }
        }
    }
}

// ---- Dialogs ----

@Composable
private fun QuickCaloriesDialog(problemOf: (String) -> String?, onDismiss: () -> Unit, onAdd: (name: String, kcal: String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var kcal by rememberSaveable { mutableStateOf("") }
    val problem = problemOf(kcal)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Quick add calories") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it.take(60) }, singleLine = true, label = { Text("Name (optional)") })
                OutlinedTextField(
                    value = kcal,
                    onValueChange = { kcal = it.filter { c -> c.isDigit() || c == '.' }.take(7) },
                    singleLine = true,
                    label = { Text("Calories (kcal)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = kcal.isNotBlank() && problem != null,
                    supportingText = { Text(if (kcal.isNotBlank() && problem != null) problem else "Protein, carbs and fat stay not listed.") },
                )
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(name, kcal) }, enabled = problem == null) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun NameMealDialog(suggested: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(suggested) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save as meal template") },
        text = { OutlinedTextField(value = name, onValueChange = { name = it.take(60) }, singleLine = true, label = { Text("Name") }) },
        confirmButton = { TextButton(onClick = { onSave(name.trim()) }, enabled = name.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun TimeDialog(initial: LocalTime, onDismiss: () -> Unit, onSet: (LocalTime) -> Unit) {
    val context = LocalContext.current
    val state = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = android.text.format.DateFormat.is24HourFormat(context),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Time for new entries") },
        text = { TimePicker(state = state) },
        confirmButton = { TextButton(onClick = { onSet(LocalTime.of(state.hour, state.minute)) }) { Text("Set time") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
