package com.zeroindex.intake.ui.screens.train

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.zeroindex.intake.domain.model.ProgressionType
import com.zeroindex.intake.domain.training.ProgressionLabels
import com.zeroindex.intake.domain.training.generator.BodyArea
import com.zeroindex.intake.domain.training.generator.Equipment
import com.zeroindex.intake.domain.training.generator.EquipmentPreset
import com.zeroindex.intake.domain.training.generator.Experience
import com.zeroindex.intake.domain.training.generator.GeneratedDay
import com.zeroindex.intake.domain.training.generator.GeneratedProgram
import com.zeroindex.intake.domain.training.generator.Muscles
import com.zeroindex.intake.domain.training.generator.RepRange
import com.zeroindex.intake.domain.training.generator.TrainingGoal
import com.zeroindex.intake.domain.training.generator.VolumeGuide
import com.zeroindex.intake.ui.components.Eyebrow
import com.zeroindex.intake.ui.components.Glossary
import com.zeroindex.intake.ui.components.InfoTip
import com.zeroindex.intake.ui.components.IntakeCard
import com.zeroindex.intake.ui.components.SandNote
import com.zeroindex.intake.ui.components.SubHead
import com.zeroindex.intake.ui.theme.IntakeShape

private const val LAST_STEP = 6

/** "Make one for me": a short wizard of friendly questions, then a preview with a reason for every choice. */
@Composable
fun ProgramGeneratorScreen(
    contentPadding: PaddingValues,
    onSaved: (programId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProgramGeneratorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler(enabled = state.step > 0 || state.result != null) { viewModel.back() }

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
        val result = state.result
        if (result != null) {
            previewItems(
                result = result,
                howBuilt = com.zeroindex.intake.domain.goals.TrainingExplainer.howBuilt(state.inputs, result),
                saving = state.saving,
                onSave = { activate -> viewModel.save(activate, onSaved) },
                onChange = viewModel::back,
            )
        } else {
            item(key = "heading") {
                SubHead("Make one for me", "Question ${state.step + 1} of ${LAST_STEP + 1}. Every question has a sensible default.")
            }
            item(key = "question-${state.step}") { Question(state, viewModel) }
            item(key = "nav") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    if (state.step > 0) {
                        OutlinedButton(
                            onClick = viewModel::back,
                            shape = IntakeShape.Button,
                            modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                        ) { Text("Back") }
                    }
                    Button(
                        onClick = { if (state.step == LAST_STEP) viewModel.generate() else viewModel.next() },
                        enabled = state.step != 4 || state.inputs.equipment.isNotEmpty(),
                        shape = IntakeShape.Button,
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                    ) {
                        Text(
                            when {
                                state.step == LAST_STEP -> "Make my program"
                                state.step == 5 && state.inputs.focusMuscles.isEmpty() -> "Skip"
                                else -> "Next"
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Question(state: GeneratorUiState, vm: ProgramGeneratorViewModel) {
    val inputs = state.inputs
    IntakeCard {
        when (state.step) {
            0 -> {
                QuestionTitle("What is your main goal?")
                TrainingGoal.entries.forEach { g ->
                    OptionCard(g.label, g.blurb, g == inputs.goal) { vm.setGoal(g) }
                }
            }
            1 -> {
                QuestionTitle("How much have you trained?")
                Experience.entries.forEach { e ->
                    OptionCard(e.label, e.blurb, e == inputs.experience) { vm.setExperience(e) }
                }
            }
            2 -> {
                QuestionTitle("How many days a week can you train?")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (2..6).forEach { d -> TrainChip("$d days", d == inputs.daysPerWeek) { vm.setDays(d) } }
                }
                Hint("Pick what you can keep up. Three good days beat five you skip.")
            }
            3 -> {
                QuestionTitle("How long is a session?")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(30, 45, 60, 90).forEach { m -> TrainChip("$m min", m == inputs.sessionMinutes) { vm.setMinutes(m) } }
                }
                Hint("Counting sets, reps and rest. Shorter sessions get fewer sets so they still fit.")
            }
            4 -> {
                QuestionTitle("What can you train with?")
                EquipmentPreset.entries.forEach { p ->
                    OptionCard(p.label, equipmentBlurb(p), p == state.preset) { vm.setPreset(p) }
                }
                if (state.preset == EquipmentPreset.CUSTOM) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Equipment.ALL.forEach { k -> TrainChip(Equipment.label(k), k in inputs.equipment) { vm.toggleEquipment(k) } }
                    }
                    if (inputs.equipment.isEmpty()) Hint("Pick at least one.")
                }
            }
            5 -> {
                QuestionTitle("Any muscles you want to bring up? (optional)")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Muscles.PROGRAMMED.forEach { m -> TrainChip(Muscles.label(m), m in inputs.focusMuscles) { vm.toggleFocus(m) } }
                }
                Hint("Chosen muscles get about 25% more sets, early in the session.")
            }
            else -> {
                QuestionTitle("Anything to protect? (optional)")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BodyArea.entries.forEach { a -> TrainChip(a.label, a in inputs.avoid) { vm.toggleAvoid(a) } }
                }
                Hint("Exercises that load these areas are left out, for example a bad shoulder.")
                AdvancedSection(state, vm)
            }
        }
    }
}

private fun equipmentBlurb(p: EquipmentPreset) = when (p) {
    EquipmentPreset.FULL_GYM -> "Barbells, dumbbells, machines and cables"
    EquipmentPreset.HOME_DUMBBELLS -> "Dumbbells and your own bodyweight"
    EquipmentPreset.BODYWEIGHT_ONLY -> "No equipment"
    EquipmentPreset.CUSTOM -> "Choose exactly what you have"
}

@Composable
private fun QuestionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun Hint(text: String) {
    Spacer(Modifier.height(8.dp))
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun OptionCard(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        shape = IntakeShape.Field,
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).clickable(role = Role.RadioButton, onClick = onClick),
    ) {
        Column(Modifier.heightIn(min = 56.dp).padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.Center) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Collapsed by default: the volume table, rep ranges and deload frequency. */
@Composable
private fun AdvancedSection(state: GeneratorUiState, vm: ProgramGeneratorViewModel) {
    var open by remember { mutableStateOf(false) }
    val inputs = state.inputs
    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text("Advanced", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Switch(checked = open, onCheckedChange = { open = it })
    }
    if (!open) return

    Eyebrow("Hard sets per muscle per week")
    val range = VolumeGuide.range(inputs.experience)
    Text(
        "Usual range for ${inputs.experience.label.lowercase()}: ${range.first} to ${range.last}. Smaller muscles get a share.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    state.volumeTable.forEach { (muscle, sets) ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(Muscles.label(muscle), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = { vm.setVolume(muscle, sets - 1) }, enabled = sets > 0, modifier = Modifier.heightIn(min = 44.dp)) { Text("−") }
            Text("$sets", modifier = Modifier.padding(horizontal = 14.dp), style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = { vm.setVolume(muscle, sets + 1) }, modifier = Modifier.heightIn(min = 44.dp)) { Text("+") }
        }
    }
    if (inputs.weeklySetOverrides.isNotEmpty()) TextButton(onClick = vm::resetVolume) { Text("Back to the usual numbers") }

    Spacer(Modifier.height(8.dp))
    Eyebrow("Rep ranges")
    RepRangeRow("Compound lifts", inputs.compoundReps, vm::setCompoundReps)
    RepRangeRow("Isolation lifts", inputs.isolationReps, vm::setIsolationReps)

    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Eyebrow("Deload")
        InfoTip(Glossary.DELOAD)
    }
    val usual = VolumeGuide.deloadEvery(inputs.experience)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(4, 5, 6, 8).forEach { w ->
            TrainChip("Every $w weeks", (inputs.deloadEveryWeeks ?: usual) == w) { vm.setDeload(if (w == usual) null else w) }
        }
    }
}

@Composable
private fun RepRangeRow(label: String, current: RepRange?, onChange: (RepRange?) -> Unit) {
    var min by remember { mutableStateOf(current?.min?.toString().orEmpty()) }
    var max by remember { mutableStateOf(current?.max?.toString().orEmpty()) }
    fun push() {
        val lo = min.toIntOrNull()
        val hi = max.toIntOrNull()
        onChange(if (lo != null && hi != null && lo in 1..100 && hi >= lo) RepRange(lo, hi) else null)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1.2f))
        OutlinedTextField(
            value = min, onValueChange = { min = it.filter(Char::isDigit).take(3); push() },
            label = { Text("From") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = max, onValueChange = { max = it.filter(Char::isDigit).take(3); push() },
            label = { Text("To") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
        )
    }
}

// ------------------------------------------------------------------ preview

private fun androidx.compose.foundation.lazy.LazyListScope.previewItems(
    result: GeneratedProgram,
    howBuilt: com.zeroindex.intake.domain.goals.HowBuilt,
    saving: Boolean,
    onSave: (activate: Boolean) -> Unit,
    onChange: () -> Unit,
) {
    item(key = "p-heading") { SubHead(result.name, "Nothing is saved until you say so. Every number can be edited afterwards.") }
    result.warnings.forEach { w -> item(key = "warn-$w") { SandNote(w) } }
    item(key = "p-why") {
        IntakeCard {
            Eyebrow("Why this plan")
            Spacer(Modifier.height(6.dp))
            result.whys.forEach { line ->
                Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(bottom = 6.dp))
            }
        }
    }
    item(key = "p-howbuilt") { HowBuiltCard(howBuilt) }
    item(key = "p-volume") {
        IntakeCard {
            Eyebrow("Hard sets per week")
            Spacer(Modifier.height(6.dp))
            result.weeklySets.forEach { (m, n) ->
                val target = result.targetSets[m]
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(Muscles.label(m), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        if (target != null && target != n) "$n (aimed for $target)" else "$n",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
    items(items = result.days, key = { "day-${it.name}" }) { DayPreview(it) }
    item(key = "p-actions") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onSave(true) },
                enabled = !saving,
                shape = IntakeShape.Button,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) { Text(if (saving) "Saving…" else "Save and make it my program") }
            OutlinedButton(
                onClick = { onSave(false) },
                enabled = !saving,
                shape = IntakeShape.Button,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) { Text("Save, but keep my current program") }
            TextButton(onClick = onChange, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Change my answers") }
        }
    }
}

@Composable
private fun DayPreview(day: GeneratedDay) {
    IntakeCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(day.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("about ${day.estimatedMinutes} min", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(day.why, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        day.exercises.forEach { e ->
            Spacer(Modifier.height(10.dp))
            Text(e.name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            val timed = e.repsMin >= 30
            Text(
                text = summaryLine(e.sets, e.repsMin, e.repsMax, e.rpe, e.restSec) + if (timed) " (seconds)" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(e.why, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
            if (e.prescription.isNotBlank()) {
                Text(e.prescription, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            e.startWeightKg?.let { kg ->
                Text(
                    "Start at about ${weightText(kg)} kg. ${e.startNote.orEmpty()}",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}

@Suppress("unused")
private fun progressionName(type: ProgressionType?) = type?.let(ProgressionLabels::title) ?: "None"
