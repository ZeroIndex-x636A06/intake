package com.zeroindex.intake.ui.screens.workout

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zeroindex.intake.data.db.entity.SetLogEntity
import com.zeroindex.intake.data.repository.ExercisePrescription
import com.zeroindex.intake.domain.training.OneRepMax
import com.zeroindex.intake.domain.training.PlateCalculator
import com.zeroindex.intake.domain.training.SetResult
import com.zeroindex.intake.ui.components.AccentNote
import com.zeroindex.intake.ui.components.ChoiceChip
import com.zeroindex.intake.ui.components.Eyebrow
import com.zeroindex.intake.ui.components.IntakeCard
import com.zeroindex.intake.ui.components.PlateVisual
import com.zeroindex.intake.ui.components.SandStrip
import com.zeroindex.intake.ui.components.SectionLine
import com.zeroindex.intake.ui.theme.IntakeShape
import com.zeroindex.intake.timer.TimerService
import kotlin.math.roundToInt

@Composable
fun ActiveWorkoutScreen(
    contentPadding: PaddingValues,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ActiveWorkoutViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val timer by TimerService.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val unit = state.settings.weightUnit

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 4.dp,
            bottom = contentPadding.calculateBottomPadding() + 32.dp,
            start = 16.dp,
            end = 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "top") {
            WorkoutTopBar(
                title = state.current?.exercise?.name?.let { _ ->
                    "Exercise ${state.currentIndex + 1} of ${state.prescriptions.size}"
                } ?: "Freestyle",
                elapsed = timer.takeIf { it.running || it.remainingSeconds > 0 }
                    ?.let { TimerService.format(it.remainingSeconds) },
            )
        }

        // SPEC §10: rest runs in a foreground service, so it keeps counting with
        // the screen off. The strip is sand rather than red: it is active
        // information, not a warning.
        if (timer.running || timer.remainingSeconds > 0) {
            item(key = "restbar") {
                SandStrip {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = timer.label.ifBlank { "Rest timer" },
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    Text(
                        text = TimerService.format(timer.remainingSeconds),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.width(10.dp))
                    TextButton(onClick = { TimerService.stop(context) }) {
                        Text("Stop", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        if (state.prescriptions.isEmpty()) {
            item(key = "freestyle") {
                AccentNote(
                    lead = "Freestyle session",
                    body = "No program day attached, so there are no prescriptions. Finish when " +
                        "you are done and the sets still count towards your weekly volume.",
                    container = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            item(key = "picker") {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    state.prescriptions.forEachIndexed { index, item ->
                        val done = state.setsFor(item.exercise.id).count { !it.isWarmup }
                        ChoiceChip(
                            label = "${item.exercise.name} $done/${item.prescription?.sets ?: 0}",
                            selected = index == state.currentIndex,
                            onClick = { viewModel.selectExercise(index) },
                        )
                    }
                }
            }

            state.current?.let { current ->
                item(key = "current-${current.exercise.id}") {
                    ExerciseCard(item = current, state = state)
                }

                item(key = "sets-heading") {
                    SectionLine("Log your sets", note = "Tap ✓ when done")
                }

                item(key = "grid-${current.exercise.id}") {
                    SetGrid(
                        item = current,
                        state = state,
                        onLogSet = { setIndex, weight, reps, isFailure ->
                            viewModel.logSet(
                                exerciseId = current.exercise.id,
                                setIndex = setIndex,
                                weightKg = unit.toKg(weight),
                                reps = reps,
                                rpe = null,
                                isWarmup = false,
                                isFailure = isFailure,
                                restTakenSec = null,
                            )
                            // SPEC §10: rest starts on set completion.
                            TimerService.start(
                                context = context,
                                seconds = current.programExercise?.restSec
                                    ?: current.exercise.defaultRestSec,
                                label = "Rest · ${current.exercise.name}",
                                vibrate = state.settings.restTimerVibrate,
                            )
                        },
                        onDeleteSet = viewModel::deleteSet,
                    )
                }

                current.prescription?.takeIf { it.weightKg > 0 }?.let { prescription ->
                    item(key = "plates-${current.exercise.id}") {
                        val load = state.plateLoad(prescription.weightKg)
                        PlateVisual(
                            title = "Plate calculator · " +
                                "${fmt(unit.fromKg(prescription.weightKg))} ${unit.label} total",
                            perSide = load.perSide,
                            description = "Per side: ${PlateCalculator.describe(load)}",
                        )
                    }
                }

                item(key = "nav") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = viewModel::previousExercise,
                            enabled = state.currentIndex > 0,
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Previous exercise",
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                viewModel.moveExercise(state.currentIndex, state.currentIndex + 1)
                            },
                            enabled = state.currentIndex < state.prescriptions.lastIndex,
                            shape = IntakeShape.Button,
                            modifier = Modifier.weight(1f).height(50.dp),
                        ) { Text("Move later") }
                        IconButton(
                            onClick = viewModel::nextExercise,
                            enabled = state.currentIndex < state.prescriptions.lastIndex,
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Next exercise",
                            )
                        }
                    }
                }
            }
        }

        item(key = "finish") {
            Button(
                onClick = {
                    TimerService.stop(context)
                    viewModel.finish(onFinished)
                },
                shape = IntakeShape.Button,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text("Finish session") }
        }
    }
}

/** The mockup's `.workout-top`: where you are, and the clock. */
@Composable
private fun WorkoutTopBar(title: String, elapsed: String?) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        if (elapsed != null) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = IntakeShape.Tile,
            ) {
                Text(
                    text = elapsed,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/**
 * The mockup's `.exercise-card`: the prescription as the largest number on the
 * screen, with SPEC §10's "why this weight" reason directly beneath it and last
 * session's numbers on a rule below.
 */
@Composable
private fun ExerciseCard(item: ExercisePrescription, state: ActiveWorkoutUiState) {
    val unit = state.settings.weightUnit
    val prescription = item.prescription

    IntakeCard {
        Eyebrow(
            text = item.exercise.primaryMuscle +
                if (item.exercise.equipment.isNotBlank()) " · ${item.exercise.equipment}" else "",
            color = MaterialTheme.colorScheme.secondary,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = item.exercise.name,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )

        if (item.ruleError != null) {
            // SPEC §10: a malformed rule shows a validation error, and the
            // session carries on regardless.
            Spacer(Modifier.height(8.dp))
            Text(
                text = "That exercise's progression rule could not run: ${item.ruleError} " +
                    "Log by hand and fix the rule in the builder.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        } else if (prescription != null) {
            Spacer(Modifier.height(5.dp))
            val reps = buildString {
                append(prescription.sets)
                append(" × ")
                append(prescription.repsMin)
                if (prescription.repsMax > prescription.repsMin) append("–${prescription.repsMax}")
            }
            Text(
                text = if (prescription.weightKg > 0) {
                    "$reps · ${fmt(unit.fromKg(prescription.weightKg))} ${unit.label}"
                } else {
                    reps
                },
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = prescription.reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (prescription.weightKg > 0) {
                val warmups = state.warmups(prescription.weightKg)
                if (warmups.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Warmup: " + warmups.joinToString(", ") {
                            "${fmt(unit.fromKg(it.weightKg))} × ${it.reps}"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // SPEC §10: last session's numbers inline, for comparison.
        if (item.lastSession.isNotEmpty()) {
            Spacer(Modifier.height(9.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(9.dp))
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                        append("Last session   ")
                    }
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface)) {
                        append(
                            item.lastSession.joinToString(" · ") {
                                "${fmt(unit.fromKg(it.weightKg))} × ${it.reps}"
                            },
                        )
                    }
                },
                style = MaterialTheme.typography.labelSmall,
            )
            OneRepMax.best(item.lastSession.map { SetResult(it.weightKg, it.reps) })?.let { best ->
                Text(
                    text = "Estimated 1RM then: ${fmt(unit.fromKg(best))} ${unit.label} " +
                        "(${OneRepMax.FORMULA})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * The mockup's set grid, which is a real improvement on a single shared input
 * row: every prescribed set is visible at once, pre-filled with the prescribed
 * numbers, so a session that goes to plan is one tap per set. Logged rows show
 * what was recorded and a filled tick that undoes it.
 */
@Composable
private fun SetGrid(
    item: ExercisePrescription,
    state: ActiveWorkoutUiState,
    onLogSet: (Int, Double, Int, Boolean) -> Unit,
    onDeleteSet: (String) -> Unit,
) {
    val unit = state.settings.weightUnit
    val prescription = item.prescription
    val logged = state.setsFor(item.exercise.id).filter { !it.isWarmup }

    val prescribedWeight = prescription?.weightKg
        ?.takeIf { it > 0 }
        ?.let { fmt(unit.fromKg(it)) }
        .orEmpty()
    val prescribedReps = prescription?.repsMin?.toString().orEmpty()

    // One row per prescribed set, plus a spare so an extra set is always
    // available; freestyle work gets three to start with.
    val rowCount = maxOf(prescription?.sets ?: 3, logged.size + 1)

    val edits = remember(item.exercise.id) { mutableStateMapOf<Int, Pair<String, String>>() }

    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GridCell(Modifier.width(34.dp)) { Eyebrow("Set") }
            GridCell(Modifier.weight(1f)) { Eyebrow(unit.label) }
            GridCell(Modifier.weight(1f)) { Eyebrow("Reps") }
            GridCell(Modifier.width(38.dp)) { Eyebrow("Done") }
        }

        repeat(rowCount) { index ->
            val existing = logged.firstOrNull { it.setIndex == index }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SetRow(
                index = index,
                existing = existing,
                weight = edits[index]?.first ?: prescribedWeight,
                reps = edits[index]?.second ?: prescribedReps,
                unitLabel = unit.label,
                displayWeight = { kg -> fmt(unit.fromKg(kg)) },
                onWeightChange = { next ->
                    edits[index] = next to (edits[index]?.second ?: prescribedReps)
                },
                onRepsChange = { next ->
                    edits[index] = (edits[index]?.first ?: prescribedWeight) to next
                },
                onLog = { weight, reps ->
                    onLogSet(
                        index,
                        weight,
                        reps,
                        prescription != null && reps < prescription.repsMin,
                    )
                },
                onUndo = { existing?.let { onDeleteSet(it.id) } },
            )
        }
    }
}

@Composable
private fun GridCell(modifier: Modifier, content: @Composable () -> Unit) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun SetRow(
    index: Int,
    existing: SetLogEntity?,
    weight: String,
    reps: String,
    unitLabel: String,
    displayWeight: (Double) -> String,
    onWeightChange: (String) -> Unit,
    onRepsChange: (String) -> Unit,
    onLog: (Double, Int) -> Unit,
    onUndo: () -> Unit,
) {
    val setNumber = index + 1
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GridCell(Modifier.width(34.dp)) {
            Text(
                text = "$setNumber",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (existing != null) {
            GridCell(Modifier.weight(1f)) {
                Text(
                    text = displayWeight(existing.weightKg),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            GridCell(Modifier.weight(1f)) {
                Text(
                    text = "${existing.reps}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        } else {
            GridCell(Modifier.weight(1f)) {
                NumberCell(
                    value = weight,
                    onValueChange = onWeightChange,
                    label = "Set $setNumber weight in $unitLabel",
                )
            }
            GridCell(Modifier.weight(1f)) {
                NumberCell(
                    value = reps,
                    onValueChange = onRepsChange,
                    label = "Set $setNumber reps",
                )
            }
        }

        GridCell(Modifier.width(38.dp)) {
            val done = existing != null
            val enabled = done ||
                (weight.toDoubleOrNull() != null && reps.toIntOrNull() != null)
            Surface(
                color = if (done) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                },
                contentColor = if (done) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onPrimaryContainer
                },
                shape = IntakeShape.ExtraSmall,
                modifier = Modifier.size(34.dp),
                enabled = enabled,
                onClick = {
                    if (done) {
                        onUndo()
                    } else {
                        val w = weight.toDoubleOrNull()
                        val r = reps.toIntOrNull()
                        if (w != null && r != null) onLog(w, r)
                    }
                },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = if (done) {
                            "Undo set $setNumber"
                        } else {
                            "Complete set $setNumber"
                        },
                        modifier = Modifier.size(17.dp),
                    )
                }
            }
        }
    }
}

/** The mockup's `.entry`: a compact centred number field inside a grid cell. */
@Composable
private fun NumberCell(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = IntakeShape.ExtraSmall,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 3.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = { next -> onValueChange(next.filter { it.isDigit() || it == '.' }) },
            singleLine = true,
            textStyle = MaterialTheme.typography.titleSmall.copy(
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 11.dp)
                .semantics { contentDescription = label },
        )
    }
}

private fun fmt(value: Double): String =
    if (value % 1.0 == 0.0) {
        value.roundToInt().toString()
    } else {
        ((value * 10).roundToInt() / 10.0).toString()
    }
