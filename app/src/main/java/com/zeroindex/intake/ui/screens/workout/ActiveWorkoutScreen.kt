@file:OptIn(ExperimentalMaterial3Api::class)

package com.zeroindex.intake.ui.screens.workout

import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import com.zeroindex.intake.ui.theme.IntakeShape
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
            value = WorkoutClock.trainedMs(startedAt, pause, System.currentTimeMillis())
            delay(1_000)
        }
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
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = contentPadding.calculateBottomPadding() + 32.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val current = state.current
                if (current == null) {
                    item(key = "empty") {
                        AccentNote(
                            lead = "Empty workout",
                            body = "Pick the exercises as you go and log your sets. Everything still counts towards your history and weekly volume.",
                            container = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    item(key = "add-first") {
                        Button(
                            onClick = { picking = true },
                            shape = IntakeShape.Button,
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                        ) { Text("Add an exercise") }
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
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = IntakeShape.Card,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column {
                                    others.forEachIndexed { n, (index, p) ->
                                        UpNextRow(p, state, onClick = { viewModel.pick(index) })
                                        if (n != others.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(start = 76.dp))
                                    }
                                }
                            }
                        }
                    }
                    item(key = "add-more") {
                        OutlinedButton(
                            onClick = { picking = true },
                            shape = IntakeShape.Button,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Add exercise")
                        }
                    }
                    if (state.totalSets > 0 && state.doneSets >= state.totalSets) {
                        item(key = "all-done") {
                            Button(
                                onClick = { confirmFinish = true },
                                shape = IntakeShape.Button,
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                            ) { Text("Finish workout") }
                        }
                    }
                }
            }
        }
        // The rest timer is pinned under the list, in one fixed place: starting it never moves a set row.
        if (timer.running || timer.remainingSeconds > 0) {
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
        Box(Modifier.fillMaxSize().alpha(pulseAlpha).background(MaterialTheme.colorScheme.primary))
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
        ModalBottomSheet(onDismissRequest = { plateSheetFor = null }, sheetState = rememberModalBottomSheetState()) {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
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

/** The mockup's `.workout-top`: minimise, the day and the clock, Finish. */
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
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onMinimise, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Minimise the workout. It keeps running.")
        }
        Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, color = MaterialTheme.colorScheme.onBackground)
            Text(
                if (paused) "$clock · Paused · $progress" else "$clock · $progress",
                style = MaterialTheme.typography.bodySmall,
                color = if (paused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { contentDescription = (if (paused) "Paused at $clock" else "Elapsed $clock") + ", $progress" },
            )
        }
        IconButton(onClick = onTogglePause, modifier = Modifier.size(48.dp)) {
            Icon(
                if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                contentDescription = if (paused) "Resume the workout clock" else "Pause the workout clock and rest timer",
            )
        }
        Button(
            onClick = onFinish,
            shape = IntakeShape.Button,
            modifier = Modifier.heightIn(min = 48.dp),
        ) { Text("Finish") }
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
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = IntakeShape.Banner,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RestRing(
                    remainingFraction = MotionRules.restRemainingFraction(remaining, total),
                    size = 48.dp,
                    stroke = 5.dp,
                    color = MaterialTheme.colorScheme.primary,
                    track = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                    description = "$label, ${TimerService.format(remaining)} left" + if (paused) ", paused" else "",
                ) {}
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(TimerService.format(remaining), style = MaterialTheme.typography.headlineSmall)
                    Text(if (paused) "Paused" else label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
                TextButton(onClick = onSubtract, modifier = Modifier.heightIn(min = 48.dp)) { Text("-15 s") }
                TextButton(onClick = onAdd, modifier = Modifier.heightIn(min = 48.dp)) { Text("+15 s") }
                TextButton(onClick = onSkip, modifier = Modifier.heightIn(min = 48.dp)) { Text("Skip") }
            }
            if (upNext != null) {
                Text(
                    "Up next: $upNext",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

/**
 * The rest for one exercise, for this workout only: a "No timer" choice and the usual times as chips, with a 15 s stepper up
 * to 15 minutes. The program is not changed. Choosing takes effect from the next set you log.
 */
@Composable
private fun RestSheet(exerciseName: String, seconds: Int, onChange: (Int) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Rest after $exerciseName", style = MaterialTheme.typography.headlineSmall)
            Text(
                "This workout only: your program keeps its own rest. It applies from the next set you log.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                OutlinedButton(
                    onClick = { onChange(RestRules.step(seconds, -1)) },
                    enabled = seconds > RestRules.NO_TIMER,
                    shape = IntakeShape.Button,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("-15 s") }
                Text(
                    RestRules.label(seconds),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.width(150.dp).padding(horizontal = 8.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                OutlinedButton(
                    onClick = { onChange(RestRules.step(seconds, 1)) },
                    enabled = seconds < RestRules.MAX_SECONDS,
                    shape = IntakeShape.Button,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("+15 s") }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                RestRules.presets.forEach { preset ->
                    ChoiceChip(RestRules.label(preset), seconds == preset, { onChange(preset) })
                }
            }
            Button(onClick = onDismiss, shape = IntakeShape.Button, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Done") }
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
            .padding(horizontal = 14.dp, vertical = 10.dp),
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
            Icon(Icons.Filled.Check, contentDescription = "Done", tint = MaterialTheme.colorScheme.primary)
        } else {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                            Icon(Icons.Filled.SwapHoriz, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
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
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
            OutlinedButton(onClick = onOpenDetail, shape = IntakeShape.Button, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Form")
            }
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More for ${item.exercise.name}")
                }
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
                    if (prescription.weightKg > 0) append(" · ").append(fmt(unit.fromKg(prescription.weightKg))).append(' ').append(unit.label)
                    if (amrap) append(" · ").append(RepTarget.AMRAP_LABEL)
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(com.zeroindex.intake.domain.units.WeightText.inUnit(prescription.reason, unit), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = onOpenRest, modifier = Modifier.heightIn(min = 48.dp)) {
            Icon(Icons.Filled.Timer, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (restSec > 0) "Rest ${RestRules.label(restSec)}" else "No rest timer")
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

internal fun fmt(value: Double): String =
    if (value % 1.0 == 0.0) Math.round(value).toString() else (Math.round(value * 10) / 10.0).toString()
