package com.zeroindex.intake.ui.screens.fooddata

import com.zeroindex.intake.domain.units.KcalFormat
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
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
import com.zeroindex.intake.domain.food.ServingText
import com.zeroindex.intake.domain.model.FoodSource
import com.zeroindex.intake.domain.model.MealSlot
import com.zeroindex.intake.domain.nfc.TemplateLink
import com.zeroindex.intake.domain.nutrition.LabelFormat
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
import com.zeroindex.intake.ui.components.NutritionFactsLabel
import com.zeroindex.intake.ui.components.SandNote
import com.zeroindex.intake.ui.components.Segmented
import com.zeroindex.intake.ui.components.SectionCard
import com.zeroindex.intake.ui.components.SubHead
import com.zeroindex.intake.ui.images.FoodThumb
import com.zeroindex.intake.ui.components.ChipTone
import com.zeroindex.intake.ui.components.Eyebrow
import com.zeroindex.intake.ui.components.DangerButton
import com.zeroindex.intake.ui.components.GhostButton
import com.zeroindex.intake.ui.components.ChoiceChip
import com.zeroindex.intake.ui.components.IconCircleButton
import com.zeroindex.intake.ui.components.IconCircleStyle
import com.zeroindex.intake.ui.components.IntakeCard
import com.zeroindex.intake.ui.components.IntakeChip
import com.zeroindex.intake.ui.components.LineButton
import com.zeroindex.intake.ui.components.ListCard
import com.zeroindex.intake.ui.components.ListDivider
import com.zeroindex.intake.ui.theme.IconSize
import com.zeroindex.intake.ui.theme.IntakeIcons
import com.zeroindex.intake.ui.theme.IntakeShape
import com.zeroindex.intake.ui.theme.IntakeSpacing
import com.zeroindex.intake.ui.theme.NumberSmall
import com.zeroindex.intake.ui.theme.intake
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

    if (food == null && !state.loaded) {
        // Still reading: show nothing rather than claim the food is missing.
        Box(modifier.padding(contentPadding).fillMaxSize())
        return
    }
    if (food == null) {
        Column(modifier.padding(contentPadding).padding(IntakeSpacing.ScreenGutter)) {
            Text("This food is not on this phone.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LineButton(onClick = onBack) { Text("Back") }
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
    var slotChoice by remember(slot) { mutableStateOf(slot?.let { s -> MealSlot.entries.firstOrNull { it.name == s } } ?: if (isDrink) MealSlot.DRINKS else TemplateLink.slotFor(java.time.LocalTime.now())) }
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
                    start = IntakeSpacing.ScreenGutter,
                    end = IntakeSpacing.ScreenGutter,
                ),
            verticalArrangement = Arrangement.spacedBy(IntakeSpacing.StackGap),
        ) {
            // The picture (when the food has one) sits on a `soft` block with the menu at the corner.
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) {
                    if (food.imageUrl != null) FoodThumb(food, size = 72.dp)
                }
                Box {
                    IconCircleButton(IntakeIcons.More, "More: edit, contribute, remove", { menu = true })
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
                GhostButton(onClick = viewModel::dismissMessage) { Text("Dismiss") }
            }

            IntakeCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IntakeChip(sourceLabel(food))
                        DrinkCategory.fromStored(food.drinkCategory)?.label?.let { IntakeChip(it) }
                    }
                    Text(food.name, style = MaterialTheme.typography.headlineMedium)
                    food.brand?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    // The kcal figure is the exact one an entry logs (the same as the list row), not the printed label's rounding (QA Q5a-22).
                    fun grams(v: Double) = "${LabelFormat.grams(v)} g"
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        // "logged" says this is the exact figure an entry takes; the printed label below rounds it (Q5b-13).
                        MacroNumber(KcalFormat.format(food.kcalPer100g * f), "kcal logged", MaterialTheme.colorScheme.onSurface)
                        MacroNumber(grams(food.proteinPer100g * f), "protein", MaterialTheme.intake.protein)
                        MacroNumber(grams(food.carbsPer100g * f), "carbs", MaterialTheme.intake.carb)
                        MacroNumber(grams(food.fatPer100g * f), "fat", MaterialTheme.intake.fat)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (isDrink) {
                                    if (servings >= 0.995 && servings <= 1.005) (if (food.servingLabel.firstOrNull()?.isDigit() == true) food.servingLabel else "1 ${food.servingLabel.ifBlank { "serving" }}") else "${fmt(Math.round(servings * 100.0) / 100.0)} × ${food.servingLabel.ifBlank { "serving" }}"
                                } else {
                                    // The servings in words; the grams sit under them.
                                    ServingText.of(food.servingLabel, food.servingSizeGrams, servings).count
                                },
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(amountShown, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        // `.stepper`: a soft pill holding two round buttons.
                        Surface(color = MaterialTheme.intake.soft, shape = IntakeShape.Pill) {
                            Row(Modifier.padding(3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                IconCircleButton(IntakeIcons.Minus, "Half a serving less", { setAmount(amount - food.servingSizeGrams.coerceAtLeast(1.0) / 2.0) })
                                IconCircleButton(IntakeIcons.Plus, "Half a serving more", { setAmount(amount + food.servingSizeGrams.coerceAtLeast(1.0) / 2.0) })
                            }
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
                }
            }

            AllergenBanner(state.scan)

            IntakeCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Segmented(LabelMode.entries.map { if (it == LabelMode.PER_100) (if (isDrink) "Per 100 ml" else it.label) else it.label }, LabelMode.entries.indexOf(mode), { mode = LabelMode.entries[it] })
                    NutritionFactsLabel(label)
                    Text(
                        "The label rounds the way a printed Nutrition Facts panel does: calories to the nearest 5 (10 above 50), grams to the nearest half under 5.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

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
                    Text("Sent to Open Food Facts on ${DateFormat.getDateInstance().format(Date(it))}.", style = MaterialTheme.typography.bodyMedium)
                }
                if (!state.contributeEnabled || !state.hasCredentials) {
                    LineButton(onClick = onOpenFoodData, modifier = Modifier.fillMaxWidth()) {
                        Text("Set up contributing to Open Food Facts")
                    }
                }
            }
        }

        // The sticky bottom bar: the meal it goes to, then Log, then Swap for another.
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
        ) {
            Column(Modifier.padding(start = IntakeSpacing.ScreenGutter, end = IntakeSpacing.ScreenGutter, top = 8.dp, bottom = contentPadding.calculateBottomPadding() + 10.dp)) {
                if (!isDrink) {
                    Box {
                        GhostButton(onClick = { slotMenu = true }) {
                            Text("To: ${slotLabel(slotChoice)}")
                            Icon(IntakeIcons.ChevronDown, contentDescription = null, modifier = Modifier.padding(start = 6.dp).size(IconSize.Small))
                        }
                        DropdownMenu(expanded = slotMenu, onDismissRequest = { slotMenu = false }) {
                            (if (isDrink) MealSlot.entries else MealSlot.MEALS).forEach { s -> DropdownMenuItem(text = { Text(slotLabel(s)) }, onClick = { slotChoice = s; slotMenu = false }) }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val risky = state.scan.hasAnything
                    LineButton(
                        onClick = onBack,
                        modifier = Modifier.weight(1f),
                    ) { Text("Swap for another") }
                    if (risky) {
                        // Logging a flagged food stays the quiet, danger-soft path behind the warning sheet.
                        DangerButton(
                            onClick = { viewModel.log(food, amount, slotChoice.name, atMillis, confirmed = false, onLogged = onLogged) },
                            modifier = Modifier.weight(1f),
                        ) { Text(if (isDrink) "Log anyway…" else "Log anyway…") }
                    } else {
                        Button(
                            onClick = { viewModel.log(food, amount, slotChoice.name, atMillis, confirmed = false, onLogged = onLogged) },
                            shape = IntakeShape.Button,
                            modifier = Modifier.weight(1f).heightIn(min = IntakeSpacing.TouchTarget),
                        ) { Text("Log") }
                    }
                }
            }
        }
    }

    if (state.needsConfirm) {
        AllergenWarningSheet(
            onFindSwap = com.zeroindex.intake.ui.allergen.rememberFindSwap(),
            foodName = food.name,
            scan = state.scan,
            ingredientText = food.allergenTags.takeIf { it.isNotBlank() },
            foodDetail = "$amountShown · ${KcalFormat.format(food.kcalPer100g * f)} kcal",
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
    MealSlot.DRINKS -> "Drinks"
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
            .padding(top = contentPadding.calculateTopPadding() + 12.dp, bottom = contentPadding.calculateBottomPadding() + 32.dp, start = IntakeSpacing.ScreenGutter, end = IntakeSpacing.ScreenGutter),
        verticalArrangement = Arrangement.spacedBy(IntakeSpacing.StackGap),
    ) {
        SubHead("Edit ${food.name}", sourceLabel(food))
        state.message?.let {
            SandNote(it)
            GhostButton(onClick = viewModel::dismissMessage) { Text("Dismiss") }
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
                num("Fiber g", fiber, { fiber = it }, Modifier.weight(1f))
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
            modifier = Modifier.fillMaxWidth().heightIn(min = IntakeSpacing.TouchTarget),
        ) { Text("Save changes") }
        LineButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Back to the food") }
        ListDivider()
        if (OffContributionForm.isEligible(food) && (!state.contributeEnabled || !state.hasCredentials)) {
            LineButton(onClick = onOpenFoodData, modifier = Modifier.fillMaxWidth()) { Text("Food database settings") }
        }
        Spacer(Modifier.height(4.dp))
    }
}

/** One of the four numbers under the title: a coloured figure over a muted label (the mockup draws them bare, no tile). */
@Composable
private fun MacroNumber(value: String, label: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = NumberSmall.copy(color = color), maxLines = 1)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}
