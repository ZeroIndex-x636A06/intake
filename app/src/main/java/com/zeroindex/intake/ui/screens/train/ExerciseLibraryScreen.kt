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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.zeroindex.intake.domain.training.ExerciseGuideIndex
import com.zeroindex.intake.ui.components.ChoiceChip
import com.zeroindex.intake.ui.components.ExerciseThumb
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
            ) { Text("Add your own exercise") }
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
        item(key = "credit") {
            Text(
                ExerciseGuideIndex.CREDIT,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }

    detail?.let { selected ->
        ExerciseDetailSheet(
            exerciseId = selected.id,
            onDismiss = { detail = null },
            onEdit = { detail = null; form = FormTarget.Edit(selected) },
            onHideToggle = {
                if (selected.deletedAt != null) viewModel.restore(selected.id) else viewModel.hide(selected.id)
                detail = null
            },
        )
    }
    form?.let { target ->
        ExerciseFormSheet(
            existing = (target as? FormTarget.Edit)?.exercise,
            viewModel = viewModel,
            onDismiss = { form = null },
            onSave = { f ->
                val e = (target as? FormTarget.Edit)?.exercise
                if (e == null) viewModel.add(f) else viewModel.update(e, f)
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
fun ExerciseRow(exercise: ExerciseEntity, onClick: () -> Unit, trailing: String? = null, onInfo: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ExerciseThumb(exercise.id, exercise.photoPath)
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
        if (onInfo != null) {
            IconButton(onClick = onInfo, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Outlined.Info, contentDescription = "About ${exercise.name}: photos, muscles and how to")
            }
        }
    }
}

internal fun weightText(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else ((value * 10).roundToInt() / 10.0).toString()

