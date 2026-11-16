package com.zeroindex.intake.ui.screens.train

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zeroindex.intake.data.db.entity.ProgramEntity
import com.zeroindex.intake.data.repository.ProgramImportPreview
import com.zeroindex.intake.domain.training.WorkoutSchedule
import com.zeroindex.intake.ui.components.Eyebrow
import com.zeroindex.intake.ui.components.IntakeCard
import com.zeroindex.intake.ui.components.SandNote
import com.zeroindex.intake.ui.screens.hub.HubItem
import com.zeroindex.intake.ui.screens.hub.HubList
import com.zeroindex.intake.ui.screens.plan.readPlanFile
import com.zeroindex.intake.ui.screens.script.ScriptDiagnosticsSheet
import com.zeroindex.intake.ui.screens.script.ScriptMimeTypes
import com.zeroindex.intake.ui.screens.script.writeText
import com.zeroindex.intake.ui.theme.IntakeShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Programs (IA v5 6d), one tap inside Train. The active program with its days, the others, new programs (build, generate,
 * template, import a script), My workouts, and archived programs. Editing a program opens the builder from here only.
 */
@Composable
fun ProgramsScreen(
    contentPadding: PaddingValues,
    onOpenSession: (String) -> Unit,
    onOpenBuilder: (programId: String?) -> Unit,
    onOpenGenerator: () -> Unit,
    onOpenTemplates: () -> Unit,
    onOpenVideo: (videoId: String) -> Unit,
    onOpenScriptReference: (page: String, anchor: String?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProgramsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val importState by viewModel.importState.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmArchive by remember { mutableStateOf<ProgramEntity?>(null) }
    var showArchived by remember { mutableStateOf(false) }
    var pendingExport by remember { mutableStateOf<String?>(null) }

    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            viewModel.startReading()
            scope.launch {
                val text = withContext(Dispatchers.IO) { readPlanFile(context, uri) }
                if (text == null) viewModel.importFailed("That file could not be read, or it is too large (the limit is 2 MB).")
                else viewModel.previewImport(text)
            }
        }
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        val text = pendingExport
        pendingExport = null
        if (uri != null && text != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) { writeText(context, uri, text) }
                viewModel.showNotice(if (ok) "Saved the program as a script." else "That file could not be saved.")
            }
        }
    }
    val export = { program: ProgramEntity ->
        scope.launch {
            val pair = viewModel.exportScript(program.id)
            if (pair == null) {
                viewModel.showNotice("That program could not be exported.")
            } else {
                pendingExport = pair.first
                exporter.launch(pair.second)
            }
        }
        Unit
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 4.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
            start = 16.dp,
            end = 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        notice?.let { n ->
            item(key = "notice") {
                Column {
                    SandNote(n)
                    TextButton(onClick = viewModel::clearNotice, modifier = Modifier.heightIn(min = 48.dp)) { Text("Dismiss") }
                }
            }
        }

        val active = state.activeProgram
        item(key = "active") {
            if (active == null) {
                IntakeCard {
                    Text("No active program", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "A program is your plan for the week: which exercises, how many sets and reps, and how the weight goes up. You can train without one. Start whichever way suits you below.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            } else {
                ActiveProgramCard(
                    program = active,
                    dayLines = state.days.map { d -> d.name to d.weekday?.let { WorkoutSchedule.weekdayName(it, short = true) } },
                    onEdit = { onOpenBuilder(active.id) },
                    onDuplicate = { viewModel.duplicate(active.id) },
                    onArchive = { confirmArchive = active },
                    onExport = { export(active) },
                )
            }
        }

        if (state.otherPrograms.isNotEmpty()) {
            item(key = "others-head") { Eyebrow("Your other programs") }
            item(key = "others") {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = IntakeShape.Card,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        state.otherPrograms.forEachIndexed { i, p ->
                            OtherProgramRow(
                                program = p,
                                onMakeActive = { viewModel.activate(p.id) },
                                onEdit = { onOpenBuilder(p.id) },
                                onDuplicate = { viewModel.duplicate(p.id) },
                                onArchive = { confirmArchive = p },
                                onExport = { export(p) },
                            )
                            if (i != state.otherPrograms.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }

        item(key = "new-head") { Eyebrow("New program") }
        item(key = "new") {
            HubList(
                listOf(
                    HubItem("Make me one", "Answer a few questions and get a plan that fits your days, time and equipment.", Icons.Filled.AutoAwesome, onOpenGenerator),
                    HubItem("Build from scratch", "Name it, add days, pick exercises, set sets, reps and progression.", Icons.Filled.Edit, { onOpenBuilder(null) }),
                    HubItem("From a template", "Start from a well known plan and change anything.", Icons.Filled.ContentCopy, onOpenTemplates),
                    HubItem("Import a script", "Open a program saved as text, here or from a friend.", Icons.Filled.FileOpen, { importPicker.launch(ScriptMimeTypes) }),
                ),
            )
        }
        item(key = "script-help") {
            TextButton(onClick = { onOpenScriptReference("program", null) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Script help") }
        }

        item(key = "workouts") {
            MyWorkoutsSection(onOpenSession = onOpenSession, onEditWorkout = { onOpenBuilder(it) }, onOpenVideo = onOpenVideo)
        }

        if (state.archived.isNotEmpty()) {
            item(key = "archived") {
                Column {
                    TextButton(onClick = { showArchived = !showArchived }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("${if (showArchived) "Hide" else "Show"} archived programs (${state.archived.size})")
                    }
                    if (showArchived) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = IntakeShape.Card,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column {
                                state.archived.forEachIndexed { i, p ->
                                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(p.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                        TextButton(onClick = { viewModel.restore(p.id) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Restore") }
                                    }
                                    if (i != state.archived.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    confirmArchive?.let { p ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmArchive = null },
            title = { Text("Archive ${p.name}?") },
            text = { Text("It leaves your list and shows under archived programs, where you can restore it. Workouts you logged with it are kept.") },
            confirmButton = { TextButton(onClick = { viewModel.archive(p.id); confirmArchive = null }) { Text("Archive") } },
            dismissButton = { TextButton(onClick = { confirmArchive = null }) { Text("Keep") } },
        )
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

@Composable
private fun ActiveProgramCard(
    program: ProgramEntity,
    dayLines: List<Pair<String, String?>>,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onArchive: () -> Unit,
    onExport: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    IntakeCard(color = MaterialTheme.colorScheme.primaryContainer) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Eyebrow("Active program · week ${program.currentWeek}", color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(program.name, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                if (program.description.isNotBlank()) {
                    Text(program.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More for ${program.name}", tint = MaterialTheme.colorScheme.onPrimaryContainer) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Duplicate") }, onClick = { menu = false; onDuplicate() })
                    DropdownMenuItem(text = { Text("Export as a script") }, onClick = { menu = false; onExport() })
                    DropdownMenuItem(text = { Text("Archive") }, onClick = { menu = false; onArchive() })
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (dayLines.isEmpty()) {
            Text("No days yet. Edit the program to add some.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        } else {
            dayLines.forEach { (name, weekday) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text(name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.weight(1f))
                    weekday?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer) }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onEdit, shape = IntakeShape.Button, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Edit program") }
    }
}

@Composable
private fun OtherProgramRow(
    program: ProgramEntity,
    onMakeActive: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onArchive: () -> Unit,
    onExport: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(program.name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            if (program.description.isNotBlank()) {
                Text(program.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        OutlinedButton(onClick = onMakeActive, shape = IntakeShape.Chip, modifier = Modifier.heightIn(min = 48.dp)) { Text("Make active") }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More for ${program.name}") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Edit") }, onClick = { menu = false; onEdit() })
                DropdownMenuItem(text = { Text("Duplicate") }, onClick = { menu = false; onDuplicate() })
                DropdownMenuItem(text = { Text("Export as a script") }, onClick = { menu = false; onExport() })
                DropdownMenuItem(text = { Text("Archive") }, onClick = { menu = false; onArchive() })
            }
        }
    }
}
