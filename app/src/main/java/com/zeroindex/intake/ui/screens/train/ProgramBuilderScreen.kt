@file:OptIn(ExperimentalMaterial3Api::class)

package com.zeroindex.intake.ui.screens.train

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zeroindex.intake.domain.model.ProgressionType
import com.zeroindex.intake.ui.components.ScreenHeading
import com.zeroindex.intake.ui.theme.IntakeShape

@Composable
fun ProgramBuilderScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    viewModel: ProgramBuilderViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

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
        item(key = "heading") { ScreenHeading("Program builder") }

        state.message?.let { message ->
            item(key = "message") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = IntakeShape.Banner,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                ) {
                    Text(message, modifier = Modifier.padding(14.dp))
                }
            }
        }

        item(key = "program") { ProgramSection(state, viewModel) }

        if (state.program != null) {
            item(key = "days") { DaysSection(state, viewModel) }
            if (state.selectedDayId != null) {
                item(key = "exercise") { AddExerciseSection(state, viewModel) }
            }
        }

        item(key = "rules") { RulesSection(state, viewModel) }
    }
}

@Composable
private fun ProgramSection(state: ProgramBuilderUiState, viewModel: ProgramBuilderViewModel) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    Section("Program") {
        if (state.program == null) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Notes") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { viewModel.createProgram(name.trim(), description.trim()) },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) { Text("Create and make it active") }
        } else {
            Text(state.program.name, style = MaterialTheme.typography.titleMedium)
            Text(
                text = state.program.description.ifBlank { "No notes." },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            if (state.allPrograms.size > 1) {
                Text("Switch active program", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.allPrograms.forEach { program ->
                        FilterChip(
                            selected = program.id == state.program.id,
                            onClick = { viewModel.activate(program.id) },
                            label = { Text(program.name) },
                        )
                    }
                }
            }
            OutlinedButton(
                onClick = viewModel::startNewProgram,
                modifier = Modifier.fillMaxWidth().height(48.dp).padding(top = 8.dp),
            ) { Text("Start another program") }
        }
    }
}

@Composable
private fun DaysSection(state: ProgramBuilderUiState, viewModel: ProgramBuilderViewModel) {
    var dayName by remember { mutableStateOf("") }

    Section("Days") {
        state.days.forEach { day ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(day.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = "${state.exerciseCounts[day.id] ?: 0} exercises",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilterChip(
                    selected = state.selectedDayId == day.id,
                    onClick = { viewModel.selectDay(day.id) },
                    label = { Text(if (state.selectedDayId == day.id) "Editing" else "Edit") },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedTextField(
                value = dayName,
                onValueChange = { dayName = it },
                label = { Text("New day, e.g. Upper push") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(
                onClick = {
                    viewModel.addDay(dayName.trim())
                    dayName = ""
                },
                enabled = dayName.isNotBlank(),
            ) { Text("Add") }
        }
    }
}

@Composable
private fun AddExerciseSection(state: ProgramBuilderUiState, viewModel: ProgramBuilderViewModel) {
    var exerciseExpanded by remember { mutableStateOf(false) }
    var ruleExpanded by remember { mutableStateOf(false) }
    var sets by remember { mutableStateOf("3") }
    var repsMin by remember { mutableStateOf("8") }
    var repsMax by remember { mutableStateOf("10") }
    var rpe by remember { mutableStateOf("") }
    var rest by remember { mutableStateOf("120") }

    Section("Add an exercise to ${state.selectedDayName ?: "this day"}") {
        ExposedDropdownMenuBox(
            expanded = exerciseExpanded,
            onExpandedChange = { exerciseExpanded = it },
        ) {
            OutlinedTextField(
                value = state.chosenExercise?.name ?: "Pick an exercise",
                onValueChange = {},
                readOnly = true,
                label = { Text("Exercise") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(exerciseExpanded) },
                modifier = Modifier.fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(
                expanded = exerciseExpanded,
                onDismissRequest = { exerciseExpanded = false },
            ) {
                state.exercises.forEach { exercise ->
                    DropdownMenuItem(
                        text = { Text(exercise.name) },
                        onClick = {
                            viewModel.chooseExercise(exercise.id)
                            exerciseExpanded = false
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        ExposedDropdownMenuBox(expanded = ruleExpanded, onExpandedChange = { ruleExpanded = it }) {
            OutlinedTextField(
                value = state.chosenRule?.name ?: "No progression rule",
                onValueChange = {},
                readOnly = true,
                label = { Text("Progression") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(ruleExpanded) },
                modifier = Modifier.fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(
                expanded = ruleExpanded,
                onDismissRequest = { ruleExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text("No progression rule") },
                    onClick = {
                        viewModel.chooseRule(null)
                        ruleExpanded = false
                    },
                )
                state.rules.forEach { rule ->
                    DropdownMenuItem(
                        text = { Text("${rule.name} (${rule.type.name.lowercase()})") },
                        onClick = {
                            viewModel.chooseRule(rule.id)
                            ruleExpanded = false
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Num(sets, { sets = it }, "Sets", Modifier.weight(1f))
            Num(repsMin, { repsMin = it }, "Reps min", Modifier.weight(1f))
            Num(repsMax, { repsMax = it }, "Reps max", Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Num(rpe, { rpe = it }, "Target RPE (optional)", Modifier.weight(1f))
            Num(rest, { rest = it }, "Rest seconds", Modifier.weight(1f))
        }

        Spacer(Modifier.height(10.dp))
        Button(
            onClick = {
                viewModel.addExerciseToDay(
                    sets = sets.toIntOrNull() ?: 3,
                    repsMin = repsMin.toIntOrNull() ?: 8,
                    repsMax = repsMax.toIntOrNull() ?: 10,
                    targetRpe = rpe.toDoubleOrNull(),
                    restSec = rest.toIntOrNull() ?: 120,
                )
            },
            enabled = state.chosenExercise != null,
            modifier = Modifier.fillMaxWidth().height(50.dp),
        ) { Text("Add to day") }
    }
}

@Composable
private fun RulesSection(state: ProgramBuilderUiState, viewModel: ProgramBuilderViewModel) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(ProgressionType.LINEAR) }
    var increment by remember { mutableStateOf("2.5") }
    var percentage by remember { mutableStateOf("75") }
    var expression by remember { mutableStateOf("") }

    Section("Progression rules") {
        state.rules.forEach { rule ->
            Text(
                text = "${rule.name} · ${rule.type.name.lowercase()}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }

        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Rule name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ProgressionType.entries.take(3).forEach { option ->
                FilterChip(
                    selected = type == option,
                    onClick = { type = option },
                    label = { Text(option.name.lowercase().replace('_', ' ')) },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ProgressionType.entries.drop(3).forEach { option ->
                FilterChip(
                    selected = type == option,
                    onClick = { type = option },
                    label = { Text(option.name.lowercase().replace('_', ' ')) },
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        when (type) {
            ProgressionType.LINEAR, ProgressionType.DOUBLE_PROGRESSION ->
                Num(increment, { increment = it }, "Increment kg", Modifier.fillMaxWidth())
            ProgressionType.PERCENTAGE_1RM ->
                Num(percentage, { percentage = it }, "% of 1RM", Modifier.fillMaxWidth())
            ProgressionType.CUSTOM -> {
                OutlinedTextField(
                    value = expression,
                    onValueChange = { expression = it },
                    label = { Text("Expression") },
                    placeholder = { Text("if (reps >= 8) weight + 2.5 else weight") },
                    modifier = Modifier.fillMaxWidth(),
                )
                // SPEC §10: a malformed rule shows a validation error here,
                // rather than blowing up mid-workout.
                state.expressionError?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Text(
                    text = "Variables: weight, reps, sets, completedReps, rpe, failureCount, " +
                        "week. Also min, max and round.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = { viewModel.validateExpression(expression) },
                    modifier = Modifier.padding(top = 6.dp),
                ) { Text("Check it") }
            }
            ProgressionType.RPE_BASED ->
                Text(
                    text = "Uses the target RPE set on each exercise.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
        }

        Spacer(Modifier.height(10.dp))
        Button(
            onClick = {
                viewModel.createRule(
                    name = name.trim(),
                    type = type,
                    incrementKg = increment.toDoubleOrNull(),
                    percentageOf1rm = percentage.toDoubleOrNull(),
                    expression = expression.trim(),
                )
                name = ""
                expression = ""
            },
            enabled = name.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(50.dp),
        ) { Text("Save rule") }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = IntakeShape.Card,
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            content()
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
