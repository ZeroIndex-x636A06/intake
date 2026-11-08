@file:OptIn(ExperimentalMaterial3Api::class)

package com.zeroindex.intake.ui.screens.menu

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zeroindex.intake.data.db.entity.AllergenStatus
import com.zeroindex.intake.data.db.entity.MenuItemEntity
import com.zeroindex.intake.data.db.entity.MenuRunStatus
import com.zeroindex.intake.data.db.entity.MenuSourceEntity
import com.zeroindex.intake.data.db.entity.MenuSourceKind
import com.zeroindex.intake.ui.components.AccentNote
import com.zeroindex.intake.ui.components.Flag
import com.zeroindex.intake.ui.components.IntakeCard
import com.zeroindex.intake.ui.components.Segmented
import com.zeroindex.intake.ui.components.SubHead
import com.zeroindex.intake.ui.components.Tag
import com.zeroindex.intake.ui.theme.IntakeShape
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun MenuSourcesScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    viewModel: MenuSourcesViewModel = hiltViewModel(),
) {
    val sources by viewModel.sources.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val items by viewModel.openItems.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    val dates = remember { DateTimeFormatter.ofPattern("d MMM") }

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
            SubHead("Menu import", "Restaurant menus parsed into items with macros, checked for allergens on this phone. Re-imported weekly.")
        }
        if (!state.hasKey) {
            item(key = "nokey") {
                AccentNote(
                    lead = "Needs an OpenRouter key",
                    body = "Menus are read by an AI model. Add a key in Settings → AI.",
                    container = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        state.running?.let { item(key = "running") { AccentNote(lead = "Working", body = it) } }
        state.error?.let { err ->
            item(key = "error") {
                AccentNote(
                    lead = "Import failed",
                    body = err,
                    container = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.clickable(onClick = viewModel::clearError),
                )
            }
        }
        item(key = "add") {
            Button(onClick = { adding = true }, shape = IntakeShape.Button, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("Add a menu")
            }
        }
        items(sources, key = { it.id }) { source ->
            IntakeCard(modifier = Modifier.clickable { viewModel.open(source) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(source.restaurantName, style = MaterialTheme.typography.titleSmall)
                        Text(
                            buildString {
                                append(source.kind.name.lowercase().replaceFirstChar(Char::uppercase))
                                append(" · ${source.itemCount} items")
                                source.lastRunAt?.let {
                                    append(" · ${dates.format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()))}")
                                }
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    when (source.lastStatus) {
                        MenuRunStatus.OK -> Tag("OK")
                        MenuRunStatus.FAILED -> Flag("Failed")
                        MenuRunStatus.RUNNING -> Tag("Running")
                        MenuRunStatus.NEVER_RUN -> Tag("Never run")
                    }
                }
                source.lastError?.takeIf { source.lastStatus == MenuRunStatus.FAILED }?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
                }
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { viewModel.run(source) }, enabled = state.running == null, shape = IntakeShape.Chip) {
                        Text("Import now")
                    }
                    TextButton(onClick = { viewModel.delete(source) }) { Text("Remove") }
                }
            }
        }
    }

    if (adding) AddSourceSheet(onDismiss = { adding = false }, onAdd = { n, k, l, p -> viewModel.add(n, k, l, p); adding = false })
    state.review?.let { ReviewSheet(it, onToggle = viewModel::toggle, onSave = viewModel::commit, onDiscard = viewModel::discardReview) }
    state.openSourceId?.let { id ->
        sources.firstOrNull { it.id == id }?.let { src -> ItemsSheet(src, items, onDismiss = { viewModel.open(null) }) }
    }
}

@Composable
private fun AddSourceSheet(onDismiss: () -> Unit, onAdd: (String, MenuSourceKind, String, String?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(MenuSourceKind.URL) }
    var url by remember { mutableStateOf("") }
    var pdf by remember { mutableStateOf<String?>(null) }
    var text by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> pdf = uri?.toString() }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.padding(horizontal = 16.dp).navigationBarsPadding().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Add a menu", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(name, { name = it }, label = { Text("Restaurant") }, singleLine = true, shape = IntakeShape.Field, modifier = Modifier.fillMaxWidth())
            Segmented(
                options = listOf("Web page", "PDF", "Paste text"),
                selectedIndex = MenuSourceKind.entries.indexOf(kind),
                onSelect = { kind = MenuSourceKind.entries[it] },
            )
            when (kind) {
                MenuSourceKind.URL -> {
                    OutlinedTextField(url, { url = it }, label = { Text("Menu page address") }, singleLine = true, shape = IntakeShape.Field, modifier = Modifier.fillMaxWidth())
                    Text(
                        "Delivery apps such as DoorDash forbid automated access in their terms and usually block it. " +
                            "For those, save the menu as a PDF or paste its text.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                MenuSourceKind.PDF -> OutlinedButton(
                    onClick = { picker.launch(arrayOf("application/pdf")) },
                    shape = IntakeShape.Button,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) { Text(if (pdf == null) "Choose a PDF" else "PDF chosen · change") }
                MenuSourceKind.TEXT -> OutlinedTextField(
                    text, { text = it }, label = { Text("Menu text") }, minLines = 6, shape = IntakeShape.Field,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            val ready = name.isNotBlank() && when (kind) {
                MenuSourceKind.URL -> url.startsWith("http")
                MenuSourceKind.PDF -> pdf != null
                MenuSourceKind.TEXT -> text.length > 40
            }
            Button(
                onClick = {
                    when (kind) {
                        MenuSourceKind.URL -> onAdd(name, kind, url, null)
                        MenuSourceKind.PDF -> onAdd(name, kind, pdf.orEmpty(), null)
                        MenuSourceKind.TEXT -> onAdd(name, kind, "", text)
                    }
                },
                enabled = ready,
                shape = IntakeShape.Button,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Add and import") }
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** Nothing from a manual import is saved until the user has seen it. */
@Composable
private fun ReviewSheet(review: Review, onToggle: (Int) -> Unit, onSave: () -> Unit, onDiscard: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDiscard, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding()) {
            Text("Review ${review.source.restaurantName}", style = MaterialTheme.typography.headlineSmall)
            Text(
                "${review.items.size} items found. Untick any to leave out. Items that contain an allergen you avoid are unticked " +
                    "and are never offered by Plan B.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
                items(review.items.size) { i ->
                    val staged = review.items[i]
                    Row(
                        Modifier.fillMaxWidth().clickable { onToggle(i) }.heightIn(min = 56.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = staged.accepted, onCheckedChange = { onToggle(i) })
                        ItemText(staged.entity, Modifier.weight(1f))
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
            Row(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDiscard, shape = IntakeShape.Button, modifier = Modifier.weight(1f).height(52.dp)) { Text("Discard") }
                Button(onClick = onSave, shape = IntakeShape.Button, modifier = Modifier.weight(1f).height(52.dp)) {
                    Text("Save ${review.items.count { it.accepted }}")
                }
            }
        }
    }
}

@Composable
private fun ItemsSheet(source: MenuSourceEntity, items: List<MenuItemEntity>, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding()) {
            Text(source.restaurantName, style = MaterialTheme.typography.headlineSmall)
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
                items(items, key = { it.id }) { item ->
                    ItemText(item, Modifier.fillMaxWidth().padding(vertical = 8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ItemText(item: MenuItemEntity, modifier: Modifier) {
    Column(modifier) {
        Text(item.name, style = MaterialTheme.typography.titleSmall)
        Text(
            listOfNotNull(
                item.kcal?.let { "${it.roundToInt()} kcal" },
                item.proteinG?.let { "${it.roundToInt()} g protein" },
                "estimated".takeIf { !item.macrosPublished && item.kcal != null },
            ).joinToString(" · ").ifBlank { "No nutrition" },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when (item.allergenStatus) {
            AllergenStatus.CONTAINS -> Flag("Contains ${item.allergenMatches}")
            AllergenStatus.MAY_CONTAIN -> Flag("Check: ${item.allergenMatches}")
            else -> Unit
        }
    }
}
