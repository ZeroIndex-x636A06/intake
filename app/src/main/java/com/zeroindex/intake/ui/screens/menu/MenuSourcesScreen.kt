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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.ui.text.input.KeyboardType
import com.zeroindex.intake.ui.components.EmptyState
import com.zeroindex.intake.ui.components.ErrorState
import com.zeroindex.intake.ui.components.LabeledField
import com.zeroindex.intake.ui.components.LoadingState
import com.zeroindex.intake.ui.components.PillTone
import com.zeroindex.intake.ui.components.ResultCard
import com.zeroindex.intake.ui.components.StatusPill
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zeroindex.intake.data.db.entity.AllergenStatus
import com.zeroindex.intake.ui.allergen.CheckLabelChip
import com.zeroindex.intake.data.db.entity.MenuItemEntity
import com.zeroindex.intake.data.db.entity.MenuRunStatus
import com.zeroindex.intake.data.db.entity.MenuSourceEntity
import com.zeroindex.intake.data.db.entity.MenuSourceKind
import com.zeroindex.intake.ui.components.IntakeCard
import com.zeroindex.intake.ui.components.SectionLine
import com.zeroindex.intake.ui.components.Segmented
import com.zeroindex.intake.ui.components.SubHead
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
    val allergenOnly by viewModel.allergenOnlyIds.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    val dates = remember { DateTimeFormatter.ofPattern("d MMM") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val refused = state.refusedSource
        if (uri != null && refused != null) viewModel.replaceWithPdf(refused, uri.toString())
    }
    val nutritionPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.addNutritionPdf(uri.toString())
    }
    var linkText by remember { mutableStateOf("") }
    var askingLink by remember { mutableStateOf(false) }

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
        // The top bar already says "Menu import"; no second heading here.
        item(key = "head") {
            Text(
                "Keep a local copy of menus you trust. Refreshed weekly, or when you choose.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )
        }
        if (!state.hasKey) {
            item(key = "nokey") {
                EmptyState(
                    title = "AI is off or has no key",
                    body = "A link whose page publishes its menu as structured data is read without AI. A PDF with a nutrition table is read on this phone without AI (Android 15 or later). Any other page, a PDF the phone cannot read, or pasted text is read by an AI model: turn on “Use AI features” and add a key in Settings → AI. Saved menus keep working without one.",
                )
            }
        }
        state.running?.let { progress ->
            item(key = "running") {
                IntakeCard {
                    Text(progress.message, style = MaterialTheme.typography.bodyMedium)
                    if (progress.fraction != null) {
                        LinearProgressIndicator(
                            progress = { progress.fraction },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    }
                    Text(
                        "Allergens are checked on this phone. Large PDFs are read a few pages at a time.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    OutlinedButton(
                        onClick = viewModel::cancelImport,
                        shape = IntakeShape.Button,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 8.dp),
                    ) { Text("Cancel") }
                }
            }
        }
        state.nutritionPrompt?.let { chart ->
            item(key = "nutrition-prompt") {
                IntakeCard {
                    Text("Add the nutrition menu?", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "This chart has allergens but no calories. Add ${chart.restaurantName}'s nutrition menu? " +
                            "Calories and macros come from it; allergens from both files are kept.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    if (askingLink) {
                        OutlinedTextField(
                            linkText, { linkText = it.trim() }, label = { Text("Nutrition menu link") }, singleLine = true,
                            shape = IntakeShape.Field, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        )
                        Button(
                            onClick = { viewModel.addNutritionLink(linkText); askingLink = false },
                            enabled = linkText.startsWith("http"),
                            shape = IntakeShape.Button,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 8.dp),
                        ) { Text("Read the link") }
                    } else {
                        Button(
                            onClick = { nutritionPicker.launch(arrayOf("application/pdf")) },
                            shape = IntakeShape.Button,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 8.dp),
                        ) { Text("Choose a PDF") }
                        OutlinedButton(
                            onClick = { askingLink = true },
                            shape = IntakeShape.Button,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 6.dp),
                        ) { Text("Paste a link") }
                    }
                    state.nutritionAiOffer?.let { offer ->
                        Text("No table read on this phone", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 10.dp))
                        Text(offer.reason, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                        Text(
                            if (state.hasKey) {
                                "Nothing was sent anywhere. If you read it with AI, the file is sent to your AI provider on your own key. " +
                                    "A PDF is read a few pages at a time, so a long one costs a few cents; the cost is added to AI usage in Settings. " +
                                    "The AI reads the numbers as printed and never invents any; allergens from it can only add warnings."
                            } else {
                                "Nothing was sent anywhere. AI is off or has no key: turn on “Use AI features” and add a key in Settings → AI to use it."
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        Button(
                            onClick = viewModel::readNutritionWithAi,
                            enabled = state.hasKey,
                            shape = IntakeShape.Button,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 8.dp),
                        ) { Text("Read it with AI") }
                        TextButton(onClick = viewModel::dismissNutritionAiOffer, modifier = Modifier.fillMaxWidth()) { Text("Not now") }
                    }
                    TextButton(onClick = { askingLink = false; viewModel.skipNutritionPrompt() }, modifier = Modifier.fillMaxWidth()) { Text("Skip") }
                }
            }
        }
        state.aiOffer?.let { offer ->
            item(key = "ai-offer") {
                IntakeCard {
                    Text("No table read on this phone", style = MaterialTheme.typography.titleSmall)
                    Text(
                        offer.reason,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Text(
                        if (state.hasKey) {
                            "Nothing was sent anywhere. If you read it with AI, the PDF is sent to your AI provider."
                        } else {
                            "Nothing was sent anywhere. AI is off or has no key: turn on “Use AI features” and add a key in Settings → AI to use it."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Button(
                        onClick = viewModel::readWithAi,
                        enabled = state.hasKey,
                        shape = IntakeShape.Button,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 8.dp),
                    ) { Text("Read it with AI") }
                    TextButton(onClick = viewModel::dismissAiOffer, modifier = Modifier.fillMaxWidth()) { Text("Not now") }
                }
            }
        }
        state.error?.let { err ->
            item(key = "error") {
                ErrorState(title = "Import failed", body = err) {
                    state.refusedSource?.let { refused ->
                        Button(
                            onClick = { picker.launch(arrayOf("application/pdf")) },
                            shape = IntakeShape.Button,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) { Text("Choose a PDF") }
                    }
                    TextButton(onClick = viewModel::clearError) { Text("Dismiss") }
                }
            }
        }
        item(key = "sources-head") {
            SectionLine("Saved sources") {
                TextButton(onClick = { adding = true }) { Text("＋ Add source") }
            }
        }
        if (sources.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    title = "No menus yet",
                    body = "Add a restaurant's nutrition page, PDF or pasted menu text. Swap can then suggest from it offline.",
                )
            }
        } else {
            item(key = "sources") {
                IntakeCard {
                    sources.forEachIndexed { i, source ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .clickable(onClickLabel = "Show items and actions") { viewModel.open(source) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(source.restaurantName, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    when (source.kind) {
                                        MenuSourceKind.URL -> "URL · ${source.location.removePrefix("https://").removePrefix("http://").removePrefix("www.").take(40)}"
                                        MenuSourceKind.PDF -> "PDF · local file"
                                        MenuSourceKind.TEXT -> "Pasted text"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    (source.lastRunAt?.let { dates.format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())) } ?: "Never imported") +
                                        " · ${source.itemCount} items",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            when (source.lastStatus) {
                                MenuRunStatus.OK -> StatusPill("OK")
                                MenuRunStatus.FAILED -> StatusPill("Failed", tone = PillTone.Bad)
                                MenuRunStatus.RUNNING -> StatusPill("Running", tone = PillTone.Wait)
                                MenuRunStatus.NEVER_RUN -> StatusPill("Never run", tone = PillTone.Wait)
                                MenuRunStatus.TO_REVIEW -> StatusPill("To review", tone = PillTone.Wait)
                            }
                        }
                        if (source.lastStatus == MenuRunStatus.TO_REVIEW) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Button(onClick = { viewModel.reviewStaged(source) }, shape = IntakeShape.Button, modifier = Modifier.heightIn(min = 48.dp)) { Text("Review import") }
                                TextButton(onClick = { viewModel.askDiscard(source) }) { Text("Discard import") }
                            }
                        }
                        if (source.id in allergenOnly) {
                            TextButton(onClick = { viewModel.askForNutrition(source) }) { Text("Add nutrition menu") }
                        }
                        if (i != sources.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
            MenuErrorCards.sourceFailure(sources, state.error, state.aiOffer?.source?.id)?.let { failed ->
                item(key = "failed") {
                    ErrorState(
                        title = "Import failed · ${failed.restaurantName}",
                        body = "${failed.lastError} You can paste menu text or choose a PDF instead.",
                    )
                }
            }
        }
    }

    if (adding) AddSourceSheet(onDismiss = { adding = false }, onAdd = { n, k, l, p -> viewModel.add(n, k, l, p); adding = false })
    state.review?.let { ReviewSheet(it, onToggle = viewModel::toggle, onSave = viewModel::commit, onClose = viewModel::closeReview, onDiscard = { viewModel.askDiscard(it.source) }) }
    state.confirmDiscard?.let { src ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { viewModel.askDiscard(null) },
            title = { Text("Discard this import?") },
            text = { Text("The items read from ${src.restaurantName} that you have not accepted are removed. Items already saved are not touched.") },
            confirmButton = { TextButton(onClick = { viewModel.discardImport(src) }) { Text("Discard import") } },
            dismissButton = { TextButton(onClick = { viewModel.askDiscard(null) }) { Text("Keep it") } },
        )
    }
    state.openSourceId?.let { id ->
        sources.firstOrNull { it.id == id }?.let { src ->
            ItemsSheet(
                src,
                items,
                // A link can be refreshed with AI off: a page with structured menu data needs no model.
                canRun = state.running == null && (state.hasKey || src.kind == com.zeroindex.intake.data.db.entity.MenuSourceKind.URL || src.kind == com.zeroindex.intake.data.db.entity.MenuSourceKind.PDF),
                onRun = { viewModel.run(src); viewModel.open(null) },
                onRemove = { viewModel.delete(src); viewModel.open(null) },
                onDismiss = { viewModel.open(null) },
            )
        }
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
            Text("Add source", style = MaterialTheme.typography.headlineSmall)
            LabeledField(label = "Restaurant", value = name, onValueChange = { name = it }, placeholder = "e.g. CAVA")
            Segmented(
                options = listOf("Paste URL", "Pick PDF", "Paste text"),
                selectedIndex = MenuSourceKind.entries.indexOf(kind),
                onSelect = { kind = MenuSourceKind.entries[it] },
            )
            when (kind) {
                MenuSourceKind.URL -> {
                    LabeledField(label = "Menu URL", value = url, onValueChange = { url = it.trim() }, placeholder = "https://…", keyboardType = KeyboardType.Uri)
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
            ) { Text("Import menu") }
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** Nothing from a manual import is saved until the user has seen it. */
@Composable
private fun ReviewSheet(review: Review, onToggle: (Int) -> Unit, onSave: () -> Unit, onClose: () -> Unit, onDiscard: () -> Unit) {
    val flagged = review.items.count { it.entity.allergenStatus == AllergenStatus.CONTAINS || it.entity.allergenStatus == AllergenStatus.MAY_CONTAIN }
    val accepted = review.items.count { it.accepted }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding()) {
            SubHead("Review imported menu", "Nothing is added to your menus until you accept. Close this and it waits on the source as \"To review\".")
            review.warnings.forEach { w ->
                Text(
                    w,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                shape = RoundedCornerShape(17.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (review.chartNote != null) {
                        review.chartNote + (if (review.readOnPhone) " Read on this phone, no AI." else "") + if (flagged > 0) " $flagged need review." else ""
                    } else {
                        "Parsed ${review.items.size} items from ${review.source.restaurantName}" +
                            (if (review.readOnPhone) " · read on this phone, no AI" else "") +
                            if (flagged > 0) " · $flagged need review" else ""
                    },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(15.dp),
                )
            }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
                items(review.items.size) { i ->
                    val staged = review.items[i]
                    val item = staged.entity
                    ResultCard(dimmed = !staged.accepted, modifier = Modifier.padding(vertical = 4.dp)) {
                        Row(verticalAlignment = Alignment.Top) {
                            Text(item.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).padding(end = 6.dp))
                            when (item.allergenStatus) {
                                AllergenStatus.CONTAINS -> StatusPill("Allergen match · review", tone = PillTone.Bad)
                                AllergenStatus.MAY_CONTAIN -> StatusPill("Possible match · review", tone = PillTone.Wait)
                                AllergenStatus.CHECK_LABEL -> CheckLabelChip(null)
                                AllergenStatus.CLEAR, AllergenStatus.MENTIONED_AVOIDED -> StatusPill("Ingredient check passed")
                                AllergenStatus.UNKNOWN -> StatusPill("No ingredients listed", tone = PillTone.Wait)
                            }
                        }
                        Text(macroLine(item), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                        if (review.readOnPhone && item.description.isNotBlank()) {
                            Text(
                                item.description,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                            )
                        }
                        Text(
                            when (item.allergenStatus) {
                                AllergenStatus.CONTAINS, AllergenStatus.MAY_CONTAIN -> "Matched: ${item.allergenMatches}"
                                AllergenStatus.CHECK_LABEL ->
                                    if (review.chartNote != null) "Not stated by this chart: it cannot show an item is free of an allergen" else "Often hides dairy: ${item.allergenMatches}"
                                AllergenStatus.CLEAR, AllergenStatus.MENTIONED_AVOIDED -> "No terms you avoid were found"
                                AllergenStatus.UNKNOWN -> "Check with staff before ordering"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            TextButton(onClick = { if (staged.accepted) onToggle(i) }, enabled = staged.accepted) { Text("Reject") }
                            TextButton(onClick = { if (!staged.accepted) onToggle(i) }, enabled = !staged.accepted) {
                                Text(if (staged.accepted) "Accepted" else "Accept item")
                            }
                        }
                    }
                }
            }
            Button(
                onClick = onSave,
                enabled = accepted > 0,
                shape = IntakeShape.Button,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(52.dp),
            ) { Text("Save $accepted accepted item${if (accepted == 1) "" else "s"}") }
            TextButton(onClick = onDiscard, modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp)) {
                Text("Discard import")
            }
        }
    }
}

private fun macroLine(item: MenuItemEntity): String = listOfNotNull(
    item.kcal?.let { "${it.roundToInt()} kcal" },
    item.proteinG?.let { "${it.roundToInt()} g protein" },
    "estimated".takeIf { !item.macrosPublished && item.kcal != null },
).joinToString(" · ").ifBlank { "Nutrition not listed" }

@Composable
private fun ItemsSheet(
    source: MenuSourceEntity,
    items: List<MenuItemEntity>,
    canRun: Boolean,
    onRun: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding()) {
            Text(source.restaurantName, style = MaterialTheme.typography.headlineSmall)
            Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onRun, enabled = canRun, shape = IntakeShape.Chip) { Text("Refresh now") }
                TextButton(onClick = onRemove) { Text("Remove source") }
            }
            if (items.isEmpty()) EmptyState(body = "No items saved from this source yet.")
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
        Text(macroLine(item), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        when (item.allergenStatus) {
            AllergenStatus.CONTAINS -> StatusPill("Contains ${item.allergenMatches}", tone = PillTone.Bad)
            AllergenStatus.MAY_CONTAIN -> StatusPill("Check: ${item.allergenMatches}", tone = PillTone.Wait)
            AllergenStatus.CHECK_LABEL -> CheckLabelChip(null)
            else -> Unit
        }
    }
}

/** Which cards say what failed, so one failure is never shown twice. */
internal object MenuErrorCards {
    /**
     * The saved source whose last run failed, or null when the same words are already on screen: the top error card
     * ([topError]) or the "No table read" offer for that source ([offerSourceId]).
     */
    fun sourceFailure(sources: List<MenuSourceEntity>, topError: String?, offerSourceId: String?): MenuSourceEntity? {
        val failed = sources.firstOrNull { it.lastStatus == MenuRunStatus.FAILED && it.lastError != null } ?: return null
        if (topError != null && topError == failed.lastError) return null
        if (offerSourceId != null && offerSourceId == failed.id) return null
        return failed
    }
}
