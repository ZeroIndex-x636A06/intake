package com.zeroindex.intake.ui.screens.plan

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zeroindex.intake.data.db.entity.AllergenStatus
import com.zeroindex.intake.ui.allergen.CheckLabelChip
import com.zeroindex.intake.ui.allergen.ProvideCheckLabelTerms
import com.zeroindex.intake.ui.allergen.checkLabelReasonFor
import com.zeroindex.intake.data.db.entity.RecipeEntity
import com.zeroindex.intake.data.db.entity.RecipeOrigin
import com.zeroindex.intake.data.db.entity.ShoppingListItemEntity
import com.zeroindex.intake.data.repository.PlanImportPreview
import com.zeroindex.intake.data.repository.RecipeContent
import com.zeroindex.intake.data.repository.WeekContent
import com.zeroindex.intake.domain.model.StoreCategory
import com.zeroindex.intake.domain.nutrition.Macros
import com.zeroindex.intake.domain.plan.PlanFileParser
import com.zeroindex.intake.domain.plan.PlanMeta
import com.zeroindex.intake.domain.plan.ShoppingListBuilder
import com.zeroindex.intake.domain.units.WeightUnit
import com.zeroindex.intake.ui.components.AccentNote
import com.zeroindex.intake.ui.components.EmptyState
import com.zeroindex.intake.ui.components.Eyebrow
import com.zeroindex.intake.ui.components.Flag
import com.zeroindex.intake.ui.components.IntakeCard
import com.zeroindex.intake.ui.components.PillTone
import com.zeroindex.intake.ui.components.SandNote
import com.zeroindex.intake.ui.components.SectionLine
import com.zeroindex.intake.ui.components.StatusPill
import com.zeroindex.intake.ui.components.SubHead
import com.zeroindex.intake.ui.components.Tag
import com.zeroindex.intake.ui.screens.script.ScriptDiagnosticsSheet
import com.zeroindex.intake.ui.screens.script.ScriptMimeTypes
import com.zeroindex.intake.ui.screens.script.writeText
import com.zeroindex.intake.ui.theme.IntakeShape
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Food > Meal plans (SPEC §9): the meal plan week by week, with import and export. Layout
 * follows the mockup's `plan()` and `parking()`. Recipes, Recipe Hunter and Plan B have their
 * own places in the Food hub.
 */
@Composable
fun PlanScreen(
    contentPadding: PaddingValues,
    onOpenPlanB: () -> Unit,
    onOpenCook: (String) -> Unit,
    onOpenScriptReference: (page: String, anchor: String?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MealPlanViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val week by viewModel.currentWeek.collectAsStateWithLifecycle()
    val planned by viewModel.plannedWeeks.collectAsStateWithLifecycle()
    val content by viewModel.content.collectAsStateWithLifecycle()
    val meta by viewModel.meta.collectAsStateWithLifecycle()
    val shopping by viewModel.shopping.collectAsStateWithLifecycle()
    val recipes by viewModel.recipes.collectAsStateWithLifecycle()
    val unit by viewModel.weightUnit.collectAsStateWithLifecycle()
    val eatOut by viewModel.eatOut.collectAsStateWithLifecycle()
    val hidden by viewModel.hiddenEatOutCount.collectAsStateWithLifecycle()
    val showingAll by viewModel.showingAllEatOut.collectAsStateWithLifecycle()
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val importState by viewModel.importState.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val checkTerms by viewModel.checkLabelTerms.collectAsStateWithLifecycle()

    var assigning by remember { mutableStateOf(false) }
    var parking by remember { mutableStateOf(false) }
    var editingProtein by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            viewModel.startReading()
            scope.launch {
                val text = withContext(Dispatchers.IO) { readPlanFile(context, uri) }
                if (text == null) viewModel.importFailed("That file could not be read, or it is too large (the limit is 2 MB).")
                else viewModel.previewImport(text)
            }
        }
    }
    val launchImport = { picker.launch(ScriptMimeTypes) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = writeText(context, uri, viewModel.exportScript())
                viewModel.postNotice(if (ok) "Saved your plan as a script." else "That file could not be saved.")
            }
        }
    }

    ProvideCheckLabelTerms(checkTerms) {
    Box(modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = contentPadding.calculateTopPadding(),
                // The sticky buttons below, then the floating navigation pill under them.
                bottom = 150.dp + contentPadding.calculateBottomPadding(),
                start = 16.dp,
                end = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "head") {
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        SubHead("Your meal plan", "12 weeks · built around what works for you")
                    }
                    TextButton(onClick = launchImport, modifier = Modifier.heightIn(min = 48.dp)) { Text("Import plan") }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = { onOpenScriptReference("meal-plan", null) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text("Script reference") }
                    if (planned.isNotEmpty()) {
                        TextButton(
                            onClick = { exporter.launch("meal-plan.md") },
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) { Text("Export plan as script") }
                    }
                }
            }
            notice?.let { n ->
                item(key = "notice") {
                    SandNote(n)
                    TextButton(onClick = viewModel::clearNotice) { Text("Dismiss") }
                }
            }
            item(key = "strip") {
                WeekStrip(current = week, planned = planned, onSelect = viewModel::selectWeek)
            }

            val c = content
            if (c == null) {
                item(key = "empty") {
                    EmptyState(
                        title = "Week $week isn't planned yet",
                        body = "Pick one of your saved recipes for this week, or import a plan file. Nothing is filled in for you.",
                        action = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { assigning = true },
                                    shape = IntakeShape.Button,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                                ) { Text("Choose a recipe for week $week") }
                                OutlinedButton(
                                    onClick = launchImport,
                                    shape = IntakeShape.Button,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                                ) { Text("Import a plan file") }
                            }
                        },
                    )
                }
            } else {
                item(key = "dish") {
                    DishCard(
                        content = c,
                        onEditProtein = { editingProtein = true },
                        onChange = { assigning = true },
                        onRemove = { viewModel.clearWeek(c.week.weekNumber) },
                    )
                }
                item(key = "shop") {
                    ShoppingAccordion(
                        sections = shopping,
                        unit = unit,
                        onCheck = viewModel::setChecked,
                        onRemove = viewModel::removeItem,
                        onAdd = viewModel::addItem,
                        onShare = { viewModel.shareText()?.let { shareText(context, it) } },
                    )
                }
                item(key = "steps") {
                    StepsAccordion(main = c.main, onOpenCook = { c.main?.let { onOpenCook(it.recipe.id) } })
                }
                item(key = "other") { OtherWaysAccordion(c) }
            }

            if (!meta.isEmpty) item(key = "every") { EveryWeekAccordion(meta) }

            // Recipes, Recipe Hunter and Plan B live in the Food hub now; "Can't eat what I
            // cooked?" below still reaches Plan B from here.
        }

        // The mockup's sticky "Log this meal" and the parking-lot button, always in reach.
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(bottom = contentPadding.calculateBottomPadding())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (content?.main != null) {
                Button(
                    onClick = viewModel::logMeal,
                    shape = IntakeShape.Button,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                ) { Text("Log this meal") }
            }
            Surface(
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = IntakeShape.Banner,
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
                    .clickable(role = Role.Button) { parking = true },
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text("Can't eat what I cooked?", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Find a safer option nearby · works offline",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }

    if (assigning) {
        AssignRecipeSheet(
            weekNumber = week,
            recipes = recipes,
            initialProtein = content?.week?.proteinType.orEmpty(),
            onDismiss = { assigning = false },
            onPick = { recipe, protein -> viewModel.assign(week, recipe, protein); assigning = false },
        )
    }
    if (editingProtein) {
        var text by remember { mutableStateOf(content?.week?.proteinType.orEmpty()) }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { editingProtein = false },
            title = { Text("Protein type") },
            text = { OutlinedTextField(text, { text = it }, singleLine = true, label = { Text("For example Chicken") }) },
            confirmButton = {
                TextButton(onClick = { viewModel.setProtein(week, text); editingProtein = false }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editingProtein = false }) { Text("Cancel") } },
        )
    }
    if (parking) {
        EatOutSheet(
            rows = eatOut,
            hiddenCount = hidden,
            showingAll = showingAll,
            weekLabel = content?.let { "week ${it.week.weekNumber} (${it.week.proteinType.ifBlank { "no protein set" }})" } ?: "any week",
            onToggleAll = viewModel::toggleShowAllEatOut,
            onLog = { row -> parking = false; viewModel.logEatOut(row.option) },
            onOpenPlanB = { parking = false; onOpenPlanB() },
            onDismiss = { parking = false },
        )
    }
    pending?.let { p ->
        when (p) {
            is PendingLog.Meal -> {
                val m = p.content.macros
                MacroConfirmDialog(
                    title = "Macros are incomplete",
                    explanation = if (m.isEmpty) {
                        "${p.content.recipe.name} has no nutrition data. Enter the macros for one serving."
                    } else {
                        "${m.missing.size} ingredients have no nutrition data and are not counted: " +
                            "${m.missing.take(6).joinToString()}${if (m.missing.size > 6) "…" else ""}. " +
                            "Adjust the numbers for one serving if you know them."
                    },
                    initial = m.perServing,
                    onDismiss = viewModel::dismissPending,
                    onConfirm = viewModel::confirmPending,
                )
            }
            is PendingLog.EatOut -> MacroConfirmDialog(
                title = "No estimate for this order",
                explanation = "${p.option.venueName} has no macro estimate on file. Enter your own, or log zeros.",
                initial = Macros(),
                onDismiss = viewModel::dismissPending,
                onConfirm = viewModel::confirmPending,
            )
        }
    }
    (importState.preview as? PlanImportPreview.ScriptErrors)?.let { errors ->
        ScriptDiagnosticsSheet(
            diagnostics = errors.diagnostics,
            onHowToFix = { page, anchor -> viewModel.cancelImport(); onOpenScriptReference(page, anchor) },
            onDismiss = viewModel::cancelImport,
        )
    }
    importState.preview?.takeUnless { it is PlanImportPreview.ScriptErrors }?.let { preview ->
        ImportPreviewDialog(
            preview = preview,
            onConfirm = { (preview as? PlanImportPreview.Ready)?.let { viewModel.confirmImport(it.batch) } },
            onDismiss = viewModel::cancelImport,
        )
    }
    }
}

/** The shared bounded reader (2 MB, API 29 safe); the Train tab's program import uses it too. */
internal fun readPlanFile(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.openInputStream(uri)?.use { input ->
        // Bounded copy instead of InputStream.readNBytes, which needs API 33.
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            total += n
            if (total > PlanFileParser.MAX_BYTES) return@use null
            out.write(buffer, 0, n)
        }
        out.toByteArray().toString(Charsets.UTF_8)
    }
}.getOrNull()

private fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, "Share shopping list"))
}

@Composable
private fun WeekStrip(current: Int, planned: Set<Int>, onSelect: (Int) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items((1..12).toList()) { n ->
            val selected = n == current
            val has = n in planned
            Surface(
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                shape = IntakeShape.Chip,
                border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .widthIn(min = 52.dp)
                    .semantics {
                        contentDescription = "Week $n, ${if (has) "planned" else "not planned"}${if (selected) ", current" else ""}"
                    }
                    .clickable(role = Role.Tab) { onSelect(n) },
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp)) {
                    Text(
                        "W$n" + if (has) "" else " ·",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun DishCard(content: WeekContent, onEditProtein: () -> Unit, onChange: () -> Unit, onRemove: () -> Unit) {
    val main = content.main
    var menu by remember { mutableStateOf(false) }
    IntakeCard {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Eyebrow("Week ${content.week.weekNumber}")
                Text(content.week.mainDishName, style = MaterialTheme.typography.titleLarge)
            }
            Box {
                TextButton(
                    onClick = { menu = true },
                    modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "More for this week" },
                ) { Text("•••") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Edit protein type") }, onClick = { menu = false; onEditProtein() })
                    DropdownMenuItem(text = { Text("Change recipe") }, onClick = { menu = false; onChange() })
                    DropdownMenuItem(text = { Text("Remove from this week") }, onClick = { menu = false; onRemove() })
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat(content.week.proteinType.ifBlank { "Not set" }, "Protein", Modifier.weight(1f))
            Stat(main?.recipe?.servings?.toString() ?: "–", "Servings", Modifier.weight(1f))
            Stat(main?.recipe?.totalTimeMinutes?.takeIf { it > 0 }?.let { "$it min" } ?: "–", "Total", Modifier.weight(1f))
        }
        if (content.extras.serving.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Eyebrow(content.extras.servingTitle.ifBlank { "How to serve it" })
            Text(content.extras.serving, style = MaterialTheme.typography.bodySmall)
        }
        if (main != null) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                when (main.allergenStatus) {
                    AllergenStatus.CONTAINS -> StatusPill("Contains an allergen", tone = PillTone.Bad)
                    AllergenStatus.MAY_CONTAIN -> StatusPill("Check allergens", tone = PillTone.Wait)
                    AllergenStatus.CHECK_LABEL -> CheckLabelChip(null)
                    AllergenStatus.CLEAR, AllergenStatus.MENTIONED_AVOIDED -> StatusPill("Passed allergen check")
                    AllergenStatus.UNKNOWN -> StatusPill("Allergens not checked", tone = PillTone.Wait)
                }
                if (main.macros.isEmpty) Tag("Macros: no data")
                else Text(
                    "${main.macros.perServing.kcal.roundToInt()} kcal · ${main.macros.perServing.protein.roundToInt()} g protein" +
                        if (main.macros.isComplete) " per serving" else " per serving, incomplete",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (main.allergenTerms.isNotEmpty() && main.allergenStatus != AllergenStatus.CLEAR &&
                main.allergenStatus != AllergenStatus.CHECK_LABEL
            ) {
                Spacer(Modifier.height(6.dp))
                SandNote("Matched on this phone: ${main.allergenTerms.joinToString()}. Check the labels before cooking.")
            }
            if (!main.macros.isComplete && !main.macros.isEmpty) {
                Spacer(Modifier.height(6.dp))
                SandNote("Not counted, no nutrition data: ${main.macros.missing.take(5).joinToString()}${if (main.macros.missing.size > 5) "…" else ""}")
            }
        } else {
            Spacer(Modifier.height(8.dp))
            SandNote("This week has no recipe linked, so there are no steps or macros. Use ••• to change recipe.")
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = IntakeShape.Tile, modifier = modifier) {
        Column(Modifier.padding(vertical = 9.dp, horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleSmall, maxLines = 2)
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The mockup's `.accordion`: a header row that opens its body. At least 56dp tall to tap. */
@Composable
private fun Accordion(
    title: String,
    modifier: Modifier = Modifier,
    initiallyOpen: Boolean = false,
    nested: Boolean = false,
    body: @Composable () -> Unit,
) {
    var open by rememberSaveable(title) { mutableStateOf(initiallyOpen) }
    val inner: @Composable () -> Unit = {
        Column {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp)
                    .clickable(role = Role.Button, onClickLabel = if (open) "Collapse $title" else "Expand $title") { open = !open },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = if (nested) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(if (open) "⌃" else "⌄", style = MaterialTheme.typography.titleMedium)
            }
            AnimatedVisibility(open) { Column(Modifier.padding(bottom = 10.dp)) { body() } }
        }
    }
    if (nested) Column(modifier) { inner() } else IntakeCard(modifier) { inner() }
}

@Composable
private fun ShoppingAccordion(
    sections: com.zeroindex.intake.domain.plan.ShoppingSections,
    unit: WeightUnit,
    onCheck: (String, Boolean) -> Unit,
    onRemove: (String) -> Unit,
    onAdd: (String, String, StoreCategory) -> Unit,
    onShare: () -> Unit,
) {
    Accordion("Shopping list · ${sections.count} items") {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (sections.count == 0) {
                Text("No items yet. Add one below.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            sections.sections.forEach { (category, items) ->
                Eyebrow(ShoppingListBuilder.sectionTitle(category), Modifier.padding(top = 8.dp))
                items.forEach { ShoppingRow(it, unit, onCheck, onRemove) }
            }
            if (sections.pantry.isNotEmpty()) {
                Accordion("Pantry staples · ${sections.pantry.size}", nested = true) {
                    sections.pantry.forEach { ShoppingRow(it, unit, onCheck, onRemove) }
                }
            }
            AddItemRow(onAdd)
            OutlinedButton(
                onClick = onShare,
                shape = IntakeShape.Button,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text("Share as text") }
        }
    }
}

@Composable
private fun ShoppingRow(
    item: ShoppingListItemEntity,
    unit: WeightUnit,
    onCheck: (String, Boolean) -> Unit,
    onRemove: (String) -> Unit,
) {
    val qty = ShoppingListBuilder.displayQuantity(item.quantity, unit)
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = item.isChecked, onCheckedChange = { onCheck(item.id, it) })
        Column(Modifier.weight(1f).clickable { onCheck(item.id, !item.isChecked) }) {
            Text(
                listOf(item.name, qty).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                textDecoration = if (item.isChecked) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
            )
            // Shopping is where the label gets read, so this is the most useful place for the nudge.
            if (!item.isChecked) checkLabelReasonFor(item.name)?.let { CheckLabelChip(it, Modifier.padding(top = 2.dp)) }
        }
        TextButton(
            onClick = { onRemove(item.id) },
            modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Remove ${item.name}" },
        ) { Text("×") }
    }
}

@Composable
private fun AddItemRow(onAdd: (String, String, StoreCategory) -> Unit) {
    var name by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("") }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(name, { name = it }, label = { Text("Add an item") }, singleLine = true, modifier = Modifier.weight(2f), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done))
        OutlinedTextField(qty, { qty = it }, label = { Text("Qty") }, singleLine = true, modifier = Modifier.weight(1f))
        Button(
            onClick = {
                val key = ShoppingListBuilder.normalise(name)
                onAdd(name, qty, ShoppingListBuilder.categoryOf(key))
                name = ""; qty = ""
            },
            enabled = name.isNotBlank(),
            shape = IntakeShape.Button,
            modifier = Modifier.heightIn(min = 56.dp),
        ) { Text("Add") }
    }
}

@Composable
private fun StepsAccordion(main: RecipeContent?, onOpenCook: () -> Unit) {
    val steps = main?.steps.orEmpty()
    Accordion("Step by step · ${steps.size} steps") {
        if (steps.isEmpty()) {
            Text("No steps saved for this recipe.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            steps.forEachIndexed { i, s ->
                Row(Modifier.padding(vertical = 4.dp)) {
                    Text("${i + 1}.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.widthIn(min = 26.dp))
                    Text(s, style = MaterialTheme.typography.bodyMedium)
                }
            }
            TextButton(onClick = onOpenCook, modifier = Modifier.heightIn(min = 48.dp)) { Text("Open cook mode →") }
        }
        if (main != null && main.ingredientLines.isNotEmpty()) {
            Eyebrow("Ingredients", Modifier.padding(top = 8.dp))
            main.ingredientLines.forEach { line ->
                Text(line, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 1.dp))
                checkLabelReasonFor(line)?.let { CheckLabelChip(it, Modifier.padding(bottom = 4.dp)) }
            }
        }
    }
}

@Composable
private fun OtherWaysAccordion(c: WeekContent) {
    Accordion("Other ways to eat it") {
        val any = c.extras.variations.isNotEmpty() || c.variations.isNotEmpty() || c.extras.note.isNotBlank()
        if (!any) {
            Text("Nothing added for this week.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        c.extras.variations.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 3.dp)) }
        c.variations.forEach { v ->
            Text(
                "• ${v.recipe.name}" + if (v.macros.isComplete) " · ${v.macros.perServing.kcal.roundToInt()} kcal" else "",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 3.dp),
            )
        }
        if (c.extras.note.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            SandNote(c.extras.note, title = "Note for this week")
        }
    }
}

@Composable
private fun EveryWeekAccordion(meta: PlanMeta) {
    Accordion("Every week") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (meta.intro.isNotBlank()) Text(meta.intro, style = MaterialTheme.typography.bodySmall)
            listOf(
                "Standing breakfasts" to meta.standingBreakfasts,
                "Standing snacks" to meta.standingSnacks,
                "Hidden dairy checklist" to meta.hiddenDairyChecklist,
                "Pantry: buy once" to meta.pantry,
            ).forEach { (title, list) ->
                if (list.isNotEmpty()) {
                    Eyebrow(title)
                    list.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                }
            }
            if (meta.eatOutNotes.isNotBlank()) {
                Eyebrow("Eating out")
                Text(meta.eatOutNotes, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
internal fun RecipeRow(recipe: RecipeEntity, onClick: () -> Unit) {
    IntakeCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(recipe.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = listOfNotNull(
                        recipe.kcalPerServing?.let { "${it.roundToInt()} kcal" },
                        recipe.proteinPerServing?.let { "${it.roundToInt()} g protein" },
                        "per serving".takeIf { recipe.kcalPerServing != null },
                    ).joinToString(" · ").ifBlank { "${recipe.servings} servings" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (recipe.origin == RecipeOrigin.HUNTER) Tag("From the web")
        }
        if (recipe.allergenStatus == AllergenStatus.MAY_CONTAIN) {
            Spacer(Modifier.height(6.dp))
            Flag("Check allergens")
        } else if (recipe.allergenStatus == AllergenStatus.CHECK_LABEL) {
            Spacer(Modifier.height(6.dp))
            CheckLabelChip(null)
        }
    }
}
