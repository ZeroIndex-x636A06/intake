@file:OptIn(ExperimentalMaterial3Api::class)

package com.zeroindex.intake.ui.screens.train

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zeroindex.intake.data.training.ProgramExporter
import com.zeroindex.intake.domain.model.ProgressionType
import com.zeroindex.intake.domain.training.ProgressionLabels
import com.zeroindex.intake.domain.units.WeightUnit
import com.zeroindex.intake.domain.training.WorkoutSchedule
import com.zeroindex.intake.ui.components.ChoiceChip
import com.zeroindex.intake.ui.components.ExerciseThumb
import androidx.compose.material.icons.outlined.Info
import com.zeroindex.intake.ui.components.Eyebrow
import com.zeroindex.intake.domain.training.RepTarget
import com.zeroindex.intake.ui.components.Glossary
import com.zeroindex.intake.ui.components.InfoTip
import com.zeroindex.intake.ui.components.IntakeCard
import com.zeroindex.intake.ui.components.SandNote
import com.zeroindex.intake.ui.components.StatusPill
import com.zeroindex.intake.ui.components.SubHead
import com.zeroindex.intake.ui.screens.script.writeText
import com.zeroindex.intake.ui.theme.IntakeShape
import kotlinx.coroutines.launch

/**
 * The manual program builder: name it, add days, add exercises from the library, then set sets,
 * reps, effort, rest and progression in plain words. Everything saves as you go.
 */
@Composable
fun ProgramBuilderScreen(
    contentPadding: PaddingValues,
    onOpenScriptReference: (page: String, anchor: String?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProgramBuilderViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        val program = state.program
        if (uri != null && program != null) {
            scope.launch {
                val ok = viewModel.exportScript()?.let { writeText(context, uri, it.first) } ?: false
                viewModel.showMessage(if (ok) "Saved \"${program.name}\" as a script." else "That file could not be saved.")
            }
        }
    }
    var pickerForDay by remember { mutableStateOf<String?>(null) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var renameProgram by remember { mutableStateOf(false) }
    var infoFor by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 32.dp,
            start = 16.dp,
            end = 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val program = state.program
        item(key = "heading") {
            SubHead(
                title = program?.name ?: "New program",
                subcopy = when {
                    program == null -> "Give it a name, then add days and exercises."
                    program.isStandalone -> "A saved workout: add exercises and set sets and reps. Start it any time from Train."
                    else -> "Add days, then exercises. Everything saves as you go."
                },
            )
        }
        state.message?.let { m ->
            item(key = "message") {
                Column {
                    SandNote(m)
                    TextButton(onClick = viewModel::clearMessage) { Text("Dismiss") }
                }
            }
        }

        if (program == null) {
            if (state.loaded) item(key = "new") { NewProgramCard(onCreate = viewModel::createProgram) }
        } else {
            item(key = "status") {
                IntakeCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = program.description.ifBlank { "No notes." },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3,
                            )
                        }
                        if (program.isActive) StatusPill("Active")
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!program.isActive && !program.isStandalone) {
                            Button(
                                onClick = viewModel::activate,
                                enabled = state.days.any { it.rows.isNotEmpty() },
                                shape = IntakeShape.Button,
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            ) { Text("Make active") }
                        }
                        OutlinedButton(
                            onClick = { renameProgram = true },
                            shape = IntakeShape.Button,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        ) { Text("Name and notes") }
                    }
                }
            }

            if (state.days.isEmpty()) {
                item(key = "no-days") {
                    SandNote(
                        title = "No days yet",
                        body = "A day is one workout, like \"Push\" or \"Day 1\". Add your first one below.",
                    )
                }
            }
            items(items = state.days, key = { "day-${it.day.id}" }) { d ->
                DayCard(
                    day = d,
                    isFirst = d.day.id == state.days.first().day.id,
                    isLast = d.day.id == state.days.last().day.id,
                    viewModel = viewModel,
                    onAddExercise = { pickerForDay = d.day.id },
                    onEdit = { editingId = it },
                    onInfo = { infoFor = it },
                    standalone = program.isStandalone,
                    unit = state.weightUnit,
                )
            }
            if (!(state.program?.isStandalone ?: false)) {
                item(key = "add-day") { AddDayCard(nextNumber = state.days.size + 1, onAdd = viewModel::addDay) }
            }

            item(key = "tools") {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Eyebrow("Share or write by hand")
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        TextButton(onClick = { exporter.launch(ProgramExporter.fileName(program.name)) }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text("Export as script")
                        }
                        TextButton(onClick = { onOpenScriptReference("program", null) }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text("Script reference")
                        }
                    }
                }
            }
        }
    }

    infoFor?.let { id -> ExerciseDetailSheet(exerciseId = id, onDismiss = { infoFor = null }) }
    pickerForDay?.let { dayId ->
        ExercisePickerSheet(
            title = "Add to ${state.days.firstOrNull { it.day.id == dayId }?.day?.name ?: "this day"}",
            onPick = { exerciseId ->
                viewModel.addExercise(dayId, exerciseId)
                pickerForDay = null
            },
            onDismiss = { pickerForDay = null },
        )
    }
    editingId?.let { id ->
        val row = state.days.flatMap { it.rows }.firstOrNull { it.row.id == id }
        if (row == null) editingId = null else ExerciseEditSheet(
            item = row,
            unit = state.weightUnit,
            viewModel = viewModel,
            onDismiss = { editingId = null },
        )
    }
    if (renameProgram && state.program != null) {
        RenameProgramDialog(
            name = state.program!!.name,
            notes = state.program!!.description,
            onDismiss = { renameProgram = false },
            onSave = { n, d -> viewModel.rename(n, d); renameProgram = false },
        )
    }
}

@Composable
private fun NewProgramCard(onCreate: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    IntakeCard {
        OutlinedTextField(
            value = name, onValueChange = { name = it }, label = { Text("Program name") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = notes, onValueChange = { notes = it }, label = { Text("Notes (optional)") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { onCreate(name, notes) },
            enabled = name.isNotBlank(),
            shape = IntakeShape.Button,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) { Text("Create program") }
    }
}

@Composable
private fun RenameProgramDialog(name: String, notes: String, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var n by remember { mutableStateOf(name) }
    var d by remember { mutableStateOf(notes) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Name and notes") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = n, onValueChange = { n = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(value = d, onValueChange = { d = it }, label = { Text("Notes") })
            }
        },
        confirmButton = { TextButton(onClick = { onSave(n, d) }, enabled = n.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun AddDayCard(nextNumber: Int, onAdd: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    IntakeCard {
        Eyebrow("Add a day")
        Spacer(Modifier.height(6.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf("Push", "Pull", "Legs", "Upper", "Lower", "Full body").forEach { suggestion ->
                TrainChip(suggestion, selected = name == suggestion, onClick = { name = suggestion })
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Day $nextNumber name") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = { onAdd(name); name = "" },
                shape = IntakeShape.Button,
                modifier = Modifier.heightIn(min = 56.dp),
            ) { Text("Add") }
        }
    }
}

@Composable
private fun DayCard(
    day: BuilderDay,
    isFirst: Boolean,
    isLast: Boolean,
    viewModel: ProgramBuilderViewModel,
    onAddExercise: () -> Unit,
    onEdit: (String) -> Unit,
    onInfo: (String) -> Unit,
    standalone: Boolean,
    unit: WeightUnit,
) {
    var menu by remember { mutableStateOf(false) }
    var weekdayDialog by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    IntakeCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(day.day.name, style = MaterialTheme.typography.titleMedium)
                if (!standalone) {
                    Text(
                        text = day.day.weekday?.let { "Scheduled on ${WorkoutSchedule.weekdayName(it)}" } ?: "Next in the rotation",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (!standalone) {
                IconButton(onClick = { viewModel.moveDay(day.day.id, -1) }, enabled = !isFirst) {
                    Icon(Icons.Filled.ArrowUpward, contentDescription = "Move ${day.day.name} up")
                }
                IconButton(onClick = { viewModel.moveDay(day.day.id, 1) }, enabled = !isLast) {
                    Icon(Icons.Filled.ArrowDownward, contentDescription = "Move ${day.day.name} down")
                }
            }
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "More for ${day.day.name}")
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; renaming = true })
                    if (!standalone) {
                        DropdownMenuItem(text = { Text("Set weekday") }, onClick = { menu = false; weekdayDialog = true })
                        DropdownMenuItem(text = { Text("Copy to My workouts") }, onClick = { menu = false; viewModel.copyDayAsWorkout(day.day.id) })
                        DropdownMenuItem(text = { Text("Duplicate this day") }, onClick = { menu = false; viewModel.duplicateDay(day.day.id) })
                        DropdownMenuItem(text = { Text("Delete this day") }, onClick = { menu = false; confirmDelete = true })
                    }
                }
            }
        }
        if (day.rows.isEmpty()) {
            Text(
                "No exercises yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 6.dp),
            )
        }
        day.rows.forEachIndexed { index, item ->
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ExerciseSummaryRow(
                item = item,
                isFirst = index == 0,
                isLast = index == day.rows.lastIndex,
                unit = unit,
                onEdit = { onEdit(item.row.id) },
                onMove = { delta -> viewModel.moveExercise(item.row.id, delta) },
                onInfo = { item.exercise?.let { onInfo(it.id) } },
            )
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onAddExercise,
            shape = IntakeShape.Button,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text("Add exercise") }
    }
    if (weekdayDialog) {
        AlertDialog(
            onDismissRequest = { weekdayDialog = false },
            title = { Text("When is ${day.day.name}?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Today and the coach follow the weekdays you set. Leave it on the rotation to do days in order, whenever you train.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        ChoiceChip("Rotation", selected = day.day.weekday == null, onClick = { viewModel.setDayWeekday(day.day.id, null); weekdayDialog = false })
                        (1..7).forEach { d ->
                            ChoiceChip(
                                WorkoutSchedule.weekdayName(d, short = true), selected = day.day.weekday == d,
                                onClick = { viewModel.setDayWeekday(day.day.id, d); weekdayDialog = false },
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { weekdayDialog = false }) { Text("Close") } },
        )
    }
    if (renaming) {
        var text by remember { mutableStateOf(day.day.name) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Rename day") },
            text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { viewModel.renameDay(day.day.id, text); renaming = false }, enabled = text.isNotBlank()) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancel") } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${day.day.name}?") },
            text = { Text("The day leaves your program. Workouts you already logged are kept.") },
            confirmButton = { TextButton(onClick = { viewModel.deleteDay(day.day.id); confirmDelete = false }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } },
        )
    }
}

@Composable
private fun ExerciseSummaryRow(
    item: BuilderRow,
    isFirst: Boolean,
    isLast: Boolean,
    unit: WeightUnit,
    onEdit: () -> Unit,
    onMove: (Int) -> Unit,
    onInfo: () -> Unit,
) {
    val pe = item.row
    var menu by remember { mutableStateOf(false) }
    // The whole row opens the editor. The text has the row's full width: the less-used actions (about, move up, move
    // down) sit behind one menu, and the long "why this exercise" note is kept to two lines here.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onEdit)
            .padding(vertical = 10.dp),
    ) {
        item.exercise?.let { ExerciseThumb(it.id, it.photoPath, size = 52.dp) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.exercise?.name ?: "Removed exercise", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(
                text = summaryLine(pe.targetSets, pe.targetRepsMin, pe.targetRepsMax, pe.targetRpe, pe.restSec),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = item.rule?.let { ProgressionLabels.summary(it.type, it.params, unit) }
                    ?: "No progression: weight stays as you set it",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            pe.notes?.takeIf { it.isNotBlank() }?.let { note ->
                Text(
                    text = com.zeroindex.intake.domain.units.WeightText.inUnit(note, unit),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
        Box {
            IconButton(onClick = { menu = true }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.MoreVert, contentDescription = "More for ${item.exercise?.name ?: "this exercise"}")
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Edit sets, reps and progression") },
                    onClick = { menu = false; onEdit() },
                )
                DropdownMenuItem(
                    text = { Text("Photos, muscles and how to") },
                    leadingIcon = { Icon(Icons.Outlined.Info, contentDescription = null) },
                    onClick = { menu = false; onInfo() },
                )
                DropdownMenuItem(
                    text = { Text("Move up") },
                    leadingIcon = { Icon(Icons.Filled.ArrowUpward, contentDescription = null) },
                    enabled = !isFirst,
                    onClick = { menu = false; onMove(-1) },
                )
                DropdownMenuItem(
                    text = { Text("Move down") },
                    leadingIcon = { Icon(Icons.Filled.ArrowDownward, contentDescription = null) },
                    enabled = !isLast,
                    onClick = { menu = false; onMove(1) },
                )
            }
        }
    }
}

internal fun summaryLine(sets: Int, repsMin: Int, repsMax: Int, rpe: Double?, restSec: Int): String = buildString {
    append(sets).append(" × ").append(if (RepTarget.isAmrap(repsMin, repsMax)) "as many as you can" else RepTarget.label(repsMin, repsMax))
    if (rpe != null) append(" · effort ").append(weightText(rpe))
    append(" · rest ").append(restSec / 60).append(':').append((restSec % 60).toString().padStart(2, '0'))
}

/** Sets, reps range, effort, rest and progression for one exercise, plus "copy to the others". */
@Composable
private fun ExerciseEditSheet(
    item: BuilderRow,
    unit: WeightUnit,
    viewModel: ProgramBuilderViewModel,
    onDismiss: () -> Unit,
) {
    val pe = item.row
    val rule = item.rule
    var sets by remember { mutableStateOf(pe.targetSets.toString()) }
    var repsMin by remember { mutableStateOf(pe.targetRepsMin.toString()) }
    var repsMax by remember { mutableStateOf(pe.targetRepsMax.toString()) }
    var effort by remember { mutableStateOf(pe.targetRpe?.let { weightText(it) }.orEmpty()) }
    var rest by remember { mutableStateOf(pe.restSec.toString()) }
    var type by remember { mutableStateOf(rule?.type) }
    var advanced by remember { mutableStateOf(rule?.type in ProgressionLabels.ADVANCED) }
    var jump by remember {
        mutableStateOf(rule?.params?.let { ProgressionLabels.incrementOf(it) }?.let { weightText(unit.fromKg(it)) }.orEmpty())
    }
    var percent by remember { mutableStateOf(rule?.params?.let { ProgressionLabels.percentOf(it) }?.let { weightText(it) } ?: "75") }
    var formula by remember { mutableStateOf(rule?.params?.let { ProgressionLabels.expressionOf(it) }.orEmpty()) }
    var formulaError by remember { mutableStateOf<String?>(null) }
    var copyScope by remember { mutableStateOf(CopyScope.None) }
    var confirmRemove by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(item.exercise?.name ?: "Exercise", style = MaterialTheme.typography.headlineSmall)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(sets, { sets = it }, "Sets", Modifier.weight(1f))
                NumberField(repsMin, { repsMin = it }, "Reps from", Modifier.weight(1f))
                NumberField(repsMax, { repsMax = it }, "Reps to", Modifier.weight(1f))
            }
            if (RepTarget.isAmrap(repsMin.toIntOrNull() ?: 0, repsMax.toIntOrNull() ?: 0)) {
                // 1 to 99 is how "as many as you can" is stored.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(RepTarget.AMRAP_LABEL, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                    InfoTip(Glossary.AS_MANY_AS_YOU_CAN, label = RepTarget.AMRAP_LABEL)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Effort 1–10, optional", style = MaterialTheme.typography.labelLarge)
                InfoTip(Glossary.EFFORT)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(effort, { effort = it }, "Effort", Modifier.weight(1f), decimal = true)
                NumberField(rest, { rest = it }, "Rest (seconds)", Modifier.weight(1f))
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Eyebrow("How the weight moves")
            ProgressionChoiceRow("Keep the weight as I set it", selected = type == null, explanation = null) { type = null }
            ProgressionLabels.SIMPLE.forEach { t ->
                ProgressionChoiceRow(
                    label = ProgressionLabels.title(t),
                    selected = type == t,
                    explanation = if (type == t) ProgressionLabels.explanation(t) else null,
                ) {
                    type = t
                    if (t == ProgressionType.RPE_BASED && effort.isBlank()) effort = "8"
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Advanced", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Switch(checked = advanced, onCheckedChange = { advanced = it })
            }
            if (advanced) {
                ProgressionLabels.ADVANCED.forEach { t ->
                    ProgressionChoiceRow(
                        label = ProgressionLabels.title(t),
                        selected = type == t,
                        explanation = if (type == t) ProgressionLabels.explanation(t) else null,
                    ) { type = t }
                }
            }
            when (type) {
                ProgressionType.LINEAR, ProgressionType.DOUBLE_PROGRESSION ->
                    NumberField(jump, { jump = it }, "Weight jump (${unit.label}), blank for the usual", Modifier.fillMaxWidth(), decimal = true)
                ProgressionType.PERCENTAGE_1RM ->
                    NumberField(percent, { percent = it }, "Percent of your estimated max", Modifier.fillMaxWidth(), decimal = true)
                ProgressionType.CUSTOM -> {
                    OutlinedTextField(
                        value = formula,
                        onValueChange = { formula = it; formulaError = null },
                        label = { Text("Formula") },
                        placeholder = { Text("if (reps >= 8) weight + 2.5 else weight") },
                        isError = formulaError != null,
                        supportingText = { Text(formulaError ?: "You can use weight, reps, sets, rpe, failureCount and week. Also min, max and round.") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedButton(onClick = { formulaError = viewModel.checkFormula(formula) ?: "That reads fine." }) { Text("Check it") }
                }
                else -> Unit
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Eyebrow("Use these settings for other exercises")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                TrainChip("Just this one", copyScope == CopyScope.None) { copyScope = CopyScope.None }
                TrainChip("Others on this day", copyScope == CopyScope.Day) { copyScope = CopyScope.Day }
                TrainChip("Whole program", copyScope == CopyScope.Program) { copyScope = CopyScope.Program }
            }

            Button(
                onClick = {
                    if (type == ProgressionType.CUSTOM) {
                        val err = viewModel.checkFormula(formula)
                        if (err != null) { formulaError = err; return@Button }
                    }
                    val choice = type?.let {
                        RuleChoice(
                            type = it,
                            incrementKg = if (it == ProgressionType.LINEAR || it == ProgressionType.DOUBLE_PROGRESSION) {
                                jump.toDoubleOrNull()?.takeIf { v -> v > 0 }?.let(unit::toKg)
                            } else null,
                            percentOfMax = if (it == ProgressionType.PERCENTAGE_1RM) percent.toDoubleOrNull() else null,
                            expression = if (it == ProgressionType.CUSTOM) formula.trim() else null,
                        )
                    }
                    val needsEffort = type == ProgressionType.RPE_BASED
                    viewModel.saveExercise(
                        row = pe.copy(
                            targetSets = sets.toIntOrNull() ?: pe.targetSets,
                            targetRepsMin = repsMin.toIntOrNull() ?: pe.targetRepsMin,
                            targetRepsMax = repsMax.toIntOrNull() ?: pe.targetRepsMax,
                            targetRpe = effort.toDoubleOrNull()?.coerceIn(1.0, 10.0) ?: if (needsEffort) 8.0 else null,
                            restSec = rest.toIntOrNull() ?: pe.restSec,
                        ),
                        rule = choice,
                        copyTo = copyScope,
                    )
                    onDismiss()
                },
                shape = IntakeShape.Button,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) { Text("Save") }
            TextButton(onClick = { confirmRemove = true }, modifier = Modifier.fillMaxWidth()) { Text("Remove from this day") }
        }
    }
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove ${item.exercise?.name ?: "this exercise"}?") },
            text = { Text("It leaves this day. The exercise and your past sets are kept.") },
            confirmButton = { TextButton(onClick = { viewModel.removeExercise(pe.id); confirmRemove = false; onDismiss() }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Keep") } },
        )
    }
}

@Composable
private fun ProgressionChoiceRow(label: String, selected: Boolean, explanation: String?, onClick: () -> Unit) {
    Column {
        TrainChip(label, selected = selected, onClick = onClick)
        if (explanation != null) {
            Text(
                explanation,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp, top = 4.dp),
            )
        }
    }
}

@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    decimal: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { next -> onValueChange(next.filter { it.isDigit() || (decimal && it == '.') }.take(6)) },
        label = { Text(label, maxLines = 1) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier,
    )
}
