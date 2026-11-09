@file:OptIn(ExperimentalMaterial3Api::class)

package com.zeroindex.intake.ui.screens.plan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.zeroindex.intake.data.db.entity.AllergenStatus
import com.zeroindex.intake.ui.allergen.CheckLabelChip
import com.zeroindex.intake.data.db.entity.RecipeEntity
import com.zeroindex.intake.data.plan.ImportFinding
import com.zeroindex.intake.data.plan.PlanImportBatch
import com.zeroindex.intake.data.repository.PlanImportPreview
import com.zeroindex.intake.domain.nutrition.Macros
import com.zeroindex.intake.ui.components.Eyebrow
import com.zeroindex.intake.ui.components.OfflinePill
import com.zeroindex.intake.ui.components.PillTone
import com.zeroindex.intake.ui.components.SandNote
import com.zeroindex.intake.ui.components.StatusPill
import com.zeroindex.intake.ui.components.EmptyState
import com.zeroindex.intake.ui.screens.script.ScriptWarnings
import com.zeroindex.intake.ui.theme.IntakeShape
import kotlin.math.roundToInt

/** Choose a saved recipe for a week. The protein type is free text the user edits. */
@Composable
fun AssignRecipeSheet(
    weekNumber: Int,
    recipes: List<RecipeEntity>,
    initialProtein: String,
    onDismiss: () -> Unit,
    onPick: (RecipeEntity, String) -> Unit,
) {
    var protein by remember { mutableStateOf(initialProtein) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp)) {
            Text("Choose a recipe for week $weekNumber", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = protein,
                onValueChange = { protein = it },
                label = { Text("Protein type (for example Chicken)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            if (recipes.isEmpty()) {
                EmptyState(
                    title = "No saved recipes yet",
                    body = "Save one from Recipe Hunter, or import a plan file.",
                )
            } else {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    recipes.forEach { r ->
                        ListItem(
                            headlineContent = { Text(r.name) },
                            supportingContent = {
                                Text(
                                    listOfNotNull(
                                        "${r.servings} servings",
                                        r.totalTimeMinutes.takeIf { it > 0 }?.let { "$it min" },
                                    ).joinToString(" · "),
                                )
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                                .clickable { onPick(r, protein) },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** "Can't eat what I cooked?": local options for the week, one tap to log. Works with no network. */
@Composable
fun EatOutSheet(
    rows: List<EatOutRow>,
    hiddenCount: Int,
    showingAll: Boolean,
    weekLabel: String,
    onToggleAll: () -> Unit,
    onLog: (EatOutRow) -> Unit,
    onOpenPlanB: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Find something that works", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                OfflinePill()
            }
            Text(
                "Saved options for $weekLabel. Nothing here needs a signal.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (rows.isEmpty()) {
                EmptyState(
                    title = "No saved options for this week",
                    body = "Import a plan file with eat-out options, or find a place with Plan B.",
                )
            }
            rows.forEach { row -> EatOutCard(row, onLog = { onLog(row) }) }
            if (hiddenCount > 0 || showingAll) {
                OutlinedButton(
                    onClick = onToggleAll,
                    shape = IntakeShape.Button,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) { Text(if (showingAll) "Show only this week's protein" else "Show all options ($hiddenCount more)") }
            }
            OutlinedButton(
                onClick = onOpenPlanB,
                shape = IntakeShape.Button,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) { Text("Plan B · find a place nearby") }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun EatOutCard(row: EatOutRow, onLog: () -> Unit) {
    val o = row.option
    val blocked = row.status == AllergenStatus.CONTAINS
    androidx.compose.material3.Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = IntakeShape.InnerCard,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(o.venueName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                when (row.status) {
                    AllergenStatus.CONTAINS -> StatusPill("Contains an allergen", tone = PillTone.Bad)
                    AllergenStatus.MAY_CONTAIN -> StatusPill("Check: may contain", tone = PillTone.Wait)
                    AllergenStatus.CHECK_LABEL -> CheckLabelChip(null)
                    AllergenStatus.CLEAR -> StatusPill("No flagged terms")
                    AllergenStatus.MENTIONED_AVOIDED -> StatusPill(MentionedAvoided.PILL)
                    AllergenStatus.UNKNOWN -> StatusPill("Not checked", tone = PillTone.Wait)
                }
            }
            Eyebrow("Say this when ordering")
            Text("“${o.whatToOrder}”", style = MaterialTheme.typography.bodyMedium)
            if (o.cautionNote.isNotBlank()) {
                SandNote(body = o.cautionNote, title = "Important")
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Estimated meal", style = MaterialTheme.typography.labelMedium)
                Text(
                    if (row.hasEstimate) "${o.estimatedKcal} kcal · ${o.estimatedProtein} g protein" else "No estimate",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Button(
                onClick = onLog,
                enabled = !blocked,
                shape = IntakeShape.Button,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) {
                Text(
                    when {
                        blocked -> "Not logged: matches your allergens"
                        row.hasEstimate -> "Log this meal"
                        else -> "Log this meal (enter macros)"
                    },
                )
            }
        }
    }
}

/**
 * Asks for macros before logging when there are none, or only some. The
 * fields start from what is known so a partial total is never logged unseen.
 */
@Composable
fun MacroConfirmDialog(
    title: String,
    explanation: String,
    initial: Macros,
    onDismiss: () -> Unit,
    onConfirm: (Macros) -> Unit,
) {
    var kcal by remember { mutableStateOf(initial.kcal.takeIf { it > 0 }?.roundToInt()?.toString().orEmpty()) }
    var protein by remember { mutableStateOf(initial.protein.takeIf { it > 0 }?.roundToInt()?.toString().orEmpty()) }
    var carbs by remember { mutableStateOf(initial.carbs.takeIf { it > 0 }?.roundToInt()?.toString().orEmpty()) }
    var fat by remember { mutableStateOf(initial.fat.takeIf { it > 0 }?.roundToInt()?.toString().orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(explanation, style = MaterialTheme.typography.bodySmall)
                val keyboard = KeyboardOptions(keyboardType = KeyboardType.Number)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(kcal, { kcal = it.filter(Char::isDigit) }, label = { Text("kcal") }, singleLine = true, keyboardOptions = keyboard, modifier = Modifier.weight(1f))
                    OutlinedTextField(protein, { protein = it.filter(Char::isDigit) }, label = { Text("Protein g") }, singleLine = true, keyboardOptions = keyboard, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(carbs, { carbs = it.filter(Char::isDigit) }, label = { Text("Carbs g") }, singleLine = true, keyboardOptions = keyboard, modifier = Modifier.weight(1f))
                    OutlinedTextField(fat, { fat = it.filter(Char::isDigit) }, label = { Text("Fat g") }, singleLine = true, keyboardOptions = keyboard, modifier = Modifier.weight(1f))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        Macros(
                            kcal = kcal.toDoubleOrNull() ?: 0.0,
                            protein = protein.toDoubleOrNull() ?: 0.0,
                            carbs = carbs.toDoubleOrNull() ?: 0.0,
                            fat = fat.toDoubleOrNull() ?: 0.0,
                        ),
                    )
                },
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text("Log it") }
        },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel") } },
    )
}

/** Shows what the file will do, and every allergen hit the local engine found, before anything is written. */
@Composable
fun ImportPreviewDialog(
    preview: PlanImportPreview,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (preview is PlanImportPreview.Ready) "Import this plan?" else "Can't import this file") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (preview) {
                    is PlanImportPreview.Invalid -> preview.errors.take(12).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                    is PlanImportPreview.ScriptErrors -> Unit // Shown as the diagnostics sheet, not here.
                    is PlanImportPreview.Ready -> {
                        val b = preview.batch
                        b.plan?.let { p ->
                            Text(
                                if (b.isUpdate) "Updates your plan \"${p.name}\" and makes it the active plan." else "Creates a new plan, \"${p.name}\", and makes it the active plan.",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                "Starts ${com.zeroindex.intake.domain.plan.PlanSchedule.date(p.startDate)} · repeat: " +
                                    com.zeroindex.intake.domain.plan.PlanSchedule.repeatText(p.loopMode, p.loopCount),
                                style = MaterialTheme.typography.labelSmall,
                            )
                            if (b.removedWeekIds.isNotEmpty()) {
                                SandNote("${b.removedWeekIds.size} weeks in the current plan are not in this file and will be removed from it (nothing is erased).")
                            }
                        }
                        Text(
                            "${preview.weeks} weeks, ${preview.recipes} recipes, ${b.shoppingItems.size} shopping items, " +
                                "${preview.eatOut} eat-out options. Re-importing the same file updates these rather than adding copies.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        ScriptWarnings(preview.warnings)
                        if (b.ingredientsWithoutNutrition > 0) {
                            SandNote("${b.ingredientsWithoutNutrition} ingredients have no grams or nutrition, so those recipes' macros will show as incomplete.")
                        }
                        if (b.eatOutWithoutEstimate > 0) {
                            SandNote("${b.eatOutWithoutEstimate} eat-out options have no macro estimate. You will enter macros when logging them.")
                        }
                        ImportAllergenSection(b)
                        Text(
                            "The file's own allergen notes can add warnings but never remove one.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (preview is PlanImportPreview.Ready) {
                TextButton(onClick = onConfirm, modifier = Modifier.heightIn(min = 48.dp)) { Text("Import") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(if (preview is PlanImportPreview.Ready) "Cancel" else "Close")
            }
        },
    )
}

/**
 * The allergen part of both import previews (plan file and script). One row per distinct item, full text,
 * grouped by tier: Blocked opens expanded, the softer tiers are collapsed behind counts.
 */
@Composable
fun ImportAllergenSection(b: PlanImportBatch) {
    if (b.findings.isEmpty()) {
        Text("Your allergen check found nothing to flag in this file.", style = MaterialTheme.typography.bodySmall)
        return
    }
    Text(b.summaryLine, style = MaterialTheme.typography.titleSmall)
    Text(
        "Checked on this phone against your allergens.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    FindingTier("Blocked", PillTone.Bad, b.blocked, startOpen = true)
    FindingTier("May contain", PillTone.Wait, b.mayContain, startOpen = false)
    FindingTier("Check the label", PillTone.Wait, b.checkLabel, startOpen = false)
    FindingTier("Mentioned as avoided", PillTone.Ok, b.mentionedAvoided, startOpen = false)
}

@Composable
private fun FindingTier(title: String, tone: PillTone, rows: List<ImportFinding>, startOpen: Boolean) {
    if (rows.isEmpty()) return
    var open by remember { mutableStateOf(startOpen) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClickLabel = if (open) "Hide $title" else "Show $title") { open = !open },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatusPill("$title (${rows.size})", tone = tone)
        Text(
            if (open) "Hide" else "Show",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (open) {
        rows.forEach { f ->
            Column(Modifier.padding(start = 4.dp, bottom = 6.dp)) {
                Text(boldTerms(f.item, f.terms), style = MaterialTheme.typography.bodyMedium)
                if (f.where.isNotBlank()) {
                    Text(f.where, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                f.reasons.forEach { reason ->
                    Text(
                        boldTerms(reason, f.terms),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** [text] with only the first occurrence of each of [terms] in bold: the key word, nothing else. */
internal fun boldTerms(text: String, terms: List<String>): AnnotatedString = buildAnnotatedString {
    val lower = text.lowercase()
    val ranges = terms.mapNotNull { t ->
        val needle = t.lowercase().trim()
        val i = if (needle.isEmpty()) -1 else lower.indexOf(needle)
        if (i < 0) null else i until (i + needle.length)
    }.sortedBy { it.first }
    var cursor = 0
    for (r in ranges) {
        if (r.first < cursor) continue
        append(text.substring(cursor, r.first))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.substring(r.first, r.last + 1)) }
        cursor = r.last + 1
    }
    append(text.substring(cursor))
}
