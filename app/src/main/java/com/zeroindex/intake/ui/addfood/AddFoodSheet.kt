@file:OptIn(ExperimentalMaterial3Api::class)

package com.zeroindex.intake.ui.addfood

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Surface
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.graphics.Color
import com.zeroindex.intake.ui.components.Eyebrow
import com.zeroindex.intake.ui.theme.IntakeShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zeroindex.intake.data.db.entity.FoodEntity
import com.zeroindex.intake.domain.model.MealSlot
import com.zeroindex.intake.domain.nutrition.Macros
import com.zeroindex.intake.ui.allergen.AllergenWarningSheet
import com.zeroindex.intake.ui.barcode.BarcodeScannerView
import kotlin.math.roundToInt

/**
 * SPEC §5: one "Add food" button opening a sheet with the add paths as tabs.
 * Recents is the default, because in practice most logging is repetition.
 */
@Composable
fun AddFoodSheet(
    mealSlot: MealSlot,
    loggedAtMillis: Long,
    onDismiss: () -> Unit,
    viewModel: AddFoodViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var portionFor by remember { mutableStateOf<FoodEntity?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding()) {
            val portioning = portionFor
            if (portioning != null) {
                PortionStep(
                    food = portioning,
                    onCancel = { portionFor = null },
                    onLog = { grams ->
                        viewModel.requestLogFood(portioning, grams, mealSlot, loggedAtMillis) {
                            portionFor = null
                            onDismiss()
                        }
                    },
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Add food",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                }

                ScrollableTabRow(
                    selectedTabIndex = AddFoodTab.entries.indexOf(state.tab),
                    edgePadding = 14.dp,
                    containerColor = Color.Transparent,
                    divider = {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    },
                    // The mockup's tab marker is a short rounded bar inset from
                    // the label, not a full-width underline.
                    indicator = { tabPositions ->
                        val position = tabPositions[AddFoodTab.entries.indexOf(state.tab)]
                        Box(
                            Modifier
                                .tabIndicatorOffset(position)
                                .padding(horizontal = 10.dp)
                                .height(3.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(4.dp),
                                ),
                        )
                    },
                ) {
                    AddFoodTab.entries.forEach { tab ->
                        Tab(
                            selected = state.tab == tab,
                            onClick = { viewModel.selectTab(tab) },
                            selectedContentColor = MaterialTheme.colorScheme.primary,
                            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            text = {
                                Text(tab.label, style = MaterialTheme.typography.labelSmall)
                            },
                        )
                    }
                }

                when (state.tab) {
                    AddFoodTab.RECENTS -> FoodList(
                        foods = state.recents,
                        safeIds = state.safeFoodIds,
                        emptyMessage = "Nothing logged yet. Once you log a few things, your " +
                            "most-used foods land here.",
                        onPick = { portionFor = it },
                        onToggleFavorite = viewModel::toggleFavorite,
                    )

                    AddFoodTab.SEARCH -> SearchTab(
                        state = state,
                        onQueryChange = viewModel::setQuery,
                        onSearchRemote = viewModel::searchRemote,
                        onPick = { portionFor = it },
                        onToggleFavorite = viewModel::toggleFavorite,
                    )

                    AddFoodTab.BARCODE -> BarcodeTab(
                        state = state,
                        onBarcode = viewModel::onBarcodeScanned,
                        onRescan = viewModel::rescanBarcode,
                        onPick = { portionFor = it },
                        onCreateForBarcode = { barcode, name, brand, grams, label, k, p, c, f ->
                            viewModel.createFood(
                                name = name,
                                brand = brand,
                                servingGrams = grams,
                                servingLabel = label,
                                kcal = k,
                                protein = p,
                                carbs = c,
                                fat = f,
                                barcode = barcode,
                            ) { created -> portionFor = created }
                        },
                    )

                    AddFoodTab.QUICK_ADD -> QuickAddTab(
                        onQuickAdd = { name, kcal, protein, carbs, fat ->
                            viewModel.requestQuickAdd(
                                name = name,
                                macros = Macros(
                                    kcal = kcal,
                                    protein = protein,
                                    carbs = carbs,
                                    fat = fat,
                                ),
                                slot = mealSlot,
                                at = loggedAtMillis,
                                onLogged = onDismiss,
                            )
                        },
                    )

                    AddFoodTab.MY_FOODS -> MyFoodsTab(
                        myFoods = state.myFoods,
                        favorites = state.favorites,
                        safeIds = state.safeFoodIds,
                        onPick = { portionFor = it },
                        onToggleFavorite = viewModel::toggleFavorite,
                        onCreate = { name, brand, grams, label, k, p, c, f ->
                            viewModel.createFood(
                                name = name,
                                brand = brand,
                                servingGrams = grams,
                                servingLabel = label,
                                kcal = k,
                                protein = p,
                                carbs = c,
                                fat = f,
                            ) { created -> portionFor = created }
                        },
                    )
                }
            }
        }
    }

    // SPEC §7.2: nothing is written until this is answered.
    state.pendingLog?.let { pending ->
        AllergenWarningSheet(
            foodName = pending.name,
            scan = pending.scan,
            // The stored panel text, so the sheet can show the matched term in
            // context rather than just naming it.
            ingredientText = pending.food?.allergenTags?.takeIf { it.isNotBlank() },
            foodDetail = pending.grams?.let { grams ->
                val food = pending.food
                if (food != null) {
                    "${grams.roundToInt()} g · " +
                        "${(food.kcalPer100g * grams / 100.0).roundToInt()} kcal · " +
                        "${(food.proteinPer100g * grams / 100.0).roundToInt()} g protein"
                } else {
                    null
                }
            },
            onDismiss = viewModel::dismissPendingLog,
            onLogAnyway = {
                viewModel.confirmPendingLog(mealSlot, loggedAtMillis) {
                    portionFor = null
                    onDismiss()
                }
            },
        )
    }
}

@Composable
private fun FoodList(
    foods: List<FoodEntity>,
    safeIds: Set<String>,
    emptyMessage: String,
    onPick: (FoodEntity) -> Unit,
    onToggleFavorite: (FoodEntity) -> Unit,
) {
    if (foods.isEmpty()) {
        Text(
            text = emptyMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(24.dp),
        )
        return
    }
    LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
        items(items = foods, key = { it.id }) { food ->
            FoodRow(food, food.id in safeIds, onPick, onToggleFavorite)
        }
    }
}

/**
 * The mockup's `.food-row`: an initial tile, the name over its portion and
 * meal, and the energy right-aligned. The initial stands in for the mockup's
 * emoji - it needs no food-to-glyph mapping and never shows the wrong picture.
 */
@Composable
private fun FoodRow(
    food: FoodEntity,
    isSafe: Boolean,
    onPick: (FoodEntity) -> Unit,
    onToggleFavorite: (FoodEntity) -> Unit,
) {
    val perServing = food.servingSizeGrams / 100.0
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 65.dp)
                .clickable(onClickLabel = "Choose a portion of ${food.name}") { onPick(food) }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = IntakeShape.Tile,
                modifier = Modifier.size(38.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = food.name.trim().take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
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
                        // SPEC §7.5: a check on foods logged 3+ times clean.
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Logged before with no allergen flag",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
                Spacer(Modifier.size(2.dp))
                Text(
                    text = buildString {
                        food.brand?.let { append("$it · ") }
                        append(food.servingLabel)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.size(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${(food.kcalPer100g * perServing).roundToInt()}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "kcal · ${(food.proteinPer100g * perServing).roundToInt()}g P",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { onToggleFavorite(food) }, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = if (food.isFavorite) {
                        Icons.Filled.Star
                    } else {
                        Icons.Outlined.StarBorder
                    },
                    contentDescription = if (food.isFavorite) {
                        "Remove ${food.name} from favourites"
                    } else {
                        "Add ${food.name} to favourites"
                    },
                    modifier = Modifier.size(19.dp),
                )
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
    onPick: (FoodEntity) -> Unit,
    onToggleFavorite: (FoodEntity) -> Unit,
) {
    Column {
        TextField(
            value = state.query,
            onValueChange = onQueryChange,
            placeholder = {
                Text("Search foods", style = MaterialTheme.typography.bodyMedium)
            },
            leadingIcon = {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = null,
                    modifier = Modifier.size(19.dp),
                )
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

        // SPEC §5.1: local results first, above anything remote.
        if (state.localResults.isNotEmpty()) {
            Eyebrow(
                text = "On this phone",
                modifier = Modifier.padding(start = 16.dp, bottom = 4.dp),
            )
            LazyColumn(modifier = Modifier.heightIn(max = 220.dp)) {
                items(items = state.localResults, key = { it.id }) { food ->
                    FoodRow(food, food.id in state.safeFoodIds, onPick, onToggleFavorite)
                }
            }
            HorizontalDivider()
        }

        when (val search = state.searchState) {
            SearchState.Idle ->
                if (state.query.isNotBlank()) {
                    OutlinedButton(
                        onClick = onSearchRemote,
                        modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .height(52.dp),
                    ) { Text("Search the USDA database") }
                } else {
                    Text(
                        text = "Type to search foods already on this phone, then search USDA " +
                            "if nothing matches.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(20.dp),
                    )
                }

            SearchState.Loading -> Row(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(12.dp))
                Text("Searching USDA...", style = MaterialTheme.typography.bodyMedium)
            }

            is SearchState.Remote -> {
                Text(
                    text = "From USDA, saved to this phone",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
                )
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(items = search.results, key = { "r-${it.id}" }) { food ->
                        FoodRow(food, false, onPick, onToggleFavorite)
                    }
                }
            }

            // SPEC §3.1: degrade to manual entry with a clear message.
            SearchState.NeedsKey -> DegradedMessage(
                "USDA search needs a free API key. Add one in More, Settings - everything " +
                    "else works without it.",
            )
            SearchState.NoResults -> DegradedMessage(
                "Nothing came back for that. Add it by hand from My foods and it will be " +
                    "here next time.",
            )
            is SearchState.Failed -> DegradedMessage(search.message)
        }
    }
}

@Composable
private fun BarcodeTab(
    state: AddFoodUiState,
    onBarcode: (String) -> Unit,
    onRescan: () -> Unit,
    onPick: (FoodEntity) -> Unit,
    onCreateForBarcode: (
        String?, String, String?, Double, String, Double, Double, Double, Double,
    ) -> Unit,
) {
    var manualFor by remember { mutableStateOf<String?>(null) }
    val pendingManual = manualFor

    if (pendingManual != null) {
        CreateFoodForm(
            heading = "New product",
            note = "The barcode is kept with it, so the next scan of this item resolves instantly.",
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
            modifier = Modifier.fillMaxWidth().heightIn(min = 320.dp, max = 460.dp),
        ) {
            BarcodeScannerView(
                onBarcode = onBarcode,
                onEnterManually = { manualFor = "" },
            )
        }

        BarcodeState.LookingUp -> Row(
            modifier = Modifier.fillMaxWidth().padding(28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(12.dp))
            Text("Looking that barcode up...")
        }

        is BarcodeState.Found -> Column(Modifier.padding(20.dp)) {
            Text(barcode.food.name, style = MaterialTheme.typography.titleLarge)
            barcode.food.brand?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
            Text("Not in the database", style = MaterialTheme.typography.titleMedium)
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
            ) { Text("Add it by hand") }
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
private fun MyFoodsTab(
    myFoods: List<FoodEntity>,
    favorites: List<FoodEntity>,
    safeIds: Set<String>,
    onPick: (FoodEntity) -> Unit,
    onToggleFavorite: (FoodEntity) -> Unit,
    onCreate: (String, String?, Double, String, Double, Double, Double, Double) -> Unit,
) {
    var creating by remember { mutableStateOf(false) }

    if (creating) {
        CreateFoodForm(
            heading = "New food",
            note = "Enter the numbers for one serving as they appear on the label. They are " +
                "stored per 100 g so any portion works later.",
            onCancel = { creating = false },
            onCreate = { name, brand, grams, label, k, p, c, f ->
                onCreate(name, brand, grams, label, k, p, c, f)
                creating = false
            },
        )
        return
    }

    Column {
        OutlinedButton(
            onClick = { creating = true },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                .height(52.dp),
        ) { Text("Add a food by hand") }

        if (favorites.isEmpty() && myFoods.isEmpty()) {
            Text(
                text = "Foods you enter by hand show up here. Enter your regular 40 or so once " +
                    "and the food database stops being the problem.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
            return@Column
        }

        LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
            if (favorites.isNotEmpty()) {
                item(key = "fav-header") {
                    Text(
                        text = "Favourites",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp),
                    )
                }
            }
            items(items = favorites, key = { "fav-${it.id}" }) { food ->
                FoodRow(food, food.id in safeIds, onPick, onToggleFavorite)
            }
            if (myFoods.isNotEmpty()) {
                item(key = "mine-header") {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        text = "Entered by hand",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(start = 16.dp, bottom = 4.dp),
                    )
                }
            }
            items(items = myFoods, key = { "mine-${it.id}" }) { food ->
                FoodRow(food, food.id in safeIds, onPick, onToggleFavorite)
            }
        }
    }
}

@Composable
private fun CreateFoodForm(
    heading: String,
    note: String,
    onCancel: () -> Unit,
    onCreate: (String, String?, Double, String, Double, Double, Double, Double) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf("") }
    var servingGrams by remember { mutableStateOf("100") }
    var servingLabel by remember { mutableStateOf("") }
    var kcal by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }

    val grams = servingGrams.toDoubleOrNull()
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
            NumberField(servingGrams, { servingGrams = it }, "Serving grams", Modifier.weight(1f))
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

/** Grams and the resulting macros are the only portion controls that matter. */
@Composable
private fun PortionStep(
    food: FoodEntity,
    onCancel: () -> Unit,
    onLog: (Double) -> Unit,
) {
    var grams by remember(food.id) {
        mutableStateOf(food.servingSizeGrams.roundToInt().toString())
    }
    val entered = grams.toDoubleOrNull() ?: 0.0
    val factor = entered / 100.0

    Column(
        modifier = Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(food.name, style = MaterialTheme.typography.titleLarge)
        food.brand?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                food.servingSizeGrams.roundToInt() to food.servingLabel.ifBlank { "1 serving" },
                100 to "100 g",
                (food.servingSizeGrams * 2).roundToInt() to "2 servings",
            ).distinctBy { it.first }.forEach { (value, label) ->
                OutlinedButton(onClick = { grams = value.toString() }) { Text(label) }
            }
        }

        NumberField(grams, { grams = it }, "Grams", Modifier.fillMaxWidth())

        Text(
            text = "${(food.kcalPer100g * factor).roundToInt()} kcal · " +
                "${(food.proteinPer100g * factor).roundToInt()} g protein · " +
                "${(food.carbsPer100g * factor).roundToInt()} g carbs · " +
                "${(food.fatPer100g * factor).roundToInt()} g fat",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Back") }
            Button(
                onClick = { onLog(entered) },
                enabled = entered > 0,
                modifier = Modifier.weight(1f),
            ) { Text("Log it") }
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
