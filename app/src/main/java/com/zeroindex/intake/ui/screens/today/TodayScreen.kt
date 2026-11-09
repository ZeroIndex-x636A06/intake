package com.zeroindex.intake.ui.screens.today

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.zeroindex.intake.domain.motion.MotionRules
import com.zeroindex.intake.ui.motion.StaggerGate
import com.zeroindex.intake.ui.motion.pressScale
import com.zeroindex.intake.ui.motion.rememberStaggerGate
import com.zeroindex.intake.ui.motion.staggerIn
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
import com.zeroindex.intake.ui.adaptive.LocalWindowSizeClass
import com.zeroindex.intake.ui.adaptive.isExpanded
import com.zeroindex.intake.ui.components.SectionLine
import com.zeroindex.intake.domain.onboarding.TourIds
import com.zeroindex.intake.ui.onboarding.TourScrollHook
import com.zeroindex.intake.ui.onboarding.TourTargetsPresent
import com.zeroindex.intake.ui.onboarding.tourTarget
import com.zeroindex.intake.ui.screens.today.components.BaselineCard
import com.zeroindex.intake.domain.coach.FocusTarget
import com.zeroindex.intake.ui.screens.today.components.FocusCard
import com.zeroindex.intake.ui.screens.today.components.HydrationCard
import com.zeroindex.intake.ui.screens.today.components.InsightCard
import com.zeroindex.intake.ui.screens.today.components.MealSlotCard
import com.zeroindex.intake.ui.screens.today.components.WorkoutCard
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlinx.coroutines.launch

/** Where the small Today cards (baseline week, today's workout) send the person. */
private data class TodayCardActions(
    val onOpenTrain: () -> Unit,
    val onResumeWorkout: (sessionId: String) -> Unit,
    val onMakeProgram: () -> Unit,
    val onOpenProfile: () -> Unit,
    val onBuildPlan: () -> Unit,
    val onOpenCheckIn: () -> Unit,
    val onOpenBodyMetrics: () -> Unit,
    val onLogFood: () -> Unit,
    val onLogWater: () -> Unit,
)

@Composable
fun TodayScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    onOpenAiSettings: () -> Unit = {},
    onOpenTrain: () -> Unit = {},
    onResumeWorkout: (sessionId: String) -> Unit = {},
    onMakeProgram: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    onBuildPlan: () -> Unit = {},
    onOpenCheckIn: () -> Unit = {},
    onOpenBodyMetrics: () -> Unit = {},
    viewModel: TodayViewModel = hiltViewModel(),
    extrasViewModel: TodayExtrasViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val extras by extrasViewModel.extras.collectAsStateWithLifecycle()
    val lastCelebrated by viewModel.lastCelebration.collectAsStateWithLifecycle()
    val ringEvents = rememberRingEvents(state, lastCelebrated, viewModel::markCelebrated)

    var addToSlot by remember { mutableStateOf<MealSlot?>(null) }
    var showWater by remember { mutableStateOf(false) }
    var templateFor by remember { mutableStateOf<MealSlot?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var actionsFor by remember { mutableStateOf<FoodLogEntity?>(null) }
    var copyTarget by remember { mutableStateOf<FoodLogEntity?>(null) }

    val cards = TodayCardActions(
        onOpenTrain, onResumeWorkout, onMakeProgram, onOpenProfile, onBuildPlan, onOpenCheckIn, onOpenBodyMetrics,
        onLogFood = { addToSlot = defaultSlotForNow() },
        onLogWater = { showWater = true },
    )

    Box(modifier.fillMaxSize()) {
        TodayContent(
            state = state,
            extras = extras,
            cardActions = cards,
            ringEvents = ringEvents,
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
                            message = "Added ${unit.format(logged.volumeMl)} of ${logged.drinkType.label.lowercase()}",
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
                weightUnit = state.settings.weightUnit,
                manualBaseMl = state.settings.hydrationBaseMl,
                dynamic = state.settings.hydrationDynamic,
                trainingBonusMl = state.settings.hydrationTrainingBonusMl,
                onDismiss = { showWater = false },
                onLog = { ml, drink -> viewModel.logWater(ml, drink = drink) },
                onDelete = viewModel::deleteWater,
                onRestore = viewModel::restoreWater,
                onSavePreset = { name, ml, existing, drink -> viewModel.saveWaterPreset(name, ml, existing, drink) },
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
    extras: TodayExtras,
    cardActions: TodayCardActions,
    ringEvents: RingEvents,
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
    // Tour: these exist even while a lazy item is scrolled out of view.
    TourTargetsPresent(
        buildSet {
            add(TourIds.RING)
            add(TourIds.ADD_FOOD)
            if (state.isToday && extras.focus != null) add(TourIds.COACH)
            if (state.hydration != null) add(TourIds.WATER)
            if (state.isToday && (extras.baseline != null || extras.workout !is WorkoutCardState.Loading)) add(TourIds.WORKOUT)
        },
    )
    val gate = rememberStaggerGate()
    val swipeThreshold = with(LocalDensity.current) { 64.dp.toPx() }
    var dragged by remember { mutableStateOf(0f) }
    // SPEC §5: swipe left and right to change days.
    val swipeToChangeDay = Modifier.pointerInput(Unit) {
        detectHorizontalDragGestures(
            onDragEnd = {
                if (abs(dragged) > swipeThreshold) {
                    if (dragged > 0) onPreviousDay() else onNextDay()
                }
                dragged = 0f
            },
        ) { _, amount -> dragged += amount }
    }

    if (LocalWindowSizeClass.current.isExpanded) {
        // Amendment §3, mockup `tabletToday()`: the date header spans the top,
        // then nutrition on the left and meals on the right, each scrolling on
        // its own so a long day never pushes the other column off screen.
        val columnPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 24.dp)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(swipeToChangeDay)
                .padding(
                    top = contentPadding.calculateTopPadding() + 8.dp,
                    start = 16.dp,
                    end = 16.dp,
                ),
        ) {
            DayHeader(state.date, state.isToday, onPreviousDay, onNextDay, onShowToday)
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentPadding = columnPadding,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = "nutrition-heading") { SectionLine(title = "Nutrition") }
                    nutritionItems(state, extras, cardActions, ringEvents, gate, onWaterPreset, onOpenWater, onDismissInsight)
                }
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentPadding = columnPadding,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    mealItems(
                        state, gate, onAddToSlot, onEntryLongPress, onEntrySwipedToCopy, onSaveTemplate,
                    )
                }
            }
        }
    } else {
        val listState = androidx.compose.foundation.lazy.rememberLazyListState()
        TourScrollHook(listState)
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().then(swipeToChangeDay),
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
            nutritionItems(state, extras, cardActions, ringEvents, gate, onWaterPreset, onOpenWater, onDismissInsight)
            mealItems(state, gate, onAddToSlot, onEntryLongPress, onEntrySwipedToCopy, onSaveTemplate)
        }
    }
}

/** The macro ring, its notes, water and the daily insight: the "Nutrition" column on a tablet. */
private fun LazyListScope.nutritionItems(
    state: TodayUiState,
    extras: TodayExtras,
    cardActions: TodayCardActions,
    ringEvents: RingEvents,
    gate: StaggerGate,
    onWaterPreset: (HydrationPresetEntity) -> Unit,
    onOpenWater: () -> Unit,
    onDismissInsight: (AiInsightEntity) -> Unit,
) {
    var order = 0
    // "One thing to do today": exactly one item, picked by fixed rules (domain/coach/DailyFocus.kt).
    if (state.isToday) {
        extras.focus?.let { focus ->
            val at = order++
            item(key = "focus") {
                FocusCard(
                    focus = focus,
                    modifier = Modifier.staggerIn(gate, "focus", at).tourTarget(TourIds.COACH),
                    onAction = {
                        when (focus.target) {
                            FocusTarget.CHECK_IN -> cardActions.onOpenCheckIn()
                            FocusTarget.LOG_WEIGHT -> cardActions.onOpenBodyMetrics()
                            FocusTarget.LOG_FOOD -> cardActions.onLogFood()
                            FocusTarget.WORKOUT -> cardActions.onOpenTrain()
                            FocusTarget.WATER -> cardActions.onLogWater()
                            FocusTarget.BASELINE -> cardActions.onOpenProfile()
                            FocusTarget.NONE -> Unit
                        }
                    },
                )
            }
        }
    }
    // The baseline week sits above the ring while it runs, so it is seen and then gone.
    if (state.isToday) {
        extras.baseline?.let { baseline ->
            val at = order++
            item(key = "baseline") {
                BaselineCard(
                    baseline,
                    onOpenProfile = cardActions.onOpenProfile,
                    onBuildPlan = cardActions.onBuildPlan,
                    modifier = Modifier.staggerIn(gate, "baseline", at).tourTarget(TourIds.WORKOUT),
                )
            }
        }
    }
    val ringAt = order++
    item(key = "ring") {
        IntakeCard(Modifier.staggerIn(gate, "ring", ringAt).tourTarget(TourIds.RING)) {
            MacroRing(
                progress = state.progress,
                pulseTrigger = ringEvents.pulse,
                burstTrigger = ringEvents.burst,
            )
        }
    }
    // The one obvious action on this screen.
    val logAt = order++
    item(key = "log") {
        Button(
            onClick = cardActions.onLogFood,
            modifier = Modifier
                .staggerIn(gate, "log", logAt)
                .fillMaxWidth()
                .height(52.dp)
                .pressScale()
                .tourTarget(TourIds.ADD_FOOD),
        ) { Text("Log food") }
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
        val at = order++
        item(key = "water") {
            HydrationCard(
                day = day,
                presets = state.hydrationPresets,
                unit = state.settings.volumeUnit,
                onPreset = onWaterPreset,
                onOpen = onOpenWater,
                modifier = Modifier.staggerIn(gate, "water", at).tourTarget(TourIds.WATER),
            )
        }
    }
    if (state.isToday && extras.workout !is WorkoutCardState.Loading) {
        val at = order++
        item(key = "workout") {
            WorkoutCard(
                state = extras.workout,
                onStart = cardActions.onOpenTrain,
                onResume = cardActions.onResumeWorkout,
                onMakeProgram = cardActions.onMakeProgram,
                modifier = Modifier.staggerIn(gate, "workout", at).then(
                    if (state.isToday && extras.baseline != null) Modifier else Modifier.tourTarget(TourIds.WORKOUT),
                ),
            )
        }
    }
    state.insight?.let { insight ->
        item(key = "insight") { InsightCard(insight, onDismiss = { onDismissInsight(insight) }) }
    }
}

/** The meal slot cards: the "Meals" column on a tablet. Logging is the Log food button under the ring. */
private fun LazyListScope.mealItems(
    state: TodayUiState,
    gate: StaggerGate,
    onAddToSlot: (MealSlot) -> Unit,
    onEntryLongPress: (FoodLogEntity) -> Unit,
    onEntrySwipedToCopy: (FoodLogEntity) -> Unit,
    onSaveTemplate: (MealSlot) -> Unit,
) {
    item(key = "meals-heading") {
        SectionLine(title = "Meals", note = "Long-press an entry to edit")
    }

    items(items = state.slots, key = { it.slot.name }) { slotUi ->
        MealSlotCard(
            modifier = Modifier.staggerIn(gate, "slot-${slotUi.slot.name}", slotUi.slot.ordinal),
            slotUi = slotUi,
            onAdd = { onAddToSlot(slotUi.slot) },
            onEntryLongPress = onEntryLongPress,
            onEntrySwipedToCopy = onEntrySwipedToCopy,
            onSaveAsTemplate = { onSaveTemplate(slotUi.slot) },
        )
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

/** Counters the ring watches: each increase plays the pulse or the protein burst once. */
private data class RingEvents(val pulse: Int = 0, val burst: Int = 0)

/**
 * Watches today's totals and decides, with the pure rules in [MotionRules], when the ring pulses after a
 * log and when the protein burst plays (once per day). Nothing fires for the first emission, for other
 * days, or for deletes. The confirm haptic goes with the pulse.
 */
@Composable
private fun rememberRingEvents(
    state: TodayUiState,
    lastCelebrated: Long?,
    onCelebrated: (Long) -> Unit,
): RingEvents {
    var pulse by remember { mutableIntStateOf(0) }
    var burst by remember { mutableIntStateOf(0) }
    val previous = remember(state.date) { arrayOfNulls<Double>(2) }
    val playedToday = remember { longArrayOf(Long.MIN_VALUE) }
    val haptics = LocalHapticFeedback.current
    val kcal = state.progress.consumed.kcal
    val protein = state.progress.consumed.protein
    LaunchedEffect(state.date, state.loaded, kcal, protein) {
        if (!state.loaded) return@LaunchedEffect
        val before = previous.copyOf()
        previous[0] = kcal
        previous[1] = protein
        val day = state.date.toEpochDay()
        if (MotionRules.shouldPulseAfterLog(state.isToday, before[0], kcal)) {
            pulse++
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        }
        val already = if (playedToday[0] == day) day else lastCelebrated
        if (MotionRules.shouldCelebrateProtein(state.isToday, state.progress.target?.proteinG, before[1], protein, already, day)) {
            burst++
            playedToday[0] = day
            onCelebrated(day)
        }
    }
    return RingEvents(pulse, burst)
}

/**
 * SPEC §5: remaining calories *and* remaining protein. The ring and the protein bar above already show
 * both numbers, so with a target this is one quiet sentence of encouragement, not a third copy of the
 * figures. Without a target it says how to set one.
 */
@Composable
private fun RemainingNote(state: TodayUiState) {
    val kcalLeft = state.progress.kcalRemaining
    val proteinLeft = state.progress.proteinRemaining

    if (kcalLeft == null || proteinLeft == null) {
        AccentNote(
            lead = "No calorie target yet",
            body = "Open More, then Profile & goals, to set one. Or log two weeks of weigh-ins and Intake works it out.",
            container = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    val over = kcalLeft < 0
    val body = when {
        !over && proteinLeft > 0 -> "One meal at a time. There is room to finish strong."
        !over -> "Protein is done. Comfortably placed for the rest of the day."
        proteinLeft > 0 -> "Worth a lean, high-protein choice from here."
        else -> "Nothing to fix today; tomorrow is a fresh target."
    }
    Text(
        text = body,
        style = MaterialTheme.typography.bodySmall,
        color = if (over) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp),
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
