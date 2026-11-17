package com.zeroindex.intake.ui.screens.workout

import com.zeroindex.intake.domain.time.DateText
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import com.zeroindex.intake.domain.training.SetEdit
import com.zeroindex.intake.domain.training.history.ReopenOutcome
import com.zeroindex.intake.ui.screens.train.ExercisePickerSheet
import com.zeroindex.intake.ui.screens.train.ExerciseDetailSheet
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zeroindex.intake.domain.training.NextTimeChange
import com.zeroindex.intake.domain.training.PrKind
import com.zeroindex.intake.domain.training.RecordLine
import com.zeroindex.intake.domain.training.RegionVolume
import com.zeroindex.intake.domain.training.SessionSummary
import com.zeroindex.intake.domain.training.TrainText
import com.zeroindex.intake.domain.training.WeeklyVolume
import com.zeroindex.intake.domain.units.WeightUnit
import com.zeroindex.intake.ui.components.EmptyState
import com.zeroindex.intake.ui.components.IntakeCard
import com.zeroindex.intake.ui.components.MuscleMapView
import com.zeroindex.intake.ui.components.Tag
import com.zeroindex.intake.ui.theme.IntakeShape

/**
 * The post-workout summary (the `a-summary` frame): new records, the four totals, muscles worked against this week's
 * targets with the body map, the exercise list, what changes next time, and (for an empty workout) "Keep this workout?".
 */
@Composable
fun WorkoutSummaryScreen(
    contentPadding: PaddingValues,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    onRepeat: (sessionId: String) -> Unit = {},
    /** Undo Finish worked: open the session as the active workout again. */
    onReopened: (sessionId: String) -> Unit = {},
    viewModel: WorkoutSummaryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<SummarySetRow?>(null) }
    var detailFor by remember { mutableStateOf<String?>(null) }
    var editingTime by remember { mutableStateOf(false) }
    var addingSetFor by remember { mutableStateOf<Pair<String, String>?>(null) }
    var pickingExercise by remember { mutableStateOf(false) }
    var changingExerciseOf by remember { mutableStateOf<String?>(null) }
    var reopenConflict by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var keepName by remember { mutableStateOf("Workout " + DateText.medium(java.time.LocalDate.now())) }

    Column(modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
        when {
            state.loading -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.summary == null -> Box(Modifier.fillMaxWidth().weight(1f).padding(16.dp)) {
                EmptyState(
                    title = "This workout is not here any more",
                    body = "It may have been discarded. Your other sessions are in Train > Recent sessions.",
                )
            }
            else -> {
                val summary = state.summary!!
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item(key = "head") {
                        Column {
                            Text(state.title, style = MaterialTheme.typography.headlineSmall)
                            val started = state.session?.startedAt
                            if (started != null) {
                                Text(
                                    java.time.format.DateTimeFormatter.ofPattern("EEEE d MMMM, HH:mm")
                                        .format(java.time.Instant.ofEpochMilli(started).atZone(java.time.ZoneId.systemDefault())),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    if (summary.records.isNotEmpty()) {
                        item(key = "records") { RecordsCard(summary.records, state.unit) }
                    }
                    item(key = "stats") { StatsRow(summary, state.unit) }
                    if (summary.regionSets.isNotEmpty()) {
                        item(key = "muscles") { MusclesCard(summary, state.week, state.hint) }
                    }
                    item(key = "exercises-head") { Text("Exercises", style = MaterialTheme.typography.titleLarge) }
                    item(key = "exercises") {
                        if (state.exercises.isEmpty()) {
                            EmptyState(body = "No working sets were logged in this workout.")
                        } else {
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = IntakeShape.Card,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column {
                                    state.exercises.forEachIndexed { i, e ->
                                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Column(Modifier.weight(1f)) {
                                                Text(
                                                    e.name,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    modifier = Modifier
                                                        .heightIn(min = 48.dp)
                                                        .clickable(role = Role.Button, onClickLabel = "Open ${e.name}") { detailFor = e.exerciseId }
                                                        .wrapContentHeight(Alignment.CenterVertically),
                                                )
                                                Text(e.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                e.setRows.forEach { row ->
                                                    Text(
                                                        row.label,
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .heightIn(min = 48.dp)
                                                            .clickable(role = Role.Button, onClickLabel = "Change this set") { editing = row }
                                                            .wrapContentHeight(Alignment.CenterVertically),
                                                    )
                                                }
                                                if (state.session?.finishedAt != null) {
                                                    TextButton(onClick = { addingSetFor = e.exerciseId to e.name }, modifier = Modifier.heightIn(min = 48.dp)) {
                                                        Text("Add a set")
                                                    }
                                                }
                                            }
                                            if (e.records.isNotEmpty()) Tag("PR")
                                        }
                                        if (i != state.exercises.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    }
                                }
                            }
                        }
                    }
                    if (state.session != null) {
                        item(key = "edit") {
                            IntakeCard {
                                Text("Edit this workout", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Fix the date, time or length, add a set you forgot, or change a set's exercise by tapping the set.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (state.session?.finishedAt != null) {
                                        OutlinedButton(
                                            onClick = { editingTime = true },
                                            shape = IntakeShape.Button,
                                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                        ) { Text("Change date, time and length") }
                                        OutlinedButton(
                                            onClick = { pickingExercise = true },
                                            shape = IntakeShape.Button,
                                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                        ) { Text("Add an exercise") }
                                    }
                                    if (state.reopen == ReopenOutcome.Allowed || state.reopen is ReopenOutcome.AnotherActive) {
                                        OutlinedButton(
                                            onClick = {
                                                viewModel.reopen(false, onReopened) { reopenConflict = true }
                                            },
                                            shape = IntakeShape.Button,
                                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                        ) { Text("Reopen this workout") }
                                    }
                                    if (state.canRepeat) {
                                        OutlinedButton(
                                            onClick = {
                                                viewModel.repeat { id ->
                                                    if (id != null) onRepeat(id) else scope.launch {
                                                        snackbar.showSnackbar("Another workout is running. Finish it first, then repeat this one.")
                                                    }
                                                }
                                            },
                                            shape = IntakeShape.Button,
                                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                                        ) { Text("Repeat this workout") }
                                    }
                                }
                            }
                        }
                    }
                    if (state.nextTime.isNotEmpty()) {
                        item(key = "next") { NextTimeCard(state.nextTime, state.unit) }
                    }
                    if (state.canKeep) {
                        item(key = "keep") {
                            IntakeCard {
                                Text("Keep this workout?", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Save it to repeat it: as a workout you can start any time, or as a day of your program. Optional.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
                                )
                                OutlinedTextField(
                                    value = keepName,
                                    onValueChange = { keepName = it },
                                    label = { Text("Name") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = { viewModel.keepAsWorkout(keepName.trim()) },
                                        enabled = keepName.isNotBlank(),
                                        shape = IntakeShape.Button,
                                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                    ) { Text("Save as workout") }
                                    OutlinedButton(
                                        onClick = { viewModel.keepAsProgramDay(keepName.trim()) },
                                        enabled = keepName.isNotBlank(),
                                        shape = IntakeShape.Button,
                                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                    ) { Text("Add to program") }
                                }
                            }
                        }
                    }
                    state.kept?.let { message ->
                        item(key = "kept") {
                            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = IntakeShape.Banner, modifier = Modifier.fillMaxWidth()) {
                                Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(14.dp))
                            }
                        }
                    }
                    item(key = "notes") {
                        OutlinedTextField(
                            value = state.notes,
                            onValueChange = viewModel::setNotes,
                            label = { Text("Notes (optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                        )
                    }
                }
            }
        }
        Surface(color = MaterialTheme.colorScheme.background, tonalElevation = 0.dp, modifier = Modifier.fillMaxWidth()) {
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Button(
                    onClick = { viewModel.saveNotes(onDone) },
                    shape = IntakeShape.Button,
                    // The bar is the last thing on the page, so it keeps clear of the system navigation bar itself.
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp + contentPadding.calculateBottomPadding())
                        .height(56.dp),
                ) { Text("Done") }
            }
        }
        SnackbarHost(snackbar, modifier = Modifier.fillMaxWidth())
    }

    detailFor?.let { id -> ExerciseDetailSheet(exerciseId = id, onDismiss = { detailFor = null }) }
    if (editingTime) {
        val session = state.session
        val finished = session?.finishedAt
        if (session != null && finished != null) {
            EditTimeDialog(
                startedAt = session.startedAt,
                finishedAt = finished,
                today = java.time.LocalDate.now(),
                problemWith = viewModel::timeProblem,
                onSave = { date, time, minutes -> editingTime = false; viewModel.retime(date, time, minutes) },
                onDismiss = { editingTime = false },
            )
        }
    }
    if (pickingExercise) {
        ExercisePickerSheet(
            title = "Add an exercise",
            onPick = { id -> pickingExercise = false; viewModel.askAddSet(id) { name -> addingSetFor = id to name } },
            onDismiss = { pickingExercise = false },
        )
    }
    changingExerciseOf?.let { setId ->
        ExercisePickerSheet(
            title = "Change the exercise",
            onPick = { id -> changingExerciseOf = null; viewModel.changeExercise(setId, id) },
            onDismiss = { changingExerciseOf = null },
        )
    }
    addingSetFor?.let { (exerciseId, name) ->
        val last = state.exercises.firstOrNull { it.exerciseId == exerciseId }?.setRows?.lastOrNull { !it.isWarmup }
        AddSetDialog(
            exerciseName = name,
            unit = state.unit,
            initialWeightKg = last?.weightKg ?: 0.0,
            initialReps = last?.reps,
            onSave = { weight, reps -> viewModel.addSet(exerciseId, weight, reps); addingSetFor = null },
            onDismiss = { addingSetFor = null },
        )
    }
    if (reopenConflict) {
        AlertDialog(
            onDismissRequest = { reopenConflict = false },
            title = { Text("Another workout is running") },
            text = {
                Text(
                    "Only one workout can be running at a time. Finish the other one first, then this one reopens. " +
                        "If the other workout has no sets in it, it is discarded instead.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    reopenConflict = false
                    viewModel.reopen(true, onReopened) {}
                }) { Text("Finish it and reopen this") }
            },
            dismissButton = { TextButton(onClick = { reopenConflict = false }) { Text("Cancel") } },
        )
    }
    editing?.let { row ->
        EditSetDialog(
            row = row,
            unit = state.unit,
            onSave = { weight, reps -> viewModel.editSet(row.setId, weight, reps); editing = null },
            onRemove = {
                viewModel.removeSet(row.setId)
                editing = null
                scope.launch {
                    val result = snackbar.showSnackbar("Set removed from this workout.", actionLabel = "Undo", duration = SnackbarDuration.Long)
                    if (result == SnackbarResult.ActionPerformed) viewModel.restoreSet(row.setId)
                }
            },
            onChangeExercise = { editing = null; changingExerciseOf = row.setId },
            onDismiss = { editing = null },
        )
    }
}

/** Change one logged set: weight, reps, whether it counts (done), or remove it. Removing is a soft delete with an Undo. */
@Composable
private fun EditSetDialog(
    row: SummarySetRow,
    unit: WeightUnit,
    onSave: (weight: String, reps: String) -> Unit,
    onRemove: () -> Unit,
    onChangeExercise: () -> Unit,
    onDismiss: () -> Unit,
) {
    var weight by remember(row.setId) { mutableStateOf(SetEdit.weightText(row.weightKg, unit)) }
    var reps by remember(row.setId) { mutableStateOf(row.reps.toString()) }
    var done by remember(row.setId) { mutableStateOf(true) }
    val valid = SetEdit.parse(weight, reps, unit) != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(row.label.substringBefore(':')) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = weight,
                    onValueChange = { v -> weight = v.filter { it.isDigit() || it == '.' }.take(7) },
                    label = { Text("Weight (${unit.label})") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = reps,
                    onValueChange = { v -> reps = v.filter { it.isDigit() }.take(3) },
                    label = { Text("Reps") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = done, role = Role.Checkbox) { done = it },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = done, onCheckedChange = null)
                    Text("  Done (untick to take it out of the totals)", style = MaterialTheme.typography.bodyMedium)
                }
                TextButton(onClick = onChangeExercise, modifier = Modifier.heightIn(min = 48.dp)) { Text("Change exercise") }
                TextButton(onClick = onRemove, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Delete this set", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (done) onSave(weight, reps) else onRemove() }, enabled = valid || !done) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun RecordsCard(records: List<RecordLine>, unit: WeightUnit) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer, shape = IntakeShape.Card, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(Icons.Filled.EmojiEvents, contentDescription = null, modifier = Modifier.size(34.dp))
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(TrainText.recordsTitle(records.size), style = MaterialTheme.typography.titleLarge)
                records.forEach { r ->
                    Text(recordText(r, unit), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/** "Bench press: estimated 1-rep max 104 lb, up 4 lb" and "Overhead press: 5 reps at 95 lb (was 4)". */
internal fun recordText(r: RecordLine, unit: WeightUnit): String = when (r.kind) {
    PrKind.ESTIMATED_MAX -> {
        val now = fmt(unit.fromKg(r.value))
        val up = r.previous?.let { ", up ${fmt(unit.fromKg(r.value - it))} ${unit.label}" }.orEmpty()
        "${r.exerciseName}: estimated 1-rep max $now ${unit.label}$up"
    }
    PrKind.REP_RECORD -> {
        val was = r.previous?.let { " (was ${it.toInt()})" }.orEmpty()
        "${r.exerciseName}: ${r.value.toInt()} reps at ${fmt(unit.fromKg(r.weightKg))} ${unit.label}$was"
    }
    PrKind.NONE -> r.exerciseName
}

@Composable
private fun StatsRow(summary: SessionSummary, unit: WeightUnit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTileBox(Modifier.weight(1f), "${summary.durationMin}", if (summary.durationMin == 1) "minute" else "minutes")
        StatTileBox(Modifier.weight(1f), TrainText.thousands(unit.fromKg(summary.volumeKg)), "${unit.label} volume")
        StatTileBox(Modifier.weight(1f), "${summary.workingSets}", if (summary.workingSets == 1) "set" else "sets")
        StatTileBox(Modifier.weight(1f), "${summary.records.size}", if (summary.records.size == 1) "PR" else "PRs")
    }
}

@Composable
private fun StatTileBox(modifier: Modifier, value: String, label: String) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = IntakeShape.InnerCard,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.semantics { contentDescription = "$value $label" },
    ) {
        Column(Modifier.padding(vertical = 12.dp, horizontal = 4.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleLarge, maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

/** Muscles worked: the body map lit by this session, and this week's sets per region with today's share highlighted. */
@Composable
private fun MusclesCard(summary: SessionSummary, week: List<RegionVolume>, hint: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text("Muscles worked", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Text("sets this week", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IntakeCard {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                MuscleMapView(highlight = summary.highlight, detail = false, modifier = Modifier.widthIn(max = 240.dp).fillMaxWidth())
            }
            Spacer(Modifier.height(10.dp))
            val worked = week.filter { w -> (summary.regionSets[w.region] ?: 0.0) > 0.0 }.ifEmpty { week.filter { it.sets > 0.0 } }
            worked.forEach { v ->
                val today = summary.regionSets[v.region] ?: 0.0
                RegionBar(v, today)
                Spacer(Modifier.height(8.dp))
            }
            hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

/** A thin bar: this week's sets against the target, with the part this session added in the stronger colour. */
@Composable
private fun RegionBar(v: RegionVolume, today: Double) {
    val full = maxOf(v.target.toDouble(), v.sets, 1.0)
    val total = (v.sets / full).toFloat().coerceIn(0f, 1f)
    val share = (today.coerceAtMost(v.sets) / full).toFloat().coerceIn(0f, 1f)
    val text = "${v.region.label}: ${WeeklyVolume.formatSets(v.sets)} of ${v.target} sets this week, ${WeeklyVolume.formatSets(today)} today, ${WeeklyVolume.words(v.status)}"
    Row(Modifier.fillMaxWidth().semantics { contentDescription = text }, verticalAlignment = Alignment.CenterVertically) {
        Text(v.region.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(92.dp))
        Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(total).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)))
            Box(Modifier.fillMaxHeight().fillMaxWidth(share).background(MaterialTheme.colorScheme.primary))
        }
        Text(
            "${WeeklyVolume.formatSets(v.sets)} / ${v.target}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.width(76.dp),
        )
    }
}

@Composable
private fun NextTimeCard(changes: List<NextTimeChange>, unit: WeightUnit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Next time", style = MaterialTheme.typography.titleLarge)
        IntakeCard {
            changes.forEachIndexed { i, c ->
                Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text(
                        "${c.exerciseName}: ${fmt(unit.fromKg(c.fromKg))} → ${fmt(unit.fromKg(c.toKg))} ${unit.label} × ${c.reps}",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(com.zeroindex.intake.domain.units.WeightText.inUnit(c.reason, unit), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (i != changes.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}


/** Add a set to a finished workout: weight in the person's unit and reps, starting from the exercise's last set. */
@Composable
private fun AddSetDialog(
    exerciseName: String,
    unit: WeightUnit,
    initialWeightKg: Double,
    initialReps: Int?,
    onSave: (weight: String, reps: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var weight by remember(exerciseName) { mutableStateOf(if (initialWeightKg > 0) SetEdit.weightText(initialWeightKg, unit) else "") }
    var reps by remember(exerciseName) { mutableStateOf(initialReps?.toString().orEmpty()) }
    val valid = SetEdit.parse(weight, reps, unit) != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a set: $exerciseName") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = weight,
                    onValueChange = { v -> weight = v.filter { it.isDigit() || it == '.' }.take(7) },
                    label = { Text("Weight (${unit.label}), blank for bodyweight") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = reps,
                    onValueChange = { v -> reps = v.filter { it.isDigit() }.take(3) },
                    label = { Text("Reps") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(weight, reps) }, enabled = valid) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Change a finished workout's date, start time and length. Refusals are shown in words and Save stays off. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun EditTimeDialog(
    startedAt: Long,
    finishedAt: Long,
    today: java.time.LocalDate,
    problemWith: (java.time.LocalDate, java.time.LocalTime, Int?) -> String?,
    onSave: (java.time.LocalDate, java.time.LocalTime, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val zone = java.time.ZoneId.systemDefault()
    val start = java.time.Instant.ofEpochMilli(startedAt).atZone(zone)
    var date by remember { mutableStateOf(start.toLocalDate()) }
    var minutesText by remember { mutableStateOf(com.zeroindex.intake.domain.training.history.SessionEditor.durationMin(startedAt, finishedAt).toString()) }
    val timeState = androidx.compose.material3.rememberTimePickerState(
        initialHour = start.hour,
        initialMinute = start.minute,
        is24Hour = android.text.format.DateFormat.is24HourFormat(androidx.compose.ui.platform.LocalContext.current),
    )
    val time = java.time.LocalTime.of(timeState.hour, timeState.minute)
    val minutes = minutesText.toIntOrNull()
    val problem = problemWith(date, time, minutes)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change date, time and length") },
        text = {
            Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                com.zeroindex.intake.ui.screens.train.WhenFields(date, { date = it }, timeState, minutesText, { minutesText = it }, today)
                Text(
                    "The sets move with the start time.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                problem?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(date, time, minutes ?: return@TextButton) }, enabled = problem == null) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
