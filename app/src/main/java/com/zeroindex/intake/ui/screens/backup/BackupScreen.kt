package com.zeroindex.intake.ui.screens.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zeroindex.intake.data.backup.remote.ConnectionTest
import com.zeroindex.intake.data.db.entity.BackupStatus
import com.zeroindex.intake.domain.backup.BackupProtocol
import com.zeroindex.intake.domain.backup.BackupSchedule
import com.zeroindex.intake.domain.backup.WebDavUrlPolicy
import com.zeroindex.intake.ui.components.ErrorState
import com.zeroindex.intake.ui.components.FormRow
import com.zeroindex.intake.ui.components.IntakeCard
import com.zeroindex.intake.ui.components.LabeledField
import com.zeroindex.intake.ui.components.LoadingState
import com.zeroindex.intake.ui.components.PillTone
import com.zeroindex.intake.ui.components.SandNote
import com.zeroindex.intake.ui.components.SectionLine
import com.zeroindex.intake.ui.components.Segmented
import com.zeroindex.intake.ui.components.SettingRow
import com.zeroindex.intake.ui.components.StatusPill
import com.zeroindex.intake.ui.components.SubHead
import com.zeroindex.intake.ui.theme.IntakeShape
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val DAY_MS = 86_400_000L

/**
 * Mockup v2's "Backup & sync": the encrypted nightly backup to a server the
 * user runs, optional device sync through a server the user runs
 * (amendment §3), export/import to a file, and CSVs.
 */
@Composable
fun BackupScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    viewModel: BackupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val test by viewModel.test.collectAsStateWithLifecycle()
    val testing by viewModel.testing.collectAsStateWithLifecycle()
    val backingUp by viewModel.backingUp.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val import by viewModel.import.collectAsStateWithLifecycle()
    val when_ = remember { DateTimeFormatter.ofPattern("MMM d, h:mm a") }
    fun at(millis: Long) = when_.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

    var exportPassphrase by remember { mutableStateOf("") }
    var pendingCsv by remember { mutableStateOf(CsvKind.FOOD) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        uri?.let { viewModel.exportTo(it, exportPassphrase) }
    }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { viewModel.csvTo(it, pendingCsv) }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importFrom)
    }

    val d = draft.destination
    val lastRun = state.runs.firstOrNull { it.status != BackupStatus.RUNNING }
    val lastOk = state.runs.firstOrNull { it.status == BackupStatus.OK }
    val stamp = java.time.LocalDate.now().toString()

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
        item(key = "head") { SubHead("Backup & sync", "Backups are encrypted. Sync is optional and self-hosted.") }
        message?.let { m ->
            item(key = "msg") {
                SandNote(m)
                TextButton(onClick = viewModel::clearMessage) { Text("Dismiss") }
            }
        }

        item(key = "backup-head") {
            SectionLine("Encrypted backup") {
                when {
                    state.settings.backupDestination == null -> StatusPill("Not set up", tone = PillTone.Wait)
                    lastRun?.status == BackupStatus.FAILED -> StatusPill("Last backup failed", tone = PillTone.Bad)
                    lastOk != null -> StatusPill("Last backup · ${at(lastOk.finishedAt ?: lastOk.startedAt)}")
                    else -> StatusPill("No backup yet", tone = PillTone.Wait)
                }
            }
        }
        item(key = "server") {
            IntakeCard {
                Segmented(
                    options = BackupProtocol.entries.map { it.label },
                    selectedIndex = BackupProtocol.entries.indexOf(d.protocol),
                    onSelect = { viewModel.setProtocol(BackupProtocol.entries[it]) },
                    modifier = Modifier.fillMaxWidth(),
                )
                val webdavVerdict = if (d.protocol == BackupProtocol.WEBDAV && d.host.isNotBlank()) WebDavUrlPolicy.check(d.host) else null
                LabeledField(
                    label = when (d.protocol) {
                        BackupProtocol.WEBDAV -> "Server address"
                        BackupProtocol.SFTP -> "Host"
                        BackupProtocol.SMB -> "Host or NAS name"
                    },
                    value = d.host,
                    onValueChange = { v -> viewModel.edit { it.copy(destination = it.destination.copy(host = v.trim())) } },
                    placeholder = if (d.protocol == BackupProtocol.WEBDAV) "https://nas.example.net/dav" else "nas.example.net",
                    keyboardType = KeyboardType.Uri,
                    isError = webdavVerdict is WebDavUrlPolicy.Verdict.Rejected,
                    supporting = when (webdavVerdict) {
                        is WebDavUrlPolicy.Verdict.Rejected -> webdavVerdict.reason
                        WebDavUrlPolicy.Verdict.LocalPlainHttp -> "Plain http: allowed only because this is a local address."
                        else -> null
                    },
                    modifier = Modifier.padding(top = 6.dp),
                )
                if (d.protocol == BackupProtocol.SMB) {
                    FormRow(
                        left = { m ->
                            LabeledField("Share", d.share, { v -> viewModel.edit { it.copy(destination = it.destination.copy(share = v.trim())) } },
                                placeholder = "backups", modifier = m)
                        },
                        right = { m ->
                            LabeledField("Domain · optional", d.domain, { v -> viewModel.edit { it.copy(destination = it.destination.copy(domain = v.trim())) } },
                                placeholder = "WORKGROUP", modifier = m)
                        },
                    )
                }
                FormRow(
                    left = { m ->
                        LabeledField("Path", d.path, { v -> viewModel.edit { it.copy(destination = it.destination.copy(path = v)) } }, modifier = m)
                    },
                    right = { m ->
                        LabeledField("Username", d.username, { v -> viewModel.edit { it.copy(destination = it.destination.copy(username = v.trim())) } }, modifier = m)
                    },
                )
                if (d.protocol == BackupProtocol.SFTP) {
                    LabeledField(
                        label = "Port",
                        value = d.port?.toString().orEmpty(),
                        onValueChange = { v -> viewModel.edit { it.copy(destination = it.destination.copy(port = v.filter(Char::isDigit).take(5).toIntOrNull())) } },
                        placeholder = "22",
                        keyboardType = KeyboardType.Number,
                    )
                }
                val saved = d.protocol in state.secrets.passwords
                LabeledField(
                    label = if (d.protocol == BackupProtocol.SFTP) "Password · or use a key below" else "Password",
                    value = draft.password,
                    onValueChange = { v -> viewModel.edit { it.copy(password = v) } },
                    placeholder = if (saved) "Saved · type to replace" else null,
                    secret = true,
                )
                if (d.protocol == BackupProtocol.SFTP) {
                    Text(
                        if (state.secrets.hasPrivateKey) "Private key saved · paste to replace" else "Private key · optional (OpenSSH or PEM)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 5.dp),
                    )
                    OutlinedTextField(
                        value = draft.privateKey,
                        onValueChange = { v -> viewModel.edit { it.copy(privateKey = v) } },
                        placeholder = { Text("-----BEGIN OPENSSH PRIVATE KEY-----", style = MaterialTheme.typography.labelSmall) },
                        minLines = 2,
                        maxLines = 4,
                        textStyle = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        shape = IntakeShape.Field,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    d.hostKeySha256?.let {
                        Text(
                            "Trusted server key: $it",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
                LabeledField(
                    label = "Backup passphrase",
                    value = draft.passphrase,
                    onValueChange = { v -> viewModel.edit { it.copy(passphrase = v) } },
                    placeholder = if (state.secrets.hasPassphrase) "Saved · type to replace" else "Needed to restore",
                    secret = true,
                    supporting = "Write it down somewhere other than this phone. Without it a backup cannot be opened.",
                )

                OutlinedButton(
                    onClick = viewModel::saveAndTest,
                    enabled = !testing && d.isComplete,
                    shape = IntakeShape.Chip,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                    modifier = Modifier.padding(top = 9.dp).heightIn(min = 44.dp),
                ) { Text(if (testing) "Testing…" else "Save and test connection", fontWeight = FontWeight.ExtraBold) }

                when (val t = test) {
                    null -> Unit
                    is ConnectionTest.Ok -> StatusPill(t.message, modifier = Modifier.padding(top = 8.dp))
                    is ConnectionTest.Failed -> ErrorState(body = t.message)
                    is ConnectionTest.ConfirmHostKey -> {
                        SandNote(
                            title = "Confirm this is your server",
                            body = "First connection to ${d.host}. Its ${t.keyType} key fingerprint is:\n${t.fingerprint}\n" +
                                "On the server, `ssh-keygen -lf /etc/ssh/ssh_host_${t.keyType.removePrefix("ssh-").substringBefore('-')}_key.pub` " +
                                "should print the same. Trust it only if it matches.",
                        )
                        Button(onClick = { viewModel.trustHostKey(t.fingerprint) }, shape = IntakeShape.Chip, modifier = Modifier.padding(top = 6.dp)) {
                            Text("It matches · trust this server")
                        }
                    }
                    is ConnectionTest.HostKeyChanged -> {
                        ErrorState(
                            title = "The server's identity changed",
                            body = "It now presents ${t.fingerprint}. If you reinstalled the server, check that fingerprint on it before " +
                                "trusting the new key. Otherwise do not continue: someone may be intercepting the connection.",
                        )
                        OutlinedButton(onClick = { viewModel.trustHostKey(t.fingerprint) }, shape = IntakeShape.Chip) {
                            Text("I checked it · trust the new key")
                        }
                    }
                }

                SettingRow(title = "Schedule", subtitle = "Nightly runs around 2 AM when the battery is not low") {
                    Segmented(
                        options = listOf("Nightly", "Manual"),
                        selectedIndex = BackupSchedule.entries.indexOf(state.settings.backupSchedule),
                        onSelect = { viewModel.setSchedule(BackupSchedule.entries[it]) },
                    )
                }
                lastOk?.let {
                    Text(
                        "Last backup: ${at(it.finishedAt ?: it.startedAt)} · ${"%.1f".format((it.bytes ?: 0) / 1_048_576.0)} MB · ${it.destination}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                Button(
                    onClick = viewModel::backUpNow,
                    enabled = !backingUp && state.settings.backupDestination != null,
                    shape = IntakeShape.Button,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(52.dp),
                ) { Text(if (backingUp) "Backing up…" else "Back up now") }
                if (state.settings.backupDestination != null) {
                    TextButton(onClick = viewModel::forgetServer) {
                        Text("Forget this server")
                    }
                }
            }
        }
        if (backingUp) item(key = "running") { LoadingState(listOf("Exporting", "Encrypting", "Uploading")) }
        lastRun?.takeIf { it.status == BackupStatus.FAILED }?.let { run ->
            item(key = "failed") {
                ErrorState(
                    title = "Last backup failed · ${at(run.startedAt)}",
                    body = "${run.error ?: "Unknown error."} Your local data is unchanged. Retry when your server is reachable.",
                )
            }
        }

        item(key = "sync") { SyncSection() }

        item(key = "file-head") {
            SectionLine("Export to a file") {
                val last = state.settings.lastExportAtMillis
                when {
                    last == null -> StatusPill("Never exported", tone = PillTone.Wait)
                    state.now - last > 30 * DAY_MS -> StatusPill("Over 30 days ago", tone = PillTone.Wait)
                    else -> StatusPill("Last · ${at(last)}")
                }
            }
        }
        item(key = "file") {
            IntakeCard {
                Text(
                    "Everything, soft-deleted rows included, as one file. Importing merges by last change and shows what " +
                        "will change before anything is written.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LabeledField(
                    label = "Passphrase for this file · recommended",
                    value = exportPassphrase,
                    onValueChange = { exportPassphrase = it },
                    secret = true,
                    placeholder = "Leave blank for plain JSON",
                )
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { exportLauncher.launch("intake-export-$stamp" + if (exportPassphrase.isEmpty()) ".json" else ".intake") },
                        shape = IntakeShape.Button,
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) { Text("Export") }
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*")) },
                        shape = IntakeShape.Button,
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) { Text("Import") }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 10.dp))
                Text("Spreadsheets (CSV, not encrypted)", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                    CsvKind.entries.forEach { kind ->
                        OutlinedButton(
                            onClick = { pendingCsv = kind; csvLauncher.launch("intake-${kind.fileStem}-$stamp.csv") },
                            shape = IntakeShape.Chip,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        ) { Text(kind.label, style = MaterialTheme.typography.labelSmall) }
                    }
                }
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
