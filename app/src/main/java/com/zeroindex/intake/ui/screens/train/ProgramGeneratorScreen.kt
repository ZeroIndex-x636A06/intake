package com.zeroindex.intake.ui.screens.train

import com.zeroindex.intake.domain.training.equipment.EquipmentItems
import com.zeroindex.intake.ui.components.EquipmentChecklist
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.width
import com.zeroindex.intake.domain.training.RepTarget
import androidx.compose.material3.Icon
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
import com.zeroindex.intake.ui.components.IntakeSwitch
import com.zeroindex.intake.ui.components.ListDivider
import com.zeroindex.intake.ui.components.SoftButton
import com.zeroindex.intake.ui.components.IntakeChip
import com.zeroindex.intake.ui.components.SoftCard
import com.zeroindex.intake.ui.theme.IconSize
import com.zeroindex.intake.ui.theme.IntakeIcons
import com.zeroindex.intake.ui.theme.IntakeShape
import com.zeroindex.intake.ui.theme.IntakeSpacing
import com.zeroindex.intake.ui.theme.intake
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box

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

    val result = state.result
    Column(modifier.fillMaxWidth()) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth().weight(1f),
        contentPadding = trainPadding(
            top = contentPadding.calculateTopPadding(),
            bottom = if (result != null) 16.dp else contentPadding.calculateBottomPadding() + 32.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(IntakeSpacing.StackGap),
    ) {
        if (result != null) {
            previewItems(
                result = result,
                howBuilt = com.zeroindex.intake.domain.goals.TrainingExplainer.howBuilt(state.inputs, result),
                unit = state.unit,
                saving = state.saving,
                onSave = { activate -> viewModel.save(activate, onSaved) },
                onChange = viewModel::back,
            )
        } else {
            item(key = "heading") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StepBar(step = state.step, total = LAST_STEP + 1)
                    Text("Question ${state.step + 1} of ${LAST_STEP + 1}. Every question has a sensible default.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item(key = "question-${state.step}") { Question(state, viewModel) }
            item(key = "nav") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    if (state.step > 0) {
                        SoftButton(
                            onClick = viewModel::back,
                            modifier = Modifier.heightIn(min = 56.dp),
                        ) { Text("Back") }
                    }
                    Button(
                        onClick = { if (state.step == LAST_STEP) viewModel.generate() else viewModel.next() },
                        enabled = true,
                        shape = IntakeShape.Button,
                        modifier = Modifier.weight(1f).heightIn(min = 56.dp),
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
    // The plan is long, so the main action stays in reach instead of sitting under the last day.
    if (result != null) {
        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp, modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { viewModel.save(true, onSaved) },
                enabled = !state.saving,
                shape = IntakeShape.Button,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = IntakeSpacing.ScreenGutter, end = IntakeSpacing.ScreenGutter, top = 12.dp, bottom = contentPadding.calculateBottomPadding() + 12.dp)
                    .heightIn(min = 52.dp),
            ) { Text(if (state.saving) "Saving…" else "Save and make it my program") }
        }
    }
    }
}

@Composable
private fun Question(state: GeneratorUiState, vm: ProgramGeneratorViewModel) {
    val inputs = state.inputs
    Column(verticalArrangement = Arrangement.spacedBy(IntakeSpacing.RowGap)) {
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
                if (inputs.experience != Experience.NEW) {
                    IntakeCard(Modifier.padding(top = 8.dp)) {
                    Text("Know your numbers? (optional)", style = MaterialTheme.typography.titleMedium)
                    Hint("A weight you can lift for a few good reps, not an all-out max. Your starting weights come from these; leave any blank and Intake starts light.")
                    com.zeroindex.intake.domain.training.KnownLifts.KEY_LIFTS.forEach { lift ->
                        val typed = state.knownLifts[lift] ?: ("" to "")
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        ) {
                            Text(lift, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            androidx.compose.material3.OutlinedTextField(
                                value = typed.first,
                                onValueChange = { vm.setKnownLift(lift, it, typed.second) },
                                label = { Text(state.unit.label) },
                                singleLine = true,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                                modifier = Modifier.width(96.dp).semantics { contentDescription = "$lift weight in ${state.unit.label}" },
                            )
                            androidx.compose.material3.OutlinedTextField(
                                value = typed.second,
                                onValueChange = { vm.setKnownLift(lift, typed.first, it) },
                                label = { Text("reps") },
                                singleLine = true,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                                modifier = Modifier.width(80.dp).semantics { contentDescription = "$lift reps" },
                            )
                        }
                    }
                    }
                }
            }
            2 -> {
                QuestionTitle("How many days a week can you train?")
                (2..6).toList().chunked(3).forEach { rowDays ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(IntakeSpacing.GridGap3)) {
                        rowDays.forEach { d -> NumberTile("$d", "$d days", d == inputs.daysPerWeek, Modifier.weight(1f)) { vm.setDays(d) } }
                        repeat(3 - rowDays.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                Hint("Pick what you can keep up. Three good days beat five you skip.")
                Answers(state)
            }
            3 -> {
                QuestionTitle("How long is a session?")
                listOf(30, 45, 60, 90).chunked(2).forEach { rowMins ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(IntakeSpacing.GridGap3)) {
                        rowMins.forEach { m -> NumberTile("$m", "$m min", m == inputs.sessionMinutes, Modifier.weight(1f)) { vm.setMinutes(m) } }
                    }
                }
                Hint("Counting sets, reps and rest. Shorter sessions get fewer sets so they still fit.")
                Answers(state)
            }
            4 -> {
                QuestionTitle("What can you train with?")
                EquipmentPreset.entries.filter { it != EquipmentPreset.CUSTOM }.forEach { p ->
                    OptionCard(p.label, equipmentBlurb(p), p == state.preset) { vm.setPreset(p) }
                }
                var advanced by remember { mutableStateOf(state.preset == EquipmentPreset.CUSTOM) }
                OptionCard(
                    "Advanced: pick each item",
                    if (state.preset == EquipmentPreset.CUSTOM) "Your own list: ${inputs.equipmentItems?.size ?: 0} items" else equipmentBlurb(EquipmentPreset.CUSTOM),
                    advanced,
                ) { advanced = !advanced }
                if (advanced) {
                    IntakeCard {
                        EquipmentChecklist(
                            selected = inputs.equipmentItems ?: EquipmentItems.fromLegacy(inputs.equipment),
                            onChange = vm::setEquipmentItems,
                            dumbbellMaxKg = null,
                            onDumbbellMax = { },
                            unit = state.unit,
                            showDumbbellMax = false,
                        )
                    }
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
                IntakeCard(Modifier.padding(top = 8.dp)) { AdvancedSection(state, vm) }
            }
        }
    }
}

private fun equipmentBlurb(p: EquipmentPreset) = when (p) {
    EquipmentPreset.FULL_GYM -> "Every item: barbells, racks, dumbbells, machines and cables"
    EquipmentPreset.HOME_DUMBBELLS -> "Dumbbells and your own bodyweight"
    EquipmentPreset.BODYWEIGHT_ONLY -> "Your own bodyweight and a pull-up bar"
    EquipmentPreset.CUSTOM -> "Tick every barbell, bench, machine and extra you have"
}

@Composable
private fun QuestionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
    Spacer(Modifier.height(6.dp))
}

/** The segmented step bar: finished and current steps in ink, the current one in lime, the rest `line`. */
@Composable
private fun StepBar(step: Int, total: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(total) { i ->
            Box(
                Modifier
                    .weight(1f)
                    .height(4.dp)
                    .background(
                        when {
                            i < step -> MaterialTheme.colorScheme.primary
                            i == step -> MaterialTheme.intake.accent
                            else -> MaterialTheme.colorScheme.outlineVariant
                        },
                        IntakeShape.Pill,
                    ),
            )
        }
    }
}

/** A big number tile (`2`, `3`, `4`): white, ink when chosen. [label] is its spoken name. */
@Composable
private fun NumberTile(text: String, label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        shape = IntakeShape.TileLarge,
        modifier = modifier
            .heightIn(min = 72.dp)
            .semantics { contentDescription = label }
            .clickable(role = Role.RadioButton, onClick = onClick),
    ) {
        Box(Modifier.fillMaxWidth().heightIn(min = 72.dp), contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.headlineMedium)
        }
    }
}

/** `Your answers so far`: the choices made on earlier questions, as chips on a soft card. */
@Composable
private fun Answers(state: GeneratorUiState) {
    val inputs = state.inputs
    val answers = buildList {
        add(inputs.goal.label)
        add(inputs.experience.label)
        if (state.step > 2) add("${inputs.daysPerWeek} days")
        if (state.step > 3) add("${inputs.sessionMinutes} min")
    }
    SoftCard(Modifier.padding(top = 8.dp)) {
        Eyebrow("Your answers so far")
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            answers.forEach { IntakeChip(it, tone = com.zeroindex.intake.ui.components.ChipTone.Outline) }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Spacer(Modifier.height(4.dp))
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun OptionCard(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        contentColor = fg,
        shape = IntakeShape.TileLarge,
        modifier = Modifier.fillMaxWidth().clickable(role = Role.RadioButton, onClick = onClick),
    ) {
        Column(Modifier.heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.Center) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = fg.copy(alpha = 0.72f))
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
        IntakeSwitch(checked = open, onCheckedChange = { open = it })
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
    unit: com.zeroindex.intake.domain.units.WeightUnit,
    saving: Boolean,
    onSave: (activate: Boolean) -> Unit,
    onChange: () -> Unit,
) {
    // What the plan IS comes first and reads at a glance: the name, three numbers, then each day as a short list.
    // Every explanation is still here, one tap away, for the people who want it.
    item(key = "p-heading") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(result.name, style = MaterialTheme.typography.headlineSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                PlanFact("${result.days.size}", "days a week", Modifier.weight(1f))
                PlanFact("${result.days.maxOfOrNull { it.estimatedMinutes } ?: 0}", "min a session", Modifier.weight(1f))
                PlanFact("${result.deloadEveryWeeks}", "weeks, then an easy one", Modifier.weight(1f))
            }
            Text(
                "Nothing is saved yet, and every number can be changed later. Tap an exercise to see why it is there.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    items(items = result.days, key = { "day-${it.name}" }) { DayPreview(it, unit) }
    if (result.warnings.isNotEmpty()) {
        item(key = "p-warnings") {
            Expander("Worth knowing", if (result.warnings.size == 1) "1 note about this plan" else "${result.warnings.size} notes about this plan") {
                result.warnings.forEach { w -> SandNote(w) }
            }
        }
    }
    item(key = "p-why") {
        Expander("Why this plan", "The split, the rep ranges and how it progresses") {
            result.whys.forEach { line ->
                Text(line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
    item(key = "p-volume") {
        Expander("Sets per muscle each week", "What each muscle gets against what was aimed for") {
            result.weeklySets.forEach { (m, n) ->
                val target = result.targetSets[m]
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(Muscles.label(m), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (target != null && target != n) "$n (aimed for $target)" else "$n",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
    item(key = "p-howbuilt") {
        Expander("How this plan was built", "Which of your answers were used") { HowBuiltCard(howBuilt) }
    }
    item(key = "p-actions") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SoftButton(
                onClick = { onSave(false) },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) { Text("Save, but keep my current program") }
            TextButton(onClick = onChange, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Change my answers") }
        }
    }
}

/** One big number with a small label under it. */
@Composable
private fun PlanFact(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.intake.soft,
        shape = IntakeShape.TileLarge,
        modifier = modifier,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A closed card with a title and one line about what is inside; tapping it opens the detail. */
@Composable
private fun Expander(title: String, summary: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    var open by androidx.compose.runtime.saveable.rememberSaveable(title) { androidx.compose.runtime.mutableStateOf(false) }
    IntakeCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(
                    role = androidx.compose.ui.semantics.Role.Button,
                    onClickLabel = if (open) "Close $title" else "Open $title",
                ) { open = !open },
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                if (open) IntakeIcons.ChevronUp else IntakeIcons.ChevronDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(IconSize.Default),
            )
        }
        androidx.compose.animation.AnimatedVisibility(visible = open) {
            Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
        }
    }
}

@Composable
private fun DayPreview(day: GeneratedDay, unit: com.zeroindex.intake.domain.units.WeightUnit) {
    IntakeCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(day.name, style = MaterialTheme.typography.titleLarge)
                Text(
                    listOfNotNull(
                        day.weekday?.let { com.zeroindex.intake.domain.training.WorkoutSchedule.weekdayName(it) },
                        "about ${day.estimatedMinutes} min",
                        "${day.exercises.size} exercises",
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        day.exercises.forEachIndexed { index, e ->
            if (index == 0) Spacer(Modifier.height(8.dp))
            ListDivider()
            ExercisePreviewRow(e, unit)
        }
    }
}

/** One exercise: picture, name, the sets and the starting weight. The reasons open on a tap. */
@Composable
private fun ExercisePreviewRow(e: com.zeroindex.intake.domain.training.generator.GeneratedExercise, unit: com.zeroindex.intake.domain.units.WeightUnit) {
    var open by androidx.compose.runtime.saveable.rememberSaveable(e.exerciseId) { androidx.compose.runtime.mutableStateOf(false) }
    val timed = e.repsMin >= 30
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(
                role = androidx.compose.ui.semantics.Role.Button,
                onClickLabel = if (open) "Hide why ${e.name} is here" else "Show why ${e.name} is here",
            ) { open = !open }
            .padding(vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            com.zeroindex.intake.ui.components.ExerciseThumb(e.exerciseId, photoPath = null, size = 48.dp)
            Column(Modifier.weight(1f)) {
                Text(e.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    text = "${e.sets} × " + (if (RepTarget.isAmrap(e.repsMin, e.repsMax)) "as many as you can" else RepTarget.label(e.repsMin, e.repsMax)) +
                        (if (timed) " seconds" else " reps"),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            e.startWeightKg?.let { kg ->
                Column(horizontalAlignment = Alignment.End) {
                    Text(com.zeroindex.intake.domain.units.WeightText.format(kg, unit), style = MaterialTheme.typography.titleMedium)
                    Text("to start", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        androidx.compose.animation.AnimatedVisibility(visible = open) {
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Effort ${e.rpe?.let { weightText(it) } ?: "as feels right"} of 10 · rest ${e.restSec / 60}:${(e.restSec % 60).toString().padStart(2, '0')}",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface,
                )
                Text(e.why, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                if (e.prescription.isNotBlank()) {
                    Text(
                        com.zeroindex.intake.domain.units.WeightText.inUnit(e.prescription, unit),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                e.startNote?.takeIf { it.isNotBlank() }?.let { note ->
                    Text(
                        com.zeroindex.intake.domain.units.WeightText.inUnit(note, unit),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Suppress("unused")
private fun progressionName(type: ProgressionType?) = type?.let(ProgressionLabels::title) ?: "None"
