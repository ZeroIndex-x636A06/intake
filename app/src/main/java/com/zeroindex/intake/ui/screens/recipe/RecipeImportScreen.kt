package com.zeroindex.intake.ui.screens.recipe

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import com.zeroindex.intake.ui.motion.SkeletonLine
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.zeroindex.intake.data.db.entity.RecipeOrigin
import com.zeroindex.intake.data.recipe.FetchResult
import com.zeroindex.intake.data.recipe.RecipeAiExtractor
import com.zeroindex.intake.data.recipe.RecipeFetcher
import com.zeroindex.intake.data.repository.RecipeRepository
import com.zeroindex.intake.data.repository.RecipeSaveRequest
import com.zeroindex.intake.domain.allergen.AllergenScanResult
import com.zeroindex.intake.domain.recipe.IngredientDraft
import com.zeroindex.intake.domain.recipe.IngredientSections
import com.zeroindex.intake.domain.recipe.RecipeNutritionLabels
import com.zeroindex.intake.domain.recipe.RecipeTimes
import com.zeroindex.intake.ui.images.RecipePhoto
import com.zeroindex.intake.domain.recipe.PageNutrition
import com.zeroindex.intake.domain.recipe.PageParse
import com.zeroindex.intake.domain.recipe.RecipeDraft
import com.zeroindex.intake.domain.recipe.RecipeEditing
import com.zeroindex.intake.domain.recipe.RecipePageParser
import com.zeroindex.intake.ui.components.IntakeCard
import com.zeroindex.intake.ui.components.SandNote
import com.zeroindex.intake.ui.components.SubHead
import com.zeroindex.intake.ui.theme.IntakeShape
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The editable preview of a page's recipe. Everything the user can change is text. */
data class ImportForm(
    val name: String = "",
    val servings: String = "4",
    val minutes: String = "",
    val ingredientsText: String = "",
    val stepsText: String = "",
    /** Per-serving numbers the page published. Shown as "From the recipe page". */
    val nutrition: PageNutrition? = null,
    val sourceUrl: String = "",
    val readByAi: Boolean = false,
    /** The recipe's Notes section, editable. */
    val notes: String = "",
    val prepMinutes: Int? = null,
    val cookMinutes: Int? = null,
    /** The total as the page gave it: prep and cook are kept only while the total still matches. */
    val readMinutes: String = "",
    /** The photo's address on the page. Shown above the form and saved with the recipe. */
    val imageUrl: String? = null,
)

sealed interface ImportStage {
    data object Idle : ImportStage
    data object Fetching : ImportStage
    data object ReadingWithAi : ImportStage
    data class Preview(val form: ImportForm, val scan: AllergenScanResult = AllergenScanResult.CLEAN) : ImportStage
    /** Nothing to read. [aiAvailable] shows the optional "Try with AI" button. */
    data class NoRecipe(val pageText: String, val url: String, val aiAvailable: Boolean, val message: String? = null) : ImportStage
    data class Failed(val message: String) : ImportStage
    data class Saved(val recipeId: String) : ImportStage
}

@HiltViewModel
class RecipeImportViewModel @Inject constructor(
    private val fetcher: RecipeFetcher,
    private val recipes: RecipeRepository,
    private val ai: RecipeAiExtractor,
) : ViewModel() {

    private val _url = MutableStateFlow("")
    val url: StateFlow<String> = _url.asStateFlow()
    private val _stage = MutableStateFlow<ImportStage>(ImportStage.Idle)
    val stage: StateFlow<ImportStage> = _stage.asStateFlow()

    fun setUrl(v: String) { _url.value = v }

    /** Runs only when the user taps Import: this is the one network call of a link import. */
    fun import() {
        if (_stage.value is ImportStage.Fetching) return
        _stage.value = ImportStage.Fetching
        viewModelScope.launch {
            when (val r = fetcher.fetch(_url.value)) {
                is FetchResult.Failed -> _stage.value = ImportStage.Failed(r.message)
                is FetchResult.Page -> when (val parsed = RecipePageParser.parse(r.html, r.finalUrl)) {
                    is PageParse.Found -> showPreview(formOf(parsed.recipe, r.finalUrl, readByAi = false))
                    is PageParse.NoRecipe -> _stage.value =
                        ImportStage.NoRecipe(parsed.pageText, r.finalUrl, aiAvailable = ai.isAvailable() && parsed.pageText.isNotBlank())
                }
            }
        }
    }

    /** Sends the page text to the AI gateway. Only ever called from the "Try with AI" button. */
    fun tryWithAi() {
        val s = _stage.value as? ImportStage.NoRecipe ?: return
        _stage.value = ImportStage.ReadingWithAi
        viewModelScope.launch {
            val draft = runCatching { ai.extract(s.pageText, s.url) }
            val d = draft.getOrNull()
            if (d != null) {
                showPreview(formOf(d, s.url, readByAi = true))
            } else {
                _stage.value = s.copy(
                    message = if (draft.isFailure) "The AI step failed: ${draft.exceptionOrNull()?.message ?: "try again later"}." else "The AI could not find a recipe in that text.",
                )
            }
        }
    }

    private fun formOf(d: RecipeDraft, url: String, readByAi: Boolean) = ImportForm(
        name = d.name,
        servings = (d.servings ?: 4).toString(),
        minutes = d.totalMinutes?.toString().orEmpty(),
        // A "## For the Sauce" line starts a group for the lines under it; the user can edit them like any text.
        ingredientsText = IngredientSections.toTextWithHeaders(d.ingredients, d.ingredientGroups),
        stepsText = d.steps.joinToString("\n"),
        nutrition = d.nutrition,
        sourceUrl = d.url?.takeIf { it.startsWith("http") } ?: url,
        readByAi = readByAi,
        notes = d.notes.orEmpty(),
        prepMinutes = d.prepMinutes,
        cookMinutes = d.cookMinutes,
        readMinutes = d.totalMinutes?.toString().orEmpty(),
        imageUrl = d.imageUrl,
    )

    private fun showPreview(form: ImportForm) {
        _stage.value = ImportStage.Preview(form)
        rescan(form)
    }

    fun edit(block: (ImportForm) -> ImportForm) {
        val s = _stage.value as? ImportStage.Preview ?: return
        val f = block(s.form)
        _stage.value = s.copy(form = f)
        rescan(f)
    }

    private fun drafts(f: ImportForm): List<IngredientDraft> {
        var n = 0
        return RecipeEditing.draftsFromText(f.ingredientsText, { "i${n++}" }).map { it.copy(grams = null) }
    }

    private fun rescan(f: ImportForm) {
        viewModelScope.launch {
            val scan = recipes.scan(f.name, drafts(f))
            val s = _stage.value as? ImportStage.Preview ?: return@launch
            if (s.form == f) _stage.value = s.copy(scan = scan)
        }
    }

    fun save() {
        val s = _stage.value as? ImportStage.Preview ?: return
        val f = s.form
        if (f.name.isBlank()) return
        viewModelScope.launch {
            val saved = recipes.save(
                RecipeSaveRequest(
                    id = null, name = f.name, servings = f.servings.toIntOrNull() ?: 1,
                    totalMinutes = f.minutes.toIntOrNull() ?: 0,
                    steps = RecipeEditing.stepsFromText(f.stepsText),
                    ingredients = drafts(f), origin = RecipeOrigin.LINK, sourceUrl = f.sourceUrl.ifBlank { null },
                    fallbackNutrition = f.nutrition,
                    notes = f.notes,
                    prepMinutes = f.prepMinutes.takeIf { f.minutes == f.readMinutes },
                    cookMinutes = f.cookMinutes.takeIf { f.minutes == f.readMinutes },
                    imageUrl = f.imageUrl,
                ),
            )
            _stage.value = ImportStage.Saved(saved.id)
        }
    }

    fun reset() { _stage.value = ImportStage.Idle }
}

/** Food > Recipes > Import from a link. Paste an address, read the recipe off the page, review, save. */
@Composable
fun RecipeImportScreen(
    contentPadding: PaddingValues,
    onSaved: (recipeId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecipeImportViewModel = hiltViewModel(),
) {
    val url by viewModel.url.collectAsStateWithLifecycle()
    val stage by viewModel.stage.collectAsStateWithLifecycle()
    LaunchedEffect(stage) { (stage as? ImportStage.Saved)?.let { onSaved(it.recipeId) } }

    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(contentPadding).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SubHead("Import from a link", "Paste a recipe page. Intake reads the recipe data on the page, with no AI.")
        OutlinedTextField(
            url, viewModel::setUrl, label = { Text("Recipe web address") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = viewModel::import, enabled = url.isNotBlank() && stage != ImportStage.Fetching,
            shape = IntakeShape.Button, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) { Text("Read this page") }
        Text(
            "Tapping this fetches the page straight from that site, so the site can see your phone's IP address. Nothing is sent anywhere else.",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        when (val s = stage) {
            ImportStage.Idle, is ImportStage.Saved -> Unit
            ImportStage.Fetching, ImportStage.ReadingWithAi -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (s == ImportStage.Fetching) "Fetching the page…" else "Asking the AI to read the page text…")
                // A recipe-shaped skeleton: a title, then ingredient lines.
                SkeletonLine(fraction = 0.7f, height = 18.dp)
                SkeletonLine(fraction = 1f)
                SkeletonLine(fraction = 0.9f)
                SkeletonLine(fraction = 0.8f)
            }
            is ImportStage.Failed -> SandNote(s.message, title = "Could not import")
            is ImportStage.NoRecipe -> {
                SandNote("This page has no recipe data we can read.", title = "No recipe found")
                s.message?.let { SandNote(it) }
                if (s.aiAvailable) {
                    OutlinedButton(
                        onClick = viewModel::tryWithAi, shape = IntakeShape.Button,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    ) { Text("Try with AI") }
                    Text(
                        "Optional. Sends this page's text to your AI model through OpenRouter, using your privacy settings. It costs a little and runs only when you tap.",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        "You can still add this recipe by hand from Food, Recipes.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            is ImportStage.Preview -> Preview(s, viewModel)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Preview(s: ImportStage.Preview, vm: RecipeImportViewModel) {
    val f = s.form
    if (f.readByAi) SandNote("Read from the page text by an AI model. Check every line against the page before you save.", title = "AI draft")
    RecipePhoto(f.imageUrl, height = 160.dp)
    OutlinedTextField(f.name, { v -> vm.edit { it.copy(name = v) } }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    RecipeTimes.label(f.prepMinutes.takeIf { f.minutes == f.readMinutes }, f.cookMinutes.takeIf { f.minutes == f.readMinutes }, f.minutes.toIntOrNull())
        ?.takeIf { f.prepMinutes != null || f.cookMinutes != null }
        ?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            f.servings, { v -> vm.edit { it.copy(servings = v.filter(Char::isDigit).take(3)) } }, label = { Text("Servings") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            f.minutes, { v -> vm.edit { it.copy(minutes = v.filter(Char::isDigit).take(4)) } }, label = { Text("Total minutes") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
        )
    }
    OutlinedTextField(
        f.ingredientsText, { v -> vm.edit { it.copy(ingredientsText = v) } },
        label = { Text("Ingredients, one per line") }, minLines = 4, modifier = Modifier.fillMaxWidth(),
        supportingText = { Text("A line starting with ## is a group heading, like \"## For the Sauce\".") },
    )
    AllergenChips(s.scan)
    OutlinedTextField(
        f.stepsText, { v -> vm.edit { it.copy(stepsText = v) } },
        label = { Text("Steps, one per line") }, minLines = 4, modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        f.notes, { v -> vm.edit { it.copy(notes = v) } },
        label = { Text("Notes (storage, swaps, tips)") }, minLines = 2, modifier = Modifier.fillMaxWidth(),
    )
    f.nutrition?.takeIf { it.kcal != null }?.let { n ->
        IntakeCard {
            Text("From the recipe page", style = MaterialTheme.typography.titleSmall)
            Text(
                "${n.kcal!!.roundToInt()} kcal · protein ${n.proteinG.listed()} · carbs ${n.carbsG.listed()} · fat ${n.fatG.listed()} per serving",
                style = MaterialTheme.typography.bodyMedium,
            )
            RecipeNutritionLabels.extras(n.fiberG, n.sugarG, n.saturatedFatG, n.sodiumMg).takeIf { it.isNotEmpty() }?.let { extras ->
                Text(
                    extras.joinToString(" · ") { (label, value) -> "$label $value" },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (n.proteinG == null || n.carbsG == null || n.fatG == null) {
                Text(
                    "The page did not list everything. Intake will estimate what is missing from the ingredients and label it as an estimate.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                "These are the site's own numbers, not checked by Intake. They are saved with the recipe so a serving can be logged.",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } ?: Text(
        "The page published no nutrition. Intake will estimate it from the ingredients, labelled as an estimate, or you can add ingredient macros by editing the recipe.",
        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (f.sourceUrl.isNotBlank()) {
        Text("Source: ${f.sourceUrl}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Button(
        onClick = vm::save, enabled = f.name.isNotBlank(), shape = IntakeShape.Button,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
    ) { Text("Save recipe") }
}

/** "12 g", or "not listed" for a number the page did not give. Never "0 g" for a missing one. */
private fun Double?.listed(): String = this?.let { "${it.roundToInt()} g" } ?: "not listed"
