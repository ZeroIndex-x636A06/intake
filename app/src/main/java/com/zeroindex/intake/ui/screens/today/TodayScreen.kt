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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zeroindex.intake.data.db.entity.AiInsightEntity
import com.zeroindex.intake.data.db.entity.FoodLogEntity
import com.zeroindex.intake.domain.coach.FocusTarget
import com.zeroindex.intake.domain.model.MealSlot
import com.zeroindex.intake.domain.motion.MotionRules
import com.zeroindex.intake.domain.onboarding.TourIds
import com.zeroindex.intake.domain.settings.ExportReminderRule
import com.zeroindex.intake.ui.adaptive.LocalWindowSizeClass
import com.zeroindex.intake.ui.adaptive.isExpanded
import com.zeroindex.intake.ui.addfood.AddFoodSheet
import com.zeroindex.intake.ui.addfood.AddFoodTab
import com.zeroindex.intake.ui.components.SandNote
import com.zeroindex.intake.ui.components.SectionLine
import com.zeroindex.intake.ui.motion.StaggerGate
import com.zeroindex.intake.ui.motion.rememberStaggerGate
import com.zeroindex.intake.ui.motion.staggerIn
import com.zeroindex.intake.ui.navigation.TabRootScreen
import com.zeroindex.intake.ui.navigation.TopLevelDestination
import com.zeroindex.intake.ui.onboarding.TourScrollHook
import com.zeroindex.intake.ui.onboarding.TourTargetsPresent
import com.zeroindex.intake.ui.onboarding.tourTarget
import com.zeroindex.intake.ui.screens.today.components.BaselineCard
import com.zeroindex.intake.ui.screens.today.components.CalorieCard
import com.zeroindex.intake.ui.screens.today.components.HydrationCard
import com.zeroindex.intake.ui.screens.today.components.InsightCard
import com.zeroindex.intake.ui.screens.today.components.MealSlotCard
import com.zeroindex.intake.ui.screens.today.components.NextUpCard
import com.zeroindex.intake.ui.screens.today.components.WeightTile
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlinx.coroutines.launch

/** Where the Today cards send the person. */
private class TodayActions(
    val onOpenTrain: () -> Unit,
    val onResumeWorkout: (sessionId: String) -> Unit,
    val onMakeProgram: () -> Unit,
    val onOpenProfile: () -> Unit,
    val onBuildPlan: () -> Unit,
    val onOpenCheckIn: () -> Unit,
    val onOpenBodyMetrics: () -> Unit,
    val onLogFood: () -> Unit,
    val onScan: () -> Unit,
    val onPhoto: () -> Unit,
    val onOpenDrinks: () -> Unit,
    val onSwap: (String?) -> Unit,
    val onOpenPlan: () -> Unit,
    val onExport: () -> Unit,
)

/**
 * Today, Option A "Familiar": a top bar with the date and its arrows and the avatar; the calorie card (ring, protein,
 * fat and carbs, Log food, Scan, Photo); Next up (the planned meal with Swap and Log it, the next workout, the coach's
 * one thing); Water (today's drinks with a minus and a plus); Weight; Meals. The four optional cards are switched in
 * Settings > Appearance and units. Today has no gear and no settings.
 */
@Composable
fun TodayScreen(
    onGoToToday: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenAiSettings: () -> Unit = {},
    onOpenTrain: () -> Unit = {},
    onResumeWorkout: (sessionId: String) -> Unit = {},
    onMakeProgram: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    onBuildPlan: () -> Unit = {},
    onOpenCheckIn: () -> Unit = {},
    onOpenBodyMetrics: () -> Unit = {},
    onOpenDrinks: () -> Unit = {},
    onSwap: (String?) -> Unit = {},
    onOpenPlan: () -> Unit = {},
    onOpenSettings: (group: String, focus: String?) -> Unit = { _, _ -> },
    viewModel: TodayViewModel = hiltViewModel(),
    extrasViewModel: TodayExtrasViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val extras by extrasViewModel.extras.collectAsStateWithLifecycle()
    val lastCelebrated by viewModel.lastCelebration.collectAsStateWithLifecycle()
    val ringEvents = rememberRingEvents(state, lastCelebrated, viewModel::markCelebrated)

    var addToSlot by remember { mutableStateOf<MealSlot?>(null) }
    var addTab by remember { mutableStateOf(AddFoodTab.RECENTS) }
    var templateFor by remember { mutableStateOf<MealSlot?>(null) }
    val scope = rememberCoroutineScope()
    var actionsFor by remember { mutableStateOf<FoodLogEntity?>(null) }
    var copyTarget by remember { mutableStateOf<FoodLogEntity?>(null) }

    fun openAdd(slot: MealSlot, tab: AddFoodTab) { addTab = tab; addToSlot = slot }
    val actions = TodayActions(
        onOpenTrain, onResumeWorkout, onMakeProgram, onOpenProfile, onBuildPlan, onOpenCheckIn, onOpenBodyMetrics,
        onLogFood = { openAdd(defaultSlotForNow(), AddFoodTab.RECENTS) },
        onScan = { openAdd(defaultSlotForNow(), AddFoodTab.BARCODE) },
        onPhoto = { openAdd(defaultSlotForNow(), AddFoodTab.PHOTO) },
        onOpenDrinks = onOpenDrinks,
        onSwap = onSwap,
        onOpenPlan = onOpenPlan,
        onExport = { onOpenSettings("sync", "exportPass") },
    )

    TabRootScreen(
        tab = TopLevelDestination.TODAY,
        title = "Today",
        onGoToToday = onGoToToday,
        centerTitle = true,
        modifier = modifier,
        titleContent = { DayTitle(state.date, state.isToday, viewModel::showPreviousDay, viewModel::showNextDay) },
    ) { contentPadding ->
        TodayContent(
            state = state,
            extras = extras,
            actions = actions,
            extrasViewModel = extrasViewModel,
            ringEvents = ringEvents,
            contentPadding = contentPadding,
            onPreviousDay = viewModel::showPreviousDay,
            onNextDay = viewModel::showNextDay,
            onShowToday = viewModel::showToday,
            onAddToSlot = { openAdd(it, AddFoodTab.RECENTS) },
            onEntryLongPress = { actionsFor = it },
            onEntrySwipedToCopy = { copyTarget = it },
            onWaterMinus = viewModel::removeLast,
            onWaterPlus = viewModel::addAnother,
            onSaveTemplate = { templateFor = it },
            onDismissInsight = viewModel::dismissInsight,
        )
    }

    templateFor?.let { slot ->
        NameTemplateDialog(
            suggested = "${slot.displayName()} prep",
            onDismiss = { templateFor = null },
            onSave = { name ->
                viewModel.saveSlotAsTemplate(slot, name) { }
                templateFor = null
            },
        )
    }

    addToSlot?.let { slot ->
        AddFoodSheet(
            mealSlot = slot,
            loggedAtMillis = viewModel.timestampForSelectedDay(),
            onDismiss = { addToSlot = null },
            onOpenAiSettings = onOpenAiSettings,
            initialTab = addTab,
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

/** The top bar's centre: the date between two arrows. */
@Composable
private fun DayTitle(date: LocalDate, isToday: Boolean, onPrevious: () -> Unit, onNext: () -> Unit) {
    val fmt = remember { DateTimeFormatter.ofPattern("EEE, MMM d") }
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous day") }
        Text(
            (if (isToday) "" else "") + date.format(fmt),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        IconButton(onClick = onNext) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next day") }
    }
}

@Composable
private fun TodayContent(
    state: TodayUiState,
    extras: TodayExtras,
    actions: TodayActions,
    extrasViewModel: TodayExtrasViewModel,
    ringEvents: RingEvents,
    contentPadding: PaddingValues,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onShowToday: () -> Unit,
    onAddToSlot: (MealSlot) -> Unit,
    onEntryLongPress: (FoodLogEntity) -> Unit,
    onEntrySwipedToCopy: (FoodLogEntity) -> Unit,
    onWaterMinus: (com.zeroindex.intake.data.db.entity.HydrationLogEntity) -> Unit,
    onWaterPlus: (com.zeroindex.intake.data.db.entity.HydrationLogEntity) -> Unit,
    onSaveTemplate: (MealSlot) -> Unit,
    onDismissInsight: (AiInsightEntity) -> Unit,
) {
    val s = state.settings
    // Tour: these exist even while a lazy item is scrolled out of view.
    TourTargetsPresent(
        buildSet {
            add(TourIds.RING)
            add(TourIds.ADD_FOOD)
            if (state.isToday && extras.focus != null) add(TourIds.COACH)
            if (state.hydration != null && s.todayCardWater) add(TourIds.WATER)
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
    val bottom = contentPadding.calculateBottomPadding() + 24.dp
    val top = contentPadding.calculateTopPadding() + 4.dp

    if (LocalWindowSizeClass.current.isExpanded) {
        Row(
            modifier = Modifier.fillMaxSize().then(swipeToChangeDay).padding(start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LazyColumn(Modifier.weight(1f).fillMaxHeight(), contentPadding = PaddingValues(top = top, bottom = bottom), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                mainItems(state, extras, actions, extrasViewModel, ringEvents, gate, onShowToday, onWaterMinus, onWaterPlus, onDismissInsight)
            }
            LazyColumn(Modifier.weight(1f).fillMaxHeight(), contentPadding = PaddingValues(top = top, bottom = bottom), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                mealItems(state, gate, onAddToSlot, onEntryLongPress, onEntrySwipedToCopy, onSaveTemplate)
            }
        }
    } else {
        val listState = androidx.compose.foundation.lazy.rememberLazyListState()
        TourScrollHook(listState)
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().then(swipeToChangeDay),
            contentPadding = PaddingValues(top = top, bottom = bottom, start = 16.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            mainItems(state, extras, actions, extrasViewModel, ringEvents, gate, onShowToday, onWaterMinus, onWaterPlus, onDismissInsight)
            mealItems(state, gate, onAddToSlot, onEntryLongPress, onEntrySwipedToCopy, onSaveTemplate)
        }
    }
}

/** Calories, Next up, Water, Weight: the first column on a tablet. */
private fun LazyListScope.mainItems(
    state: TodayUiState,
    extras: TodayExtras,
    actions: TodayActions,
    extrasViewModel: TodayExtrasViewModel,
    ringEvents: RingEvents,
    gate: StaggerGate,
    onShowToday: () -> Unit,
    onWaterMinus: (com.zeroindex.intake.data.db.entity.HydrationLogEntity) -> Unit,
    onWaterPlus: (com.zeroindex.intake.data.db.entity.HydrationLogEntity) -> Unit,
    onDismissInsight: (AiInsightEntity) -> Unit,
) {
    val s = state.settings
    var order = 0
    if (!state.isToday) {
        item(key = "back-to-today") { AssistChip(onClick = onShowToday, label = { Text("Back to today") }) }
    }
    // The baseline week sits on top while it runs, so it is seen and then gone.
    if (state.isToday) {
        extras.baseline?.let { baseline ->
            val at = order++
            item(key = "baseline") {
                BaselineCard(
                    baseline,
                    onOpenProfile = actions.onOpenProfile,
                    onBuildPlan = actions.onBuildPlan,
                    modifier = Modifier.staggerIn(gate, "baseline", at).tourTarget(TourIds.WORKOUT),
                )
            }
        }
        if (extras.exportDue) {
            item(key = "export-reminder") {
                Column {
                    SandNote(ExportReminderRule.message(extras.lastExportAt, System.currentTimeMillis()) + " Without sync, this phone holds the only copy.")
                    TextButton(onClick = actions.onExport, modifier = Modifier.heightIn(min = 48.dp)) { Text("Export now") }
                }
            }
        }
    }
    val ringAt = order++
    item(key = "calories") {
        CalorieCard(
            progress = state.progress,
            activityBonusKcal = state.activityBonusKcal,
            pulseTrigger = ringEvents.pulse,
            onLogFood = actions.onLogFood,
            onScan = actions.onScan,
            onPhoto = actions.onPhoto,
            modifier = Modifier.staggerIn(gate, "ring", ringAt).tourTarget(TourIds.RING),
            logModifier = Modifier.tourTarget(TourIds.ADD_FOOD),
        )
    }
    if (s.todayCardNext && state.isToday) {
        val focus = extras.focus
        val at = order++
        item(key = "next-up") {
            NextUpCard(
                meal = extras.nextMeal,
                workout = extras.workout,
                focus = focus,
                showMeal = true,
                onSwap = { actions.onSwap(extras.nextMeal?.slot?.name) },
                onLogMeal = extrasViewModel::logNextMeal,
                onOpenPlan = actions.onOpenPlan,
                onFocusAction = {
                    when (focus?.target) {
                        FocusTarget.CHECK_IN -> actions.onOpenCheckIn()
                        FocusTarget.LOG_WEIGHT -> actions.onOpenBodyMetrics()
                        FocusTarget.LOG_FOOD -> actions.onLogFood()
                        FocusTarget.WORKOUT -> actions.onOpenTrain()
                        FocusTarget.WATER -> actions.onOpenDrinks()
                        FocusTarget.BASELINE -> actions.onOpenProfile()
                        FocusTarget.NONE, null -> Unit
                    }
                },
                onStart = actions.onOpenTrain,
                onResume = actions.onResumeWorkout,
                onMakeProgram = actions.onMakeProgram,
                modifier = Modifier.staggerIn(gate, "next", at),
                focusModifier = Modifier.tourTarget(TourIds.COACH),
                workoutModifier = if (extras.baseline != null) Modifier else Modifier.tourTarget(TourIds.WORKOUT),
            )
        }
    }
    if (s.todayCardWater) {
        state.hydration?.let { day ->
            val at = order++
            item(key = "water") {
                HydrationCard(
                    day = day,
                    unit = s.volumeUnit,
                    onMinus = onWaterMinus,
                    onPlus = onWaterPlus,
                    onOpenDrinks = actions.onOpenDrinks,
                    modifier = Modifier.staggerIn(gate, "water", at).tourTarget(TourIds.WATER),
                )
            }
        }
    }
    if (s.todayCardWeight) {
        item(key = "weight") {
            WeightTile(state.latestWeightKg, state.weightWeekDeltaKg, s.weightUnit, actions.onOpenBodyMetrics)
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
    if (!state.settings.todayCardMeals) return
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
