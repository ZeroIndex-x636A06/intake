package com.zeroindex.intake.ui.screens.fooddata

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zeroindex.intake.data.db.entity.FoodEntity
import com.zeroindex.intake.data.remote.openfoodfacts.FoodIngredients
import com.zeroindex.intake.data.remote.openfoodfacts.OffContributionForm
import com.zeroindex.intake.domain.barcode.Barcodes
import com.zeroindex.intake.domain.drinks.DrinkCategory
import com.zeroindex.intake.domain.drinks.DrinkSizes
import com.zeroindex.intake.domain.model.FoodSource
import com.zeroindex.intake.domain.model.MealSlot
import com.zeroindex.intake.domain.nfc.TemplateLink
import com.zeroindex.intake.domain.nutrition.LabelMode
import com.zeroindex.intake.domain.nutrition.NutrientKey
import com.zeroindex.intake.domain.nutrition.NutrientsPer100
import com.zeroindex.intake.domain.nutrition.NutritionLabel
import com.zeroindex.intake.domain.nutrition.Unlisted
import com.zeroindex.intake.domain.settings.SodiumDisplay
import com.zeroindex.intake.domain.units.PortionUnit
import com.zeroindex.intake.domain.units.VolumeUnit
import com.zeroindex.intake.ui.allergen.AllergenBanner
import com.zeroindex.intake.ui.allergen.AllergenWarningSheet
import com.zeroindex.intake.ui.components.ChoiceChip
import com.zeroindex.intake.ui.components.LabeledField
import com.zeroindex.intake.ui.components.MacroTiles
import com.zeroindex.intake.ui.components.NutritionFactsLabel
import com.zeroindex.intake.ui.components.SandNote
import com.zeroindex.intake.ui.components.Segmented
import com.zeroindex.intake.ui.components.SectionCard
import com.zeroindex.intake.ui.components.SubHead
import com.zeroindex.intake.ui.images.FoodThumb
import com.zeroindex.intake.ui.theme.CarbAccent
import com.zeroindex.intake.ui.theme.FatAccent
import com.zeroindex.intake.ui.theme.IntakeShape
import com.zeroindex.intake.ui.theme.ProteinAccent
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

private fun sourceLabel(food: FoodEntity) = when {
    food.drinkCategory != null && food.source == FoodSource.DATABASE && food.isVerified && food.createdAt == com.zeroindex.intake.data.db.entity.SEED_TIMESTAMP -> "Built-in drink"
    else -> when (food.source) {
        FoodSource.MANUAL -> "Made by you"
        FoodSource.BARCODE -> "Scanned or edited by you"
        FoodSource.OPEN_FOOD_FACTS -> "Open Food Facts"
        FoodSource.DATABASE -> "USDA database"
        FoodSource.SEARCH -> "USDA search"
        FoodSource.AI_PHOTO -> "Photo estimate"
        FoodSource.RECIPE -> "From a recipe"
    }
}

private fun fmt(value: Double): String {
    val rounded = Math.round(value * 100.0) / 100.0
    return if (rounded == Math.floor(rounded)) rounded.toLong().toString() else rounded.toString()
}

/**
 * Food detail (IA 5c): the product picture, the allergen block, a portion stepper that rescales the macro tiles and the
 * Nutrition Facts label live, and the actions (Log, Swap for another, Contribute to Open Food Facts when eligible). Opened
 * from search, scan, Mine and a drink. Editing the food is behind the menu. Portions follow Settings: g or oz for food
 * ([PortionUnit]), ml or fl oz for a drink; the label's sodium or salt follows the dashboard setting.
 */
@Composable
fun FoodDetailScreen(
    foodId: String,
    slot: String?,
    atMillis: Long,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onLogged: () -> Unit,
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

    var editing by remember(foodId) { mutableStateOf(false) }
    if (editing) {
        FoodEditor(food, state, viewModel, contentPadding, onDone = { editing = false }, onRemoved = onBack, onOpenFoodData = onOpenFoodData, modifier = modifier)
        return
    }

    val isDrink = food.drinkCategory != null
    val portionUnit = state.settings.portionUnit
    val volumeUnit = state.settings.volumeUnit
    // The portion is held in grams (millilitres for a drink, counted as grams); only the display changes.
    var amount by remember(food.id) { mutableStateOf(food.servingSizeGrams.coerceAtLeast(1.0)) }
    var amountText by remember(food.id, portionUnit, volumeUnit) {
        mutableStateOf(if (isDrink) VolumeUnit.MILLILITRES.let { volumeUnit.fromMl(amount).let { v -> if (volumeUnit == VolumeUnit.MILLILITRES) v.roundToInt().toString() else "%.1f".format(java.util.Locale.US, v) } } else portionUnit.number(amount))
    }
    fun setAmount(grams: Double) {
        amount = grams.coerceIn(1.0, 5000.0)
        amountText = if (isDrink) {
            if (volumeUnit == VolumeUnit.MILLILITRES) amount.roundToInt().toString() else "%.1f".format(java.util.Locale.US, volumeUnit.fromMl(amount))
        } else portionUnit.number(amount)
    }
    var mode by remember { mutableStateOf(LabelMode.PER_PORTION) }
    var menu by remember { mutableStateOf(false) }
    var slotChoice by remember(slot) { mutableStateOf(slot?.let { s -> MealSlot.entries.firstOrNull { it.name == s } } ?: TemplateLink.slotFor(java.time.LocalTime.now())) }
    var slotMenu by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf(false) }
    var quantity by remember { mutableStateOf("") }
    var confirmRemove by remember { mutableStateOf(false) }

    val servings = amount / food.servingSizeGrams.coerceAtLeast(1.0)
    val per100 = NutrientsPer100.of(
        food.kcalPer100g, food.proteinPer100g, food.carbsPer100g, food.fatPer100g,
        food.fiberPer100g, food.sugarPer100g, food.sodiumPer100g, food.saturatedFatPer100g,
        Unlisted.decode(food.unlistedNutrients),
    )
    val amountShown = if (isDrink) volumeUnit.format(amount) else portionUnit.format(amount)
    val label = NutritionLabel.build(
        per100 = per100,
        grams = amount,
        mode = mode,
        servingText = if (mode == LabelMode.PER_100) (if (isDrink) "100 ml" else "100 g") else amountShown,
        saltInsteadOfSodium = state.settings.showSodium == SodiumDisplay.SALT,
    )
    val f = amount / 100.0

    Box(modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    top = contentPadding.calculateTopPadding() + 4.dp,
                    bottom = contentPadding.calculateBottomPadding() + 110.dp,
                    start = 16.dp,
                    end = 16.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FoodThumb(food, size = 72.dp)
                Column(Modifier.weight(1f)) {
                    Text(food.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                    Text(
                        listOfNotNull(food.brand, sourceLabel(food), DrinkCategory.fromStored(food.drinkCategory)?.label).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box {
                    IconButton(onClick = { menu = true }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.MoreHoriz, contentDescription = "More: edit, contribute, remove")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Edit") }, onClick = { menu = false; editing = true })
                        if (OffContributionForm.isEligible(food)) {
                            DropdownMenuItem(text = { Text("Contribute to Open Food Facts") }, onClick = { menu = false; confirming = true })
                        }
                        DropdownMenuItem(text = { Text("Remove from my foods") }, onClick = { menu = false; confirmRemove = true })
                    }
                }
            }
            state.message?.let {
                SandNote(it)
                TextButton(onClick = viewModel::dismissMessage) { Text("Dismiss") }
            }

            AllergenBanner(state.scan)

            Surface(color = MaterialTheme.colorScheme.surface, shape = IntakeShape.Card, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (servings >= 0.995 && servings <= 1.005) (if (food.servingLabel.firstOrNull()?.isDigit() == true) food.servingLabel else "1 ${food.servingLabel.ifBlank { "serving" }}") else "${fmt(Math.round(servings * 100.0) / 100.0)} × ${food.servingLabel.ifBlank { "serving" }}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(amountShown, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { setAmount(amount - food.servingSizeGrams.coerceAtLeast(1.0) / 2.0) }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Filled.Remove, contentDescription = "Half a serving less")
                        }
                        IconButton(onClick = { setAmount(amount + food.servingSizeGrams.coerceAtLeast(1.0) / 2.0) }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Filled.Add, contentDescription = "Half a serving more")
                        }
                    }
                    LabeledField(
                        label = "Amount (${if (isDrink) volumeUnit.label else portionUnit.label})",
                        value = amountText,
                        onValueChange = { t ->
                            amountText = t.filter { c -> c.isDigit() || c == '.' }
                            val typed = amountText.toDoubleOrNull()
                            if (typed != null && typed > 0) {
                                amount = (if (isDrink) volumeUnit.toMl(typed) else portionUnit.toGrams(typed)).coerceIn(1.0, 5000.0)
                            }
                        },
                        keyboardType = KeyboardType.Decimal,
                    )
                    if (isDrink) {
                        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            DrinkSizes.defaults(volumeUnit).forEach { s -> ChoiceChip(s.label, kotlin.math.abs(amount - s.ml) < 1.0, { setAmount(s.ml) }) }
                        }
                    }
                    MacroTiles(
                        listOf(
                            Triple("${(food.proteinPer100g * f).roundToInt()} g", "protein", ProteinAccent),
                            Triple("${(food.carbsPer100g * f).roundToInt()} g", "carbs", CarbAccent),
                            Triple("${(food.fatPer100g * f).roundToInt()} g", "fat", FatAccent),
                            Triple("${(food.kcalPer100g * f).roundToInt()}", "kcal", MaterialTheme.colorScheme.onSurface),
                        ),
                    )
                }
            }

            Segmented(LabelMode.entries.map { if (it == LabelMode.PER_100) (if (isDrink) "Per 100 ml" else it.label) else it.label }, LabelMode.entries.indexOf(mode), { mode = LabelMode.entries[it] })
            NutritionFactsLabel(label)

            val ingredients = FoodIngredients.text(food.allergenTags)
            if (ingredients.isNotBlank()) {
                SectionCard("Ingredients") {
                    Text(ingredients, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "The allergen check reads this text each time you log the food. A model can raise a warning here, never clear one.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
            if (OffContributionForm.isEligible(food)) {
                food.offContributedAt?.let {
                    Text("Sent to Open Food Facts on ${DateFormat.getDateInstance().format(Date(it))}.", style = MaterialTheme.typography.bodySmall)
                }
                if (!state.contributeEnabled || !state.hasCredentials) {
                    OutlinedButton(onClick = onOpenFoodData, shape = IntakeShape.Button, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                        Text("Set up contributing to Open Food Facts")
                    }
                }
            }
        }

        // The sticky bottom bar: the meal it goes to, then Log, then Swap for another.
        Surface(
            color = MaterialTheme.colorScheme.background,
            tonalElevation = 3.dp,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
        ) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = contentPadding.calculateBottomPadding() + 10.dp)) {
                if (!isDrink) {
                    Box {
                        TextButton(onClick = { slotMenu = true }) { Text("To: ${slotLabel(slotChoice)}  ▾") }
                        DropdownMenu(expanded = slotMenu, onDismissRequest = { slotMenu = false }) {
                            MealSlot.entries.forEach { s -> DropdownMenuItem(text = { Text(slotLabel(s)) }, onClick = { slotChoice = s; slotMenu = false }) }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val risky = state.scan.hasAnything
                    OutlinedButton(
                        onClick = onBack,
                        shape = IntakeShape.Button,
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) { Text("Swap for another") }
                    if (risky) {
                        OutlinedButton(
                            onClick = { viewModel.log(food, amount, slotChoice.name, atMillis, confirmed = false, onLogged = onLogged) },
                            shape = IntakeShape.Button,
                            modifier = Modifier.weight(1f).height(52.dp),
                        ) { Text(if (isDrink) "Log anyway…" else "Log anyway…") }
                    } else {
                        Button(
                            onClick = { viewModel.log(food, amount, slotChoice.name, atMillis, confirmed = false, onLogged = onLogged) },
                            shape = IntakeShape.Button,
                            modifier = Modifier.weight(1f).height(52.dp),
                        ) { Text("Log") }
                    }
                }
            }
        }
    }

    if (state.needsConfirm) {
        AllergenWarningSheet(
            foodName = food.name,
            scan = state.scan,
            ingredientText = food.allergenTags.takeIf { it.isNotBlank() },
            foodDetail = "$amountShown · ${(food.kcalPer100g * f).roundToInt()} kcal",
            onDismiss = viewModel::dismissConfirm,
            onLogAnyway = { viewModel.log(food, amount, slotChoice.name, atMillis, confirmed = true, onLogged = onLogged) },
        )
    }

    if (confirming) {
        val preview = viewModel.preview(food, quantity)
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("Send to Open Food Facts?") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (!state.contributeEnabled || !state.hasCredentials) {
                        Text("Contributing is not set up yet. Switch it on and add your account in Settings > Food database first.", style = MaterialTheme.typography.bodySmall)
                    }
                    Text("This is exactly what will be sent:", style = MaterialTheme.typography.bodySmall)
                    preview.previewLines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    Text("Along with your Open Food Facts username and password, over HTTPS, in the request body.", style = MaterialTheme.typography.bodySmall)
                    LabeledField(label = "Package quantity (optional)", value = quantity, onValueChange = { quantity = it }, placeholder = "For example 250 g")
                    Text("Your contribution is public under the ODbL licence. The nutrition values are sent as numbers; a photo of the label is a separate, optional step and a generated image is never uploaded.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Button(
                    enabled = state.contributeEnabled && state.hasCredentials && !state.sending,
                    onClick = { confirming = false; viewModel.contribute(food, quantity) },
                ) { Text("Send") }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text("Cancel") } },
        )
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove ${food.name}?") },
            text = { Text("Past log entries keep their numbers. You can add the food again any time.") },
            confirmButton = { Button(onClick = { confirmRemove = false; viewModel.remove(food, onBack) }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancel") } },
        )
    }
}

private fun slotLabel(slot: MealSlot): String = when (slot) {
    MealSlot.BREAKFAST -> "Breakfast"
    MealSlot.LUNCH -> "Lunch"
    MealSlot.DINNER -> "Dinner"
    MealSlot.SNACK -> "Snacks"
}

/** The old detail screen, now behind "Edit": every value per 100 g, the serving only sets the default portion. */
@Composable
private fun FoodEditor(
    food: FoodEntity,
    state: FoodDetailUiState,
    viewModel: FoodDetailViewModel,
    contentPadding: PaddingValues,
    onDone: () -> Unit,
    onRemoved: () -> Unit,
    onOpenFoodData: () -> Unit,
    modifier: Modifier,
) {
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

    val barcodeText = Barcodes.digitsOnly(barcode)
    val normalizedBarcode = if (barcodeText.isBlank()) "" else Barcodes.normalize(barcodeText)
    val barcodeBad = barcodeText.isNotBlank() && normalizedBarcode == null
    val grams = servingGrams.toDoubleOrNull()
    val canSave = name.isNotBlank() && !barcodeBad && grams != null && grams > 0 && kcal.toDoubleOrNull() != null
    fun doubleOf(text: String) = text.toDoubleOrNull() ?: 0.0
    val oldUnlisted = Unlisted.decode(food.unlistedNutrients)
    // A nutrient that was not listed stays so until a value is typed in.
    val newUnlisted = buildSet {
        if (NutrientKey.FIBER in oldUnlisted && doubleOf(fiber) == 0.0) add(NutrientKey.FIBER)
        if (NutrientKey.SUGAR in oldUnlisted && doubleOf(sugar) == 0.0) add(NutrientKey.SUGAR)
        if (NutrientKey.SODIUM in oldUnlisted && doubleOf(sodium) == 0.0) add(NutrientKey.SODIUM)
        if (NutrientKey.SATURATED_FAT in oldUnlisted && doubleOf(satFat) == 0.0) add(NutrientKey.SATURATED_FAT)
    }
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
            .padding(top = contentPadding.calculateTopPadding() + 12.dp, bottom = contentPadding.calculateBottomPadding() + 32.dp, start = 16.dp, end = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SubHead("Edit ${food.name}", sourceLabel(food))
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
        SectionCard(if (food.drinkCategory != null) "Nutrition per 100 ml" else "Nutrition per 100 g") {
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
            if (oldUnlisted.isNotEmpty()) {
                Text(
                    "Left at 0 means \"not listed\" for ${oldUnlisted.joinToString { it.label }}. Type a value to list it.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
                    Unlisted.encode(newUnlisted),
                )
            },
            enabled = canSave && dirty,
            shape = IntakeShape.Button,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("Save changes") }
        OutlinedButton(onClick = onDone, shape = IntakeShape.Button, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("Back to the food") }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        if (OffContributionForm.isEligible(food) && (!state.contributeEnabled || !state.hasCredentials)) {
            OutlinedButton(onClick = onOpenFoodData, shape = IntakeShape.Button, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Food database settings") }
        }
        Spacer(Modifier.height(4.dp))
    }
}
