package com.zeroindex.intake.ui.screens.backup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zeroindex.intake.data.db.entity.SyncConflictEntity
import com.zeroindex.intake.domain.sync.SyncMerge
import com.zeroindex.intake.ui.components.ErrorState
import com.zeroindex.intake.ui.components.IntakeCard
import com.zeroindex.intake.ui.components.LabeledField
import com.zeroindex.intake.ui.components.PillTone
import com.zeroindex.intake.ui.components.ResultCard
import com.zeroindex.intake.ui.components.SandNote
import com.zeroindex.intake.ui.components.SectionLine
import com.zeroindex.intake.ui.components.StatusPill
import com.zeroindex.intake.ui.theme.IntakeShape
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private fun ago(millis: Long?): String {
    if (millis == null) return "never"
    val d = Duration.ofMillis((System.currentTimeMillis() - millis).coerceAtLeast(0))
    return when {
        d.toMinutes() < 1 -> "just now"
        d.toHours() < 1 -> "${d.toMinutes()} min ago"
        d.toDays() < 1 -> "${d.toHours()} h ago"
        else -> "${d.toDays()} d ago"
    }
}

/** Mockup v2's "Optional device sync" block, with the conflict review below it. */
@Composable
fun SyncSection(viewModel: SyncViewModel = hiltViewModel()) {
    val sync by viewModel.sync.collectAsStateWithLifecycle()
    val conflicts by viewModel.conflicts.collectAsStateWithLifecycle()
    val serverUrl by viewModel.serverUrl.collectAsStateWithLifecycle()
    val code by viewModel.code.collectAsStateWithLifecycle()
    val deviceName by viewModel.deviceName.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val newCode by viewModel.newCode.collectAsStateWithLifecycle()

    LaunchedEffect(sync.paired) { if (sync.paired) viewModel.loadDevices() }

    Column {
        SectionLine("Optional device sync") {
            when {
                !sync.paired -> StatusPill("Not paired", tone = PillTone.Wait)
                busy -> StatusPill("Syncing…", tone = PillTone.Wait)
                sync.lastError != null -> StatusPill("Sync failed", tone = PillTone.Bad)
                else -> StatusPill("Up to date · ${ago(sync.lastSyncAt)}")
            }
        }
        IntakeCard {
            if (!sync.paired) {
                LabeledField(
                    label = "Self-hosted sync server URL",
                    value = serverUrl,
                    onValueChange = { viewModel.serverUrl.value = it.trim() },
                    placeholder = "https://intake-sync.example.com",
                    keyboardType = KeyboardType.Uri,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LabeledField(
                        label = "Pairing code",
                        value = code,
                        onValueChange = { viewModel.code.value = it.uppercase().take(9) },
                        placeholder = "QCBQ-8FQS",
                        modifier = Modifier.weight(1f),
                    )
                    LabeledField(
                        label = "Name this device",
                        value = deviceName,
                        onValueChange = { viewModel.deviceName.value = it.take(40) },
                        modifier = Modifier.weight(1f),
                    )
                }
                Button(
                    onClick = viewModel::pair,
                    enabled = !busy && serverUrl.startsWith("http") && code.count { it.isLetterOrDigit() } == 8,
                    shape = IntakeShape.Button,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(52.dp),
                ) { Text(if (busy) "Pairing…" else "Pair this device") }
                Text(
                    "The server prints a one-time code when it first starts, or run `intake_sync.py pair` on it. " +
                        "An already paired device can also make one with Add another device. See server/README.md.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            } else {
                Text(sync.serverUrl.orEmpty(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Last sync ${ago(sync.lastSyncAt)}. Syncs on its own every half hour with a network, and shortly after you log something.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = viewModel::syncNow,
                    enabled = !busy,
                    shape = IntakeShape.Button,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(52.dp),
                ) { Text(if (busy) "Syncing…" else "Sync now") }

                newCode?.let { c ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = IntakeShape.InnerCard,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    ) {
                        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("PAIR DEVICE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold)
                            Text(c.code, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, letterSpacing = 2.sp)
                            Text(
                                "On the other device: Backup & sync → this server's address → this code. " +
                                    "Works once, until ${DateTimeFormatter.ofPattern("h:mm a").format(Instant.ofEpochMilli(c.expiresAt).atZone(ZoneId.systemDefault()))}.",
                                style = MaterialTheme.typography.labelSmall,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))
                devices.forEach { d ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = MaterialTheme.colorScheme.primary, shape = CircleShape, modifier = Modifier.size(9.dp)) {}
                        Spacer(Modifier.width(8.dp))
                        Text(d.name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Text(
                            " · " + if (d.current) "this device" else "seen ${ago(d.lastSeenAt)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        if (!d.current) TextButton(onClick = { viewModel.remove(d) }, enabled = !busy) { Text("Remove") }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedButton(onClick = viewModel::addDevice, enabled = !busy, shape = IntakeShape.Chip) { Text("Add another device") }
                    TextButton(onClick = viewModel::unpair, enabled = !busy) { Text("Stop syncing here") }
                }
            }
            (error ?: sync.lastError)?.let { ErrorState(body = "$it Your data on this device is unchanged.") }
        }

        SandNote(
            title = "If edits conflict",
            body = "The newer edit wins on every device. The other version is kept below with what changed, " +
                "so you can bring it back. Nothing is silently overwritten.",
        )
        if (conflicts.isNotEmpty()) {
            SectionLine("Conflicts to review", note = "${conflicts.size} open")
            conflicts.forEach { c -> ConflictCard(c, onRestore = { viewModel.restore(c) }, onKeep = { viewModel.keepCurrent(c) }) }
        }
    }
}

@Composable
private fun ConflictCard(conflict: SyncConflictEntity, onRestore: () -> Unit, onKeep: () -> Unit) {
    val changes = SyncViewModel.changes(conflict)
    ResultCard(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            conflict.tableName.replace('_', ' ').replaceFirstChar(Char::uppercase) + " · edited on two devices",
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            "The other version was made on ${if (conflict.otherSource == SyncMerge.THIS_DEVICE) "this device" else "another device"}.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        changes.take(5).forEach { ch ->
            Text("${ch.field}: ${ch.kept.take(40)} → ${ch.other.take(40)}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 3.dp))
        }
        if (changes.size > 5) Text("and ${changes.size - 5} more fields", style = MaterialTheme.typography.labelSmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onKeep) { Text("Keep current") }
            TextButton(onClick = onRestore) { Text("Use the other version") }
        }
    }
}
