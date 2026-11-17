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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Color
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
import com.zeroindex.intake.domain.training.Effort
import com.zeroindex.intake.domain.training.EffortBand
import com.zeroindex.intake.domain.training.EffortScale
import com.zeroindex.intake.domain.training.PrKind
import com.zeroindex.intake.domain.training.PreviousSets
import com.zeroindex.intake.domain.training.RepTarget
import com.zeroindex.intake.domain.training.SetResult
import com.zeroindex.intake.domain.training.SetRowText
import com.zeroindex.intake.domain.training.SetRows
import com.zeroindex.intake.domain.units.WeightText
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
    onLogSet: (setIndex: Int, weightKg: Double, reps: Int, isFailure: Boolean, rpe: Double?) -> Unit,
    onUndoSet: (String) -> Unit,
    onSetEffort: (setId: String, rpe: Double?) -> Unit,
    onShowPlates: (kg: Double, label: String) -> Unit,
    onAddSet: () -> Unit,
) {
    val unit = state.settings.weightUnit
    val exerciseId = item.exercise.id
    val prescription = item.prescription
    val logged = state.setsFor(exerciseId).filter { !it.isWarmup }
    val lastResults = remember(item.lastSession) { item.lastSession.map { SetResult(it.weightKg, it.reps, it.rpe) } }
    val effortScale = state.settings.effortScale
    val amrap = prescription != null && RepTarget.isAmrap(prescription.repsMin, prescription.repsMax)
    val bodyweight = item.exercise.isBodyweight

    val rows = SetRows.plan(prescription?.sets ?: 3, logged.map { it.setIndex }.toSet(), state.extraRows[exerciseId] ?: 0)
    val edits = remember(exerciseId) { mutableStateMapOf<Int, Pair<String, String>>() }
    var flashTick by remember(exerciseId) { mutableIntStateOf(0) }
    val flashed = remember(exerciseId) { mutableStateMapOf<Int, Int>() }
    // Effort picked on a row that is not logged yet; it is saved with the set.
    val pendingEffort = remember(exerciseId) { mutableStateMapOf<Int, Double>() }

    fun defaults(index: Int): Pair<String, String> {
        val prev = PreviousSets.forSet(lastResults, index)
        val kg = prescription?.weightKg?.takeIf { it > 0 } ?: prev?.weightKg?.takeIf { it > 0 }
        // A bodyweight set with nothing added starts blank and shows "BW" (see [SetRowText]), not "0".
        val weight = kg?.let { WeightText.setRow(it, unit) }.orEmpty()
        val reps = prescription?.takeUnless { amrap }?.repsMin?.toString() ?: prev?.reps?.toString().orEmpty()
        return weight to reps
    }

    fun valuesOf(index: Int): Pair<String, String> = edits[index] ?: defaults(index)

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            HeaderCell(Modifier.width(32.dp), "Set")
            HeaderCell(Modifier.weight(if (effortScale == EffortScale.OFF) 1.1f else 0.95f), "Previous")
            HeaderCell(Modifier.weight(1f), if (bodyweight) "+${unit.label}" else unit.label)
            HeaderCell(Modifier.weight(0.9f), "Reps")
            if (effortScale != EffortScale.OFF) HeaderCell(Modifier.weight(0.8f), effortScale.label)
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
                previous = previous?.let { SetRowText.previous(it.weightKg, it.reps, bodyweight, unit) },
                previousEffort = if (effortScale == EffortScale.OFF) null else previous?.rpe?.let { Effort.shortFor(it, effortScale) },
                effortScale = effortScale,
                effort = existing?.rpe ?: pendingEffort[row.index],
                onEffortChange = { next ->
                    if (existing != null) {
                        onSetEffort(existing.id, next)
                    } else if (next == null) {
                        pendingEffort.remove(row.index)
                    } else {
                        pendingEffort[row.index] = next
                    }
                },
                weight = weightText,
                reps = repsText,
                unitLabel = unit.label,
                weightPlaceholder = if (bodyweight) SetRowText.BODYWEIGHT else null,
                canLog = repsText.toIntOrNull() != null &&
                    (weightText.toDoubleOrNull() != null || (bodyweight && weightText.isBlank())),
                displayWeight = { kg -> SetRowText.load(kg, bodyweight, unit) },
                flash = flashed[row.index] ?: 0,
                onCopyPrevious = previous?.let { p ->
                    {
                        val copied = if (bodyweight && p.weightKg <= 0.0) "" else WeightText.setRow(p.weightKg, unit)
                        edits[row.index] = copied to p.reps.toString()
                    }
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
                            pendingEffort[row.index],
                        )
                        pendingEffort.remove(row.index)
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
                PlateChip(text, onClick = { onShowPlates(kg, "${WeightText.setRow(kg, unit)} ${unit.label}") })
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
    previousEffort: String?,
    effortScale: EffortScale,
    effort: Double?,
    onEffortChange: (Double?) -> Unit,
    weight: String,
    reps: String,
    unitLabel: String,
    weightPlaceholder: String?,
    canLog: Boolean,
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
                .weight(if (effortScale == EffortScale.OFF) 1.1f else 0.95f)
                .heightIn(min = 48.dp)
                .then(if (onCopyPrevious != null && !done) Modifier.clickable(role = Role.Button, onClickLabel = "Use last time's numbers", onClick = onCopyPrevious) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            // A record is named inside the row (under the Previous numbers), so the rows below never move.
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    previous ?: "–",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                if (!done && previousEffort != null) {
                    Text(previousEffort, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                if (done && record != PrKind.NONE) {
                    Text(record.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
            }
        }
        if (done && existing != null) {
            ShownCell(Modifier.weight(1f), displayWeight(existing.weightKg))
            ShownCell(Modifier.weight(0.9f), "${existing.reps}", trophy = record)
        } else {
            NumberCell(Modifier.weight(1f), weight, onWeightChange, "Set $number weight in $unitLabel", weightPlaceholder)
            NumberCell(Modifier.weight(0.9f), reps, onRepsChange, "Set $number reps")
        }
        if (effortScale != EffortScale.OFF) {
            EffortCell(Modifier.weight(0.8f), number, effortScale, effort, onEffortChange)
        }
        val enabled = done || canLog
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
private fun NumberCell(
    modifier: Modifier,
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String? = null,
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = IntakeShape.ExtraSmall,
        border = BorderStroke(if (focused) 2.dp else 1.dp, if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.padding(horizontal = 3.dp).height(48.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (value.isEmpty() && placeholder != null) {
                Text(
                    placeholder,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
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

/** The colour of an effort band: easy sits quiet, near failure is loud. The cell always carries the number as well. */
@Composable
internal fun effortColor(band: EffortBand): Color = when (band) {
    EffortBand.EASY -> MaterialTheme.colorScheme.surfaceVariant
    EffortBand.MODERATE -> MaterialTheme.colorScheme.secondaryContainer
    EffortBand.HARD -> MaterialTheme.colorScheme.primaryContainer
    EffortBand.VERY_HARD -> MaterialTheme.colorScheme.tertiaryContainer
    EffortBand.MAX -> MaterialTheme.colorScheme.errorContainer
}

/**
 * The effort of one set: a small cell that shows the rating as a number on a tint (RIR 2 or RPE 8) and opens a short list
 * of the five levels, each with a plain line such as "2 left in the tank". Tapping the chosen level again clears it.
 */
@Composable
private fun EffortCell(modifier: Modifier, setNumber: Int, scale: EffortScale, effort: Double?, onChange: (Double?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val description = if (effort == null) {
        "Set $setNumber effort, not rated. Opens the ${scale.label} choices."
    } else {
        "Set $setNumber effort, ${Effort.shortFor(effort, scale)}, ${Effort.line(effort)}. Opens the ${scale.label} choices."
    }
    Box(modifier.padding(horizontal = 3.dp), contentAlignment = Alignment.Center) {
        Surface(
            color = effort?.let { effortColor(Effort.band(it)) } ?: MaterialTheme.colorScheme.surface,
            shape = IntakeShape.ExtraSmall,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clickable(role = Role.Button, onClick = { open = true })
                .semantics { contentDescription = description },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    effort?.let { Effort.cellText(it, scale) } ?: "–",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (effort == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Effort.levels(scale).forEach { level ->
                val selected = effort == level.rpe
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(level.short, style = MaterialTheme.typography.titleSmall)
                            Text(level.line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    leadingIcon = {
                        Surface(color = effortColor(level.band), shape = IntakeShape.ExtraSmall, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.size(20.dp)) {}
                    },
                    onClick = {
                        open = false
                        onChange(if (selected) null else level.rpe)
                    },
                )
            }
            if (effort != null) {
                DropdownMenuItem(text = { Text("Clear") }, onClick = { open = false; onChange(null) })
            }
        }
    }
}
