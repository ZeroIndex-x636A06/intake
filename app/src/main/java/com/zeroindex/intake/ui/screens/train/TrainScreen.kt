package com.zeroindex.intake.ui.screens.train

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.zeroindex.intake.data.repository.ProgramImportPreview
import com.zeroindex.intake.ui.components.SandNote
import com.zeroindex.intake.ui.screens.plan.readPlanFile
import com.zeroindex.intake.ui.screens.script.ScriptDiagnosticsSheet
import com.zeroindex.intake.ui.screens.script.ScriptMimeTypes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zeroindex.intake.data.db.projection.MuscleSetCount
import com.zeroindex.intake.ui.components.AccentNote
import com.zeroindex.intake.ui.components.Eyebrow
import com.zeroindex.intake.ui.components.Flag
import com.zeroindex.intake.ui.components.IntakeCard
import com.zeroindex.intake.ui.components.SectionLine
import com.zeroindex.intake.ui.components.SolidBanner
import com.zeroindex.intake.ui.components.SubHead
import com.zeroindex.intake.ui.theme.IntakeShape
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun TrainScreen(
    contentPadding: PaddingValues,
    onOpenSession: (String) -> Unit,
    onOpenBuilder: () -> Unit,
    onOpenScriptReference: (page: String, anchor: String?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TrainViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val importState by viewModel.importState.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            viewModel.startReading()
            scope.launch {
                val text = withContext(Dispatchers.IO) { readPlanFile(context, uri) }
                if (text == null) viewModel.importFailed("That file could not be read, or it is too large (the limit is 2 MB).")
                else viewModel.previewImport(text)
            }
        }
    }

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
            SubHead("Programs", "Your plan, your pace. Built to progress steadily.")
        }
        item(key = "import-row") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = { picker.launch(ScriptMimeTypes) },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("Import program") }
                TextButton(
                    onClick = { onOpenScriptReference("program", null) },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("Script help") }
            }
        }
        notice?.let { n ->
            item(key = "notice") {
                SandNote(n)
                TextButton(onClick = viewModel::clearNotice) { Text("Dismiss") }
            }
        }

        val program = state.activeProgram
        val inProgress = state.inProgress

        when {
            // A half-finished session outranks everything else on this screen.
            inProgress != null -> item(key = "resume") {
                IntakeCard {
                    Eyebrow("Session in progress")
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Started ${timeOf(inProgress.startedAt)}",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { onOpenSession(inProgress.id) },
                        shape = IntakeShape.Button,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) { Text("Carry on") }
                }
            }

            program == null -> item(key = "empty") {
                IntakeCard {
                    Text(
                        text = "No program yet",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = "A program here is data with progression rules attached, so the " +
                            "weights move on their own. Start from a four-day upper/lower " +
                            "template and change whatever you like.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { viewModel.createStarterProgram() },
                        enabled = !state.isWorking,
                        shape = IntakeShape.Button,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        Text(if (state.isWorking) "Setting up…" else "Create a starter program")
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onOpenBuilder,
                        shape = IntakeShape.Button,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) { Text("Build one from scratch") }
                }
            }

            else -> {
                item(key = "banner") {
                    SolidBanner(
                        eyebrow = "Active program · week ${program.currentWeek}",
                        title = program.name,
                        body = if (state.days.isEmpty()) {
                            "No days set up yet"
                        } else {
                            "${state.days.size} days/week · next up: " +
                                (state.nextDay?.name ?: "nothing scheduled")
                        },
                    )
                }
                item(key = "nextday") {
                    NextWorkoutCard(
                        dayName = state.nextDay?.name,
                        busy = state.isWorking,
                        onStart = { viewModel.startSession(state.nextDay?.id, onOpenSession) },
                        onFreestyle = { viewModel.startSession(null, onOpenSession) },
                    )
                }
            }
        }

        if (state.days.size > 1) {
            item(key = "days-heading") { SectionLine("Program days") }
            items(items = state.days, key = { "day-${it.id}" }) { day ->
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = IntakeShape.Field,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .heightIn(min = 56.dp)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = day.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedButton(
                            onClick = { viewModel.startSession(day.id, onOpenSession) },
                            shape = IntakeShape.Chip,
                        ) { Text("Start") }
                    }
                }
            }
        }

        item(key = "volume-heading") {
            SectionLine(
                title = "This week's working sets",
                note = "Target ≥${TrainUiState.WEEKLY_SET_FLOOR} / muscle",
            )
        }

        if (state.weeklySets.isEmpty()) {
            item(key = "volume-empty") {
                AccentNote(
                    lead = "Nothing logged in the last seven days",
                    body = "Working sets per muscle group appear here once you finish a session.",
                    container = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(items = state.weeklySets, key = { "muscle-${it.primaryMuscle}" }) { row ->
                MuscleRow(row)
            }
        }

        if (state.recentSessions.isNotEmpty()) {
            item(key = "recent-heading") { SectionLine("Recent sessions") }
            items(items = state.recentSessions, key = { it.id }) { session ->
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = IntakeShape.Field,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .heightIn(min = 54.dp)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = dateOf(session.startedAt),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = if (session.finishedAt != null) {
                                "${(session.finishedAt - session.startedAt) / 60_000} min"
                            } else {
                                "Unfinished"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item(key = "links") {
            Column(
                modifier = Modifier.padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onOpenBuilder,
                    shape = IntakeShape.Button,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text("Program builder") }
            }
        }
    }

    (importState.preview as? ProgramImportPreview.ScriptErrors)?.let { errors ->
        ScriptDiagnosticsSheet(
            diagnostics = errors.diagnostics,
            onHowToFix = { page, anchor -> viewModel.cancelImport(); onOpenScriptReference(page, anchor) },
            onDismiss = viewModel::cancelImport,
        )
    }
    importState.preview?.takeUnless { it is ProgramImportPreview.ScriptErrors }?.let { preview ->
        ProgramImportPreviewDialog(
            preview = preview,
            onConfirm = { (preview as? ProgramImportPreview.Ready)?.let { viewModel.confirmImport(it.batch) } },
            onDismiss = viewModel::cancelImport,
        )
    }
}

/** The mockup's `.nextday`: what is next on the left, a tall Start on the right. */
@Composable
private fun NextWorkoutCard(
    dayName: String?,
    busy: Boolean,
    onStart: () -> Unit,
    onFreestyle: () -> Unit,
) {
    IntakeCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Eyebrow("Next workout")
                Spacer(Modifier.height(2.dp))
                Text(
                    text = dayName ?: "Nothing scheduled",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (dayName == null) {
                        "Start a freestyle session instead"
                    } else {
                        "Progression is applied when you open it"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Button(
                onClick = onStart,
                enabled = !busy,
                shape = IntakeShape.Field,
                modifier = Modifier.heightIn(min = 52.dp),
            ) {
                Text(
                    text = "Start\nworkout",
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = onFreestyle,
            shape = IntakeShape.Button,
            modifier = Modifier.fillMaxWidth().height(50.dp),
        ) { Text("Freestyle session") }
    }
}

/**
 * The mockup's `.muscle` row. SPEC §10 asks for anything under ten working sets
 * a week to be flagged; the flag carries the words "low · under 10" and the row
 * repeats it as a sentence, so the warning never rests on colour alone.
 */
@Composable
private fun MuscleRow(row: MuscleSetCount) {
    val low = row.setCount < TrainUiState.WEEKLY_SET_FLOOR
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = IntakeShape.Field,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 56.dp)
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = row.primaryMuscle.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = if (low) "Below weekly target" else "On track",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "${row.setCount}",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(3.dp))
            Text(
                text = "sets",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (low) {
                Spacer(Modifier.width(6.dp))
                Flag("Low · under ${TrainUiState.WEEKLY_SET_FLOOR}")
            }
        }
    }
}

private fun timeOf(millis: Long): String = DateTimeFormatter.ofPattern("HH:mm")
    .format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

private fun dateOf(millis: Long): String = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm")
    .format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
