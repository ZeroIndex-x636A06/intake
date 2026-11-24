@file:OptIn(ExperimentalMaterial3Api::class)

package com.zeroindex.intake.ui.screens.workout

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zeroindex.intake.data.repository.ExercisePrescription
import com.zeroindex.intake.domain.motion.MotionRules
import com.zeroindex.intake.domain.training.RankedSwap
import com.zeroindex.intake.domain.training.RestRules
import com.zeroindex.intake.domain.training.UpNext
import com.zeroindex.intake.domain.training.WorkoutClock
import com.zeroindex.intake.domain.training.RepTarget
import com.zeroindex.intake.domain.training.TrainText
import com.zeroindex.intake.timer.TimerService
import com.zeroindex.intake.ui.components.AccentNote
import com.zeroindex.intake.ui.components.ChoiceChip
import com.zeroindex.intake.ui.components.ExerciseThumb
import com.zeroindex.intake.ui.components.IntakeCard
import com.zeroindex.intake.ui.components.PlateVisual
import com.zeroindex.intake.ui.components.Tag
import com.zeroindex.intake.ui.motion.RestRing
import com.zeroindex.intake.ui.screens.train.ExerciseDetailSheet
import com.zeroindex.intake.ui.screens.train.ExercisePickerSheet
import com.zeroindex.intake.domain.training.history.ForgottenWorkoutPolicy
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.zeroindex.intake.ui.components.AccentButton
import com.zeroindex.intake.ui.components.IconCircleButton
import com.zeroindex.intake.ui.components.IconCircleStyle
import com.zeroindex.intake.ui.components.LineButton
import com.zeroindex.intake.ui.components.ListCard
import com.zeroindex.intake.ui.components.ListDivider
import com.zeroindex.intake.ui.components.SoftButton
import com.zeroindex.intake.ui.components.heroMuted
import com.zeroindex.intake.ui.theme.IconSize
import com.zeroindex.intake.ui.theme.IntakeIcons
import com.zeroindex.intake.ui.theme.IntakeShape
import com.zeroindex.intake.ui.theme.IntakeSpacing
import com.zeroindex.intake.ui.theme.SpaceGrotesk
import com.zeroindex.intake.ui.theme.intake
import kotlinx.coroutines.delay

/**
 * The active workout (IA v5 6e, the `a-workout` frame): header with the clock, sets done and Finish; a rest strip with a
 * countdown ring; the exercise in hand as a card of set rows with a Previous column; Up next below. The back arrow
 * minimises the session (the session bar on the tab roots brings it back), Finish opens the summary.
 */
@Composable
fun ActiveWorkoutScreen(
    contentPadding: PaddingValues,
    onMinimise: () -> Unit,
    onFinished: (sessionId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ActiveWorkoutViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val timer by TimerService.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var picking by remember { mutableStateOf(false) }
    var detailFor by remember { mutableStateOf<String?>(null) }
    var swapFor by remember { mutableStateOf<String?>(null) }
    var plateSheetFor by remember { mutableStateOf<Pair<Double, String>?>(null) }
    var confirmFinish by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var restSheetFor by remember { mutableStateOf<String?>(null) }

    // Clock: trained time, which stops while the workout is paused (the pause lives in memory and preferences, not the database).
    val startedAt = state.session?.startedAt
    val pause = state.pause
    val paused = pause?.isPaused == true
    val elapsed by produceState(0L, startedAt, pause) {
        while (startedAt != null) {
            val now = System.currentTimeMillis()
            // The clock was set back past the start: move the start to now rather than sit at 0:00 until it catches up (Q8-10).
            if (WorkoutClock.reanchoredStart(startedAt, now) != startedAt) viewModel.reanchorClock()
            value = WorkoutClock.trainedMs(startedAt, pause, now)
            delay(1_000)
        }
    }

    // "Still training?": a workout with no activity for the threshold (Settings > Training) offers to end at the last set.
    var keepGoingAt by remember { mutableStateOf<Long?>(null) }
    val nowTick by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(30_000)
        }
    }
    val forgotten = state.session?.takeIf { !state.loading && it.finishedAt == null }?.let { session ->
        val last = state.loggedSets.maxByOrNull { it.completedAt }
        ForgottenWorkoutPolicy.check(
            startedAt = session.startedAt,
            lastSetAt = last?.completedAt,
            lastSetRestSec = last?.restTakenSec,
            keepGoingAt = keepGoingAt,
            now = nowTick,
            thresholdMillis = ForgottenWorkoutPolicy.thresholdMillis(state.settings.idleFinishHours),
        )
    }

    // The screen stays on while a workout is open, unless the person turned that off in Settings > Training.
    val view = LocalView.current
    DisposableEffect(state.settings.keepScreenAwake) {
        view.keepScreenOn = state.settings.keepScreenAwake
        onDispose { view.keepScreenOn = false }
    }

    // A short light pulse when a rest runs out (only if the setting is on). Skipping or ending a rest by hand does not pulse.
    var lastRemaining by remember { mutableIntStateOf(0) }
    var pulse by remember { mutableStateOf(false) }
    LaunchedEffect(timer.remainingSeconds, timer.totalSeconds) {
        if (state.settings.restEndFlash && lastRemaining > 0 && timer.remainingSeconds == 0 && timer.totalSeconds > 0) {
            pulse = true
            delay(450)
            pulse = false
        }
        lastRemaining = timer.remainingSeconds
    }
    val pulseAlpha by animateFloatAsState(if (pulse) 0.35f else 0f, tween(if (pulse) 80 else 350), label = "rest-end-pulse")

    val setPaused = { value: Boolean ->
        viewModel.setPaused(value)
        if (value) TimerService.pause(context) else TimerService.resume(context)
    }

    val finishNow = {
        TimerService.stop(context)
        viewModel.finish(onFinished)
        Unit
    }

    Box(modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = contentPadding.calculateTopPadding())
            .imePadding(),
    ) {
        WorkoutHeader(
            title = state.title,
            clock = TrainText.clock(elapsed),
            progress = TrainText.setsProgress(state.doneSets, state.totalSets),
            paused = paused,
            onTogglePause = { setPaused(!paused) },
            onMinimise = onMinimise,
            onFinish = { confirmFinish = true },
        )
        if (state.loading) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = IntakeSpacing.ScreenGutter,
                    end = IntakeSpacing.ScreenGutter,
                    top = 8.dp,
                    bottom = contentPadding.calculateBottomPadding() + 32.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(IntakeSpacing.StackGap),
            ) {
                val current = state.current
                if (current == null) {
                    item(key = "empty") {
                        AccentNote(
                            lead = "Empty workout",
                            body = "Pick the exercises as you go and log your sets. Everything still counts towards your history and weekly volume.",
                            container = MaterialTheme.intake.soft,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    item(key = "add-first") {
                        Button(
                            onClick = { picking = true },
                            shape = IntakeShape.Button,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        ) { Text("Add an exercise", style = MaterialTheme.typography.labelLarge) }
                    }
                } else {
                    item(key = "card-${current.exercise.id}") {
                        ExerciseSetsCard(
                            item = current,
                            state = state,
                            canMoveUp = state.currentIndex > 0,
                            canMoveDown = state.currentIndex < state.prescriptions.lastIndex,
                            onOpenDetail = { detailFor = current.exercise.id },
                            onSwap = { swapFor = current.exercise.id },
                            onMove = { delta -> viewModel.move(state.currentIndex, delta) },
                            onAddSet = { viewModel.addSet(current.exercise.id) },
                            onShowPlates = { kg, label -> plateSheetFor = kg to label },
                            onLogSet = { setIndex, weightKg, reps, isFailure, rpe ->
                                // Logging a set carries a paused workout on.
                                if (paused) setPaused(false)
                                viewModel.logSet(
                                    exerciseId = current.exercise.id,
                                    setIndex = setIndex,
                                    weightKg = weightKg,
                                    reps = reps,
                                    rpe = rpe,
                                    isWarmup = false,
                                    isFailure = isFailure,
                                    restTakenSec = null,
                                )
                                // SPEC §10: rest starts when a set is done. "No timer" (0 seconds) starts nothing.
                                val restSeconds = RestRules.clamp(state.restSecFor(current))
                                if (restSeconds > RestRules.NO_TIMER) {
                                    TimerService.start(
                                        context = context,
                                        seconds = restSeconds,
                                        label = "Rest · ${current.exercise.name}",
                                        vibrate = state.settings.restTimerVibrate,
                                        sound = state.settings.restEndSound,
                                    )
                                } else {
                                    TimerService.stop(context)
                                }
                            },
                            onUndoSet = viewModel::deleteSet,
                            onSetEffort = viewModel::setEffort,
                            restSec = state.restSecFor(current),
                            onOpenRest = { restSheetFor = current.exercise.id },
                        )
                    }
                    val others = state.prescriptions.withIndex().filter { it.index != state.currentIndex }
                    if (others.isNotEmpty()) {
                        item(key = "next-head") {
                            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.Bottom) {
                                Text("Up next", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                                Text(
                                    "${others.size} ${if (others.size == 1) "exercise" else "exercises"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        item(key = "next-list") {
                            ListCard {
                                others.forEachIndexed { n, (index, p) ->
                                    if (n > 0) ListDivider()
                                    UpNextRow(p, state, onClick = { viewModel.pick(index) })
                                }
                            }
                        }
                    }
                    item(key = "add-more") {
                        LineButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(IntakeIcons.Plus, contentDescription = null, modifier = Modifier.size(IconSize.Small))
                            Spacer(Modifier.width(6.dp))
                            Text("Add exercise", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    if (state.totalSets > 0 && state.doneSets >= state.totalSets) {
                        item(key = "all-done") {
                            AccentButton(onClick = { confirmFinish = true }, modifier = Modifier.fillMaxWidth()) {
                                Text("Finish workout", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
        }
        // The rest timer is pinned under the list, in one fixed place: starting it never moves a set row.
        // While the keyboard is open for a set row the strip steps aside: it would sit between the list and the keyboard and hide
        // the row being edited (Q7-8). The timer keeps running (and its notification), and the strip returns with the keyboard gone.
        val keyboardOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
        if ((timer.running || timer.remainingSeconds > 0) && !keyboardOpen) {
            RestStrip(
                remaining = timer.remainingSeconds,
                total = timer.totalSeconds,
                label = timer.label.ifBlank { "Rest" },
                paused = timer.paused,
                upNext = state.upNext?.let { next ->
                    val unit = state.settings.weightUnit
                    val weight = when {
                        next.weightKg > 0 -> com.zeroindex.intake.domain.units.WeightText.format(next.weightKg, unit).let { if (next.bodyweight) "+$it" else it }
                        next.bodyweight -> com.zeroindex.intake.domain.training.SetRowText.BODYWEIGHT
                        else -> ""
                    }
                    UpNext.text(next, weight)
                },
                onAdd = { TimerService.extend(context, RestRules.STEP_SECONDS, state.settings.restTimerVibrate) },
                onSubtract = { TimerService.extend(context, -RestRules.STEP_SECONDS, state.settings.restTimerVibrate) },
                onSkip = { TimerService.stop(context) },
                modifier = Modifier.padding(bottom = contentPadding.calculateBottomPadding()),
            )
        }
    }
    if (pulseAlpha > 0f) {
        Box(Modifier.fillMaxSize().alpha(pulseAlpha).background(MaterialTheme.intake.accent))
    }
    }

    detailFor?.let { id -> ExerciseDetailSheet(exerciseId = id, onDismiss = { detailFor = null }) }
    restSheetFor?.let { id ->
        val p = state.prescriptions.firstOrNull { it.exercise.id == id }
        if (p != null) {
            RestSheet(
                exerciseName = p.exercise.name,
                seconds = state.restSecFor(p),
                onChange = { viewModel.setRest(id, it) },
                onDismiss = { restSheetFor = null },
            )
        }
    }
    if (picking) {
        ExercisePickerSheet(
            title = "Add an exercise",
            onPick = { id -> viewModel.addExercise(id); picking = false },
            onDismiss = { picking = false },
        )
    }
    swapFor?.let { id ->
        SwapSheet(
            exerciseName = state.prescriptions.firstOrNull { it.exercise.id == id }?.exercise?.name.orEmpty(),
            load = { viewModel.swapOptions(id) },
            onPick = { newId -> viewModel.swap(id, newId); swapFor = null },
            onDismiss = { swapFor = null },
        )
    }
    plateSheetFor?.let { (kg, label) ->
        val load = state.plateLoad(kg)
        ModalBottomSheet(onDismissRequest = { plateSheetFor = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = IntakeSpacing.ScreenGutter).padding(bottom = 28.dp)) {
                PlateVisual(
                    title = "Plates · $label total",
                    perSide = load.perSide,
                    description = "Per side: ${com.zeroindex.intake.domain.training.PlateCalculator.describe(load, state.settings.weightUnit)}",
                )
            }
        }
    }
    if (confirmFinish) {
        FinishDialog(
            doneSets = state.doneSets,
            hasWorkingSets = state.hasWorkingSets,
            onFinish = { confirmFinish = false; finishNow() },
            onKeepGoing = { confirmFinish = false },
            onDiscard = { confirmFinish = false; confirmDiscard = true },
        )
    }
    if (forgotten != null && !confirmFinish && !confirmDiscard) {
        StillTrainingDialog(
            idleSince = forgotten.idleSince,
            finishAt = forgotten.finishAt,
            now = nowTick,
            onFinish = {
                TimerService.stop(context)
                viewModel.finishAt(forgotten.finishAt, onFinished)
            },
            onKeepGoing = { keepGoingAt = System.currentTimeMillis() },
            onDiscard = { keepGoingAt = System.currentTimeMillis(); confirmDiscard = true },
        )
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard this workout?") },
            text = { Text("The sets you logged in it are hidden and will not count towards your history or your weekly volume.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    TimerService.stop(context)
                    viewModel.discard(onMinimise)
                }) { Text("Discard", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep going") } },
        )
    }
}

/**
 * "Still training?" after a long quiet stretch. Finish ends the workout at the last set and its rest, never at the moment you
 * answer, so the length stays honest; nothing is thrown away.
 */
@Composable
private fun StillTrainingDialog(
    idleSince: Long,
    finishAt: Long?,
    now: Long,
    onFinish: () -> Unit,
    onKeepGoing: () -> Unit,
    onDiscard: () -> Unit,
) {
    val time = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
    fun clockOf(millis: Long) = time.format(java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()))
    val hours = ((now - idleSince) / 3_600_000L).toInt().coerceAtLeast(1)
    AlertDialog(
        onDismissRequest = onKeepGoing,
        title = { Text("Still training?") },
        text = {
            Text(
                if (finishAt != null) {
                    "Nothing has been logged for about $hours ${if (hours == 1) "hour" else "hours"}. Your last set was at ${clockOf(idleSince)}. " +
                        "Finish this workout at ${clockOf(finishAt)}, or keep going."
                } else {
                    "No set has been logged in this workout for about $hours ${if (hours == 1) "hour" else "hours"}. Keep going, or discard it."
                },
            )
        },
        confirmButton = {
            if (finishAt != null) TextButton(onClick = onFinish) { Text("Finish at ${clockOf(finishAt)}") }
            else TextButton(onClick = onKeepGoing) { Text("Keep going") }
        },
        dismissButton = {
            if (finishAt != null) TextButton(onClick = onKeepGoing) { Text("Keep going") }
            else TextButton(onClick = onDiscard) { Text("Discard", color = MaterialTheme.colorScheme.error) }
        },
    )
}

/** Finish is a choice, not a tap you can regret: finish, keep going, or throw the session away. */
@Composable
private fun FinishDialog(
    doneSets: Int,
    hasWorkingSets: Boolean,
    onFinish: () -> Unit,
    onKeepGoing: () -> Unit,
    onDiscard: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onKeepGoing,
        title = { Text(if (hasWorkingSets) "Finish workout?" else "Nothing logged yet") },
        text = {
            Text(
                if (hasWorkingSets) {
                    "You logged $doneSets ${if (doneSets == 1) "set" else "sets"}. Finishing opens your summary."
                } else {
                    "You have not logged a set. Keep going, or discard this workout."
                },
            )
        },
        confirmButton = {
            if (hasWorkingSets) TextButton(onClick = onFinish) { Text("Finish workout") } else TextButton(onClick = onKeepGoing) { Text("Keep going") }
        },
        dismissButton = {
            Row {
                if (hasWorkingSets) TextButton(onClick = onKeepGoing) { Text("Keep going") }
                TextButton(onClick = onDiscard) { Text("Discard", color = MaterialTheme.colorScheme.error) }
            }
        },
    )
}

/** The mockup's top bar: minimise, the day with the clock under it, pause, and a lime Finish pill. */
@Composable
private fun WorkoutHeader(
    title: String,
    clock: String,
    progress: String,
    paused: Boolean,
    onTogglePause: () -> Unit,
    onMinimise: () -> Unit,
    onFinish: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = IntakeSpacing.ScreenGutter, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconCircleButton(IntakeIcons.ChevronDown, "Minimise the workout. It keeps running.", onMinimise)
        Column(Modifier.weight(1f).padding(horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onBackground)
            Text(
                if (paused) "$clock · Paused · $progress" else "$clock · $progress",
                // One line at any font size (Q7-23): large text steps down a size, then ellipsis as the last resort.
                style = (if (LocalDensity.current.fontScale > 1.25f) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium)
                    .copy(fontFamily = SpaceGrotesk, fontFeatureSettings = "tnum"),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (paused) MaterialTheme.intake.warn else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { contentDescription = (if (paused) "Paused at $clock" else "Elapsed $clock") + ", $progress" },
            )
        }
        IconButton(
            onClick = onTogglePause,
            modifier = Modifier.size(IntakeSpacing.TouchTarget).semantics {
                contentDescription = if (paused) "Resume the workout clock" else "Pause the workout clock and rest timer"
            },
        ) {
            if (paused) Icon(IntakeIcons.Play, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(IconSize.Default)) else PauseGlyph()
        }
        AccentButton(onClick = onFinish, small = true) { Text("Finish", style = MaterialTheme.typography.labelLarge) }
    }
}

/** Two rounded bars: the pause glyph, which the icon set does not have. */
@Composable
private fun PauseGlyph() {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(2) { Box(Modifier.size(width = 4.dp, height = 16.dp).clip(IntakeShape.Pill).background(MaterialTheme.colorScheme.onBackground)) }
    }
}

/** Rest, pinned at the bottom while it runs: a ring that empties, the time, -15 s, +15 s and Skip, and the next set below. */
@Composable
private fun RestStrip(
    remaining: Int,
    total: Int,
    label: String,
    paused: Boolean,
    upNext: String?,
    onAdd: () -> Unit,
    onSubtract: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val onHero = MaterialTheme.intake.onHero
    Surface(
        color = MaterialTheme.intake.hero,
        contentColor = onHero,
        shape = IntakeShape.Card,
        modifier = modifier.fillMaxWidth().padding(horizontal = IntakeSpacing.ScreenGutter, vertical = 4.dp),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RestRing(
                    remainingFraction = MotionRules.restRemainingFraction(remaining, total),
                    size = 48.dp,
                    stroke = 5.dp,
                    color = MaterialTheme.intake.accent,
                    track = onHero.copy(alpha = 0.14f),
                    description = "$label, ${TimerService.format(remaining)} left" + if (paused) ", paused" else "",
                ) {}
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Text(TimerService.format(remaining), style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"), color = onHero)
                    Text(if (paused) "Paused" else label, style = MaterialTheme.typography.labelSmall, color = heroMuted(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                RestStepButton("-15 s", onSubtract)
                RestStepButton("+15 s", onAdd)
                AccentButton(onClick = onSkip, small = true) { Text("Skip", style = MaterialTheme.typography.labelLarge) }
            }
            if (upNext != null) {
                Text(
                    "Up next: $upNext",
                    style = MaterialTheme.typography.bodySmall,
                    color = heroMuted(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/** A 44 dp round `onHero` 14% button of the rest strip. */
@Composable
private fun RestStepButton(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.intake.onHero.copy(alpha = 0.14f),
        contentColor = MaterialTheme.intake.onHero,
        shape = IntakeShape.Pill,
        modifier = Modifier.size(IntakeSpacing.TouchTarget),
    ) {
        Box(contentAlignment = Alignment.Center) { Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1) }
    }
}

/**
 * The rest for one exercise, for this workout only: a "No timer" choice and the usual times as chips, with a 15 s stepper up
 * to 15 minutes. The program is not changed. Choosing takes effect from the next set you log.
 */
@Composable
private fun RestSheet(exerciseName: String, seconds: Int, onChange: (Int) -> Unit, onDismiss: () -> Unit) {
    // Fully expanded from the start (Q7-4): the default opens a tall sheet at half the screen height, which cut off the bottom of
    // this one (the Done button) whatever padding was added. The column scrolls when the content is taller than the screen.
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = IntakeSpacing.ScreenGutter).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Rest after $exerciseName", style = MaterialTheme.typography.headlineSmall)
            Text(
                "This workout only: your program keeps its own rest. It applies from the next set you log.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                LineButton(onClick = { onChange(RestRules.step(seconds, -1)) }, enabled = seconds > RestRules.NO_TIMER) { Text("-15 s", style = MaterialTheme.typography.labelLarge) }
                Text(
                    RestRules.label(seconds),
                    style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"),
                    modifier = Modifier.width(150.dp).padding(horizontal = 8.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                LineButton(onClick = { onChange(RestRules.step(seconds, 1)) }, enabled = seconds < RestRules.MAX_SECONDS) { Text("+15 s", style = MaterialTheme.typography.labelLarge) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                RestRules.presets.forEach { preset ->
                    ChoiceChip(RestRules.label(preset), seconds == preset, { onChange(preset) })
                }
            }
            Button(onClick = onDismiss, shape = IntakeShape.Button, modifier = Modifier.fillMaxWidth().heightIn(min = IntakeSpacing.TouchTarget)) { Text("Done", style = MaterialTheme.typography.labelLarge) }
        }
    }
}

@Composable
private fun UpNextRow(p: ExercisePrescription, state: ActiveWorkoutUiState, onClick: () -> Unit) {
    val done = state.setsFor(p.exercise.id).count { !it.isWarmup }
    val planned = (p.prescription?.sets ?: 0) + (state.extraRows[p.exercise.id] ?: 0)
    val finished = planned > 0 && done >= planned
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable(role = Role.Button, onClickLabel = "Work on ${p.exercise.name}", onClick = onClick)
            .padding(horizontal = IntakeSpacing.ListRowPadH, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ExerciseThumb(p.exercise.id, p.exercise.photoPath, size = 48.dp)
        Column(Modifier.weight(1f)) {
            Text(p.exercise.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(
                text = when {
                    finished -> TrainText.doneLine(done, planned)
                    done > 0 -> "${TrainText.doneLine(done, planned)} · ${prescriptionText(p)}"
                    else -> prescriptionText(p)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (finished) {
            Icon(IntakeIcons.Check, contentDescription = "Done", tint = MaterialTheme.intake.ok, modifier = Modifier.size(IconSize.Default))
        } else {
            Icon(IntakeIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(IconSize.Default))
        }
    }
}

private fun prescriptionText(p: ExercisePrescription): String {
    val pr = p.prescription ?: return "Pick a weight"
    return TrainText.prescription(pr.sets, pr.repsMin, pr.repsMax)
}

/** The sheet behind "Swap exercise": alternatives for the same muscles, ones you can do with your equipment first. */
@Composable
private fun SwapSheet(
    exerciseName: String,
    load: suspend () -> List<RankedSwap>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var options by remember { mutableStateOf<List<RankedSwap>?>(null) }
    LaunchedEffect(Unit) { options = load() }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = IntakeSpacing.ScreenGutter).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Swap $exerciseName", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Same muscles first, then what you own. This workout only: your program is not changed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            val list = options
            when {
                list == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                list.isEmpty() -> Text(
                    "Nothing else in the library works the same muscles. Add your own exercise from the exercise picker.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
                else -> LazyColumn(Modifier.heightIn(max = 460.dp)) {
                    items(list.size) { i ->
                        val r = list[i]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 64.dp)
                                .clickable(role = Role.Button, onClickLabel = "Swap in ${r.candidate.name}", onClick = { onPick(r.candidate.id) })
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(IntakeIcons.Swap, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(IconSize.Default))
                            Column(Modifier.weight(1f)) {
                                Text(r.candidate.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    buildString {
                                        append(r.candidate.primaryMuscle.replaceFirstChar { it.uppercase() })
                                        if (r.candidate.equipment.isNotBlank()) append(" · ").append(r.candidate.equipment.replaceFirstChar { it.uppercase() })
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (!r.ownsEquipment) Tag("Not in your equipment")
                        }
                        ListDivider()
                    }
                }
            }
        }
    }
}

/** The card of the exercise in hand: name and thumbnail, the prescription, the set rows, plates, Add set. */
@Composable
private fun ExerciseSetsCard(
    item: ExercisePrescription,
    state: ActiveWorkoutUiState,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onOpenDetail: () -> Unit,
    onSwap: () -> Unit,
    onMove: (Int) -> Unit,
    onAddSet: () -> Unit,
    onShowPlates: (kg: Double, label: String) -> Unit,
    onLogSet: (setIndex: Int, weightKg: Double, reps: Int, isFailure: Boolean, rpe: Double?) -> Unit,
    onUndoSet: (String) -> Unit,
    onSetEffort: (setId: String, rpe: Double?) -> Unit,
    restSec: Int,
    onOpenRest: () -> Unit,
) {
    val unit = state.settings.weightUnit
    val prescription = item.prescription
    var menu by remember { mutableStateOf(false) }

    IntakeCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(role = Role.Button, onClickLabel = "Show how to do ${item.exercise.name}", onClick = onOpenDetail),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ExerciseThumb(item.exercise.id, item.exercise.photoPath, size = 56.dp)
                Column(Modifier.weight(1f)) {
                    Text(item.exercise.name, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        buildString {
                            if (item.exercise.equipment.isNotBlank()) append(item.exercise.equipment.replaceFirstChar { it.uppercase() }).append(" · ")
                            append(muscleLine(item))
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            SoftButton(onClick = onOpenDetail, small = true) {
                Icon(IntakeIcons.Play, contentDescription = null, modifier = Modifier.size(IconSize.Small))
                Spacer(Modifier.width(4.dp))
                Text("Form", style = MaterialTheme.typography.labelMedium)
            }
            Box {
                IconCircleButton(IntakeIcons.More, "More for ${item.exercise.name}", { menu = true }, style = IconCircleStyle.Soft)
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Swap exercise") }, onClick = { menu = false; onSwap() })
                    if (canMoveUp) DropdownMenuItem(text = { Text("Move earlier") }, onClick = { menu = false; onMove(-1) })
                    if (canMoveDown) DropdownMenuItem(text = { Text("Move later") }, onClick = { menu = false; onMove(1) })
                }
            }
        }

        item.programExercise?.notes?.takeIf { it.isNotBlank() }?.let { note ->
            Spacer(Modifier.height(6.dp))
            Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (item.ruleError != null) {
            // SPEC §10: a malformed rule shows a validation error, and the session carries on regardless.
            Spacer(Modifier.height(8.dp))
            Text(
                text = "That exercise's progression rule could not run: ${item.ruleError} Log by hand and fix the rule in the builder.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        } else if (prescription != null) {
            Spacer(Modifier.height(8.dp))
            val amrap = RepTarget.isAmrap(prescription.repsMin, prescription.repsMax)
            Text(
                text = buildString {
                    append(TrainText.prescription(prescription.sets, prescription.repsMin, prescription.repsMax))
                    if (prescription.weightKg > 0) append(" · ").append(com.zeroindex.intake.domain.units.WeightText.format(prescription.weightKg, unit))
                    if (amrap) append(" · ").append(RepTarget.AMRAP_LABEL)
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(com.zeroindex.intake.domain.units.WeightText.inUnit(prescription.reason, unit), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(6.dp))
        SoftButton(onClick = onOpenRest, small = true) {
            Icon(IntakeIcons.Timer, contentDescription = null, modifier = Modifier.size(IconSize.Small))
            Spacer(Modifier.width(6.dp))
            Text(if (restSec > 0) "Rest ${RestRules.label(restSec)}" else "No rest timer", style = MaterialTheme.typography.labelMedium)
        }

        Spacer(Modifier.height(12.dp))
        SetRowsBlock(item = item, state = state, onLogSet = onLogSet, onUndoSet = onUndoSet, onSetEffort = onSetEffort, onShowPlates = onShowPlates, onAddSet = onAddSet)
    }
}

private fun muscleLine(item: ExercisePrescription): String {
    val main = item.exercise.primaryMuscle
    val also = item.exercise.secondaryMuscles.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    return (listOf(main) + also).joinToString(", ")
}

