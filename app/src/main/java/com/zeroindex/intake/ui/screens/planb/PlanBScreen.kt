@file:OptIn(ExperimentalLayoutApi::class)

package com.zeroindex.intake.ui.screens.planb

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zeroindex.intake.data.ai.planb.PlanBOption
import com.zeroindex.intake.ui.adaptive.LocalWindowSizeClass
import com.zeroindex.intake.ui.adaptive.isExpanded
import com.zeroindex.intake.ui.components.AccentNote
import com.zeroindex.intake.ui.components.Eyebrow
import com.zeroindex.intake.ui.components.Flag
import com.zeroindex.intake.ui.components.IntakeCard
import com.zeroindex.intake.ui.components.OfflinePill
import com.zeroindex.intake.ui.components.SectionLine
import com.zeroindex.intake.ui.components.SubHead
import com.zeroindex.intake.ui.components.Tag
import com.zeroindex.intake.ui.screens.ai.usd
import com.zeroindex.intake.ui.screens.today.SlotPickerSheet
import com.zeroindex.intake.ui.theme.IntakeShape
import kotlin.math.roundToInt

/**
 * Amendment §1 Plan B, laid out by window size (amendment §3): on a phone a
 * list with the chosen option on top; on a landscape tablet or car head unit
 * two panes side by side with larger touch targets, readable at arm's length.
 */
@Composable
fun PlanBScreen(
    contentPadding: PaddingValues,
    onOpenAiSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlanBViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val expanded = LocalWindowSizeClass.current.isExpanded
    val target: Dp = if (expanded) 64.dp else 52.dp
    var logging by remember { mutableStateOf<PlanBOption?>(null) }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.refreshPermissions()
        if (granted) viewModel.searchNearby()
    }

    val pad = PaddingValues(
        top = contentPadding.calculateTopPadding(),
        bottom = contentPadding.calculateBottomPadding() + 16.dp,
        start = 16.dp,
        end = 16.dp,
    )

    val list: @Composable (Modifier) -> Unit = { m ->
        LazyColumn(m, contentPadding = pad, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item(key = "head") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SubHead("Find something that works", "A quick, allergen-aware backup for right now.", Modifier.weight(1f))
                    OfflinePill()
                }
            }
            item(key = "search") {
                SearchCard(
                    state = state,
                    target = target,
                    onUseLocation = {
                        if (state.hasLocation) viewModel.searchNearby() else locationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                    },
                    onPostalCode = viewModel::searchPostalCode,
                    onOpenAiSettings = onOpenAiSettings,
                )
            }
            if (state.offline.isNotEmpty()) {
                item(key = "offline-head") { SectionLine("Saved · works offline") }
                items(state.offline, key = { it.key }) { o ->
                    OptionRow(o, selected = o.key == state.selectedKey, target = target, onClick = { viewModel.select(o) })
                }
            }
            if (state.found.isNotEmpty()) {
                item(key = "found-head") { SectionLine("Nearby", note = state.costUsd?.let { usd(it) }) }
                items(state.found, key = { it.key }) { o ->
                    OptionRow(o, selected = o.key == state.selectedKey, target = target, onClick = { viewModel.select(o) })
                }
            }
            if (!expanded) {
                state.selected?.let { o ->
                    item(key = "detail") {
                        Detail(o, state, target, onLog = { logging = o }, onSave = { viewModel.save(o) }, onNext = viewModel::next)
                    }
                }
            }
        }
    }

    if (expanded) {
        Row(modifier.fillMaxWidth()) {
            list(Modifier.weight(0.45f).fillMaxHeight())
            Column(
                Modifier.weight(0.55f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(pad),
            ) {
                state.selected?.let { o ->
                    Detail(o, state, target, onLog = { logging = o }, onSave = { viewModel.save(o) }, onNext = viewModel::next)
                } ?: AccentNote(lead = "Pick an option", body = "Choose one on the left to see what to order and say.")
            }
        }
    } else {
        list(modifier.fillMaxWidth())
    }

    logging?.let { o ->
        SlotPickerSheet(
            title = "Log ${o.order} to",
            onDismiss = { logging = null },
            onPick = { slot -> viewModel.log(o, slot); logging = null },
        )
    }
}

@Composable
private fun SearchCard(
    state: PlanBUiState,
    target: Dp,
    onUseLocation: () -> Unit,
    onPostalCode: (String) -> Unit,
    onOpenAiSettings: () -> Unit,
) {
    var zip by remember { mutableStateOf("") }
    IntakeCard {
        if (!state.hasKey) {
            Text(
                "Saved options below work with no signal. Searching nearby uses the web and needs an OpenRouter key.",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onOpenAiSettings, shape = IntakeShape.Button, modifier = Modifier.fillMaxWidth().heightIn(min = target)) {
                Text("Set up AI tools")
            }
            return@IntakeCard
        }
        Eyebrow("Search nearby")
        Text(
            if (state.hasLocation) "Uses your approximate location, rounded to about a kilometre."
            else "Location is used only for this search, rounded to about a kilometre. A zip code works too.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onUseLocation,
            enabled = !state.searching,
            shape = IntakeShape.Button,
            modifier = Modifier.fillMaxWidth().heightIn(min = target),
        ) { Text("Use my location") }
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = zip,
                onValueChange = { zip = it.take(10) },
                label = { Text("Or a zip code") },
                singleLine = true,
                shape = IntakeShape.Field,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            OutlinedButton(
                onClick = { onPostalCode(zip) },
                enabled = zip.trim().length >= 3 && !state.searching,
                shape = IntakeShape.Chip,
                modifier = Modifier.heightIn(min = target),
            ) { Text("Search") }
        }
        state.status?.let {
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.height(18.dp).width(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
        }
        state.error?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun OptionRow(o: PlanBOption, selected: Boolean, target: Dp, onClick: () -> Unit) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        shape = IntakeShape.InnerCard,
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = target + 12.dp)
            .alpha(if (o.rejected) 0.55f else 1f)
            .clickable(enabled = !o.rejected, onClick = onClick),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(o.restaurant, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                o.distanceMiles?.let { Text("%.1f mi".format(it), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
            }
            Text(o.order, style = MaterialTheme.typography.bodySmall)
            Text(o.fit.summary, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (o.rejected || o.scan.mayContain.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Flag(if (o.rejected) "Removed: contains ${o.scan.contains.first().matchedTerm}" else "Check allergens")
            }
        }
    }
}

@Composable
private fun Detail(
    o: PlanBOption,
    state: PlanBUiState,
    target: Dp,
    onLog: () -> Unit,
    onSave: () -> Unit,
    onNext: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = IntakeShape.Banner) {
            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                Text(o.restaurant, style = MaterialTheme.typography.titleLarge)
                o.address?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Text(
                    o.order + if (o.modifications.isNotBlank()) " · ${o.modifications}" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        if (o.sayThis.isNotBlank()) {
            Eyebrow("Say this when ordering")
            // The mockup's `.quote`: a rule on the left, the words on pale green.
            Row {
                Surface(color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(3.dp).heightIn(min = 40.dp)) {}
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = IntakeShape.Quote, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "“${o.sayThis}”",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        }
        Surface(
            color = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            shape = IntakeShape.Tile,
        ) {
            Text(
                "Important: cross-contact is possible" +
                    (o.crossContactRisk?.let { " (usually $it risk here)" } ?: "") +
                    ". Ask the team to confirm current ingredients and prep before ordering." +
                    (o.caution.takeIf { it.isNotBlank() }?.let { " $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(11.dp),
            )
        }
        o.scan.mayContain.firstOrNull()?.let {
            AccentNote(
                lead = "Check this one", body = it.note,
                container = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Tag("${o.macros.kcal.roundToInt()} kcal")
            Tag("${o.macros.protein.roundToInt()} g protein")
            Tag("${o.macros.carbs.roundToInt()} g carbs")
            Tag("${o.macros.fat.roundToInt()} g fat")
        }
        Text(o.fit.summary, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Button(onClick = onLog, shape = IntakeShape.Button, modifier = Modifier.fillMaxWidth().heightIn(min = target)) {
            Text(if (state.logged) "Logged · log again" else "Log this meal")
        }
        if (!o.offline) {
            OutlinedButton(
                onClick = onSave,
                enabled = o.key !in state.savedKeys,
                shape = IntakeShape.Button,
                modifier = Modifier.fillMaxWidth().heightIn(min = target),
            ) { Text(if (o.key in state.savedKeys) "Saved for offline" else "Save for offline") }
        }
        OutlinedButton(onClick = onNext, shape = IntakeShape.Button, modifier = Modifier.fillMaxWidth().heightIn(min = target)) {
            Text("Show another option")
        }
    }
}
