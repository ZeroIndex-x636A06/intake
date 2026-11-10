package com.zeroindex.intake.ui.screens.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zeroindex.intake.ui.components.IntakeCard
import com.zeroindex.intake.ui.components.LabeledField
import com.zeroindex.intake.ui.components.SandNote
import com.zeroindex.intake.ui.components.SettingRow
import com.zeroindex.intake.ui.theme.IntakeShape
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Settings > Sync and export, the file half: export everything as JSON (optionally encrypted with a passphrase typed
 * for that file), three CSV spreadsheets, an optional zip of the progress photos, and import. One card holds the
 * controls for the inventory rows `exportPass`, `exportPhotos`, `exportJson`, `exportCsv` and `import`.
 */
@Composable
fun ExportSection(modifier: Modifier = Modifier, viewModel: ExportViewModel = hiltViewModel()) {
    val message by viewModel.message.collectAsStateWithLifecycle()
    val import by viewModel.import.collectAsStateWithLifecycle()
    val lastExport by viewModel.lastExportAt.collectAsStateWithLifecycle()
    val includePhotos by viewModel.includePhotos.collectAsStateWithLifecycle()
    val whenFmt = remember { DateTimeFormatter.ofPattern("MMM d, h:mm a") }
    fun at(millis: Long) = whenFmt.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

    var passphrase by remember { mutableStateOf("") }
    var pendingCsv by remember { mutableStateOf(CsvKind.FOOD) }
    val stamp = remember { java.time.LocalDate.now().toString() }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        uri?.let { viewModel.exportTo(it, passphrase) }
    }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { viewModel.csvTo(it, pendingCsv) }
    }
    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let(viewModel::photosTo)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importFrom)
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        message?.let { m ->
            SandNote(m)
            TextButton(onClick = viewModel::clearMessage) { Text("Dismiss") }
        }
        IntakeCard {
            Text(
                "Everything, deleted rows included, as one file. Importing merges by last change and shows what will change " +
                    "before anything is written. Last export: " + (lastExport?.let(::at) ?: "never") + ".",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LabeledField(
                label = "Export passphrase (optional)",
                value = passphrase,
                onValueChange = { passphrase = it },
                secret = true,
                placeholder = "Leave blank for plain JSON",
                supporting = "Used for the next export only. It is never saved. Without it an encrypted file cannot be opened.",
            )
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { exportLauncher.launch("intake-export-$stamp" + if (passphrase.isEmpty()) ".json" else ".intake") },
                    shape = IntakeShape.Button,
                    modifier = Modifier.weight(1f).height(52.dp),
                ) { Text("Export everything (JSON)") }
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*")) },
                    shape = IntakeShape.Button,
                    modifier = Modifier.weight(1f).height(52.dp),
                ) { Text("Import a backup") }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 10.dp))
            Text("Export spreadsheets (CSV, not encrypted)", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                CsvKind.entries.forEach { kind ->
                    OutlinedButton(
                        onClick = {
                            pendingCsv = kind
                            csvLauncher.launch("intake-${kind.fileStem}-$stamp.csv")
                        },
                        shape = IntakeShape.Chip,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) { Text(kind.label, style = MaterialTheme.typography.labelSmall) }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 10.dp))
            SettingRow(
                title = "Include progress photos in the export",
                subtitle = "Off by default. The JSON file carries only each photo's date and angle. With this on you can also save " +
                    "the photo files as a zip. Photos are never synced or uploaded.",
                showDivider = false,
            ) { Switch(checked = includePhotos, onCheckedChange = viewModel::setIncludePhotos) }
            if (includePhotos) {
                OutlinedButton(
                    onClick = { photoLauncher.launch("intake-progress-photos-$stamp.zip") },
                    shape = IntakeShape.Chip,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("Save photo files (zip)") }
            }
        }
    }

    when (val stage = import) {
        ImportStage.Idle -> Unit
        is ImportStage.NeedsPassphrase -> {
            var pass by remember(stage) { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = viewModel::cancelImport,
                title = { Text("Encrypted backup") },
                text = {
                    Column {
                        Text(if (stage.wrong) "That passphrase did not open it. Try again." else "Enter the passphrase this backup was made with.")
                        LabeledField("Passphrase", pass, { pass = it }, secret = true, isError = stage.wrong)
                    }
                },
                confirmButton = { TextButton(onClick = { viewModel.unlockImport(pass) }, enabled = pass.isNotEmpty()) { Text("Open") } },
                dismissButton = { TextButton(onClick = viewModel::cancelImport) { Text("Cancel") } },
            )
        }
        is ImportStage.Review -> {
            val p = stage.preview
            AlertDialog(
                onDismissRequest = viewModel::cancelImport,
                title = { Text(if (p.totalChanges == 0 && p.settingsCount == 0) "Nothing to change" else "Import ${p.totalChanges} changes?") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        p.exportedAt?.let { Text("Backup made ${at(it)}", style = MaterialTheme.typography.labelSmall) }
                        p.plans.filter { it.changes > 0 || it.keptLocal > 0 }.forEach { plan ->
                            Text(
                                "${plan.table.replace('_', ' ')}: ${plan.added} new, ${plan.updated} updated" +
                                    if (plan.keptLocal > 0) ", ${plan.keptLocal} kept (newer here)" else "",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        p.settingsLine?.let { Text("$it (${p.settingsCount} preferences)", style = MaterialTheme.typography.bodySmall) }
                        if (p.unknownTables.isNotEmpty()) {
                            Text("Ignored, not in this version: ${p.unknownTables.joinToString()}", style = MaterialTheme.typography.labelSmall)
                        }
                        Text("Nothing is deleted. Where both sides changed a row, the later change wins.", style = MaterialTheme.typography.labelSmall)
                    }
                },
                confirmButton = { TextButton(onClick = viewModel::applyImport, enabled = p.totalChanges > 0 || p.settingsCount > 0) { Text("Import") } },
                dismissButton = { TextButton(onClick = viewModel::cancelImport) { Text("Cancel") } },
            )
        }
    }
}
