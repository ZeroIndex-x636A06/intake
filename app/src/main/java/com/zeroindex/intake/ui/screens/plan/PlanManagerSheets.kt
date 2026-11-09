@file:OptIn(ExperimentalMaterial3Api::class)

package com.zeroindex.intake.ui.screens.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.zeroindex.intake.data.db.entity.MealPlanEntity
import com.zeroindex.intake.data.db.entity.PlanWeekEntity
import com.zeroindex.intake.domain.model.PlanLoopMode
import com.zeroindex.intake.domain.plan.PlanSchedule
import com.zeroindex.intake.domain.plan.PlanWeekExtras
import com.zeroindex.intake.ui.components.StatusPill
import com.zeroindex.intake.ui.theme.IntakeShape
import java.time.LocalDate

/** All plans: switch the active one, make a new one, edit or archive. Exactly one is active. */
@Composable
fun PlansSheet(
    plans: List<MealPlanEntity>,
    onSwitch: (MealPlanEntity) -> Unit,
    onNew: () -> Unit,
    onEdit: (MealPlanEntity) -> Unit,
    onArchive: (MealPlanEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    var confirmArchive by remember { mutableStateOf<MealPlanEntity?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).verticalScroll(rememberScrollState())) {
            Text("Meal plans", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            plans.forEach { p ->
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Starts ${PlanSchedule.date(p.startDate)} · repeat: ${PlanSchedule.repeatText(p.loopMode, p.loopCount)}",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (p.isActive) StatusPill("Active")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (!p.isActive) TextButton(onClick = { onSwitch(p) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Use this plan") }
                        TextButton(onClick = { onEdit(p) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Edit") }
                        TextButton(onClick = { confirmArchive = p }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Archive") }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            if (plans.isEmpty()) {
                Text("No plans yet. Make one, or import a plan file.", style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = onNew, shape = IntakeShape.Button, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("New plan") }
            Spacer(Modifier.height(16.dp))
        }
    }
    confirmArchive?.let { p ->
        AlertDialog(
            onDismissRequest = { confirmArchive = null },
            title = { Text("Archive ${p.name}?") },
            text = { Text("It leaves your list but nothing is erased. Archiving the active plan switches to another one if there is one.") },
            confirmButton = { TextButton(onClick = { onArchive(p); confirmArchive = null }) { Text("Archive") } },
            dismissButton = { TextButton(onClick = { confirmArchive = null }) { Text("Cancel") } },
        )
    }
}

/** Create a plan (name, length, repeat, start date) or edit one's name, repeat and start date. */
@Composable
fun PlanFormDialog(
    initial: MealPlanEntity?,
    today: LocalDate,
    onSave: (name: String, weeks: Int, mode: PlanLoopMode, count: Int, startDay: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var weeks by remember { mutableStateOf("4") }
    var mode by remember { mutableStateOf(initial?.loopMode ?: PlanLoopMode.NONE) }
    var count by remember { mutableStateOf((initial?.loopCount?.takeIf { it >= 2 } ?: 2).toString()) }
    var start by remember { mutableStateOf(initial?.let { PlanSchedule.date(it.startDate) }?.toString() ?: today.toString()) }
    val startDate = runCatching { LocalDate.parse(start.trim()) }.getOrNull()
    val weeksN = weeks.toIntOrNull()
    val valid = name.isNotBlank() && startDate != null && (initial != null || (weeksN != null && weeksN in 1..PlanSchedule.MAX_WEEKS)) &&
        (mode != PlanLoopMode.REPEAT_N || (count.toIntOrNull() ?: 0) >= 2)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New plan" else "Edit plan") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (initial == null) {
                    OutlinedTextField(
                        weeks, { weeks = it.filter(Char::isDigit).take(3) }, label = { Text("Length in weeks (1 to ${PlanSchedule.MAX_WEEKS})") },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    start, { start = it }, label = { Text("First day of week 1 (yyyy-mm-dd)") }, singleLine = true,
                    isError = startDate == null, modifier = Modifier.fillMaxWidth(),
                )
                Text("When it ends", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(mode == PlanLoopMode.NONE, { mode = PlanLoopMode.NONE }, label = { Text("Stop") })
                    FilterChip(mode == PlanLoopMode.REPEAT, { mode = PlanLoopMode.REPEAT }, label = { Text("Repeat") })
                    FilterChip(mode == PlanLoopMode.REPEAT_N, { mode = PlanLoopMode.REPEAT_N }, label = { Text("Repeat N times") })
                }
                if (mode == PlanLoopMode.REPEAT_N) {
                    OutlinedTextField(
                        count, { count = it.filter(Char::isDigit).take(3) }, label = { Text("Times through the whole plan") },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { onSave(name.trim(), weeksN ?: 1, mode, count.toIntOrNull() ?: 2, startDate!!.toEpochDay()) },
            ) { Text(if (initial == null) "Create" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** The hand-made parts of a week: dish name, protein, a note and one-line variation ideas. */
@Composable
fun WeekEditSheet(
    week: PlanWeekEntity,
    hasRecipe: Boolean,
    onSave: (dish: String, protein: String, note: String, variations: List<String>) -> Unit,
    onChooseRecipe: () -> Unit,
    onDismiss: () -> Unit,
) {
    val extras = remember(week.id, week.notes) { PlanWeekExtras.decode(week.notes) }
    var dish by remember { mutableStateOf(week.mainDishName) }
    var protein by remember { mutableStateOf(week.proteinType) }
    var note by remember { mutableStateOf(extras.note) }
    var variations by remember { mutableStateOf(extras.variations.joinToString("\n")) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Week ${week.weekNumber}", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(dish, { dish = it }, label = { Text("Main dish") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(protein, { protein = it }, label = { Text("Protein (for example Chicken)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(variations, { variations = it }, label = { Text("Other ways to eat it, one per line") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(note, { note = it }, label = { Text("Notes for this week") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            OutlinedButton(onClick = onChooseRecipe, shape = IntakeShape.Button, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(if (hasRecipe) "Change the recipe" else "Use a saved recipe for the steps and macros")
            }
            Button(
                onClick = { onSave(dish, protein, note, variations.lines()) },
                shape = IntakeShape.Button, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) { Text("Save week") }
            Spacer(Modifier.height(16.dp))
        }
    }
}
