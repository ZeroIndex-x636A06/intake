package com.zeroindex.intake.ui.screens.health

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.zeroindex.intake.ui.components.SwitchRow
import com.zeroindex.intake.ui.components.StatusPill
import com.zeroindex.intake.ui.components.EquationStrip
import com.zeroindex.intake.ui.components.ErrorState
import com.zeroindex.intake.ui.components.EmptyState
import com.zeroindex.intake.ui.components.IntakeCard
import com.zeroindex.intake.ui.components.SectionLine
import com.zeroindex.intake.ui.components.SettingRow
import com.zeroindex.intake.ui.components.SubHead
import com.zeroindex.intake.ui.theme.IntakeShape
import kotlin.math.roundToInt

private fun formatSyncTime(millis: Long): String =
    java.time.format.DateTimeFormatter.ofPattern("d MMM, HH:mm")
        .format(java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()))

/** SPEC §12: every type is requested on its own terms and any can be refused. */
private fun openHealthConnectSettings(context: android.content.Context) {
    runCatching {
        context.startActivity(Intent("androidx.health.ACTION_HEALTH_CONNECT_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@Composable
fun HealthConnectScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    viewModel: HealthConnectViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val request = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {
        viewModel.refresh()
        viewModel.syncNow()
    }
    val sync = state.sync

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
            start = 16.dp,
            end = 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "head") {
            SubHead("Health Connect", "Shares data with your watch and scale apps. Every type is optional.")
        }

        when (sync.availability) {
            HealthAvailability.AVAILABLE -> Unit
            HealthAvailability.NOT_INSTALLED, HealthAvailability.UPDATE_REQUIRED -> item(key = "install") {
                IntakeCard {
                    Text(
                        if (sync.availability == HealthAvailability.NOT_INSTALLED) "Health Connect is not installed" else "Health Connect needs an update",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        "Nothing else in Intake depends on it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.healthdata")),
                                )
                            }
                        },
                        shape = IntakeShape.Button,
                    ) { Text("Open in Play Store") }
                }
            }
            HealthAvailability.UNSUPPORTED -> item(key = "unsupported") {
                AccentNote(
                    lead = "Not available on this phone",
                    body = "Health Connect is not supported here. Everything else works as normal.",
                    container = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (sync.availability == HealthAvailability.AVAILABLE) {
            listOf(false to "Intake reads", true to "Intake writes").forEach { (writes, heading) ->
                val types = HealthConnectManager.TYPES.filter { it.write == writes }
                item(key = "perm-head-$writes") {
                    SectionLine(heading) {
                        StatusPill("${types.count { it.permission in sync.granted }} of ${types.size} allowed")
                    }
                }
                item(key = "perms-$writes") {
                    IntakeCard {
                        types.forEachIndexed { i, type ->
                            val granted = type.permission in sync.granted
                            SwitchRow(
                                title = type.label,
                                subtitle = type.why,
                                on = granted,
                                onToggle = {
                                    // Health Connect can grant one type at a time but only revokes
                                    // from its own screen, so "off" opens it there.
                                    if (granted) openHealthConnectSettings(context) else runCatching { request.launch(setOf(type.permission)) }
                                },
                            )
                            if (i != types.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
            val off = HealthConnectManager.TYPES.filter { !it.write && it.permission !in sync.granted }
            if (off.size == HealthConnectManager.TYPES.count { !it.write }) {
                item(key = "none") {
                    EmptyState(
                        title = "No connected health data",
                        body = "Grant only the permissions you want; manual tracking still works.",
                    )
                }
            }
            item(key = "sync") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = viewModel::syncNow, enabled = !sync.syncing, shape = IntakeShape.Chip) {
                        Text(if (sync.syncing) "Syncing…" else "Sync now")
                    }
                    Text(
                        "Syncs when you open the app and after you log something - never on a timer.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val lastAt = state.settings.healthLastSyncAt
                val summary = sync.report?.summary() ?: state.settings.healthLastReport
                Spacer(Modifier.height(8.dp))
                IntakeCard {
                    Text("Last synced", style = MaterialTheme.typography.titleSmall)
                    Text(
                        lastAt?.let { formatSyncTime(it) } ?: "Not synced yet. The first sync reads the last 30 days.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (summary != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(summary, style = MaterialTheme.typography.bodySmall)
                    }
                }
                sync.lastError?.let { ErrorState(title = "Could not read everything", body = "$it Your local data is unchanged.") }
            }
        }

        item(key = "activity-head") { SectionLine("Workout calories and your target") }
        item(key = "activity") {
            IntakeCard {
                SettingRow(
                    title = "Add workout calories to my target",
                    subtitle = "Off by default. Wearable burn estimates are often high, and counting them twice is the " +
                        "most common way a cut quietly stalls.",
                    showDivider = false,
                ) { Switch(checked = state.settings.addActiveCaloriesToTarget, onCheckedChange = viewModel::setAddActivity) }
                Spacer(Modifier.height(8.dp))
                Text("How active calories are counted", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Intake adds active energy from tracked workouts, not total daily burn. Your basal metabolic rate " +
                        "is already in your target, so it is taken out of the raw burn for the same minutes. Where a " +
                        "watch reports \"active\" calories, which already exclude BMR, nothing is taken out twice.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val today = state.today
                val raw = today?.activeKcalRaw?.takeIf { it > 0 }
                if (raw != null) {
                    EquationStrip(
                        terms = listOf(
                            "%,d".format(raw.roundToInt()) to "raw burn\nin window",
                            "%,d".format((today.bmrKcalInWindows ?: 0.0).roundToInt()) to "BMR\nsame window",
                            "%,d".format((today.activeKcalNet ?: 0.0).roundToInt()) to
                                if (state.settings.addActiveCaloriesToTarget) "added\nactive kcal" else "not added\n(option off)",
                        ),
                        operators = listOf("−", "="),
                    )
                } else {
                    EquationStrip(
                        terms = listOf("600" to "raw burn\nexample hour", "75" to "BMR\nsame hour", "525" to "would be\nadded"),
                        operators = listOf("−", "="),
                    )
                }
                sync.bmrKcalPerDay?.let { bmr ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Your BMR: ${"%,d".format(bmr.roundToInt())} kcal a day, ${sync.bmrBasis?.explanation.orEmpty()}.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                if (sync.availability == HealthAvailability.AVAILABLE) {
                    val offReads = HealthConnectManager.TYPES.filter { !it.write && it.permission !in sync.granted }
                    if (offReads.isNotEmpty() && offReads.size < HealthConnectManager.TYPES.count { !it.write }) {
                        EmptyState(
                            body = "Not allowed: ${offReads.joinToString { it.label.lowercase() }}. None of that is read.",
                        )
                    }
                }
            }
        }
        item(key = "muscle") {
            AccentNote(
                lead = "About skeletal muscle",
                body = "Health Connect has no skeletal-muscle data type, so Intake reads lean body mass from it instead - a " +
                    "related but different measure. If your scale app shows skeletal muscle, you can enter it with a weigh-in.",
                container = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
