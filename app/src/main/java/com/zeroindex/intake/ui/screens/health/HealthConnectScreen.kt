package com.zeroindex.intake.ui.screens.health

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
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
import com.zeroindex.intake.ui.components.Eyebrow
import com.zeroindex.intake.ui.components.Flag
import com.zeroindex.intake.ui.components.IntakeCard
import com.zeroindex.intake.ui.components.SectionLine
import com.zeroindex.intake.ui.components.SettingRow
import com.zeroindex.intake.ui.components.SubHead
import com.zeroindex.intake.ui.components.Tag
import com.zeroindex.intake.ui.theme.IntakeShape
import kotlin.math.roundToInt

/** SPEC §12: every type is requested on its own terms and any can be refused. */
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
            item(key = "perm-head") { SectionLine("What Intake can use", note = "${sync.granted.size} of ${HealthConnectManager.TYPES.size}") }
            item(key = "perms") {
                IntakeCard {
                    HealthConnectManager.TYPES.forEachIndexed { i, type ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(type.label, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    if (type.write) "Intake writes" else "Intake reads",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (type.permission in sync.granted) Tag("Allowed") else Flag("Not allowed")
                        }
                        if (i != HealthConnectManager.TYPES.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { request.launch(HealthConnectManager.ALL_PERMISSIONS) },
                        shape = IntakeShape.Button,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) { Text("Choose what to share") }
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
                sync.lastError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
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
                Eyebrow("How it is counted")
                Text(
                    "Only calories from tracked workouts count, never all-day step estimates. Your resting burn " +
                        "(BMR) is already in your target, so it is taken out of the workout figure first: a watch that " +
                        "reports 600 kcal for an hour's run, for someone burning 1,800 kcal a day at rest, adds " +
                        "600 − 75 = 525 kcal. Where the watch reports \"active\" calories, which already exclude BMR, " +
                        "nothing is taken out twice.",
                    style = MaterialTheme.typography.bodySmall,
                )
                sync.bmrKcalPerDay?.let { bmr ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Your BMR: ${bmr.roundToInt()} kcal a day, ${sync.bmrBasis?.explanation.orEmpty()}.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                state.today?.activeKcalRaw?.takeIf { it > 0 }?.let { raw ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Today: ${raw.roundToInt()} kcal reported − ${(state.today?.bmrKcalInWindows ?: 0.0).roundToInt()} kcal resting " +
                            "= ${(state.today?.activeKcalNet ?: 0.0).roundToInt()} kcal" +
                            if (state.settings.addActiveCaloriesToTarget) " added." else " (not added - the option is off).",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
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
