package com.zeroindex.intake.ui.screens.settings

import com.zeroindex.intake.domain.units.format
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zeroindex.intake.data.settings.AppSettings
import com.zeroindex.intake.domain.settings.SettingSpec
import com.zeroindex.intake.domain.settings.SettingsGroup
import com.zeroindex.intake.domain.settings.SettingsRegistry
import com.zeroindex.intake.ui.navigation.PushedScreen
import com.zeroindex.intake.ui.screens.health.HealthStatusHeader
import com.zeroindex.intake.ui.theme.IntakeShape

private fun SettingsGroup.icon(): ImageVector = when (this) {
    SettingsGroup.APPEARANCE -> Icons.Filled.Palette
    SettingsGroup.NUTRITION -> Icons.Filled.Restaurant
    SettingsGroup.WATER -> Icons.Filled.WaterDrop
    SettingsGroup.TRAINING -> Icons.Filled.FitnessCenter
    SettingsGroup.HEALTH -> Icons.Filled.Favorite
    SettingsGroup.FOOD_DB -> Icons.Filled.Storage
    SettingsGroup.VIDEOS -> Icons.Filled.PlayCircle
    SettingsGroup.REMINDERS -> Icons.Filled.Notifications
    SettingsGroup.SYNC -> Icons.Filled.Cloud
    SettingsGroup.AI -> Icons.Filled.AutoAwesome
    SettingsGroup.KEYS -> Icons.Filled.Key
    SettingsGroup.ABOUT -> Icons.Filled.Info
}

/** The one-line summary under a group's name on the Settings home, from the live settings. */
internal fun groupSummary(group: SettingsGroup, s: AppSettings, hasOpenRouter: Boolean, hasYoutube: Boolean, hasUsda: Boolean): String = when (group) {
    SettingsGroup.APPEARANCE -> "${s.weightUnit.label} · ${s.lengthUnit.label} · ${s.volumeUnit.label} · ${s.themeMode.name.lowercase().replaceFirstChar(Char::uppercase)}"
    SettingsGroup.NUTRITION -> (if (s.autoCalculateTargets) "Auto targets" else "Targets by hand") + " · ${s.proteinGramsPerKg} g protein per kg"
    SettingsGroup.WATER -> (s.hydrationBaseMl?.let { s.volumeUnit.format(it) } ?: "Target from your weight") + if (s.hydrationDynamic) " · adjusts daily" else ""
    SettingsGroup.TRAINING -> "${s.defaultRestSec / 60}:${"%02d".format(s.defaultRestSec % 60)} rest · ${s.weightUnit.format(s.barWeightKg)} bar"
    SettingsGroup.HEALTH -> "Read and write switches per record type"
    SettingsGroup.FOOD_DB -> "Open Food Facts and offline USDA"
    SettingsGroup.VIDEOS -> (if (s.onlyWorkouts) "Workouts only" else "Everything") + if (hasYoutube) " · key set" else " · no key"
    SettingsGroup.REMINDERS -> (if (s.coachNotify) "Check-in on" else "Check-in off") +
        (if (s.photoReminderWeeks > 0) " · photo ${s.photoReminderWeeks} wk" else "") +
        listOfNotNull(
            "workout".takeIf { s.workoutReminderOn },
            "meals".takeIf { s.mealReminderOn },
            "water".takeIf { s.waterReminderOn },
        ).let { if (it.isEmpty()) "" else " · " + it.joinToString(", ") }
    SettingsGroup.SYNC -> if (s.sync.paired) "Paired" else "Not paired · export to a file"
    SettingsGroup.AI -> if (s.aiEnabled) (if (hasOpenRouter) "On · \$${"%.0f".format(s.aiMonthlyBudgetUsd)} cap" else "On · no key") else "Off"
    SettingsGroup.KEYS -> listOf("OpenRouter" to hasOpenRouter, "USDA" to hasUsda, "YouTube" to hasYoutube).filter { it.second }.joinToString(" · ") { it.first }.ifEmpty { "No keys saved" }
    SettingsGroup.ABOUT -> "Tour, scripts help, privacy, credits"
}

/** Settings home: a search field and one row per group. It holds only settings. */
@Composable
fun SettingsHomeScreen(
    contentPadding: PaddingValues,
    onOpenGroup: (group: String, focus: String?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(query) { SettingsRegistry.search(query) }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 4.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
            start = 16.dp, end = 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "search") {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                placeholder = { Text("Search settings") },
                shape = IntakeShape.Field,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotBlank()) {
            if (results.isEmpty()) {
                item(key = "none") { Text("No setting matches \"$query\".", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            itemsIndexed(results, key = { _, r -> "r-" + r.id }) { _, spec ->
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = IntakeShape.InnerCard,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { onOpenGroup(spec.group.id, spec.id) },
                ) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Text(spec.label, style = MaterialTheme.typography.titleSmall)
                        Text(spec.group.title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            item(key = "groups") {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = IntakeShape.Card,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        SettingsGroup.entries.forEachIndexed { index, group ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 72.dp)
                                    .clickable(role = Role.Button) { onOpenGroup(group.id, null) }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Surface(color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer, shape = IntakeShape.Tile, modifier = Modifier.size(44.dp)) {
                                    Box(contentAlignment = Alignment.Center) { Icon(group.icon(), contentDescription = null, modifier = Modifier.size(22.dp)) }
                                }
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(group.title, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        groupSummary(group, state.settings, state.keys.hasOpenRouterKey, state.keys.hasYoutubeKey, state.keys.hasUsdaKey),
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (index != SettingsGroup.entries.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(start = 72.dp))
                        }
                    }
                }
            }
        }
    }
}

/**
 * One Settings group page: the controls the inventory lists for it, in order, switches and fields and sliders as
 * listed, then a collapsed "Advanced" section for the rarely used ones. [focus] (a control id) scrolls to that control
 * and opens Advanced if it is in there.
 */
@Composable
fun SettingsGroupScreen(
    groupId: String,
    focus: String?,
    onBack: () -> Unit,
    onOpenScriptReference: (String, String?) -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenWelcome: () -> Unit,
    onGoToToday: () -> Unit,
) {
    val group = SettingsGroup.byId(groupId) ?: SettingsGroup.APPEARANCE
    val nav = remember(onOpenScriptReference, onOpenPrivacy, onOpenWelcome, onGoToToday) {
        SettingsNav(onOpenScriptReference, onOpenPrivacy, onOpenWelcome, onGoToToday)
    }
    PushedScreen(group.title, onBack) { pad ->
        GroupContent(group, focus, nav, pad)
    }
}

@Composable
private fun GroupContent(group: SettingsGroup, focus: String?, nav: SettingsNav, pad: PaddingValues) {
    val all = remember(group) { SettingsRegistry.inGroup(group) }
    val main = remember(group) { all.filter { !it.advanced } }
    val advanced = remember(group) { all.filter { it.advanced } }
    var showAdvanced by rememberSaveable(group.id) { mutableStateOf(focus != null && advanced.any { it.id == focus }) }
    val listState = rememberLazyListState()
    // Header items before the controls: the blurb (and the Health status block).
    val headerCount = if (group == SettingsGroup.HEALTH) 2 else 1
    LaunchedEffect(focus) {
        if (focus == null) return@LaunchedEffect
        val i = main.indexOfFirst { it.id == focus }
        val j = advanced.indexOfFirst { it.id == focus }
        val index = when {
            i >= 0 -> headerCount + i
            j >= 0 -> headerCount + main.size + 1 + j
            else -> null
        }
        if (index != null) listState.animateScrollToItem(index)
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            top = pad.calculateTopPadding() + 4.dp,
            bottom = pad.calculateBottomPadding() + 24.dp,
            start = 16.dp, end = 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item(key = "blurb") {
            Text(group.blurb, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
        }
        if (group == SettingsGroup.HEALTH) item(key = "health-status") { HealthStatusHeader() }
        itemsIndexed(main, key = { _, s -> "m-" + s.id }) { _, spec -> Control(spec, nav) }
        if (advanced.isNotEmpty()) {
            item(key = "advanced-toggle") {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = IntakeShape.Card,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { showAdvanced = !showAdvanced },
                ) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Advanced", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                        Text("${advanced.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Icon(if (showAdvanced) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = if (showAdvanced) "Collapse" else "Expand", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            if (showAdvanced) itemsIndexed(advanced, key = { _, s -> "a-" + s.id }) { _, spec -> Control(spec, nav) }
        }
    }
}

@Composable
private fun Control(spec: SettingSpec, nav: SettingsNav) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxWidth(),
    ) {
        SettingControl(spec, nav)
    }
}
