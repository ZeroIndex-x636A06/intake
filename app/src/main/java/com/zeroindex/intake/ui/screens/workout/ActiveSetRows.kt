package com.zeroindex.intake.ui.screens.workout

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zeroindex.intake.data.db.entity.SetLogEntity
import com.zeroindex.intake.data.repository.ExercisePrescription
import com.zeroindex.intake.domain.training.PlateLine
import com.zeroindex.intake.domain.training.PrKind
import com.zeroindex.intake.domain.training.PreviousSets
import com.zeroindex.intake.domain.training.RepTarget
import com.zeroindex.intake.domain.training.SetResult
import com.zeroindex.intake.domain.training.SetRows
import com.zeroindex.intake.ui.components.Eyebrow
import com.zeroindex.intake.ui.motion.AnimatedCheck
import com.zeroindex.intake.ui.motion.flashOn
import com.zeroindex.intake.ui.theme.IntakeShape

/**
 * The mockup's set grid: Set, Previous, weight, reps and a tick. Every planned set is visible and pre-filled from the
 * prescription (or last time), so a session that goes to plan is one tap per set. Tapping the Previous cell copies it into
 * the row. Ticking logs the set, plays the check, buzzes, and starts the rest timer (the caller's [onLogSet]). A set that is
 * a record wears a small trophy and says which kind; it never blocks logging.
 */
@Composable
internal fun SetRowsBlock(
    item: ExercisePrescription,
    state: ActiveWorkoutUiState,
    onLogSet: (setIndex: Int, weightKg: Double, reps: Int, isFailure: Boolean) -> Unit,
    onUndoSet: (String) -> Unit,
    onShowPlates: (kg: Double, label: String) -> Unit,
    onAddSet: () -> Unit,
) {
    val unit = state.settings.weightUnit
    val exerciseId = item.exercise.id
    val prescription = item.prescription
    val logged = state.setsFor(exerciseId).filter { !it.isWarmup }
    val lastResults = remember(item.lastSession) { item.lastSession.map { SetResult(it.weightKg, it.reps) } }
    val amrap = prescription != null && RepTarget.isAmrap(prescription.repsMin, prescription.repsMax)
    val bodyweight = item.exercise.isBodyweight

    val rows = SetRows.plan(prescription?.sets ?: 3, logged.map { it.setIndex }.toSet(), state.extraRows[exerciseId] ?: 0)
    val edits = remember(exerciseId) { mutableStateMapOf<Int, Pair<String, String>>() }
    var flashTick by remember(exerciseId) { mutableIntStateOf(0) }
    val flashed = remember(exerciseId) { mutableStateMapOf<Int, Int>() }

    fun defaults(index: Int): Pair<String, String> {
        val prev = PreviousSets.forSet(lastResults, index)
        val kg = prescription?.weightKg?.takeIf { it > 0 } ?: prev?.weightKg?.takeIf { it > 0 }
        val weight = kg?.let { fmt(unit.fromKg(it)) } ?: if (bodyweight) "0" else ""
        val reps = prescription?.takeUnless { amrap }?.repsMin?.toString() ?: prev?.reps?.toString().orEmpty()
        return weight to reps
    }

    fun valuesOf(index: Int): Pair<String, String> = edits[index] ?: defaults(index)

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            HeaderCell(Modifier.width(32.dp), "Set")
            HeaderCell(Modifier.weight(1.1f), "Previous")
            HeaderCell(Modifier.weight(1f), unit.label)
            HeaderCell(Modifier.weight(0.9f), "Reps")
            Spacer(Modifier.width(52.dp))
        }
        rows.forEach { row ->
            val existing = logged.firstOrNull { it.setIndex == row.index }
            val (weightText, repsText) = valuesOf(row.index)
            val previous = PreviousSets.forSet(lastResults, row.index)
            SetLine(
                number = row.index + 1,
                existing = existing,
                record = existing?.let { state.records[it.id] } ?: PrKind.NONE,
                previous = previous?.let { "${fmt(unit.fromKg(it.weightKg))} × ${it.reps}" },
                weight = weightText,
                reps = repsText,
                unitLabel = unit.label,
                displayWeight = { kg -> fmt(unit.fromKg(kg)) },
                flash = flashed[row.index] ?: 0,
                onCopyPrevious = previous?.let { p ->
                    { edits[row.index] = fmt(unit.fromKg(p.weightKg)) to p.reps.toString() }
                },
                onWeightChange = { next -> edits[row.index] = next to repsText },
                onRepsChange = { next -> edits[row.index] = weightText to next },
                onLog = {
                    val w = (weightText.toDoubleOrNull() ?: if (bodyweight && weightText.isBlank()) 0.0 else null)
                    val r = repsText.toIntOrNull()
                    if (w != null && r != null) {
                        onLogSet(
                            row.index,
                            unit.toKg(w),
                            r,
                            prescription != null && r < prescription.repsMin && !amrap,
                        )
                        // A record flashes the row once; the trophy itself comes from the saved set.
                        val prior = lastResults + logged.filter { it.setIndex != row.index }.map { SetResult(it.weightKg, it.reps) } +
                            (state.history[exerciseId].orEmpty())
                        if (com.zeroindex.intake.domain.training.PrDetector.check(unit.toKg(w), r, prior) != PrKind.NONE) {
                            flashTick += 1
                            flashed[row.index] = flashTick
                        }
                    }
                },
                onUndo = { existing?.let { onUndoSet(it.id) } },
            )
        }

        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // Plate maths for barbell lifts: the first set not yet done, in the shown unit.
            val nextOpen = rows.firstOrNull { r -> logged.none { it.setIndex == r.index } }
            val kg = nextOpen?.let { unit.toKg(valuesOf(it.index).first.toDoubleOrNull() ?: 0.0) } ?: 0.0
            if (item.exercise.equipment.equals("barbell", ignoreCase = true) && kg > 0) {
                val load = state.plateLoad(kg)
                val text = PlateLine.text(load.perSide) { unit.fromKg(it) } + if (!load.isExact && load.perSide.isNotEmpty()) " (closest)" else ""
                PlateChip(text, onClick = { onShowPlates(kg, "${fmt(unit.fromKg(kg))} ${unit.label}") })
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onAddSet, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add set")
            }
        }
    }
}

@Composable
private fun HeaderCell(modifier: Modifier, text: String) {
    Box(modifier, contentAlignment = Alignment.Center) { Eyebrow(text) }
}

/** The mockup's amber plate pill: "Per side: 45 + 25 + 2.5". Tapping it opens the plate picture. */
@Composable
private fun PlateChip(text: String, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = IntakeShape.Banner,
        modifier = Modifier.heightIn(min = 44.dp).clickable(role = Role.Button, onClickLabel = "Show the plates", onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.FitnessCenter, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun SetLine(
    number: Int,
    existing: SetLogEntity?,
    record: PrKind,
    previous: String?,
    weight: String,
    reps: String,
    unitLabel: String,
    displayWeight: (Double) -> String,
    flash: Int,
    onCopyPrevious: (() -> Unit)?,
    onWeightChange: (String) -> Unit,
    onRepsChange: (String) -> Unit,
    onLog: () -> Unit,
    onUndo: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val done = existing != null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .flashOn(flash, MaterialTheme.colorScheme.tertiaryContainer),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(32.dp), contentAlignment = Alignment.Center) {
            Text("$number", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        }
        Box(
            Modifier
                .weight(1.1f)
                .heightIn(min = 48.dp)
                .then(if (onCopyPrevious != null && !done) Modifier.clickable(role = Role.Button, onClickLabel = "Use last time's numbers", onClick = onCopyPrevious) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                previous ?: "–",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (done && existing != null) {
            ShownCell(Modifier.weight(1f), displayWeight(existing.weightKg))
            ShownCell(Modifier.weight(0.9f), "${existing.reps}", trophy = record)
        } else {
            NumberCell(Modifier.weight(1f), weight, onWeightChange, "Set $number weight in $unitLabel")
            NumberCell(Modifier.weight(0.9f), reps, onRepsChange, "Set $number reps")
        }
        val enabled = done || (reps.toIntOrNull() != null && weight.toDoubleOrNull() != null)
        Box(
            modifier = Modifier
                .size(52.dp)
                .alpha(if (enabled) 1f else 0.4f)
                .toggleable(
                    value = done,
                    enabled = enabled,
                    role = Role.Checkbox,
                    onValueChange = {
                        if (done) {
                            haptics.performHapticFeedback(HapticFeedbackType.ToggleOff)
                            onUndo()
                        } else {
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            onLog()
                        }
                    },
                )
                .semantics { contentDescription = "Set $number done" },
            contentAlignment = Alignment.Center,
        ) {
            AnimatedCheck(
                checked = done,
                size = 32.dp,
                fill = MaterialTheme.colorScheme.primary,
                outline = MaterialTheme.colorScheme.onSurfaceVariant,
                tick = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
    if (done && record != PrKind.NONE) {
        Row(Modifier.fillMaxWidth().padding(start = 32.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(record.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
        }
    }
}

@Composable
private fun ShownCell(modifier: Modifier, text: String, trophy: PrKind = PrKind.NONE) {
    Box(modifier.padding(horizontal = 3.dp), contentAlignment = Alignment.Center) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = IntakeShape.ExtraSmall,
            modifier = Modifier.fillMaxWidth().height(44.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                if (trophy != PrKind.NONE) {
                    Icon(Icons.Filled.EmojiEvents, contentDescription = trophy.label, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(3.dp))
                }
                Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

/** A compact centred number field; the border lights up while it has focus. */
@Composable
private fun NumberCell(modifier: Modifier, value: String, onValueChange: (String) -> Unit, label: String) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = IntakeShape.ExtraSmall,
        border = BorderStroke(if (focused) 2.dp else 1.dp, if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.padding(horizontal = 3.dp).height(48.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            BasicTextField(
                value = value,
                onValueChange = { next -> onValueChange(next.filter { it.isDigit() || it == '.' }.take(7)) },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .onFocusChanged { focused = it.isFocused }
                    .semantics { contentDescription = label },
            )
        }
    }
}
