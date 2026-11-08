@file:OptIn(ExperimentalMaterial3Api::class)

package com.zeroindex.intake.ui.screens.train

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zeroindex.intake.data.db.entity.ExerciseEntity
import com.zeroindex.intake.data.training.ExerciseFilter
import com.zeroindex.intake.data.training.ExerciseHistory
import com.zeroindex.intake.domain.training.generator.Equipment
import com.zeroindex.intake.domain.training.generator.Muscles
import com.zeroindex.intake.domain.units.WeightUnit
import com.zeroindex.intake.ui.components.ChoiceChip
import com.zeroindex.intake.ui.components.Eyebrow
import com.zeroindex.intake.ui.components.Glossary
import com.zeroindex.intake.ui.components.InfoTip
import com.zeroindex.intake.ui.components.SubHead
import com.zeroindex.intake.ui.components.Tag
import com.zeroindex.intake.ui.theme.IntakeShape
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** Browse, search and filter the exercise library; add, edit and hide your own; see history. */
@Composable
fun ExerciseLibraryScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    viewModel: ExerciseLibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var detail by remember { mutableStateOf<ExerciseEntity?>(null) }
    var form by remember { mutableStateOf<FormTarget?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
            start = 16.dp,
            end = 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "heading") {
            SubHead("Exercises", "${state.total} to choose from. Add your own, or hide ones you never use.")
        }
        item(key = "add") {
            Button(
                onClick = { form = FormTarget.New },
                shape = IntakeShape.Button,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) { Text("Add an exercise") }
        }
        item(key = "filters") { ExerciseFilterBar(state, viewModel) }
        if (state.shown.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = if (state.showHidden) "Nothing is hidden." else "No exercise matches. Clear a filter, or add it as your own.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
        } else {
            item(key = "list") {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = IntakeShape.Card,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        state.shown.forEachIndexed { i, exercise ->
                            ExerciseRow(exercise, onClick = { detail = exercise })
                            if (i != state.shown.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
    }

    detail?.let { selected ->
        ExerciseDetailSheet(
            exercise = selected,
            unit = state.weightUnit,
            viewModel = viewModel,
            onEdit = { detail = null; form = FormTarget.Edit(selected) },
            onDismiss = { detail = null },
        )
    }
    form?.let { target ->
        ExerciseFormSheet(
            existing = (target as? FormTarget.Edit)?.exercise,
            onDismiss = { form = null },
            onSave = { name, muscle, equipment, bodyweight, rest ->
                val e = (target as? FormTarget.Edit)?.exercise
                if (e == null) viewModel.add(name, muscle, equipment, bodyweight, rest)
                else viewModel.save(e.copy(name = name.trim(), primaryMuscle = muscle, equipment = equipment, isBodyweight = bodyweight, defaultRestSec = rest))
                form = null
            },
        )
    }
}

private sealed interface FormTarget {
    data object New : FormTarget
    data class Edit(val exercise: ExerciseEntity) : FormTarget
}

/** Search box, muscle chips, equipment chips and the "hidden" switch. Shared with the picker. */
@Composable
fun ExerciseFilterBar(state: ExerciseBrowseState, viewModel: ExerciseLibraryViewModel, showHiddenSwitch: Boolean = true) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::setQuery,
            label = { Text("Search exercises") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Eyebrow("Muscle")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            ChoiceChip("All", selected = state.muscle == null, onClick = { viewModel.setMuscle(null) })
            Muscles.ALL.forEach { m ->
                ChoiceChip(Muscles.label(m), selected = state.muscle == m, onClick = { viewModel.setMuscle(if (state.muscle == m) null else m) })
            }
        }
        Eyebrow("Equipment")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            ChoiceChip("Any", selected = state.equipment == null, onClick = { viewModel.setEquipment(null) })
            (Equipment.ALL + state.equipmentOptions).distinct().forEach { e ->
                ChoiceChip(Equipment.label(e), selected = state.equipment == e, onClick = { viewModel.setEquipment(if (state.equipment == e) null else e) })
            }
        }
        if (showHiddenSwitch) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Show hidden exercises", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Switch(checked = state.showHidden, onCheckedChange = viewModel::setShowHidden)
            }
        }
    }
}

@Composable
fun ExerciseRow(exercise: ExerciseEntity, onClick: () -> Unit, trailing: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(exercise.name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(
                text = buildString {
                    append(Muscles.label(exercise.primaryMuscle))
                    if (exercise.equipment.isNotBlank()) append(" · ").append(Equipment.label(exercise.equipment))
                    if (exercise.isBodyweight) append(" · bodyweight")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (ExerciseFilter.isCustom(exercise)) Tag("Custom")
        if (trailing != null) Text(trailing, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ExerciseDetailSheet(
    exercise: ExerciseEntity,
    unit: WeightUnit,
    viewModel: ExerciseLibraryViewModel,
    onEdit: () -> Unit,
    onDismiss: () -> Unit,
) {
    val history by remember(exercise.id) { viewModel.history(exercise.id) }
        .collectAsState(initial = ExerciseHistory(emptyList(), null, null, 0))
    val custom = ExerciseFilter.isCustom(exercise)
    val hidden = exercise.deletedAt != null
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(exercise.name, style = MaterialTheme.typography.headlineSmall)
            Text(
                text = buildString {
                    append(Muscles.label(exercise.primaryMuscle))
                    if (exercise.secondaryMuscles.isNotBlank()) append(" (also ${exercise.secondaryMuscles.replace(",", ", ")})")
                    if (exercise.equipment.isNotBlank()) append(" · ").append(Equipment.label(exercise.equipment))
                    append(" · rest ${exercise.defaultRestSec}s")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Eyebrow("Estimated max")
                        InfoTip(Glossary.ESTIMATED_MAX)
                    }
                    Text(
                        text = history.bestEstimatedMaxKg?.let { "${weightText(unit.fromKg(it))} ${unit.label}" } ?: "Not enough data yet",
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
                Column(Modifier.weight(1f)) {
                    Eyebrow("Sets logged")
                    Text("${history.totalSets}", style = MaterialTheme.typography.titleLarge)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Eyebrow("History")
            if (history.days.isEmpty()) {
                Text("You have not logged this exercise yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                history.days.take(8).forEach { day ->
                    Column {
                        Text(dateText(day.startedAt), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            day.sets.joinToString("  ·  ") { "${weightText(unit.fromKg(it.weightKg))} × ${it.reps}" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                if (custom && !hidden) {
                    OutlinedButton(onClick = onEdit, shape = IntakeShape.Button, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Edit") }
                }
                OutlinedButton(
                    onClick = { if (hidden) viewModel.restore(exercise.id) else viewModel.hide(exercise.id); onDismiss() },
                    shape = IntakeShape.Button,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) { Text(if (hidden) "Show again" else "Hide") }
            }
            if (!hidden) {
                Text(
                    text = "Hiding keeps your history and any program that uses it. It only stops it showing in pickers.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Add a custom exercise, or edit one. */
@Composable
fun ExerciseFormSheet(
    existing: ExerciseEntity?,
    onDismiss: () -> Unit,
    onSave: (name: String, muscle: String, equipment: String, bodyweight: Boolean, restSec: Int) -> Unit,
    initialName: String = "",
) {
    var name by remember { mutableStateOf(existing?.name ?: initialName) }
    var muscle by remember { mutableStateOf(existing?.primaryMuscle) }
    var equipment by remember { mutableStateOf(existing?.equipment.orEmpty()) }
    var bodyweight by remember { mutableStateOf(existing?.isBodyweight ?: false) }
    var rest by remember { mutableStateOf((existing?.defaultRestSec ?: 90).toString()) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(if (existing == null) "Add an exercise" else "Edit exercise", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(
                value = name, onValueChange = { name = it }, label = { Text("Name") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Eyebrow("Main muscle")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Muscles.ALL.forEach { m -> ChoiceChip(Muscles.label(m), selected = muscle == m, onClick = { muscle = m }) }
            }
            Eyebrow("Equipment")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Equipment.ALL.forEach { e ->
                    ChoiceChip(Equipment.label(e), selected = equipment == e, onClick = { equipment = if (equipment == e) "" else e })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Bodyweight exercise", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Switch(checked = bodyweight, onCheckedChange = { bodyweight = it })
            }
            OutlinedTextField(
                value = rest,
                onValueChange = { rest = it.filter(Char::isDigit).take(3) },
                label = { Text("Usual rest between sets (seconds)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { onSave(name.trim(), muscle ?: "", equipment, bodyweight, (rest.toIntOrNull() ?: 90).coerceIn(15, 600)) },
                enabled = name.isNotBlank() && muscle != null,
                shape = IntakeShape.Button,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) { Text("Save") }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
        }
    }
}

internal fun weightText(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else ((value * 10).roundToInt() / 10.0).toString()

private fun dateText(millis: Long): String =
    DateTimeFormatter.ofPattern("EEE d MMM yyyy").format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
