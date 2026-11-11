@file:OptIn(ExperimentalMaterial3Api::class)

package com.zeroindex.intake.ui.addfood

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zeroindex.intake.data.db.entity.FoodEntity
import com.zeroindex.intake.data.foodref.RefFood
import com.zeroindex.intake.domain.model.MealSlot
import com.zeroindex.intake.domain.nutrition.Macros
import com.zeroindex.intake.domain.units.PortionUnit
import com.zeroindex.intake.ui.allergen.AllergenChip
import com.zeroindex.intake.ui.allergen.AllergenWarningSheet
import com.zeroindex.intake.ui.allergen.CheckLabelChip
import com.zeroindex.intake.ui.allergen.ProvideCheckLabelTerms
import com.zeroindex.intake.ui.allergen.checkLabelReasonFor
import com.zeroindex.intake.ui.barcode.BarcodeScannerView
import com.zeroindex.intake.ui.components.ActionTile
import com.zeroindex.intake.ui.components.ChoiceChip
import com.zeroindex.intake.ui.components.EmptyState
import com.zeroindex.intake.ui.components.Eyebrow
import com.zeroindex.intake.ui.images.FoodThumb
import com.zeroindex.intake.ui.images.ThumbTile
import com.zeroindex.intake.ui.motion.SkeletonList
import com.zeroindex.intake.ui.photo.PhotoEstimateFlow
import com.zeroindex.intake.ui.theme.IntakeShape
import kotlin.math.roundToInt

/** "Breakfast", "Lunch", "Dinner", "Snacks": the slot chip's words. */
internal fun slotWord(slot: MealSlot): String = when (slot) {
    MealSlot.BREAKFAST -> "Breakfast"
    MealSlot.LUNCH -> "Lunch"
    MealSlot.DINNER -> "Dinner"
    MealSlot.SNACK -> "Snacks"
}

/**
 * Log food, a full-screen task (IA 5b, Option A): three equal modes, Search, Scan and Photo, plus Mine (your foods,
 * favourites, a food made by hand) and Quick add (calories and macros typed in). Tapping a result opens Food detail with
 * the meal and time it was opened for; the "+" on a row logs that food's own serving at once. The allergen check runs
 * before anything is saved, on every path.
 *
 * It is registered once at the root of the nav graph, so Today and Nutrition open the same screen.
 */
@Composable
fun LogFoodScreen(
    slot: MealSlot,
    loggedAtMillis: Long,
    initialTab: AddFoodTab,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    /** Opens Food detail for the id, for the meal chosen here. */
    onOpenFood: (foodId: String, slot: MealSlot) -> Unit,
    /** Opens Drinks, with the words typed here carried into its search (blank for none). */
    onOpenDrinks: (query: String) -> Unit,
    onOpenAiSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AddFoodViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var mealSlot by rememberSaveable { mutableStateOf(slot) }
    var slotMenu by remember { mutableStateOf(false) }

    LaunchedEffect(initialTab) { viewModel.selectTab(initialTab) }

    // Adopts a USDA or Open Food Facts result into the person's foods, then opens its detail.
    val openEntity: (FoodEntity) -> Unit = { food -> onOpenFood(food.id, mealSlot) }

    ProvideCheckLabelTerms(state.checkLabelTerms) {
        Column(
            modifier
                .fillMaxSize()
                .padding(top = contentPadding.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding()),
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionTile("Search", Icons.Filled.Search, { viewModel.selectTab(AddFoodTab.SEARCH) }, Modifier.weight(1f), selected = state.tab == AddFoodTab.SEARCH)
                ActionTile("Scan", Icons.Filled.QrCodeScanner, { viewModel.selectTab(AddFoodTab.BARCODE) }, Modifier.weight(1f), selected = state.tab == AddFoodTab.BARCODE)
                ActionTile("Photo", Icons.Filled.CameraAlt, { viewModel.selectTab(AddFoodTab.PHOTO) }, Modifier.weight(1f), selected = state.tab == AddFoodTab.PHOTO)
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    ChoiceChip("${slotWord(mealSlot)}  ▾", false, { slotMenu = true })
                    DropdownMenu(expanded = slotMenu, onDismissRequest = { slotMenu = false }) {
                        MealSlot.entries.forEach { s ->
                            DropdownMenuItem(text = { Text(slotWord(s)) }, onClick = { mealSlot = s; slotMenu = false })
                        }
                    }
                }
                ChoiceChip("Mine", state.tab == AddFoodTab.MY_FOODS, { viewModel.selectTab(AddFoodTab.MY_FOODS) })
                ChoiceChip("Quick add", state.tab == AddFoodTab.QUICK_ADD, { viewModel.selectTab(AddFoodTab.QUICK_ADD) })
            }
            HorizontalDivider(Modifier.padding(top = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (state.tab) {
                    AddFoodTab.SEARCH -> SearchTab(
                        state = state,
                        onQueryChange = viewModel::setQuery,
                        onSearchRemote = viewModel::searchRemote,
                        onOpen = openEntity,
                        onQuickAdd = { food -> viewModel.quickAddDefault(food, mealSlot, loggedAtMillis) },
                        onChooseRef = { ref -> viewModel.chooseRef(ref) { food, _ -> openEntity(food) } },
                        onChooseOff = { food -> viewModel.chooseOff(food, openEntity) },
                        onOpenDrinks = onOpenDrinks,
                        onCreate = { name, brand, grams, label, k, p, c, f ->
                            viewModel.createFood(name, brand, grams, label, k, p, c, f, onCreated = openEntity)
                        },
                    )

                    AddFoodTab.BARCODE -> BarcodeTab(
                        state = state,
                        onBarcode = viewModel::onBarcodeScanned,
                        onRescan = viewModel::rescanBarcode,
                        onRetry = viewModel::retryBarcode,
                        onPick = openEntity,
                        onExit = { viewModel.selectTab(AddFoodTab.SEARCH) },
                        onCreateForBarcode = { barcode, name, brand, grams, label, k, p, c, f ->
                            viewModel.createFood(name, brand, grams, label, k, p, c, f, barcode = barcode, onCreated = openEntity)
                        },
                    )

                    AddFoodTab.PHOTO -> PhotoEstimateFlow(
                        slot = mealSlot,
                        slotLabel = slotWord(mealSlot),
                        loggedAtMillis = loggedAtMillis,
                        onOpenAiSettings = onOpenAiSettings,
                        onManualEntry = { viewModel.selectTab(AddFoodTab.QUICK_ADD) },
                        onDone = onBack,
                        // A label beats a guess: search for the product the photo looks like.
                        onFindProduct = { name ->
                            viewModel.setQuery(name)
                            viewModel.selectTab(AddFoodTab.SEARCH)
                        },
                    )

                    AddFoodTab.QUICK_ADD -> QuickAddTab(
                        onQuickAdd = { name, kcal, protein, carbs, fat ->
                            viewModel.requestQuickAdd(
                                name = name,
                                macros = Macros(kcal = kcal, protein = protein, carbs = carbs, fat = fat),
                                slot = mealSlot,
                                at = loggedAtMillis,
                                onLogged = onBack,
                            )
                        },
                    )

                    AddFoodTab.MY_FOODS -> MyFoodsTab(
                        state = state,
                        onOpen = openEntity,
                        onQuickAdd = { food -> viewModel.quickAddDefault(food, mealSlot, loggedAtMillis) },
                        onToggleFavorite = viewModel::toggleFavorite,
                        onCreate = { name, brand, grams, label, k, p, c, f ->
                            viewModel.createFood(name, brand, grams, label, k, p, c, f, onCreated = openEntity)
                        },
                    )
                }
            }
            Text(
                "Allergen check runs before saving.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
    }

    // SPEC §7.2: nothing is written until this is answered.
    state.pendingLog?.let { pending ->
        AllergenWarningSheet(
            foodName = pending.name,
            scan = pending.scan,
            ingredientText = pending.food?.allergenTags?.takeIf { it.isNotBlank() },
            foodDetail = pending.grams?.let { grams ->
                val food = pending.food
                if (food != null) {
                    "${state.portionUnit.format(grams)} · " +
                        "${(food.kcalPer100g * grams / 100.0).roundToInt()} kcal · " +
                        "${(food.proteinPer100g * grams / 100.0).roundToInt()} g protein"
                } else {
                    null
                }
            },
            onDismiss = viewModel::dismissPendingLog,
            onLogAnyway = { viewModel.confirmPendingLog(mealSlot, loggedAtMillis) { if (pending.food == null) onBack() } },
        )
    }
}

/**
 * The allergen chip of one result row, from any source. A clear result on a packaged product that lists no
 * ingredients and no allergens is not "No allergens": the chip says the list is missing (docs/ALLERGENS.md).
 */
@Composable
internal fun RowAllergenChip(allergen: RowAllergen) {
    if (allergen.nameOnly) {
        AllergenChip(com.zeroindex.intake.domain.swap.SwapAllergen.CHECK_LABEL, label = "No ingredient list: check the label")
    } else {
        AllergenChip(allergen.verdict, allergen.detail)
    }
}

/**
 * Result rows. The tile, the name over its portion, the energy, the allergen chip (when the person avoids anything) and
 * a "+" that logs one serving. Tapping the row opens Food detail.
 */
@Composable
private fun FoodRow(
    food: FoodEntity,
    isSafe: Boolean,
    allergen: RowAllergen?,
    onOpen: (FoodEntity) -> Unit,
    onQuickAdd: ((FoodEntity) -> Unit)?,
    onToggleFavorite: ((FoodEntity) -> Unit)? = null,
) {
    val perServing = food.servingSizeGrams / 100.0
    val isDrink = food.drinkCategory != null
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 65.dp)
                .clickable(onClickLabel = "Open ${food.name}") { onOpen(food) }
                .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FoodThumb(food)
            Spacer(Modifier.size(11.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = food.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (isSafe) {
                        Spacer(Modifier.size(5.dp))
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Logged before with no allergen flag",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
                Text(
                    text = buildString {
                        food.brand?.let { append("$it · ") }
                        append(food.servingLabel)
                        append(" · ${(food.kcalPer100g * perServing).roundToInt()} kcal")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                if (allergen != null) {
                    Spacer(Modifier.size(4.dp))
                    RowAllergenChip(allergen)
                } else {
                    // Soft tier: shown beside the safe check too (docs/ALLERGENS.md).
                    checkLabelReasonFor(food.name)?.let { reason ->
                        Spacer(Modifier.size(4.dp))
                        CheckLabelChip(reason)
                    }
                }
            }
            if (onToggleFavorite != null) {
                IconButton(onClick = { onToggleFavorite(food) }, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = if (food.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = if (food.isFavorite) "Remove ${food.name} from favourites" else "Add ${food.name} to favourites",
                        modifier = Modifier.size(19.dp),
                    )
                }
            }
            if (onQuickAdd != null) {
                IconButton(onClick = { onQuickAdd(food) }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.Add, contentDescription = if (isDrink) "Add one ${food.servingLabel} of ${food.name}" else "Log one serving of ${food.name}")
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun SearchTab(
    state: AddFoodUiState,
    onQueryChange: (String) -> Unit,
    onSearchRemote: () -> Unit,
    onOpen: (FoodEntity) -> Unit,
    onQuickAdd: (FoodEntity) -> Unit,
    onChooseRef: (RefFood) -> Unit,
    onChooseOff: (FoodEntity) -> Unit,
    onOpenDrinks: (String) -> Unit,
    onCreate: (String, String?, Double, String, Double, Double, Double, Double) -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    if (creating) {
        CreateFoodForm(
            heading = "Create a food",
            note = "Enter the numbers for one serving as they appear on the label. They are " +
                "stored per 100 g so any portion works later.",
            initialName = state.query.trim(),
            portionUnit = state.portionUnit,
            onCancel = { creating = false },
            onCreate = { name, brand, grams, label, k, p, c, f ->
                onCreate(name, brand, grams, label, k, p, c, f)
                creating = false
            },
        )
        return
    }

    Column(Modifier.fillMaxSize()) {
        TextField(
            value = state.query,
            onValueChange = onQueryChange,
            placeholder = { Text("Search foods", style = MaterialTheme.typography.bodyMedium) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(19.dp)) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Filled.Close, contentDescription = "Clear the search") }
                }
            },
            singleLine = true,
            shape = IntakeShape.Field,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        )

        if (state.query.isBlank()) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (state.recents.isEmpty()) {
                    item(key = "no-recents") {
                        Column(Modifier.padding(horizontal = 16.dp)) {
                            EmptyState(
                                title = "Nothing logged yet",
                                body = "Search your foods and the offline USDA database. Both work with no internet and no key. Foods you log often land here.",
                            )
                        }
                    }
                } else {
                    item(key = "h-recent") { SourceHeader("Recent") }
                    items(items = state.recents, key = { "recent-${it.id}" }) { food ->
                        FoodRow(food, food.id in state.safeFoodIds, state.rowAllergens[food.id], onOpen, onQuickAdd)
                    }
                }
                item(key = "drinks-row") { DrinksRow("Categories, a drinks search and size buttons") { onOpenDrinks("") } }
            }
            return@Column
        }

        val offLoading = state.offState == OffUiState.Loading
        val offResults = (state.offState as? OffUiState.Results)?.foods.orEmpty()
        val usdaApiResults = (state.searchState as? SearchState.Remote)?.results.orEmpty()
        val nothingYet = state.localResults.isEmpty() && state.refResults.isEmpty() &&
            offResults.isEmpty() && usdaApiResults.isEmpty()

        // Results arrive above rows that are already on screen (the phone's own foods and the offline database come in
        // over the Open Food Facts section), and a lazy list stays on the row it was showing. Without this the best
        // matches end up off the top of the screen.
        val resultsState = androidx.compose.foundation.lazy.rememberLazyListState()
        androidx.compose.runtime.LaunchedEffect(state.query, state.localResults.size, state.refResults.size) {
            resultsState.scrollToItem(0)
        }
        // Sources in order: this phone, the offline USDA database, Open Food Facts, the USDA API.
        LazyColumn(state = resultsState, modifier = Modifier.fillMaxSize()) {
            if (state.localResults.isNotEmpty()) {
                item(key = "h-local") { SourceHeader("My foods and recents") }
                items(items = state.localResults, key = { "l-${it.id}" }) { food ->
                    FoodRow(food, food.id in state.safeFoodIds, state.rowAllergens[food.id], onOpen, onQuickAdd)
                }
            }

            if (state.refResults.isNotEmpty()) {
                item(key = "h-ref") { SourceHeader("Food database (USDA, works offline)") }
                items(items = state.refResults, key = { "r-${it.id}" }) { ref ->
                    RefFoodRow(ref, state.refAllergens[ref.id], state.portionUnit, onChooseRef)
                }
            }

            when (state.offState) {
                OffUiState.Hidden -> Unit
                OffUiState.Loading -> item(key = "off-loading") {
                    Column {
                        SourceHeader("Packaged foods (Open Food Facts)")
                        Text(
                            "Searching Open Food Facts...",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                        SkeletonList(modifier = Modifier.padding(horizontal = 16.dp), rows = 3, rowHeight = 52.dp, label = "Searching Open Food Facts")
                    }
                }
                is OffUiState.Results -> if (offResults.isNotEmpty()) {
                    item(key = "h-off") { SourceHeader("Packaged foods (Open Food Facts)") }
                    items(items = offResults, key = { "o-${it.id}" }) { food ->
                        FoodRow(food, false, state.rowAllergens[food.id], onChooseOff, null)
                    }
                }
                OffUiState.Failed -> item(key = "off-failed") {
                    Column {
                        SourceHeader("Packaged foods (Open Food Facts)")
                        DegradedMessage("Open Food Facts could not be reached. The sources above still work.")
                    }
                }
            }

            // The USDA API is an extra for people who have set a key; it is never required.
            if (state.hasUsdaKey) {
                item(key = "usda-api") {
                    when (val search = state.searchState) {
                        SearchState.Idle -> OutlinedButton(
                            onClick = onSearchRemote,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(52.dp),
                        ) { Text("Also search USDA online (your key)") }
                        SearchState.Loading -> Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text("Searching USDA...", style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.size(8.dp))
                            SkeletonList(rows = 3, rowHeight = 52.dp, label = "Searching USDA")
                        }
                        is SearchState.Remote -> Unit
                        SearchState.NeedsKey -> Unit
                        SearchState.NoResults -> DegradedMessage("USDA online had nothing for that.")
                        is SearchState.Failed -> DegradedMessage(search.message)
                    }
                }
                if (usdaApiResults.isNotEmpty()) {
                    item(key = "h-usda") { SourceHeader("USDA online (your key), saved to this phone") }
                    items(items = usdaApiResults, key = { "u-${it.id}" }) { food ->
                        FoodRow(food, false, state.rowAllergens[food.id], onOpen, onQuickAdd)
                    }
                }
            }

            val typed = state.query.trim()
            if (nothingYet && !offLoading && state.queryMatchesDrinks) {
                // The words only find drinks. Foods and drinks are separate searches, so say where they went.
                item(key = "drinks-only") {
                    Column(Modifier.padding(16.dp)) {
                        Text("Drinks are logged from Drinks", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "\"$typed\" matches drinks, not foods. Drinks count toward water as well as calories, so they have their own page.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        Button(onClick = { onOpenDrinks(typed) }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(52.dp)) { Text("Search Drinks for \"$typed\"") }
                        OutlinedButton(onClick = { creating = true }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(48.dp)) { Text("It is a food: create \"$typed\"") }
                    }
                }
            } else if (nothingYet && !offLoading) {
                item(key = "create") {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Nothing matches \"$typed\".",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(onClick = { creating = true }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(52.dp)) { Text("Create a food") }
                    }
                }
                item(key = "drinks-row") { DrinksRow("Looking for a drink? Search Drinks for \"$typed\"") { onOpenDrinks(typed) } }
            } else if (!nothingYet) {
                item(key = "create-small") {
                    OutlinedButton(
                        onClick = { creating = true },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(48.dp),
                    ) { Text("Not here? Create \"$typed\"") }
                }
                // Drinks are never listed here, so the way across stays at the foot of every result list.
                item(key = "drinks-row") { DrinksRow("Looking for a drink? Search Drinks for \"$typed\"") { onOpenDrinks(typed) } }
            }
        }
    }
}

/** "Log a drink", at the foot of Search (the frame's last row): the way across to the Drinks page and its search. */
@Composable
private fun DrinksRow(note: String, onOpenDrinks: () -> Unit) {
    Surface(
        onClick = onOpenDrinks,
        color = MaterialTheme.colorScheme.surface,
        shape = IntakeShape.Card,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().padding(16.dp),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = IntakeShape.Tile, modifier = Modifier.size(46.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.WaterDrop, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer) }
            }
            Column(Modifier.weight(1f)) {
                Text("Log a drink", style = MaterialTheme.typography.titleMedium)
                Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Mine: favourites, the foods entered by hand, and "Add a food by hand". */
@Composable
private fun MyFoodsTab(
    state: AddFoodUiState,
    onOpen: (FoodEntity) -> Unit,
    onQuickAdd: (FoodEntity) -> Unit,
    onToggleFavorite: (FoodEntity) -> Unit,
    onCreate: (String, String?, Double, String, Double, Double, Double, Double) -> Unit,
) {
    var creating by remember { mutableStateOf(false) }

    if (creating) {
        CreateFoodForm(
            heading = "New food",
            note = "Enter the numbers for one serving as they appear on the label. They are " +
                "stored per 100 g so any portion works later.",
            portionUnit = state.portionUnit,
            onCancel = { creating = false },
            onCreate = { name, brand, grams, label, k, p, c, f ->
                onCreate(name, brand, grams, label, k, p, c, f)
                creating = false
            },
        )
        return
    }

    var filter by rememberSaveable { mutableStateOf("") }
    val needle = filter.trim().lowercase()
    fun matches(f: FoodEntity) = needle.isEmpty() || (f.name + " " + f.brand.orEmpty()).lowercase().contains(needle)
    val favorites = state.favorites.filter(::matches)
    val mine = state.myFoods.filter { m -> state.favorites.none { it.id == m.id } && matches(m) }
    LazyColumn(Modifier.fillMaxSize()) {
        item(key = "add") {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = filter,
                    onValueChange = { filter = it },
                    label = { Text("Filter your foods") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(onClick = { creating = true }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Add a food by hand") }
            }
        }
        if (favorites.isEmpty() && mine.isEmpty()) {
            item(key = "empty") {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    EmptyState(
                        title = if (needle.isEmpty()) "Nothing here yet" else "No match",
                        body = if (needle.isEmpty()) {
                            "Every food you search for, scan or create is kept here, and the ones you star. Enter your regular 40 or so once and the food database stops being the problem."
                        } else {
                            "None of your foods match \"$filter\"."
                        },
                    )
                }
            }
        }
        if (favorites.isNotEmpty()) {
            item(key = "fav-header") { SourceHeader("Favourites") }
            items(items = favorites, key = { "fav-${it.id}" }) { food ->
                FoodRow(food, food.id in state.safeFoodIds, state.rowAllergens[food.id], onOpen, onQuickAdd, onToggleFavorite)
            }
        }
        if (mine.isNotEmpty()) {
            item(key = "mine-header") { SourceHeader("Saved on this phone") }
            items(items = mine, key = { "mine-${it.id}" }) { food ->
                FoodRow(food, food.id in state.safeFoodIds, state.rowAllergens[food.id], onOpen, onQuickAdd, onToggleFavorite)
            }
        }
    }
}

@Composable
private fun SourceHeader(text: String) {
    Eyebrow(text = text, modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 4.dp))
}

/** A bundled USDA food: name, category, and the numbers for its first listed portion. */
@Composable
private fun RefFoodRow(ref: RefFood, allergen: RowAllergen?, unit: PortionUnit, onChoose: (RefFood) -> Unit) {
    val portion = ref.defaultPortion
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 65.dp)
                .clickable(onClickLabel = "Choose a portion of ${ref.name}") { onChoose(ref) }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // USDA foods have no photo: a generic icon for the kind of food.
            ThumbTile(name = ref.name, imageUrl = null, category = ref.category)
            Spacer(Modifier.size(11.dp))
            Column(Modifier.weight(1f)) {
                Text(ref.name, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                Spacer(Modifier.size(2.dp))
                Text(
                    text = "${portion.label} (${unit.format(portion.grams)})" +
                        if (ref.category.isNotBlank()) " · ${ref.category}" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                if (allergen != null) {
                    Spacer(Modifier.size(4.dp))
                    RowAllergenChip(allergen)
                } else {
                    checkLabelReasonFor(ref.name)?.let { reason ->
                        Spacer(Modifier.size(4.dp))
                        CheckLabelChip(reason)
                    }
                }
            }
            Spacer(Modifier.size(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text("${ref.kcalFor(portion.grams).roundToInt()}", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "kcal · ${ref.proteinFor(portion.grams).roundToInt()}g P",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun BarcodeTab(
    state: AddFoodUiState,
    onBarcode: (String) -> Unit,
    onRescan: () -> Unit,
    onRetry: (String) -> Unit,
    onPick: (FoodEntity) -> Unit,
    onExit: () -> Unit,
    onCreateForBarcode: (
        String?, String, String?, Double, String, Double, Double, Double, Double,
    ) -> Unit,
) {
    var manualFor by remember { mutableStateOf<String?>(null) }
    val pendingManual = manualFor

    if (pendingManual != null) {
        CreateFoodForm(
            heading = "Add this product",
            note = (if (pendingManual.isNotBlank()) "Barcode $pendingManual. " else "") +
                "The barcode is kept with it, so the next scan of this item resolves instantly.",
            portionUnit = state.portionUnit,
            onCancel = { manualFor = null },
            onCreate = { name, brand, grams, label, k, p, c, f ->
                onCreateForBarcode(
                    pendingManual.takeIf { it.isNotBlank() },
                    name, brand, grams, label, k, p, c, f,
                )
                manualFor = null
            },
        )
        return
    }

    when (val barcode = state.barcodeState) {
        BarcodeState.Idle, BarcodeState.Scanning -> Column(
            // Scrolls, so the manual entry stays reachable on a short screen or with the keyboard open.
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        ) {
            BarcodeScannerView(
                onBarcode = onBarcode,
                onEnterManually = { manualFor = "" },
                onExit = onExit,
            )
        }

        BarcodeState.LookingUp -> Column(Modifier.fillMaxWidth().padding(28.dp)) {
            Text("Looking that barcode up...")
            Spacer(Modifier.size(12.dp))
            SkeletonList(rows = 2, rowHeight = 56.dp, label = "Looking that barcode up")
        }

        is BarcodeState.Found -> Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FoodThumb(barcode.food, size = 64.dp)
                Spacer(Modifier.size(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(barcode.food.name, style = MaterialTheme.typography.titleLarge)
                    barcode.food.brand?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (barcode.fromCache) {
                    "Already on this phone, so no lookup was needed."
                } else {
                    "From Open Food Facts, saved to this phone."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Only when nothing stronger was found: the name match is a nudge, not a verdict.
            if (!barcode.scan.hasAnything) {
                barcode.scan.checkLabel.firstOrNull()?.let {
                    Spacer(Modifier.height(8.dp))
                    CheckLabelChip(it.note)
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { onPick(barcode.food) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Choose a portion") }
            OutlinedButton(
                onClick = onRescan,
                modifier = Modifier.fillMaxWidth().height(52.dp).padding(top = 8.dp),
            ) { Text("Scan another") }
        }

        is BarcodeState.Unknown -> Column(Modifier.padding(20.dp)) {
            Text("Not found in Open Food Facts", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                text = barcode.reason
                    ?: "Open Food Facts has no record for this barcode. Enter it once and it " +
                        "is yours from then on.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { manualFor = barcode.barcode },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Add this product") }
            OutlinedButton(
                onClick = onRescan,
                modifier = Modifier.fillMaxWidth().height(52.dp).padding(top = 8.dp),
            ) { Text("Scan again") }
        }

        is BarcodeState.LookupFailed -> Column(Modifier.padding(20.dp)) {
            Text("Could not look that up", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                text = barcode.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { onRetry(barcode.barcode) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Try again") }
            OutlinedButton(
                onClick = { manualFor = barcode.barcode },
                modifier = Modifier.fillMaxWidth().height(52.dp).padding(top = 8.dp),
            ) { Text("Add this product") }
            OutlinedButton(
                onClick = onRescan,
                modifier = Modifier.fillMaxWidth().height(52.dp).padding(top = 8.dp),
            ) { Text("Scan again") }
        }
    }
}

@Composable
private fun DegradedMessage(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(20.dp),
    )
}

/** SPEC §5.4: four number fields, no food record, no name required. */
@Composable
private fun QuickAddTab(onQuickAdd: (String, Double, Double, Double, Double) -> Unit) {
    var name by remember { mutableStateOf("") }
    var kcal by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "For when you already know the numbers. Nothing is added to your food list.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberField(kcal, { kcal = it }, "Calories", Modifier.weight(1f))
            NumberField(protein, { protein = it }, "Protein g", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberField(carbs, { carbs = it }, "Carbs g", Modifier.weight(1f))
            NumberField(fat, { fat = it }, "Fat g", Modifier.weight(1f))
        }
        Button(
            onClick = {
                onQuickAdd(
                    name,
                    kcal.toDoubleOrNull() ?: 0.0,
                    protein.toDoubleOrNull() ?: 0.0,
                    carbs.toDoubleOrNull() ?: 0.0,
                    fat.toDoubleOrNull() ?: 0.0,
                )
            },
            enabled = kcal.toDoubleOrNull()?.let { it > 0 } == true,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("Log it") }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun CreateFoodForm(
    heading: String,
    note: String,
    onCancel: () -> Unit,
    onCreate: (String, String?, Double, String, Double, Double, Double, Double) -> Unit,
    initialName: String = "",
    portionUnit: PortionUnit = PortionUnit.GRAMS,
) {
    var name by remember { mutableStateOf(initialName) }
    var brand by remember { mutableStateOf("") }
    // Typed in the person's portion unit (g or oz); stored as grams.
    var servingGrams by remember { mutableStateOf(portionUnit.number(100.0)) }
    var servingLabel by remember { mutableStateOf("") }
    var kcal by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }

    val grams = portionUnit.parseToGrams(servingGrams)
    val valid = name.isNotBlank() && grams != null && grams > 0 && kcal.toDoubleOrNull() != null

    Column(
        modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(heading, style = MaterialTheme.typography.titleMedium)
        Text(
            text = note,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = brand,
            onValueChange = { brand = it },
            label = { Text("Brand (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberField(servingGrams, { servingGrams = it }, "Serving (${portionUnit.label})", Modifier.weight(1f))
            OutlinedTextField(
                value = servingLabel,
                onValueChange = { servingLabel = it },
                label = { Text("Serving name") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberField(kcal, { kcal = it }, "Calories", Modifier.weight(1f))
            NumberField(protein, { protein = it }, "Protein g", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberField(carbs, { carbs = it }, "Carbs g", Modifier.weight(1f))
            NumberField(fat, { fat = it }, "Fat g", Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
            Button(
                onClick = {
                    onCreate(
                        name.trim(),
                        brand.trim().ifBlank { null },
                        grams ?: 100.0,
                        servingLabel.trim(),
                        kcal.toDoubleOrNull() ?: 0.0,
                        protein.toDoubleOrNull() ?: 0.0,
                        carbs.toDoubleOrNull() ?: 0.0,
                        fat.toDoubleOrNull() ?: 0.0,
                    )
                },
                enabled = valid,
                modifier = Modifier.weight(1f),
            ) { Text("Save") }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { next -> onValueChange(next.filter { it.isDigit() || it == '.' }) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}
