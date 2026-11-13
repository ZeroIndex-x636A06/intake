package com.zeroindex.intake.ui.screens.swap

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zeroindex.intake.data.swap.PlannedMeal
import com.zeroindex.intake.domain.nutrition.Macros
import com.zeroindex.intake.domain.swap.SwapKind
import com.zeroindex.intake.domain.swap.SwapMath
import com.zeroindex.intake.domain.swap.SwapOption
import com.zeroindex.intake.domain.swap.SwapSection
import com.zeroindex.intake.domain.swap.SwapSections
import com.zeroindex.intake.ui.addfood.slotWord
import com.zeroindex.intake.ui.allergen.AllergenChip
import com.zeroindex.intake.ui.allergen.AllergenColors
import com.zeroindex.intake.ui.components.EmptyState
import com.zeroindex.intake.ui.components.ErrorState
import com.zeroindex.intake.ui.components.LabeledField
import com.zeroindex.intake.ui.components.LoadingState
import com.zeroindex.intake.ui.components.SandNote
import com.zeroindex.intake.ui.screens.ai.usd
import com.zeroindex.intake.ui.theme.IntakeShape
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * The Swap sheet (round 3, R3-2), opened from Today's Next up and from the planned meal on Nutrition. It replaces Plan B
 * as a destination. Sections: Plan & recipes, Saved meals, then Eat out nearby: saved eat-out options first (they work
 * offline), then the optional "Find healthy options nearby" (AI on the person's key, only when they tap Search, within
 * the radius set in Settings).
 *
 * Every option shows its macros against the planned meal and its allergen status. A red ("contains") option is shown, dimmed,
 * with its reason, and cannot be chosen. Choosing one logs it in place of the planned meal.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwapSheet(
    slot: String?,
    onDismiss: () -> Unit,
    onOpenAiSettings: () -> Unit,
    onOpenRadiusSetting: () -> Unit,
    viewModel: SwapViewModel = hiltViewModel(),
) {
    LaunchedEffect(slot) { viewModel.load(slot) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val nearby = state.nearby
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.refreshPermissions()
        if (granted) viewModel.searchNearby()
    }
    state.pendingOverride?.let { p ->
        com.zeroindex.intake.ui.allergen.AllergenOverrideDialog(p, onCancel = viewModel::cancelOverride, onConfirm = { viewModel.confirmOverride(onDismiss) })
    }
    val timeFmt = remember(state.zone) { DateTimeFormatter.ofPattern("h:mm a").withZone(state.zone) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "title") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Swap ${slotWord(state.slot).lowercase()}", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close") }
                }
            }
            item(key = "planned") { PlannedCard(state.planned, slotWord(state.slot)) }
            state.notice?.let { n -> item(key = "notice") { SandNote(n) } }

            if (state.loading) {
                item(key = "loading") { LoadingState(listOf("Reading your plan, recipes and meal templates")) }
            }

            val planned = state.planned?.macros
            state.sections.filter { it.kind == SwapKind.PLAN_RECIPE || it.kind == SwapKind.SAVED_MEAL }.forEach { section ->
                section(section, planned, state.savedKeys, state.choosing, viewModel, onDismiss)
            }

            // Eat out nearby: saved options first, then the optional AI search and its results.
            item(key = "nearby-head") { SectionTitle("Eat out nearby", tag = "AI optional") }
            state.sections.firstOrNull { it.kind == SwapKind.SAVED_EAT_OUT }?.let { saved ->
                optionRows(saved, planned, state.savedKeys, state.choosing, viewModel, onDismiss)
            }
            item(key = "nearby-card") {
                NearbyCard(
                    nearby = nearby,
                    blurb = if (planned != null) " Ranked by how close each is to your planned meal." else "",
                    foundTime = nearby.foundAt?.let { timeFmt.format(Instant.ofEpochMilli(it)) },
                    onUseLocation = {
                        if (nearby.hasLocation) viewModel.searchNearby() else locationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                    },
                    onPostalCode = viewModel::searchPostalCode,
                    onOpenAiSettings = onOpenAiSettings,
                    onOpenRadiusSetting = onOpenRadiusSetting,
                )
            }
            state.sections.firstOrNull { it.kind == SwapKind.NEARBY }?.let { found ->
                optionRows(found, planned, state.savedKeys, state.choosing, viewModel, onDismiss, saveable = true)
            }
            item(key = "foot") { Spacer(Modifier.navigationBarsPadding().size(4.dp)) }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(
    section: SwapSection,
    planned: Macros?,
    saved: Set<String>,
    choosing: Boolean,
    vm: SwapViewModel,
    onDone: () -> Unit,
) {
    item(key = "head-${section.kind}") { SectionTitle(section.title) }
    if (section.options.isEmpty()) {
        item(key = "empty-${section.kind}") {
            Text(
                when (section.kind) {
                    SwapKind.PLAN_RECIPE -> "No other recipes yet. Recipes you save, and the week's variations, show up here."
                    SwapKind.SAVED_MEAL -> "No meal templates yet. Make one in Nutrition, More, or save a meal you logged on Today, and it shows up here."
                    else -> "Nothing here."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        optionRows(section, planned, saved, choosing, vm, onDone)
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.optionRows(
    section: SwapSection,
    planned: Macros?,
    saved: Set<String>,
    choosing: Boolean,
    vm: SwapViewModel,
    onDone: () -> Unit,
    saveable: Boolean = false,
) {
    if (section.options.isEmpty() && section.kind == SwapKind.SAVED_EAT_OUT) {
        item(key = "empty-eatout") {
            Text(
                "No saved eat-out options or imported menus yet. Save one from a nearby search, import a restaurant menu, or import a plan that has them. They work with no signal.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    items(section.options, key = { it.key }) { o ->
        OptionCard(
            o, planned, choosing, saved = o.key in saved, saveable = saveable,
            onChoose = { vm.choose(o.key, onDone) }, onSave = { vm.saveForOffline(o.key) },
            onOverride = { vm.requestOverride(o.key) },
        )
    }
}

@Composable
private fun SectionTitle(text: String, tag: String? = null) {
    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (tag != null) {
            Surface(color = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer, shape = IntakeShape.Chip) {
                Text(tag, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
            }
        }
    }
}

@Composable
private fun PlannedCard(planned: PlannedMeal?, slotLabel: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), shape = IntakeShape.Card, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (planned == null) {
                Text("NOTHING PLANNED FOR ${slotLabel.uppercase()}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "There is no planned meal to compare with, so each option shows its own macros. Choosing one logs it to ${slotLabel.lowercase()}.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Row(verticalAlignment = Alignment.Top) {
                    Text("PLANNED", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    AllergenChip(planned.allergen, label = planned.allergenReason)
                }
                Text(planned.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                Text(macroLine(planned.macros, false), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun macroLine(m: Macros, estimate: Boolean): String =
    "${m.kcal.roundToInt()} kcal · P ${m.protein.roundToInt()} · C ${m.carbs.roundToInt()} · F ${m.fat.roundToInt()}" + if (estimate) " · estimate" else ""

@Composable
internal fun OptionCard(
    o: SwapOption,
    planned: Macros?,
    choosing: Boolean,
    saved: Boolean,
    saveable: Boolean,
    onChoose: () -> Unit,
    onSave: () -> Unit,
    chooseLabel: String = "Choose",
    /** "Choose anyway" on a blocked option: opens the warning dialog. Never logs by itself. */
    onOverride: () -> Unit = {},
) {
    val blocked = !SwapSections.canChoose(o)
    val scheme = MaterialTheme.colorScheme
    Surface(
        color = if (blocked) AllergenColors.red.copy(alpha = 0.12f) else scheme.surface,
        shape = IntakeShape.Card,
        border = BorderStroke(1.dp, if (blocked) AllergenColors.red.copy(alpha = 0.6f) else scheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f).alpha(if (blocked) 0.75f else 1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(o.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                    o.subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant) }
                    Text(macroLine(o.macros, o.estimate), style = MaterialTheme.typography.bodySmall)
                    o.reason?.let { Text(it, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = scheme.primary) }
                }
                if (blocked) {
                    OutlinedButton(
                        onClick = onOverride,
                        enabled = !choosing,
                        shape = IntakeShape.Button,
                        border = BorderStroke(1.dp, AllergenColors.red),
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = AllergenColors.red),
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text("Choose anyway") }
                } else {
                    Button(
                        onClick = onChoose,
                        enabled = !choosing,
                        shape = IntakeShape.Button,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text(chooseLabel) }
                }
            }
            if (planned != null) {
                val chips = SwapMath.chips(SwapMath.delta(o.macros, planned))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    chips.forEach { c ->
                        Surface(color = scheme.surfaceVariant, shape = IntakeShape.Chip) {
                            Text(c, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                        }
                    }
                    if (o.estimate) {
                        Surface(color = scheme.surfaceVariant, shape = IntakeShape.Chip) {
                            Text("Estimate", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                        }
                    }
                }
            }
            AllergenChip(o.allergen, label = if (o.allergen == com.zeroindex.intake.domain.swap.SwapAllergen.CLEAR) "No allergens found" else o.allergenReason)
            if (blocked) {
                Text(
                    "${SwapSections.blockedReason(o)}. You can still choose it after a warning; the warning stays on the entry.",
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
            if (saveable && !blocked) {
                TextButton(onClick = onSave, enabled = !saved, modifier = Modifier.heightIn(min = 40.dp)) {
                    Text(if (saved) "Saved for offline" else "Save for offline")
                }
            }
        }
    }
}

@Composable
internal fun NearbyCard(
    nearby: NearbyState,
    title: String = "Find healthy options nearby",
    blurb: String = "",
    foundTime: String?,
    onUseLocation: () -> Unit,
    onPostalCode: (String) -> Unit,
    onOpenAiSettings: () -> Unit,
    onOpenRadiusSetting: () -> Unit,
) {
    var zip by remember { mutableStateOf("") }
    var useZip by remember { mutableStateOf(false) }
    val radius = "%.1f".format(java.util.Locale.US, nearby.radiusMi).removeSuffix(".0")
    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), shape = IntakeShape.Card, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = IntakeShape.Tile, modifier = Modifier.size(46.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(Icons.Filled.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.padding(top = 11.dp))
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                    Text(
                        if (nearby.foundAt != null && foundTime != null) {
                            "Within $radius mi. Found at $foundTime with AI on your key" +
                                (nearby.costUsd?.takeIf { it > 0 }?.let { ", about ${usd(it)}" } ?: "") +
                                ". Macros are estimates. Your allergens are checked on this phone." + blurb
                        } else {
                            "Within $radius mi. Runs only when you tap Search, with AI on your own key (a cent or so). Your allergens are checked on this phone, and the place is rounded to about a kilometre." + blurb
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            when {
                !nearby.hasKey -> {
                    Text("Needs “Use AI features” on and an OpenRouter key. Saved options above work with no signal.", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = onOpenAiSettings, shape = IntakeShape.Button, modifier = Modifier.heightIn(min = 48.dp)) { Text("Set up AI") }
                }
                nearby.searching -> LoadingState(listOfNotNull(nearby.status, "Checking allergens on this phone"))
                else -> {
                    if (!useZip) {
                        Button(onClick = onUseLocation, shape = IntakeShape.Button, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                            Icon(Icons.Filled.SwapHoriz, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.size(8.dp))
                            Text(
                                when {
                                    nearby.foundAt != null -> "Search again for more"
                                    nearby.hasLocation -> "Search near me"
                                    else -> "Allow location once and search"
                                },
                            )
                        }
                        TextButton(onClick = { useZip = true }) { Text("Use a zip code instead") }
                    } else {
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LabeledField(label = "Zip code", value = zip, onValueChange = { zip = it.take(10) }, keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f))
                            Button(
                                onClick = { onPostalCode(zip) },
                                enabled = zip.trim().length >= 3,
                                shape = IntakeShape.Button,
                                modifier = Modifier.heightIn(min = 56.dp),
                            ) { Text("Search") }
                        }
                        TextButton(onClick = { useZip = false }) { Text("Use my location instead") }
                    }
                    TextButton(onClick = onOpenRadiusSetting) { Text("Change the radius in Settings") }
                }
            }
            nearby.error?.let {
                ErrorState(title = "Search did not finish", body = "$it Saved options still work.") {
                    TextButton(onClick = onOpenAiSettings) { Text("Open AI settings") }
                }
            }
            if (nearby.foundAt != null && nearby.error == null && !nearby.searching && nearby.resultCount == 0) {
                EmptyState(title = "Nothing found", body = "No option within $radius mi fit. Saved options above still work.")
            }
        }
    }
}
