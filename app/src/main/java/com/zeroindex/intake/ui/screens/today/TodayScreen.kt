package com.zeroindex.intake.ui.screens.today

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zeroindex.intake.data.db.entity.AiInsightEntity
import com.zeroindex.intake.data.db.entity.FoodLogEntity
import com.zeroindex.intake.data.db.entity.HydrationPresetEntity
import com.zeroindex.intake.domain.model.MealSlot
import com.zeroindex.intake.ui.addfood.AddFoodSheet
import com.zeroindex.intake.ui.components.AccentNote
import com.zeroindex.intake.ui.components.IntakeCard
import com.zeroindex.intake.ui.components.MacroRing
import com.zeroindex.intake.ui.components.SectionLine
import com.zeroindex.intake.ui.screens.today.components.HydrationCard
import com.zeroindex.intake.ui.screens.today.components.InsightCard
import com.zeroindex.intake.ui.screens.today.components.MealSlotCard
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlinx.coroutines.launch

@Composable
fun TodayScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    onOpenAiSettings: () -> Unit = {},
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var addToSlot by remember { mutableStateOf<MealSlot?>(null) }
    var showWater by remember { mutableStateOf(false) }
    var templateFor by remember { mutableStateOf<MealSlot?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var actionsFor by remember { mutableStateOf<FoodLogEntity?>(null) }
    var copyTarget by remember { mutableStateOf<FoodLogEntity?>(null) }

    Box(modifier.fillMaxSize()) {
        TodayContent(
            state = state,
            contentPadding = contentPadding,
            onPreviousDay = viewModel::showPreviousDay,
            onNextDay = viewModel::showNextDay,
            onShowToday = viewModel::showToday,
            onAddToSlot = { addToSlot = it },
            onEntryLongPress = { actionsFor = it },
            onEntrySwipedToCopy = { copyTarget = it },
            onWaterPreset = { preset ->
                viewModel.logWater(preset.volumeMl, preset.id) { logged ->
                    scope.launch {
                        val unit = state.settings.volumeUnit
                        val result = snackbar.showSnackbar(
                            message = "Added ${unit.format(logged.volumeMl)} of water",
                            actionLabel = "Undo",
                            duration = SnackbarDuration.Short,
                        )
                        if (result == SnackbarResult.ActionPerformed) viewModel.deleteWater(logged)
                    }
                }
            },
            onOpenWater = { showWater = true },
            onSaveTemplate = { templateFor = it },
            onDismissInsight = viewModel::dismissInsight,
        )
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = contentPadding.calculateBottomPadding() + 8.dp),
        )
    }

    templateFor?.let { slot ->
        NameTemplateDialog(
            suggested = "${slot.displayName()} prep",
            onDismiss = { templateFor = null },
            onSave = { name ->
                viewModel.saveSlotAsTemplate(slot, name) {
                    scope.launch { snackbar.showSnackbar("Saved \"$name\" to More → Meal prep tags") }
                }
                templateFor = null
            },
        )
    }

    if (showWater) {
        state.hydration?.let { day ->
            HydrationSheet(
                day = day,
                presets = state.hydrationPresets,
                unit = state.settings.volumeUnit,
                manualBaseMl = state.settings.hydrationBaseMl,
                dynamic = state.settings.hydrationDynamic,
                trainingBonusMl = state.settings.hydrationTrainingBonusMl,
                onDismiss = { showWater = false },
                onLog = { ml -> viewModel.logWater(ml) },
                onDelete = viewModel::deleteWater,
                onRestore = viewModel::restoreWater,
                onSavePreset = viewModel::saveWaterPreset,
                onDeletePreset = viewModel::deleteWaterPreset,
                onSetUnit = viewModel::setVolumeUnit,
                onSetBase = viewModel::setHydrationBase,
                onSetDynamic = viewModel::setHydrationDynamic,
                onSetTrainingBonus = viewModel::setHydrationTrainingBonus,
            )
        }
    }

    addToSlot?.let { slot ->
        AddFoodSheet(
            mealSlot = slot,
            loggedAtMillis = viewModel.timestampForSelectedDay(),
            onDismiss = { addToSlot = null },
            onOpenAiSettings = onOpenAiSettings,
        )
    }

    actionsFor?.let { entry ->
        LogEntryActionsSheet(
            entry = entry,
            onDismiss = { actionsFor = null },
            onDelete = { viewModel.deleteLog(entry); actionsFor = null },
            onQuantityChange = { grams -> viewModel.updateQuantity(entry, grams); actionsFor = null },
            onMoveToSlot = { slot -> viewModel.moveLogToSlot(entry, slot); actionsFor = null },
            onCopyToSlot = { slot -> viewModel.copyLogToSlot(entry, slot); actionsFor = null },
        )
    }

    copyTarget?.let { entry ->
        SlotPickerSheet(
            title = "Copy \"${entry.foodNameAtLog}\" to",
            onDismiss = { copyTarget = null },
            onPick = { slot -> viewModel.copyLogToSlot(entry, slot); copyTarget = null },
        )
    }
}

@Composable
private fun TodayContent(
    state: TodayUiState,
    contentPadding: PaddingValues,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onShowToday: () -> Unit,
    onAddToSlot: (MealSlot) -> Unit,
    onEntryLongPress: (FoodLogEntity) -> Unit,
    onEntrySwipedToCopy: (FoodLogEntity) -> Unit,
    onWaterPreset: (HydrationPresetEntity) -> Unit,
    onOpenWater: () -> Unit,
    onSaveTemplate: (MealSlot) -> Unit,
    onDismissInsight: (AiInsightEntity) -> Unit,
) {
    val swipeThreshold = with(LocalDensity.current) { 64.dp.toPx() }
    var dragged by remember { mutableStateOf(0f) }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            // SPEC §5: swipe left and right to change days.
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (abs(dragged) > swipeThreshold) {
                            if (dragged > 0) onPreviousDay() else onNextDay()
                        }
                        dragged = 0f
                    },
                ) { _, amount -> dragged += amount }
            },
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
            start = 16.dp,
            end = 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "header") {
            DayHeader(state.date, state.isToday, onPreviousDay, onNextDay, onShowToday)
        }
        item(key = "ring") {
            IntakeCard { MacroRing(progress = state.progress) }
        }
        if (state.activityBonusKcal > 0) {
            item(key = "activity") {
                Text(
                    text = "Target includes +${state.activityBonusKcal} kcal from tracked workouts, resting burn already taken out",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
        item(key = "remaining") { RemainingNote(state) }
        state.hydration?.let { day ->
            item(key = "water") {
                HydrationCard(
                    day = day,
                    presets = state.hydrationPresets,
                    unit = state.settings.volumeUnit,
                    onPreset = onWaterPreset,
                    onOpen = onOpenWater,
                )
            }
        }
        state.insight?.let { insight ->
            item(key = "insight") { InsightCard(insight, onDismiss = { onDismissInsight(insight) }) }
        }
        item(key = "meals-heading") {
            SectionLine(title = "Meals", note = "Long-press an entry to edit")
        }

        items(items = state.slots, key = { it.slot.name }) { slotUi ->
            MealSlotCard(
                slotUi = slotUi,
                onAdd = { onAddToSlot(slotUi.slot) },
                onEntryLongPress = onEntryLongPress,
                onEntrySwipedToCopy = onEntrySwipedToCopy,
                onSaveAsTemplate = { onSaveTemplate(slotUi.slot) },
            )
        }

        item(key = "add") {
            Button(
                onClick = { onAddToSlot(defaultSlotForNow()) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Add food") }
        }
    }
}

/**
 * The mockup's `.date-row`: chevrons either side of a two-line centre that
 * names the day above the date. "Today · Saturday" tells you both which day you
 * are looking at and that it is the current one, so the chip below only appears
 * once you have navigated away.
 */
@Composable
private fun DayHeader(
    date: LocalDate,
    isToday: Boolean,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onShowToday: () -> Unit,
) {
    val weekday = remember { DateTimeFormatter.ofPattern("EEEE") }
    val monthDay = remember { DateTimeFormatter.ofPattern("MMMM d") }

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPreviousDay, modifier = Modifier.size(48.dp)) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Previous day",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (isToday) {
                    "Today · ${date.format(weekday)}"
                } else {
                    date.format(weekday)
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = date.format(monthDay),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!isToday) {
                AssistChip(onClick = onShowToday, label = { Text("Back to today") })
            }
        }
        IconButton(onClick = onNextDay, modifier = Modifier.size(48.dp)) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Next day",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * SPEC §5: remaining calories *and* remaining protein, because "120 g of
 * protein left with 400 kcal" is the framing you can act on. Rendered as the
 * mockup's accent block: the number as the lead, the encouragement beneath.
 */
@Composable
private fun RemainingNote(state: TodayUiState) {
    val kcalLeft = state.progress.kcalRemaining
    val proteinLeft = state.progress.proteinRemaining

    if (kcalLeft == null || proteinLeft == null) {
        AccentNote(
            lead = "No calorie target set yet",
            body = "Set one in More, or let the app work it out from two weeks of weigh-ins.",
            container = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    val over = kcalLeft < 0
    val lead = when {
        !over && proteinLeft > 0 -> "$proteinLeft g protein left · $kcalLeft kcal to spend"
        !over -> "Protein target met · $kcalLeft kcal left"
        proteinLeft > 0 -> "$proteinLeft g protein to go · ${abs(kcalLeft)} kcal over"
        else -> "Protein met · ${abs(kcalLeft)} kcal over target"
    }
    val body = when {
        !over && proteinLeft > 0 -> "One meal at a time. There is room to finish strong."
        !over -> "Comfortably placed for the rest of the day."
        proteinLeft > 0 -> "Worth a lean, high-protein choice from here."
        else -> "Nothing to fix today; tomorrow is a fresh target."
    }

    AccentNote(
        lead = lead,
        body = body,
        container = if (over) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
        contentColor = if (over) {
            MaterialTheme.colorScheme.onTertiaryContainer
        } else {
            MaterialTheme.colorScheme.onPrimaryContainer
        },
    )
}

/** Guesses the slot from the time of day so the big button needs one tap, not two. */
private fun defaultSlotForNow(): MealSlot {
    val hour = LocalTime.now().hour
    return when {
        hour < 10 -> MealSlot.BREAKFAST
        hour < 15 -> MealSlot.LUNCH
        hour < 21 -> MealSlot.DINNER
        else -> MealSlot.SNACK
    }
}

@Composable
private fun NameTemplateDialog(suggested: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(suggested) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save as meal prep") },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Name") })
        },
        confirmButton = { TextButton(onClick = { onSave(name.trim()) }, enabled = name.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
