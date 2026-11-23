package com.zeroindex.intake.ui.screens.train

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.zeroindex.intake.ui.components.AccentButton
import com.zeroindex.intake.ui.components.ChipTone
import com.zeroindex.intake.ui.components.GhostButton
import com.zeroindex.intake.ui.components.HeroCard
import com.zeroindex.intake.ui.components.IconTileStyle
import com.zeroindex.intake.ui.components.IntakeCard
import com.zeroindex.intake.ui.components.IntakeChip
import com.zeroindex.intake.ui.components.ListCard
import com.zeroindex.intake.ui.components.ListDivider
import com.zeroindex.intake.ui.components.SandNote
import com.zeroindex.intake.ui.components.SectionLine
import com.zeroindex.intake.ui.components.SoftButton
import com.zeroindex.intake.ui.components.heroMuted
import com.zeroindex.intake.ui.screens.plan.readPlanFile
import com.zeroindex.intake.ui.screens.script.ScriptDiagnosticsSheet
import com.zeroindex.intake.ui.screens.script.ScriptMimeTypes
import com.zeroindex.intake.ui.screens.script.writeText
import com.zeroindex.intake.ui.theme.IconSize
import com.zeroindex.intake.ui.theme.IntakeIcons
import com.zeroindex.intake.ui.theme.IntakeShape
import com.zeroindex.intake.ui.theme.IntakeSpacing
import com.zeroindex.intake.ui.theme.intake
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
        contentPadding = trainPadding(
            top = contentPadding.calculateTopPadding() + 4.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(IntakeSpacing.StackGap),
    ) {
        notice?.let { n ->
            item(key = "notice") {
                Column {
                    SandNote(n)
                    GhostButton(onClick = viewModel::clearNotice) { Text("Dismiss") }
                }
            }
        }
        item(key = "script-help") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                GhostButton(onClick = { onOpenScriptReference("program", null) }) { Text("Script help") }
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

        item(key = "new-head") { SectionLine("New program") }
        item(key = "new") {
            TileGrid(
                listOf(
                    { m -> TrainTile(IntakeIcons.Sliders, "Make me one", "Fits your days, time and gear", onOpenGenerator, m, IconTileStyle.Lime) },
                    { m -> TrainTile(IntakeIcons.Pencil, "Build from scratch", "Days, exercises, sets, progression", { onOpenBuilder(null) }, m) },
                    { m -> TrainTile(IntakeIcons.Grid, "From a template", "Copy one, change anything", onOpenTemplates, m) },
                    { m -> TrainTile(IntakeIcons.Code, "Import a script", "A program saved as text", { importPicker.launch(ScriptMimeTypes) }, m) },
                ),
            )
        }

        if (state.otherPrograms.isNotEmpty() || state.archived.isNotEmpty()) {
            item(key = "others-head") {
                SectionLine("Your other programs", trailing = {
                    if (state.archived.isNotEmpty()) {
                        GhostButton(onClick = { showArchived = !showArchived }) {
                            Text("${if (showArchived) "Hide" else "Show"} archived (${state.archived.size})")
                        }
                    }
                })
            }
        }
        if (state.otherPrograms.isNotEmpty()) {
            item(key = "others") {
                ListCard {
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
                            if (i != state.otherPrograms.lastIndex) ListDivider()
                        }
                    }
                }
            }
        }
        if (showArchived && state.archived.isNotEmpty()) {
            item(key = "archived") {
                ListCard {
                    Column {
                        state.archived.forEachIndexed { i, p ->
                            TrainRow(
                                title = p.name,
                                trailing = { SoftButton(onClick = { viewModel.restore(p.id) }, small = true) { Text("Restore") } },
                            )
                            if (i != state.archived.lastIndex) ListDivider()
                        }
                    }
                }
            }
        }

        item(key = "workouts") {
            MyWorkoutsSection(onOpenSession = onOpenSession, onEditWorkout = { onOpenBuilder(it) }, onOpenVideo = onOpenVideo)
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
    val onHero = MaterialTheme.intake.onHero
    HeroCard {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                IntakeChip("Active", tone = ChipTone.Lime)
                Text(
                    program.name,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Text(
                    buildString {
                        append("Week ${program.currentWeek}")
                        if (dayLines.isNotEmpty()) append(" · ${dayLines.size} ${if (dayLines.size == 1) "day" else "days"}")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = heroMuted(),
                )
                if (program.description.isNotBlank()) {
                    Text(program.description, style = MaterialTheme.typography.bodyMedium, color = heroMuted())
                }
            }
            Box {
                IconButton(
                    onClick = { menu = true },
                    modifier = Modifier.size(IntakeSpacing.TouchTarget).background(onHero.copy(alpha = 0.14f), IntakeShape.Pill),
                ) {
                    Icon(IntakeIcons.More, contentDescription = "More for ${program.name}", tint = onHero, modifier = Modifier.size(IconSize.Default))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Duplicate") }, onClick = { menu = false; onDuplicate() })
                    DropdownMenuItem(text = { Text("Export as a script") }, onClick = { menu = false; onExport() })
                    DropdownMenuItem(text = { Text("Archive") }, onClick = { menu = false; onArchive() })
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (dayLines.isEmpty()) {
            Text("No days yet. Edit the program to add some.", style = MaterialTheme.typography.bodyMedium, color = heroMuted())
        } else {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                dayLines.forEach { (name, weekday) ->
                    Surface(color = onHero.copy(alpha = 0.14f), contentColor = onHero, shape = IntakeShape.Chip) {
                        Text(
                            if (weekday != null) "$name · $weekday" else name,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            AccentButton(onClick = onEdit, modifier = Modifier.weight(1f)) { Text("Edit program") }
            Button(
                onClick = onExport,
                shape = IntakeShape.Button,
                colors = ButtonDefaults.buttonColors(containerColor = onHero.copy(alpha = 0.14f), contentColor = onHero),
                modifier = Modifier.weight(1f).heightIn(min = IntakeSpacing.TouchTarget),
            ) { Text("Export as script") }
        }
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
    TrainRow(
        title = program.name,
        subtitle = program.description.takeIf { it.isNotBlank() },
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SoftButton(onClick = onMakeActive, small = true) { Text("Make active") }
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(IntakeIcons.More, contentDescription = "More for ${program.name}", modifier = Modifier.size(IconSize.Default))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Edit") }, onClick = { menu = false; onEdit() })
                        DropdownMenuItem(text = { Text("Duplicate") }, onClick = { menu = false; onDuplicate() })
                        DropdownMenuItem(text = { Text("Export as a script") }, onClick = { menu = false; onExport() })
                        DropdownMenuItem(text = { Text("Archive") }, onClick = { menu = false; onArchive() })
                    }
                }
            }
        },
    )
}
