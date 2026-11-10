@file:OptIn(ExperimentalMaterial3Api::class)

package com.zeroindex.intake.ui.screens.recipe

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.zeroindex.intake.data.db.entity.FoodEntity
import com.zeroindex.intake.data.repository.FoodRepository
import com.zeroindex.intake.data.repository.RecipeRepository
import com.zeroindex.intake.data.repository.RecipeSaveRequest
import com.zeroindex.intake.domain.allergen.AllergenScanResult
import com.zeroindex.intake.domain.recipe.IngredientDraft
import com.zeroindex.intake.domain.recipe.IngredientSections
import com.zeroindex.intake.domain.recipe.PageNutrition
import com.zeroindex.intake.domain.recipe.RecipeEditing
import com.zeroindex.intake.ui.allergen.CheckLabelChip
import com.zeroindex.intake.ui.components.IntakeCard
import com.zeroindex.intake.ui.components.PillTone
import com.zeroindex.intake.ui.components.SandNote
import com.zeroindex.intake.ui.components.StatusPill
import com.zeroindex.intake.ui.components.SubHead
import com.zeroindex.intake.ui.theme.IntakeShape
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditorState(
    val loaded: Boolean = false,
    val id: String? = null,
    val name: String = "",
    val servings: String = "4",
    val minutes: String = "",
    val stepsText: String = "",
    val ingredients: List<IngredientDraft> = emptyList(),
    val scan: AllergenScanResult = AllergenScanResult.CLEAN,
    /** Per-serving numbers the recipe already carried (a page's, or typed before); kept when the ingredients cannot compute a total. */
    val carried: PageNutrition? = null,
    val sourceUrl: String? = null,
    val notes: String = "",
    val savedId: String? = null,
    val error: String? = null,
)

@HiltViewModel
class RecipeEditorViewModel @Inject constructor(
    private val recipes: RecipeRepository,
    private val foods: FoodRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()
    private var nextKey = 0
    private var started = false

    fun start(recipeId: String?) {
        if (started) return
        started = true
        if (recipeId == null) {
            _state.update { it.copy(loaded = true) }
            return
        }
        viewModelScope.launch {
            val e = recipes.load(recipeId)
            if (e == null) {
                _state.update { it.copy(loaded = true, error = "That recipe is no longer there.") }
                return@launch
            }
            val r = e.recipe
            _state.update {
                EditorState(
                    loaded = true, id = r.id, name = r.name, servings = r.servings.toString(),
                    minutes = r.totalTimeMinutes.takeIf { m -> m > 0 }?.toString().orEmpty(),
                    stepsText = e.steps.joinToString("\n"),
                    ingredients = e.ingredients.also { list -> nextKey = list.size + 1 },
                    carried = r.kcalPerServing?.let { k ->
                        PageNutrition(
                            k, r.proteinPerServing, r.carbsPerServing, r.fatPerServing,
                            fiberG = r.fiberPerServing, sugarG = r.sugarPerServing,
                            sodiumMg = r.sodiumMgPerServing, saturatedFatG = r.saturatedFatPerServing,
                        )
                    },
                    sourceUrl = r.sourceUrl,
                    notes = r.notes.orEmpty(),
                )
            }
            rescan()
        }
    }

    fun setName(v: String) { _state.update { it.copy(name = v) }; rescan() }
    fun setServings(v: String) = _state.update { it.copy(servings = v.filter(Char::isDigit).take(3)) }
    fun setMinutes(v: String) = _state.update { it.copy(minutes = v.filter(Char::isDigit).take(4)) }
    fun setSteps(v: String) = _state.update { it.copy(stepsText = v) }
    fun setNotes(v: String) = _state.update { it.copy(notes = v) }

    fun newKey() = "n${nextKey++}"

    fun upsertIngredient(d: IngredientDraft) {
        _state.update { s ->
            val i = s.ingredients.indexOfFirst { it.key == d.key }
            s.copy(ingredients = if (i >= 0) s.ingredients.toMutableList().also { it[i] = d } else s.ingredients + d)
        }
        rescan()
    }

    /** Pasted lines; a "## For the Sauce" line starts a group, and [group] covers lines before any heading. */
    fun addLines(text: String, group: String? = null) {
        val drafts = RecipeEditing.draftsFromText(text, ::newKey, group)
        _state.update { it.copy(ingredients = it.ingredients + drafts) }
        rescan()
    }

    fun removeIngredient(key: String) {
        _state.update { s -> s.copy(ingredients = s.ingredients.filterNot { it.key == key }) }
        rescan()
    }

    suspend fun searchFoods(query: String): List<FoodEntity> =
        if (query.isBlank()) emptyList() else foods.search(query.trim()).first()

    /** Allergen engine over the name and every ingredient, with the normal ingredient rules. */
    private fun rescan() {
        viewModelScope.launch {
            val s = _state.value
            val scan = recipes.scan(s.name, s.ingredients)
            _state.update { it.copy(scan = scan) }
        }
    }

    fun save() {
        val s = _state.value
        if (s.name.isBlank()) {
            _state.update { it.copy(error = "Give the recipe a name.") }
            return
        }
        viewModelScope.launch {
            val saved = recipes.save(
                RecipeSaveRequest(
                    id = s.id, name = s.name, servings = s.servings.toIntOrNull() ?: 1,
                    totalMinutes = s.minutes.toIntOrNull() ?: 0,
                    steps = RecipeEditing.stepsFromText(s.stepsText),
                    ingredients = s.ingredients, sourceUrl = s.sourceUrl, fallbackNutrition = s.carried,
                    notes = s.notes,
                ),
            )
            _state.update { it.copy(savedId = saved.id, error = null) }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }
}

@Composable
fun RecipeEditorScreen(
    recipeId: String?,
    contentPadding: PaddingValues,
    onDone: (savedId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecipeEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(recipeId) { viewModel.start(recipeId) }
    LaunchedEffect(state.savedId) { state.savedId?.let(onDone) }
    var sheetFor by remember { mutableStateOf<IngredientDraft?>(null) }
    var adding by remember { mutableStateOf(false) }

    if (!state.loaded) return
    val servings = state.servings.toIntOrNull() ?: 1
    val macros = RecipeEditing.macros(state.ingredients, servings)

    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(contentPadding).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SubHead(if (state.id == null) "New recipe" else "Edit recipe", "Add the ingredients and steps. Nothing is filled in for you.")
        OutlinedTextField(state.name, viewModel::setName, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                state.servings, viewModel::setServings, label = { Text("Servings") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                state.minutes, viewModel::setMinutes, label = { Text("Total minutes") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
            )
        }

        IntakeCard {
            Text("Ingredients", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            if (state.ingredients.isEmpty()) {
                Text("None yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // Group headings stay between the rows, as they will read on the recipe screen.
            IngredientSections.of(state.ingredients) { it.group }.forEach { section ->
                section.label?.let {
                    Text(it, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                }
                section.items.forEach { d ->
                    IngredientRow(d, onEdit = { sheetFor = d }, onRemove = { viewModel.removeIngredient(d.key) })
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { adding = true }, shape = IntakeShape.Button,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text("Add ingredients") }
        }

        MacroSummary(macros, servings, state.carried)
        AllergenChips(state.scan)

        OutlinedTextField(
            state.stepsText, viewModel::setSteps,
            label = { Text("Steps, one per line") }, minLines = 4, modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            state.notes, viewModel::setNotes,
            label = { Text("Notes (storage, swaps, tips)") }, minLines = 2, modifier = Modifier.fillMaxWidth(),
        )

        state.error?.let { SandNote(it) }
        Button(
            onClick = viewModel::save, shape = IntakeShape.Button,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) { Text(if (state.id == null) "Save recipe" else "Save changes") }
        Spacer(Modifier.height(24.dp))
    }

    if (adding || sheetFor != null) {
        IngredientSheet(
            initial = sheetFor,
            newKey = viewModel::newKey,
            search = viewModel::searchFoods,
            onDismiss = { adding = false; sheetFor = null },
            onSave = { viewModel.upsertIngredient(it); adding = false; sheetFor = null },
            onPasteLines = { text, group -> viewModel.addLines(text, group); adding = false; sheetFor = null },
        )
    }
}

@Composable
private fun IngredientRow(d: IngredientDraft, onEdit: () -> Unit, onRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(onClick = onEdit), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(d.line, style = MaterialTheme.typography.bodyMedium)
            Text(
                if (d.hasNutrition) "Counted in the macros" else "Not counted: needs grams and macros per 100 g",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(
            onClick = onRemove,
            modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Remove ${d.name}" },
        ) { Text("×") }
    }
}

/** Live per-serving macros, with the lines that are left out named rather than hidden. */
@Composable
internal fun MacroSummary(
    macros: com.zeroindex.intake.domain.plan.RecipeMacroResult,
    servings: Int,
    carried: PageNutrition?,
    carriedLabel: String = "Saved with this recipe",
) {
    IntakeCard {
        Text("Per serving", style = MaterialTheme.typography.titleMedium)
        if (macros.isEmpty) {
            if (carried?.kcal != null) {
                Text(
                    "${carried.kcal.roundToInt()} kcal · ${(carried.proteinG ?: 0.0).roundToInt()} g protein · " +
                        "${(carried.carbsG ?: 0.0).roundToInt()} g carbs · ${(carried.fatG ?: 0.0).roundToInt()} g fat",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(carriedLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(
                    "No macros yet. Give an ingredient its grams and macros per 100 g, or pick a food from your list.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            val p = macros.perServing
            Text(
                "${p.kcal.roundToInt()} kcal · ${p.protein.roundToInt()} g protein · ${p.carbs.roundToInt()} g carbs · ${p.fat.roundToInt()} g fat",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text("Split across $servings servings", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!macros.isComplete) {
                SandNote(
                    "Incomplete. Not counted: ${macros.missing.take(8).joinToString()}${if (macros.missing.size > 8) "…" else ""}. " +
                        "The total above is lower than the real dish until these have grams and macros.",
                )
            }
        }
    }
}

/** The local engine's verdict for the ingredients, as chips. Never softened by anything on the page. */
@Composable
internal fun AllergenChips(scan: AllergenScanResult) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        when {
            scan.contains.isNotEmpty() -> StatusPill("Contains ${scan.contains.map { it.matchedTerm }.distinct().joinToString()}", tone = PillTone.Bad)
            scan.mayContain.isNotEmpty() -> StatusPill("May contain ${scan.mayContain.map { it.matchedTerm }.distinct().joinToString()}", tone = PillTone.Wait)
            scan.checkLabel.isEmpty() -> StatusPill("Passed allergen check")
        }
        scan.checkLabel.distinctBy { it.matchedTerm }.take(6).forEach { CheckLabelChip(it.reason) }
        if (scan.contains.isNotEmpty() || scan.mayContain.isNotEmpty()) {
            Text(
                "Matched on this phone against your allergens. You can still save it, but it will not log without a warning.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun IngredientSheet(
    initial: IngredientDraft?,
    newKey: () -> String,
    search: suspend (String) -> List<FoodEntity>,
    onDismiss: () -> Unit,
    onSave: (IngredientDraft) -> Unit,
    onPasteLines: (String, String?) -> Unit,
) {
    var tab by remember { mutableIntStateOf(if (initial != null && initial.foodId == null) 1 else 0) }
    // One group for whatever this sheet adds or saves; blank is no group.
    var group by remember { mutableStateOf(initial?.group.orEmpty()) }
    val groupOrNull = group.trim().takeIf { it.isNotEmpty() }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).verticalScroll(rememberScrollState())) {
            Text(if (initial == null) "Add ingredients" else "Edit ingredient", style = MaterialTheme.typography.titleMedium)
            PrimaryTabRow(selectedTabIndex = tab) {
                listOf("From my foods", "Type it", "Paste a list").forEachIndexed { i, label ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label, maxLines = 1) })
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                group, { group = it }, label = { Text("Group (optional)") }, singleLine = true,
                supportingText = { Text("For example \"For the Sauce\". Ingredients with the same group sit together.") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            when (tab) {
                0 -> PickFood(initial, newKey, search) { onSave(it.copy(group = groupOrNull)) }
                1 -> TypedIngredient(initial, newKey) { onSave(it.copy(group = groupOrNull)) }
                else -> {
                    var text by remember { mutableStateOf("") }
                    Text(
                        "One ingredient per line, like \"1 ½ cups milk\" or \"200 g chicken breast\". Weights give grams; add macros afterwards to count them. A line starting with ## is a group heading.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(text, { text = it }, minLines = 5, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    Button(
                        onClick = { onPasteLines(text, groupOrNull) }, enabled = text.isNotBlank(), shape = IntakeShape.Button,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 48.dp),
                    ) { Text("Add these") }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PickFood(initial: IngredientDraft?, newKey: () -> String, search: suspend (String) -> List<FoodEntity>, onSave: (IngredientDraft) -> Unit) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<FoodEntity>>(emptyList()) }
    var chosen by remember { mutableStateOf<FoodEntity?>(null) }
    var grams by remember { mutableStateOf(initial?.grams?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() }.orEmpty()) }
    LaunchedEffect(query) { results = search(query) }
    OutlinedTextField(query, { query = it }, label = { Text("Search foods") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    results.take(12).forEach { f ->
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { chosen = f; if (grams.isBlank()) grams = f.servingSizeGrams.roundToInt().toString() }, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(f.name, style = MaterialTheme.typography.bodyMedium)
                Text("${f.kcalPer100g.roundToInt()} kcal per 100 g", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (chosen?.id == f.id) Text("Picked", style = MaterialTheme.typography.labelMedium)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
    chosen?.let { f ->
        OutlinedTextField(
            grams, { grams = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Grams of ${f.name}") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        Button(
            onClick = {
                onSave(
                    IngredientDraft(
                        key = initial?.key ?: newKey(), name = f.name, foodId = f.id, grams = grams.toDoubleOrNull(),
                        kcalPer100g = f.kcalPer100g, proteinPer100g = f.proteinPer100g, carbsPer100g = f.carbsPer100g,
                        fatPer100g = f.fatPer100g, allergenTags = f.allergenTags,
                    ),
                )
            },
            enabled = (grams.toDoubleOrNull() ?: 0.0) > 0.0, shape = IntakeShape.Button,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 48.dp),
        ) { Text("Add") }
    }
}

@Composable
private fun TypedIngredient(initial: IngredientDraft?, newKey: () -> String, onSave: (IngredientDraft) -> Unit) {
    fun fmt(v: Double?) = v?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() }.orEmpty()
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var qty by remember { mutableStateOf(initial?.quantityText.orEmpty()) }
    var grams by remember { mutableStateOf(fmt(initial?.grams)) }
    var kcal by remember { mutableStateOf(fmt(initial?.kcalPer100g)) }
    var protein by remember { mutableStateOf(fmt(initial?.proteinPer100g)) }
    var carbs by remember { mutableStateOf(fmt(initial?.carbsPer100g)) }
    var fat by remember { mutableStateOf(fmt(initial?.fatPer100g)) }
    val dec = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    fun clean(s: String) = s.filter { it.isDigit() || it == '.' }
    OutlinedTextField(name, { name = it }, label = { Text("Ingredient") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
        OutlinedTextField(qty, { qty = it }, label = { Text("Amount as written") }, singleLine = true, modifier = Modifier.weight(1f))
        OutlinedTextField(grams, { grams = clean(it) }, label = { Text("Grams") }, singleLine = true, keyboardOptions = dec, modifier = Modifier.weight(1f))
    }
    Text(
        "Optional: macros per 100 g. With grams and all four, this ingredient counts in the totals.",
        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
        OutlinedTextField(kcal, { kcal = clean(it) }, label = { Text("kcal") }, singleLine = true, keyboardOptions = dec, modifier = Modifier.weight(1f))
        OutlinedTextField(protein, { protein = clean(it) }, label = { Text("Protein") }, singleLine = true, keyboardOptions = dec, modifier = Modifier.weight(1f))
        OutlinedTextField(carbs, { carbs = clean(it) }, label = { Text("Carbs") }, singleLine = true, keyboardOptions = dec, modifier = Modifier.weight(1f))
        OutlinedTextField(fat, { fat = clean(it) }, label = { Text("Fat") }, singleLine = true, keyboardOptions = dec, modifier = Modifier.weight(1f))
    }
    Button(
        onClick = {
            onSave(
                IngredientDraft(
                    key = initial?.key ?: newKey(), name = name.trim(), foodId = null, quantityText = qty.trim(),
                    grams = grams.toDoubleOrNull(), kcalPer100g = kcal.toDoubleOrNull(), proteinPer100g = protein.toDoubleOrNull(),
                    carbsPer100g = carbs.toDoubleOrNull(), fatPer100g = fat.toDoubleOrNull(),
                ),
            )
        },
        enabled = name.isNotBlank() && (kcal.toDoubleOrNull() ?: 0.0) <= 900.0 &&
            ((protein.toDoubleOrNull() ?: 0.0) + (carbs.toDoubleOrNull() ?: 0.0) + (fat.toDoubleOrNull() ?: 0.0)) <= 100.5,
        shape = IntakeShape.Button,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 48.dp),
    ) { Text(if (initial == null) "Add" else "Save") }
}
