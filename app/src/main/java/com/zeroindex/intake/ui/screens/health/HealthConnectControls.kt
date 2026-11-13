package com.zeroindex.intake.ui.screens.health

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zeroindex.intake.data.health.HealthAvailability
import com.zeroindex.intake.data.health.HealthConnectManager
import com.zeroindex.intake.ui.components.AccentNote
import com.zeroindex.intake.ui.components.ErrorState
import com.zeroindex.intake.ui.components.IntakeCard
import com.zeroindex.intake.ui.components.StatusPill
import com.zeroindex.intake.ui.components.SwitchRow
import com.zeroindex.intake.ui.theme.IntakeShape
import kotlin.math.roundToInt

private fun formatSyncTime(millis: Long): String =
    java.time.format.DateTimeFormatter.ofPattern("d MMM, HH:mm")
        .format(java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()))

/** SPEC §12: every type is requested on its own terms and any can be refused; revoking happens in Health Connect. */
private fun openHealthConnectSettings(context: android.content.Context) {
    runCatching {
        context.startActivity(Intent("androidx.health.ACTION_HEALTH_CONNECT_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** The id an inventory row gives a Health Connect type: `hc.read.Steps`, `hc.write.Hydration`. */
fun healthControlId(type: com.zeroindex.intake.data.health.HealthDataType): String =
    "hc.${if (type.write) "write" else "read"}.${type.label.filter { it.isLetterOrDigit() }}"

/**
 * The status block at the top of Settings > Health Connect: whether it is available, when it last synced and how many
 * records of each type came in (device feedback B6), and today's headline numbers.
 */
@Composable
fun HealthStatusHeader(modifier: Modifier = Modifier, viewModel: HealthConnectViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val sync = state.sync
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (sync.availability) {
            HealthAvailability.AVAILABLE -> Unit
            HealthAvailability.NOT_INSTALLED, HealthAvailability.UPDATE_REQUIRED -> IntakeCard {
                Text(
                    if (sync.availability == HealthAvailability.NOT_INSTALLED) "Health Connect is not installed" else "Health Connect needs an update",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text("Nothing else in Intake depends on it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.healthdata")))
                        }
                    },
                    shape = IntakeShape.Button,
                ) { Text("Open in Play Store") }
            }
            HealthAvailability.UNSUPPORTED -> AccentNote(
                lead = "Not available on this phone",
                body = "Health Connect is not supported here. Everything else works as normal.",
                container = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (sync.availability == HealthAvailability.AVAILABLE) {
            val lastAt = state.settings.healthLastSyncAt
            val summary = sync.report?.summary() ?: state.settings.healthLastReport
            IntakeCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Last synced", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    val shown = HealthConnectManager.TYPES.filter { sync.historySupported || it.permission != HealthConnectManager.HISTORY_PERMISSION }
                    StatusPill("${shown.count { it.permission in sync.granted }} of ${shown.size} allowed")
                }
                Text(
                    lastAt?.let { formatSyncTime(it) } ?: "Not synced yet. The first sync reads the last 30 days.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (summary != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(summary, style = MaterialTheme.typography.bodySmall)
                }
                state.today?.let { t ->
                    val bits = listOfNotNull(
                        t.steps?.let { "%,d steps".format(it) },
                        t.sleepMinutes?.let { "slept ${it / 60}h ${it % 60}m" },
                        t.restingHeartRate?.let { "resting ${it.roundToInt()} bpm" },
                    )
                    if (bits.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text("Today so far: ${bits.joinToString(" · ")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = viewModel::syncNow, enabled = !sync.syncing, shape = IntakeShape.Chip) {
                    Text(if (sync.syncing) "Syncing…" else "Sync now")
                }
                Text(
                    "Syncs when you open the app and after you log something, never on a timer.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            sync.lastError?.let { ErrorState(title = "Could not read everything", body = "$it Your local data is unchanged.") }
        }
    }
}

/** One Health Connect control by inventory id: a per-type switch, or the "request permissions" action. */
@Composable
fun HealthControl(id: String, modifier: Modifier = Modifier, viewModel: HealthConnectViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val sync = state.sync
    val scope = rememberCoroutineScope()
    var allowAllAsked by remember { mutableStateOf(false) }
    val request = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {
        viewModel.refresh()
        viewModel.syncNow()
    }
    if (sync.availability != HealthAvailability.AVAILABLE) {
        if (id == "hcOpen") Unit
        return
    }
    val type = HealthConnectManager.TYPES.firstOrNull { healthControlId(it) == id }
    Column(modifier) {
        // The history permission exists only on newer Health Connect; where it does not, there is no switch to show.
        if (type != null && type.permission == HealthConnectManager.HISTORY_PERMISSION && !sync.historySupported) return@Column
        if (type != null) {
            val granted = type.permission in sync.granted
            SwitchRow(
                title = (if (type.write) "Write: " else "Read: ") + type.label,
                subtitle = type.why,
                on = granted,
                onToggle = {
                    // Health Connect can grant one type at a time but only revokes from its own screen.
                    if (granted) openHealthConnectSettings(context) else runCatching { request.launch(setOf(type.permission)) }
                },
            )
        } else if (id == "hcOpen") {
            // One system sheet for every read and write permission, so nothing has to be switched on one by one.
            val everythingAllowed = sync.granted.containsAll(HealthConnectManager.ALL_PERMISSIONS - if (sync.historySupported) emptySet() else setOf(HealthConnectManager.HISTORY_PERMISSION))
            Button(
                onClick = {
                    allowAllAsked = true
                    scope.launch { runCatching { request.launch(viewModel.allPermissions()) } }
                },
                enabled = !everythingAllowed,
                shape = IntakeShape.Button,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text(if (everythingAllowed) "Everything is allowed" else "Allow all") }
            Text(
                "Asks for every read and write permission below in one sheet. You can still switch any of them off later in Health Connect.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (allowAllAsked && !everythingAllowed && !sync.syncing) {
                // Android stops showing the sheet after a permission was refused twice; Health Connect's own screen still works.
                OutlinedButton(
                    onClick = { openHealthConnectSettings(context) },
                    shape = IntakeShape.Button,
                    modifier = Modifier.fillMaxWidth().height(48.dp).padding(top = 4.dp),
                ) { Text("Nothing changed? Open Health Connect") }
            }
        }
    }
}
