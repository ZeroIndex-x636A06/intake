package com.zeroindex.intake.ui.screens.workout

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zeroindex.intake.data.repository.ExercisePrescription
import com.zeroindex.intake.domain.training.OneRepMax
import com.zeroindex.intake.domain.training.PlateCalculator
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
            top = contentPadding.calculateTopPadding() + 12.dp,
            bottom = contentPadding.calculateBottomPadding() + 32.dp,
            start = 16.dp,
            end = 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (timer.running || timer.remainingSeconds > 0) {
            item(key = "timer") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = timer.label.ifBlank { "Rest" },
                                style = MaterialTheme.typography.labelLarge,
                            )
                            Text(
                                text = TimerService.format(timer.remainingSeconds),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        OutlinedButton(onClick = { TimerService.stop(context) }) { Text("Stop") }
                    }
                }
            }
        }

        if (state.prescriptions.isEmpty()) {
            item(key = "freestyle") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f),
                    ),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Freestyle session", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "No program day attached, so there are no prescriptions. " +
                                "Finish when you are done and the sets still count towards " +
                                "your weekly volume.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else {
            item(key = "picker") {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.prescriptions.forEachIndexed { index, item ->
                        val done = state.setsFor(item.exercise.id).count { !it.isWarmup }
                        FilterChip(
                            selected = index == state.currentIndex,
                            onClick = { viewModel.selectExercise(index) },
                            label = {
                                Text("${item.exercise.name} ($done/${item.prescription?.sets ?: 0})")
                            },
                        )
                    }
                }
            }

            state.current?.let { current ->
                item(key = "current-${current.exercise.id}") {
                    ExerciseCard(
                        item = current,
                        state = state,
                        onLogSet = { setIndex, weight, reps, rpe, isWarmup, isFailure ->
                            viewModel.logSet(
                                exerciseId = current.exercise.id,
                                setIndex = setIndex,
                                weightKg = unit.toKg(weight),
                                reps = reps,
                                rpe = rpe,
                                isWarmup = isWarmup,
                                isFailure = isFailure,
                                restTakenSec = null,
                            )
                            // SPEC §10: rest starts on set completion.
                            val rest = current.programExercise?.restSec
                                ?: current.exercise.defaultRestSec
                            TimerService.start(
                                context = context,
                                seconds = rest,
                                label = "Rest · ${current.exercise.name}",
                                vibrate = state.settings.restTimerVibrate,
                            )
                        },
                        onDeleteSet = viewModel::deleteSet,
                    )
                }

                item(key = "nav") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
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
                            modifier = Modifier.weight(1f),
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
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text("Finish session") }
        }
    }
}

@Composable
private fun ExerciseCard(
    item: ExercisePrescription,
    state: ActiveWorkoutUiState,
    onLogSet: (Int, Double, Int, Double?, Boolean, Boolean) -> Unit,
    onDeleteSet: (String) -> Unit,
) {
    val unit = state.settings.weightUnit
    val prescription = item.prescription
    val logged = state.setsFor(item.exercise.id)
    val working = logged.filter { !it.isWarmup }

    // SPEC §10: the grid is pre-filled with the prescribed values, so logging a
    // set that went to plan is one tap.
    var weightText by remember(item.exercise.id, prescription?.weightKg) {
        mutableStateOf(
            prescription?.weightKg
                ?.takeIf { it > 0 }
                ?.let { unit.fromKg(it) }
                ?.let { fmt(it) }
                ?: "",
        )
    }
    var repsText by remember(item.exercise.id, prescription?.repsMin) {
        mutableStateOf(prescription?.repsMin?.toString() ?: "")
    }
    var rpeText by remember(item.exercise.id) { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f),
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(item.exercise.name, style = MaterialTheme.typography.titleLarge)
            Text(
                text = item.exercise.primaryMuscle.replaceFirstChar { it.uppercase() } +
                    if (item.exercise.equipment.isNotBlank()) " · ${item.exercise.equipment}" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(10.dp))

            if (item.ruleError != null) {
                // SPEC §10: a malformed rule shows a validation error, and the
                // session carries on regardless.
                Text(
                    text = "That exercise's progression rule could not run: ${item.ruleError} " +
                        "Log by hand and fix the rule in the builder.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            } else if (prescription != null) {
                Text(
                    text = if (prescription.weightKg > 0) {
                        "${fmt(unit.fromKg(prescription.weightKg))} ${unit.label} · " +
                            "${prescription.sets} x ${prescription.repsMin}" +
                            if (prescription.repsMax > prescription.repsMin) {
                                "-${prescription.repsMax}"
                            } else {
                                ""
                            }
                    } else {
                        "${prescription.sets} x ${prescription.repsMin}"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                // SPEC §10: the reason always sits next to the number.
                Text(
                    text = prescription.reason,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )

                if (prescription.weightKg > 0) {
                    val load = state.plateLoad(prescription.weightKg)
                    Text(
                        text = "Plates per side: ${PlateCalculator.describe(load)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    val warmups = state.warmups(prescription.weightKg)
                    if (warmups.isNotEmpty()) {
                        Text(
                            text = "Warmup: " + warmups.joinToString(", ") {
                                "${fmt(unit.fromKg(it.weightKg))} x ${it.reps}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // SPEC §10: last session's numbers inline, for comparison.
            if (item.lastSession.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Last time: " + item.lastSession.joinToString(", ") {
                        "${fmt(unit.fromKg(it.weightKg))} x ${it.reps}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OneRepMax.best(
                    item.lastSession.map {
                        com.zeroindex.intake.domain.training.SetResult(it.weightKg, it.reps)
                    },
                )?.let { best ->
                    Text(
                        text = "Estimated 1RM then: ${fmt(unit.fromKg(best))} ${unit.label} " +
                            "(${OneRepMax.FORMULA})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            if (working.isNotEmpty()) {
                working.forEach { set ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Set ${set.setIndex + 1}: " +
                                "${fmt(unit.fromKg(set.weightKg))} ${unit.label} x ${set.reps}" +
                                (set.rpe?.let { " @ RPE ${fmt(it)}" } ?: ""),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedButton(onClick = { onDeleteSet(set.id) }) { Text("Undo") }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Num(weightText, { weightText = it }, unit.label, Modifier.weight(1f))
                Num(repsText, { repsText = it }, "reps", Modifier.weight(1f))
                Num(rpeText, { rpeText = it }, "RPE", Modifier.weight(1f))
            }

            Spacer(Modifier.height(10.dp))
            Button(
                onClick = {
                    val weight = weightText.toDoubleOrNull() ?: return@Button
                    val reps = repsText.toIntOrNull() ?: return@Button
                    onLogSet(
                        working.size,
                        weight,
                        reps,
                        rpeText.toDoubleOrNull(),
                        false,
                        prescription != null && reps < prescription.repsMin,
                    )
                },
                enabled = weightText.toDoubleOrNull() != null && repsText.toIntOrNull() != null,
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) { Text("Log set ${working.size + 1}") }
        }
    }
}

@Composable
private fun Num(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { next -> onValueChange(next.filter { it.isDigit() || it == '.' }) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

private fun fmt(value: Double): String =
    if (value % 1.0 == 0.0) value.roundToInt().toString() else ((value * 10).roundToInt() / 10.0).toString()
