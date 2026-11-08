package com.zeroindex.intake.ui.screens.fooddata

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.zeroindex.intake.data.db.entity.FoodEntity
import com.zeroindex.intake.data.keys.ApiKeyStore
import com.zeroindex.intake.data.remote.openfoodfacts.FoodIngredients
import com.zeroindex.intake.data.remote.openfoodfacts.OffContribution
import com.zeroindex.intake.data.remote.openfoodfacts.OffContributionForm
import com.zeroindex.intake.data.repository.ContributionOutcome
import com.zeroindex.intake.data.repository.FoodRepository
import com.zeroindex.intake.data.repository.OffContributionRepository
import com.zeroindex.intake.data.settings.FoodDataPrefs
import com.zeroindex.intake.domain.barcode.Barcodes
import com.zeroindex.intake.domain.model.FoodSource
import com.zeroindex.intake.ui.components.LabeledField
import com.zeroindex.intake.ui.components.SandNote
import com.zeroindex.intake.ui.components.SectionCard
import com.zeroindex.intake.ui.components.SubHead
import com.zeroindex.intake.ui.theme.IntakeShape
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FoodDetailUiState(
    val food: FoodEntity? = null,
    val contributeEnabled: Boolean = false,
    val hasCredentials: Boolean = false,
    val message: String? = null,
    val sending: Boolean = false,
)

@HiltViewModel
class FoodDetailViewModel @Inject constructor(
    private val foods: FoodRepository,
    private val contributions: OffContributionRepository,
    prefs: FoodDataPrefs,
    keys: ApiKeyStore,
) : ViewModel() {

    private val foodId = MutableStateFlow<String?>(null)
    private val message = MutableStateFlow<String?>(null)
    private val sending = MutableStateFlow(false)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<FoodDetailUiState> = combine(
        foodId.flatMapLatest { id -> if (id == null) flowOf(null) else foods.observe(id) },
        prefs.settings,
        keys.state,
        message,
        sending,
    ) { food, settings, keyState, msg, busy ->
        FoodDetailUiState(food, settings.offContribute, keyState.hasOffCredentials, msg, busy)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FoodDetailUiState())

    fun load(id: String) { foodId.value = id }

    fun dismissMessage() { message.value = null }

    fun preview(food: FoodEntity, quantity: String): OffContribution = contributions.preview(food, quantity)

    /**
     * Saves the user's edits. Editing a product fetched from Open Food Facts makes it
     * the user's own version, which is what makes it eligible to contribute.
     */
    fun save(
        original: FoodEntity,
        name: String,
        brand: String,
        barcode: String,
        servingGrams: Double,
        servingLabel: String,
        kcal: Double,
        protein: Double,
        carbs: Double,
        fat: Double,
        fiber: Double,
        sugar: Double,
        sodiumMg: Double,
        saturatedFat: Double,
        ingredients: String,
    ) = viewModelScope.launch {
        val tags = FoodIngredients.tags(original.allergenTags).split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val edited = original.copy(
            name = name.trim(),
            brand = brand.trim().ifBlank { null },
            barcode = barcode.ifBlank { null },
            servingSizeGrams = servingGrams,
            servingLabel = servingLabel.trim().ifBlank { "${servingGrams.toInt()} g" },
            kcalPer100g = kcal,
            proteinPer100g = protein,
            carbsPer100g = carbs,
            fatPer100g = fat,
            fiberPer100g = fiber,
            sugarPer100g = sugar,
            sodiumPer100g = sodiumMg,
            saturatedFatPer100g = saturatedFat,
            allergenTags = FoodIngredients.join(tags, ingredients),
        )
        val changed = edited != original
        val withSource = if (changed && original.source == FoodSource.OPEN_FOOD_FACTS && !edited.barcode.isNullOrBlank()) {
            edited.copy(source = FoodSource.BARCODE)
        } else {
            edited
        }
        foods.save(withSource)
        message.value = "Saved."
    }

    fun remove(food: FoodEntity, done: () -> Unit) = viewModelScope.launch {
        foods.softDelete(food.id)
        done()
    }

    /** Only ever called from the confirmation dialog's button. */
    fun contribute(food: FoodEntity, quantity: String) = viewModelScope.launch {
        sending.value = true
        message.value = when (val outcome = contributions.contribute(food, quantity)) {
            ContributionOutcome.Sent -> "Sent to Open Food Facts. Thank you."
            ContributionOutcome.NoCredentials -> "Add your Open Food Facts account in Food database settings first."
            ContributionOutcome.NotEnabled -> "Contributing is switched off in Food database settings."
            ContributionOutcome.NotEligible -> "This food cannot be contributed: it needs a barcode and nutrition values."
            is ContributionOutcome.Rejected -> "Open Food Facts did not accept it: ${outcome.message}"
            is ContributionOutcome.Failed -> outcome.message
        }
        sending.value = false
    }
}

private fun sourceLabel(source: FoodSource) = when (source) {
    FoodSource.MANUAL -> "Made by you"
    FoodSource.BARCODE -> "Scanned or edited by you"
    FoodSource.OPEN_FOOD_FACTS -> "From Open Food Facts"
    FoodSource.DATABASE -> "From the bundled USDA database"
    FoodSource.SEARCH -> "From USDA search"
    FoodSource.AI_PHOTO -> "From a photo estimate"
    FoodSource.RECIPE -> "From a recipe"
}

private fun fmt(value: Double): String {
    val rounded = Math.round(value * 100.0) / 100.0
    return if (rounded == Math.floor(rounded)) rounded.toLong().toString() else rounded.toString()
}

/** Food detail and edit. All values are per 100 g; the serving only sets the default portion. */
@Composable
fun FoodDetailScreen(
    foodId: String,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpenFoodData: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FoodDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(foodId) { viewModel.load(foodId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val food = state.food

    if (food == null) {
        Column(modifier.padding(contentPadding).padding(16.dp)) {
            SubHead("Food", "This food is not on this phone.")
            OutlinedButton(onClick = onBack) { Text("Back") }
        }
        return
    }

    // Reseeded when the stored food changes (after a save).
    var name by remember(food) { mutableStateOf(food.name) }
    var brand by remember(food) { mutableStateOf(food.brand.orEmpty()) }
    var barcode by remember(food) { mutableStateOf(food.barcode.orEmpty()) }
    var servingGrams by remember(food) { mutableStateOf(fmt(food.servingSizeGrams)) }
    var servingLabel by remember(food) { mutableStateOf(food.servingLabel) }
    var kcal by remember(food) { mutableStateOf(fmt(food.kcalPer100g)) }
    var protein by remember(food) { mutableStateOf(fmt(food.proteinPer100g)) }
    var carbs by remember(food) { mutableStateOf(fmt(food.carbsPer100g)) }
    var fat by remember(food) { mutableStateOf(fmt(food.fatPer100g)) }
    var fiber by remember(food) { mutableStateOf(fmt(food.fiberPer100g)) }
    var sugar by remember(food) { mutableStateOf(fmt(food.sugarPer100g)) }
    var sodium by remember(food) { mutableStateOf(fmt(food.sodiumPer100g)) }
    var satFat by remember(food) { mutableStateOf(fmt(food.saturatedFatPer100g)) }
    var ingredients by remember(food) { mutableStateOf(FoodIngredients.text(food.allergenTags)) }
    var confirming by remember { mutableStateOf(false) }
    var quantity by remember { mutableStateOf("") }
    var confirmRemove by remember { mutableStateOf(false) }

    val barcodeText = Barcodes.digitsOnly(barcode)
    val normalizedBarcode = if (barcodeText.isBlank()) "" else Barcodes.normalize(barcodeText)
    val barcodeBad = barcodeText.isNotBlank() && normalizedBarcode == null
    val grams = servingGrams.toDoubleOrNull()
    val canSave = name.isNotBlank() && !barcodeBad && grams != null && grams > 0 && kcal.toDoubleOrNull() != null

    fun doubleOf(text: String) = text.toDoubleOrNull() ?: 0.0
    val dirty = name.trim() != food.name || brand.trim() != food.brand.orEmpty() ||
        (normalizedBarcode ?: barcodeText) != food.barcode.orEmpty() ||
        doubleOf(kcal) != food.kcalPer100g || doubleOf(protein) != food.proteinPer100g ||
        doubleOf(carbs) != food.carbsPer100g || doubleOf(fat) != food.fatPer100g ||
        doubleOf(fiber) != food.fiberPer100g || doubleOf(sugar) != food.sugarPer100g ||
        doubleOf(sodium) != food.sodiumPer100g || doubleOf(satFat) != food.saturatedFatPer100g ||
        doubleOf(servingGrams) != food.servingSizeGrams || servingLabel != food.servingLabel ||
        ingredients.trim() != FoodIngredients.text(food.allergenTags)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(
                top = contentPadding.calculateTopPadding() + 12.dp,
                bottom = contentPadding.calculateBottomPadding() + 32.dp,
                start = 16.dp,
                end = 16.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SubHead(food.name, sourceLabel(food.source))
        state.message?.let {
            SandNote(it)
            TextButton(onClick = viewModel::dismissMessage) { Text("Dismiss") }
        }

        SectionCard("Details") {
            LabeledField("Name", name, { name = it })
            LabeledField("Brand (optional)", brand, { brand = it })
            LabeledField(
                label = "Barcode (optional)",
                value = barcode,
                onValueChange = { barcode = it.filter(Char::isDigit).take(14) },
                keyboardType = KeyboardType.Number,
                isError = barcodeBad,
                supporting = if (barcodeBad) "The check digit does not add up. Check the number under the bars." else null,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LabeledField("Serving grams", servingGrams, { servingGrams = it.filter { c -> c.isDigit() || c == '.' } }, Modifier.weight(1f), keyboardType = KeyboardType.Decimal)
                LabeledField("Serving name", servingLabel, { servingLabel = it }, Modifier.weight(1f))
            }
        }

        SectionCard("Nutrition per 100 g") {
            @Composable fun num(label: String, value: String, set: (String) -> Unit, m: Modifier) =
                LabeledField(label, value, { set(it.filter { c -> c.isDigit() || c == '.' }) }, m, keyboardType = KeyboardType.Decimal)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                num("Calories", kcal, { kcal = it }, Modifier.weight(1f))
                num("Protein g", protein, { protein = it }, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                num("Carbs g", carbs, { carbs = it }, Modifier.weight(1f))
                num("Fat g", fat, { fat = it }, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                num("Fibre g", fiber, { fiber = it }, Modifier.weight(1f))
                num("Sugar g", sugar, { sugar = it }, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                num("Sodium mg", sodium, { sodium = it }, Modifier.weight(1f))
                num("Saturated fat g", satFat, { satFat = it }, Modifier.weight(1f))
            }
        }

        SectionCard("Ingredients") {
            LabeledField(
                label = "Ingredient text (optional)",
                value = ingredients,
                onValueChange = { ingredients = it },
                supporting = "Also used by the allergen check each time you log this food.",
            )
        }

        Button(
            onClick = {
                viewModel.save(
                    food, name, brand, normalizedBarcode ?: barcodeText, grams ?: 100.0, servingLabel,
                    doubleOf(kcal), doubleOf(protein), doubleOf(carbs), doubleOf(fat),
                    doubleOf(fiber), doubleOf(sugar), doubleOf(sodium), doubleOf(satFat), ingredients,
                )
            },
            enabled = canSave && dirty,
            shape = IntakeShape.Button,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("Save changes") }

        if (OffContributionForm.isEligible(food)) {
            SectionCard("Open Food Facts") {
                food.offContributedAt?.let {
                    Text(
                        "Sent on ${DateFormat.getDateInstance().format(Date(it))}. You can send it again after more edits.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                when {
                    !state.contributeEnabled -> {
                        Text(
                            "Share this product with the open database so others can find it. Switch " +
                                "contributing on in Food database settings first.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        OutlinedButton(onClick = onOpenFoodData, modifier = Modifier.padding(top = 8.dp)) {
                            Text("Food database settings")
                        }
                    }
                    !state.hasCredentials -> {
                        Text(
                            "Add your own Open Food Facts username and password in Food database settings.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        OutlinedButton(onClick = onOpenFoodData, modifier = Modifier.padding(top = 8.dp)) {
                            Text("Food database settings")
                        }
                    }
                    else -> Button(
                        onClick = { confirming = true },
                        enabled = !dirty && !state.sending,
                        shape = IntakeShape.Button,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) { Text(if (dirty) "Save your changes first" else "Contribute to Open Food Facts") }
                }
            }
        }

        OutlinedButton(
            onClick = { confirmRemove = true },
            shape = IntakeShape.Button,
            modifier = Modifier.fillMaxWidth().height(50.dp),
        ) { Text("Remove from my foods") }
    }

    if (confirming) {
        val preview = viewModel.preview(food, quantity)
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("Send to Open Food Facts?") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("This is exactly what will be sent:", style = MaterialTheme.typography.bodySmall)
                    preview.previewLines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    Text(
                        "Along with your Open Food Facts username and password, over HTTPS, in the " +
                            "request body.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    LabeledField(
                        label = "Package quantity (optional)",
                        value = quantity,
                        onValueChange = { quantity = it },
                        placeholder = "For example 250 g",
                    )
                    Text(
                        "Your contribution is public under the ODbL licence.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    confirming = false
                    viewModel.contribute(food, quantity)
                }) { Text("Send") }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text("Cancel") } },
        )
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove ${food.name}?") },
            text = { Text("Past log entries keep their numbers. You can add the food again any time.") },
            confirmButton = {
                Button(onClick = {
                    confirmRemove = false
                    viewModel.remove(food, onBack)
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancel") } },
        )
    }
}
