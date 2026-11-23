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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import com.zeroindex.intake.ui.components.rememberSyncedText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zeroindex.intake.data.db.entity.FoodEntity
import com.zeroindex.intake.data.foodref.RefFood
import com.zeroindex.intake.domain.model.MealSlot
import com.zeroindex.intake.domain.nutrition.Macros
import com.zeroindex.intake.domain.nutrition.QuickAddField
import com.zeroindex.intake.domain.nutrition.QuickAddLimits
import com.zeroindex.intake.domain.units.PortionUnit
import com.zeroindex.intake.ui.allergen.AllergenChip
import com.zeroindex.intake.ui.allergen.AllergenWarningSheet
import com.zeroindex.intake.ui.allergen.CheckLabelChip
import com.zeroindex.intake.ui.allergen.ProvideCheckLabelTerms
import com.zeroindex.intake.ui.allergen.checkLabelReasonFor
import com.zeroindex.intake.ui.barcode.BarcodeScannerView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.sp
import com.zeroindex.intake.ui.components.ChipTone
import com.zeroindex.intake.ui.components.IconCircleButton
import com.zeroindex.intake.ui.components.IconCircleStyle
import com.zeroindex.intake.ui.components.IntakeChip
import com.zeroindex.intake.ui.components.LineButton
import com.zeroindex.intake.ui.components.ListDivider
import com.zeroindex.intake.ui.components.RowChevron
import com.zeroindex.intake.ui.theme.IconSize
import com.zeroindex.intake.ui.theme.IntakeIcons
import com.zeroindex.intake.ui.theme.IntakeSpacing
import com.zeroindex.intake.ui.theme.intake
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
    MealSlot.DRINKS -> "Drinks"
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
    // Saved recipes are found here too (kept in their own view model, so the food search is untouched).
    val recipeVm: RecipeSearchViewModel = hiltViewModel()
    val recipes by recipeVm.state.collectAsStateWithLifecycle()
    val pendingRecipe by recipeVm.pending.collectAsStateWithLifecycle()
    // The text field owns its own state (a TextFieldState): the keyboard edits it directly and nothing the view models
    // compute is ever fed back into it, so fast typing cannot lose letters (Q1-3, Q5a-3). The view models get the words
    // and debounce them; the lists below follow `queryText`, which waits for a pause in typing so a burst of keys does
    // not rebuild them per key (Q5a-4).
    val search = rememberSyncedText(
        external = null,
        onChange = { text ->
            viewModel.setQuery(text)
            recipeVm.setQuery(text)
        },
        initial = viewModel.currentQuery,
    )
    val queryText = search.settled
    LaunchedEffect(Unit) { recipeVm.setQuery(search.text) }
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
            // One scrolling row of pills (b.4): the meal, then Search, Scan, Photo (AI), Mine, Quick add and Drinks.
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = IntakeSpacing.ScreenGutter, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    SourcePill(slotWord(mealSlot), null, false, { slotMenu = true }, trailing = IntakeIcons.ChevronDown, soft = true)
                    DropdownMenu(expanded = slotMenu, onDismissRequest = { slotMenu = false }) {
                        MealSlot.MEALS.forEach { s ->
                            DropdownMenuItem(text = { Text(slotWord(s)) }, onClick = { mealSlot = s; slotMenu = false })
                        }
                    }
                }
                SourcePill("Search", IntakeIcons.Search, state.tab == AddFoodTab.SEARCH, { viewModel.selectTab(AddFoodTab.SEARCH) })
                SourcePill("Scan", IntakeIcons.Scan, state.tab == AddFoodTab.BARCODE, { viewModel.selectTab(AddFoodTab.BARCODE) })
                SourcePill("Photo", IntakeIcons.Camera, state.tab == AddFoodTab.PHOTO, { viewModel.selectTab(AddFoodTab.PHOTO) }, ai = true)
                SourcePill("Mine", null, state.tab == AddFoodTab.MY_FOODS, { viewModel.selectTab(AddFoodTab.MY_FOODS) })
                SourcePill("Quick add", null, state.tab == AddFoodTab.QUICK_ADD, { viewModel.selectTab(AddFoodTab.QUICK_ADD) })
                SourcePill("Drinks", null, false, { onOpenDrinks("") })
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (state.tab) {
                    AddFoodTab.SEARCH -> SearchTab(
                        state = state,
                        query = queryText,
                        field = search.box,
                        onClear = { search.edit("") },
                        onSearchRemote = viewModel::searchRemote,
                        onOpen = openEntity,
                        onQuickAdd = { food -> viewModel.quickAddDefault(food, mealSlot, loggedAtMillis) },
                        onChooseRef = { ref -> viewModel.chooseRef(ref) { food, _ -> openEntity(food) } },
                        onChooseOff = { food -> viewModel.chooseOff(food, openEntity) },
                        onLogMenu = { row -> viewModel.logMenuItem(row, mealSlot, loggedAtMillis) },
                        recipes = recipes,
                        onLogRecipe = { row -> recipeVm.requestLog(row, mealSlot, loggedAtMillis) },
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
                            search.edit(name)
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
                        recipes = recipes.all,
                        recipesLoaded = recipes.loaded,
                        onLogRecipe = { row -> recipeVm.requestLog(row, mealSlot, loggedAtMillis) },
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
                modifier = Modifier.padding(horizontal = IntakeSpacing.ScreenGutter, vertical = 6.dp),
            )
        }
    }

    // A recipe serving that matches the allergen profile waits here, exactly as a food does.
    pendingRecipe?.let { pending ->
        AllergenWarningSheet(
            foodName = pending.name,
            scan = pending.scan,
            ingredientText = pending.ingredientText,
            onDismiss = recipeVm::dismissPending,
            onLogAnyway = { recipeVm.confirmPending(mealSlot, loggedAtMillis) },
        )
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
    if (allergen.checking) {
        // Neutral: neither a warning nor a clear. The verdict is on its way.
        CheckingChip()
    } else if (allergen.cautionClear) {
        AllergenChip(com.zeroindex.intake.domain.swap.SwapAllergen.CHECK_LABEL, label = allergen.basis.chipText)
    } else {
        AllergenChip(allergen.verdict, allergen.detail, label = allergen.traceLabel)
    }
}

@Composable
private fun CheckingChip() {
    // Neutral and worded: "Checking…" is never blank and never green.
    IntakeChip("Checking\u2026", modifier = Modifier.semantics { contentDescription = "Checking allergens" })
}

// ---- Grouped result rows (DESIGN-V6 b.8): one card per source, built from light rows ----

private val GroupTopShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
private val GroupBottomShape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)

/**
 * Draws a lazy row as one slice of its source's card: gutter on both sides, `card` fill, the group's first row rounded on top
 * and its last at the bottom. Rows stay separate lazy items (so the list stays lazy) and cost one background each.
 */
@Composable
internal fun Modifier.groupRow(index: Int, count: Int, gutter: Boolean = true): Modifier {
    val shape = when {
        count <= 1 -> IntakeShape.Card
        index == 0 -> GroupTopShape
        index == count - 1 -> GroupBottomShape
        else -> RectangleShape
    }
    val base = if (gutter) this.padding(horizontal = IntakeSpacing.ScreenGutter) else this
    return (if (shape === RectangleShape) base else base.clip(shape)).background(MaterialTheme.colorScheme.surface)
}

/** The divider above every row but the first of a group. */
@Composable
internal fun GroupDivider(index: Int) {
    if (index > 0) ListDivider()
}

/** The row's "+" (44 dp hit area, one icon, no surface). */
@Composable
private fun RowPlus(description: String, onClick: () -> Unit) {
    IconCircleButton(IntakeIcons.Plus, description, onClick, style = IconCircleStyle.Clear)
}

/**
 * Result rows. The name over its portion and energy, the allergen chip (when the person avoids anything) and a "+" that logs
 * one serving. Tapping the row opens Food detail. Light on purpose: a photo tile only when the food has a picture, no
 * surface or icon otherwise.
 */
@Composable
private fun FoodRow(
    food: FoodEntity,
    isSafe: Boolean,
    allergen: RowAllergen?,
    onOpen: (FoodEntity) -> Unit,
    onQuickAdd: ((FoodEntity) -> Unit)?,
    onToggleFavorite: ((FoodEntity) -> Unit)? = null,
    index: Int = 0,
    count: Int = 1,
) {
    val perServing = food.servingSizeGrams / 100.0
    val isDrink = food.drinkCategory != null
    Column(Modifier.groupRow(index, count)) {
        GroupDivider(index)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = IntakeSpacing.ListRowMinHeight)
                .clickable(onClickLabel = "Open ${food.name}") { onOpen(food) }
                .padding(start = IntakeSpacing.ListRowPadH, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (food.imageUrl != null) {
                FoodThumb(food)
                Spacer(Modifier.size(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = food.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    // One signal: the tick is a "safe" mark, so it never sits beside a chip that says the name was the
                    // only thing checked, an ingredient list is missing, or anything is flagged or still checking.
                    val chipAllowsTick = allergen == null || allergen.allowsSafeTick
                    if (isSafe && chipAllowsTick) {
                        Spacer(Modifier.size(5.dp))
                        Icon(
                            imageVector = IntakeIcons.Check,
                            contentDescription = "Logged before with no allergen flag",
                            tint = MaterialTheme.intake.ok,
                            modifier = Modifier.size(IconSize.ExtraSmall),
                        )
                    }
                }
                Text(
                    text = buildString {
                        food.brand?.let { append("$it · ") }
                        append(food.servingLabel)
                        append(" · ${(food.kcalPer100g * perServing).roundToInt()} kcal")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
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
                RowPlus(if (isDrink) "Add one ${food.servingLabel} of ${food.name}" else "Log one serving of ${food.name}") { onQuickAdd(food) }
            }
        }
    }
}

@Composable
private fun SearchTab(
    state: AddFoodUiState,
    /** The words the results follow (settled, not per keystroke). */
    query: String,
    field: androidx.compose.foundation.text.input.TextFieldState,
    onClear: () -> Unit,
    onSearchRemote: () -> Unit,
    onOpen: (FoodEntity) -> Unit,
    onQuickAdd: (FoodEntity) -> Unit,
    onChooseRef: (RefFood) -> Unit,
    onChooseOff: (FoodEntity) -> Unit,
    onLogMenu: (MenuRow) -> Unit,
    recipes: RecipeSearchUi,
    onLogRecipe: (RecipeRowUi) -> Unit,
    onOpenDrinks: (String) -> Unit,
    onCreate: (String, String?, Double, String, Double, Double, Double, Double) -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    if (creating) {
        CreateFoodForm(
            heading = "Create a food",
            note = "Enter the numbers for one serving as they appear on the label. They are " +
                "stored per 100 g so any portion works later.",
            initialName = field.text.toString().trim(),
            portionUnit = state.portionUnit,
            onCancel = { creating = false },
            onCreate = { name, brand, grams, label, k, p, c, f ->
                onCreate(name, brand, grams, label, k, p, c, f)
                creating = false
            },
        )
        return
    }

    // Read through derivedStateOf so only a change between empty and not empty (not every key) recomposes the icon.
    val hasText by remember(field) { androidx.compose.runtime.derivedStateOf { field.text.isNotEmpty() } }
    Column(Modifier.fillMaxSize()) {
        TextField(
            state = field,
            placeholder = { Text("Search foods", style = MaterialTheme.typography.bodyLarge) },
            leadingIcon = { Icon(IntakeIcons.Search, contentDescription = null, modifier = Modifier.size(IconSize.Default)) },
            trailingIcon = {
                if (hasText) {
                    IconCircleButton(IntakeIcons.Close, "Clear the search", onClear, style = IconCircleStyle.Clear)
                }
            },
            lineLimits = androidx.compose.foundation.text.input.TextFieldLineLimits.SingleLine,
            textStyle = MaterialTheme.typography.bodyLarge,
            shape = IntakeShape.Pill,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                focusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = IntakeSpacing.ScreenGutter, vertical = 10.dp),
        )

        if (query.isBlank()) {
            if (!state.loaded || !recipes.loaded) {
                // Not read yet: say nothing rather than "Nothing logged yet" (Q1-7).
                Box(Modifier.fillMaxSize())
                return@Column
            }
            // Saved recipes that are not in Recent recipes still get listed: every saved recipe can be found here.
            val recentRecipeIds = recipes.recents.map { it.recipe.id }.toSet()
            val otherRecipes = recipes.all.filter { it.recipe.id !in recentRecipeIds }
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (state.recents.isEmpty() && recipes.recents.isEmpty() && otherRecipes.isEmpty()) {
                    item(key = "no-recents") {
                        Column(Modifier.padding(horizontal = IntakeSpacing.ScreenGutter)) {
                            EmptyState(
                                title = "Nothing logged yet",
                                body = "Search your foods and the offline USDA database. Both work with no internet and no key. Foods you log often land here.",
                            )
                        }
                    }
                }
                if (state.recents.isNotEmpty()) {
                    item(key = "h-recent") { SourceHeader("Recent") }
                    itemsIndexed(items = state.recents, key = { _, it -> "recent-${it.id}" }) { i, food ->
                        FoodRow(food, food.id in state.safeFoodIds, state.chipFor(food.id), onOpen, onQuickAdd, index = i, count = state.recents.size)
                    }
                }
                if (recipes.recents.isNotEmpty()) {
                    item(key = "h-recent-recipes") { RecipeSourceHeader("Recent recipes") }
                    itemsIndexed(items = recipes.recents, key = { _, it -> "recent-recipe-${it.recipe.id}" }) { i, row -> RecipeLogRow(row, onLogRecipe, i, recipes.recents.size) }
                }
                if (otherRecipes.isNotEmpty()) {
                    item(key = "h-saved-recipes") { RecipeSourceHeader(if (recipes.recents.isEmpty()) "Saved recipes" else "Other saved recipes") }
                    itemsIndexed(items = otherRecipes, key = { _, it -> "saved-recipe-${it.recipe.id}" }) { i, row -> RecipeLogRow(row, onLogRecipe, i, otherRecipes.size) }
                }
                item(key = "drinks-row") { DrinksRow("Categories, a drinks search and size buttons") { onOpenDrinks("") } }
            }
            return@Column
        }

        val offLoading = state.offState == OffUiState.Loading
        val offResults = (state.offState as? OffUiState.Results)?.foods.orEmpty()
        val usdaApiResults = (state.searchState as? SearchState.Remote)?.results.orEmpty()
        val nothingYet = state.localResults.isEmpty() && state.refResults.isEmpty() &&
            offResults.isEmpty() && usdaApiResults.isEmpty() && state.menuRows.isEmpty() && recipes.results.isEmpty()
        // The Open Food Facts failure copy names sources only when some are on screen above it (QA Q1-23).
        val anySourceAbove = state.localResults.isNotEmpty() || state.refResults.isNotEmpty() ||
            state.menuRows.isNotEmpty() || recipes.results.isNotEmpty()

        // Results arrive above rows that are already on screen (the phone's own foods and the offline database come in
        // over the Open Food Facts section), and a lazy list stays on the row it was showing. Without this the best
        // matches end up off the top of the screen.
        val resultsState = androidx.compose.foundation.lazy.rememberLazyListState()
        androidx.compose.runtime.LaunchedEffect(query, state.localResults.size, state.refResults.size) {
            // A forced remeasure of the whole list: skipped when it is already at the top.
            if (resultsState.firstVisibleItemIndex != 0 || resultsState.firstVisibleItemScrollOffset != 0) resultsState.scrollToItem(0)
        }
        // Sources in order: this phone, the offline USDA database, Open Food Facts, the USDA API.
        LazyColumn(state = resultsState, modifier = Modifier.fillMaxSize()) {
            // The same words also find drinks (almond milk is a drink here, not a food): a row at the top, before the
            // foods that only share a word with them, opens Drinks on the same search.
            if (state.queryMatchesDrinks && !nothingYet) {
                item(key = "drinks-top") {
                    DrinksRow("Drinks matching \"${query.trim()}\" are logged from Drinks", title = "Drinks: ${query.trim()}") { onOpenDrinks(query.trim()) }
                }
            }
            if (state.localResults.isNotEmpty()) {
                item(key = "h-local") { SourceHeader("My foods and recents") }
                itemsIndexed(items = state.localResults, key = { _, it -> "l-${it.id}" }) { i, food ->
                    FoodRow(food, food.id in state.safeFoodIds, state.chipFor(food.id), onOpen, onQuickAdd, index = i, count = state.localResults.size)
                }
            }

            // Saved recipes: on this phone, one serving through the recipe logging path.
            if (recipes.results.isNotEmpty()) {
                item(key = "h-recipes") { RecipeSourceHeader("Recipes (saved on this phone)") }
                itemsIndexed(items = recipes.results, key = { _, it -> "rcp-${it.recipe.id}" }) { i, row -> RecipeLogRow(row, onLogRecipe, i, recipes.results.size) }
            }

            // Imported restaurant menus: on this phone, logged per serving with the item's own numbers.
            if (state.menuRows.isNotEmpty()) {
                item(key = "h-menu") { SourceHeader("Restaurant menus (imported, works offline)") }
                itemsIndexed(items = state.menuRows, key = { _, it -> "m-${it.hit.item.id}" }) { i, row ->
                    MenuItemRow(row, onLogMenu, i, state.menuRows.size)
                }
            }

            if (state.refResults.isNotEmpty()) {
                item(key = "h-ref") { SourceHeader("Food database (USDA, works offline)") }
                itemsIndexed(items = state.refResults, key = { _, it -> "r-${it.id}" }) { i, ref ->
                    RefFoodRow(ref, state.refChipFor(ref.id), state.portionUnit, onChooseRef, i, state.refResults.size)
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
                            modifier = Modifier.padding(horizontal = IntakeSpacing.ScreenGutter, vertical = 4.dp),
                        )
                        SkeletonList(modifier = Modifier.padding(horizontal = IntakeSpacing.ScreenGutter), rows = 3, rowHeight = 52.dp, label = "Searching Open Food Facts")
                    }
                }
                is OffUiState.Results -> if (offResults.isNotEmpty()) {
                    item(key = "h-off") { SourceHeader("Packaged foods (Open Food Facts)") }
                    itemsIndexed(items = offResults, key = { _, it -> "o-${it.id}" }) { i, food ->
                        FoodRow(food, false, state.chipFor(food.id), onChooseOff, null, index = i, count = offResults.size)
                    }
                }
                OffUiState.Failed -> item(key = "off-failed") {
                    Column {
                        SourceHeader("Packaged foods (Open Food Facts)")
                        DegradedMessage(
                            if (anySourceAbove) "Open Food Facts could not be reached. The sources above still work."
                            else "Open Food Facts could not be reached. Check your connection and try again.",
                        )
                    }
                }
            }

            // The USDA API is an extra for people who have set a key; it is never required.
            if (state.hasUsdaKey) {
                item(key = "usda-api") {
                    when (val search = state.searchState) {
                        SearchState.Idle -> LineButton(
                            onClick = onSearchRemote,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = IntakeSpacing.ScreenGutter, vertical = 8.dp).heightIn(min = IntakeSpacing.TouchTarget),
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
                    itemsIndexed(items = usdaApiResults, key = { _, it -> "u-${it.id}" }) { i, food ->
                        FoodRow(food, false, state.chipFor(food.id), onOpen, onQuickAdd, index = i, count = usdaApiResults.size)
                    }
                }
            }

            val typed = query.trim()
            if (nothingYet && !offLoading && state.queryMatchesDrinks) {
                // The words only find drinks. Foods and drinks are separate searches, so say where they went.
                item(key = "drinks-only") {
                    Column(Modifier.padding(IntakeSpacing.ScreenGutter)) {
                        Text("Drinks are logged from Drinks", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "\"$typed\" matches drinks, not foods. Drinks count toward water as well as calories, so they have their own page.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        Button(onClick = { onOpenDrinks(typed) }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp).heightIn(min = IntakeSpacing.TouchTarget)) { Text("Search Drinks for \"$typed\"") }
                        LineButton(onClick = { creating = true }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = IntakeSpacing.TouchTarget)) { Text("It is a food: create \"$typed\"") }
                    }
                }
            } else if (nothingYet && !offLoading) {
                item(key = "create") {
                    Column(Modifier.padding(IntakeSpacing.ScreenGutter)) {
                        Text(
                            "Nothing matches \"$typed\".",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(onClick = { creating = true }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp).heightIn(min = IntakeSpacing.TouchTarget)) { Text("Create a food") }
                    }
                }
                item(key = "drinks-row") { DrinksRow("Looking for a drink? Search Drinks for \"$typed\"") { onOpenDrinks(typed) } }
            } else if (!nothingYet) {
                item(key = "create-small") {
                    LineButton(
                        onClick = { creating = true },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = IntakeSpacing.ScreenGutter, vertical = 8.dp).heightIn(min = IntakeSpacing.TouchTarget),
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
private fun DrinksRow(note: String, title: String = "Log a drink", onOpenDrinks: () -> Unit) {
    Surface(
        onClick = onOpenDrinks,
        color = Color.Transparent,
        shape = IntakeShape.Card,
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().padding(horizontal = IntakeSpacing.ScreenGutter, vertical = 8.dp),
    ) {
        Row(Modifier.padding(horizontal = IntakeSpacing.ScreenGutter, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(IntakeIcons.Cup, contentDescription = null, modifier = Modifier.size(IconSize.Default))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            RowChevron()
        }
    }
}

/** Mine: favourites, the foods entered by hand, and "Add a food by hand". */
@Composable
private fun MyFoodsTab(
    state: AddFoodUiState,
    recipes: List<RecipeRowUi>,
    recipesLoaded: Boolean,
    onLogRecipe: (RecipeRowUi) -> Unit,
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
    // A word that starts with what was typed beats a match in the middle of a word ("almo" finds Almond milk before Salmon).
    // Remembered, so a recomposition that changes nothing here does not filter and rank the whole list again.
    val favorites = remember(state.favorites, needle) {
        com.zeroindex.intake.domain.food.NameMatchRank.filterAndRank(state.favorites, needle, { it.name }, { it.brand.orEmpty() })
    }
    val mine = remember(state.myFoods, state.favorites, needle) {
        val favoriteIds = state.favorites.mapTo(HashSet()) { it.id }
        com.zeroindex.intake.domain.food.NameMatchRank.filterAndRank(
            state.myFoods.filter { it.id !in favoriteIds }, needle, { it.name }, { it.brand.orEmpty() },
        )
    }
    val myRecipes = remember(recipes, needle) {
        com.zeroindex.intake.domain.food.NameMatchRank.filterAndRank(recipes, needle, { it.recipe.name })
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item(key = "add") {
            Column(Modifier.padding(horizontal = IntakeSpacing.ScreenGutter, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = filter,
                    onValueChange = { filter = it },
                    label = { Text("Filter your foods") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LineButton(onClick = { creating = true }, modifier = Modifier.fillMaxWidth().heightIn(min = IntakeSpacing.TouchTarget)) { Text("Add a food by hand") }
            }
        }
        if (state.loaded && recipesLoaded && favorites.isEmpty() && mine.isEmpty() && myRecipes.isEmpty()) {
            item(key = "empty") {
                Column(Modifier.padding(horizontal = IntakeSpacing.ScreenGutter)) {
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
            itemsIndexed(items = favorites, key = { _, it -> "fav-${it.id}" }) { i, food ->
                FoodRow(food, food.id in state.safeFoodIds, state.chipFor(food.id), onOpen, onQuickAdd, onToggleFavorite, index = i, count = favorites.size)
            }
        }
        if (mine.isNotEmpty()) {
            item(key = "mine-header") { SourceHeader("Saved on this phone") }
            itemsIndexed(items = mine, key = { _, it -> "mine-${it.id}" }) { i, food ->
                FoodRow(food, food.id in state.safeFoodIds, state.chipFor(food.id), onOpen, onQuickAdd, onToggleFavorite, index = i, count = mine.size)
            }
        }
        if (myRecipes.isNotEmpty()) {
            item(key = "recipes-header") { RecipeSourceHeader("Recipes") }
            itemsIndexed(items = myRecipes, key = { _, it -> "mine-recipe-${it.recipe.id}" }) { i, row -> RecipeLogRow(row, onLogRecipe, i, myRecipes.size) }
        }
    }
}

@Composable
private fun SourceHeader(text: String) {
    Eyebrow(text = text, modifier = Modifier.padding(start = IntakeSpacing.ScreenGutter + 4.dp, top = 16.dp, bottom = 8.dp))
}

/**
 * An imported restaurant menu item: name over restaurant and the numbers for one serving, the allergen chip (when the
 * person avoids anything) and a "+" that logs one serving after the allergen check. An item with no ingredient
 * information says so instead of "No allergens".
 */
@Composable
private fun MenuItemRow(row: MenuRow, onLog: (MenuRow) -> Unit, index: Int = 0, count: Int = 1) {
    val item = row.hit.item
    Column(Modifier.groupRow(index, count)) {
        GroupDivider(index)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = IntakeSpacing.ListRowMinHeight)
                .padding(start = IntakeSpacing.ListRowPadH, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 2)
                Text(
                    text = buildString {
                        append(item.restaurantName)
                        append(" · 1 serving")
                        item.kcal?.let { append(" · ${it.roundToInt()} kcal") }
                        item.proteinG?.let { append(" · ${it.roundToInt()} g protein") }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
                row.allergen?.let {
                    Spacer(Modifier.size(4.dp))
                    RowAllergenChip(it)
                }
            }
            RowPlus("Log one serving of ${item.name} from ${item.restaurantName}") { onLog(row) }
        }
    }
}

/** A bundled USDA food: name, category, and the numbers for its first listed portion. */
@Composable
private fun RefFoodRow(ref: RefFood, allergen: RowAllergen?, unit: PortionUnit, onChoose: (RefFood) -> Unit, index: Int = 0, count: Int = 1) {
    val portion = ref.defaultPortion
    Column(Modifier.groupRow(index, count)) {
        GroupDivider(index)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = IntakeSpacing.ListRowMinHeight)
                .clickable(onClickLabel = "Choose a portion of ${ref.name}") { onChoose(ref) }
                .padding(horizontal = IntakeSpacing.ListRowPadH, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // USDA foods have no photo, so no tile: the row stays light.
            Column(Modifier.weight(1f)) {
                Text(ref.name, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                Spacer(Modifier.size(2.dp))
                Text(
                    text = "${portion.label} (${unit.format(portion.grams)})" +
                        if (ref.category.isNotBlank()) " · ${ref.category}" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
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
                Text("${ref.kcalFor(portion.grams).roundToInt()}", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "kcal · ${ref.proteinFor(portion.grams).roundToInt()}g P",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
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

        is BarcodeState.Found -> Column(Modifier.padding(IntakeSpacing.ScreenGutter)) {
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
                modifier = Modifier.fillMaxWidth().heightIn(min = IntakeSpacing.TouchTarget),
            ) { Text("Choose a portion") }
            LineButton(
                onClick = onRescan,
                modifier = Modifier.fillMaxWidth().heightIn(min = IntakeSpacing.TouchTarget).padding(top = 8.dp),
            ) { Text("Scan another") }
        }

        is BarcodeState.Unknown -> Column(Modifier.padding(IntakeSpacing.ScreenGutter)) {
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
                modifier = Modifier.fillMaxWidth().heightIn(min = IntakeSpacing.TouchTarget),
            ) { Text("Add this product") }
            LineButton(
                onClick = onRescan,
                modifier = Modifier.fillMaxWidth().heightIn(min = IntakeSpacing.TouchTarget).padding(top = 8.dp),
            ) { Text("Scan again") }
        }

        is BarcodeState.LookupFailed -> Column(Modifier.padding(IntakeSpacing.ScreenGutter)) {
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
                modifier = Modifier.fillMaxWidth().heightIn(min = IntakeSpacing.TouchTarget),
            ) { Text("Try again") }
            LineButton(
                onClick = { manualFor = barcode.barcode },
                modifier = Modifier.fillMaxWidth().heightIn(min = IntakeSpacing.TouchTarget).padding(top = 8.dp),
            ) { Text("Add this product") }
            LineButton(
                onClick = onRescan,
                modifier = Modifier.fillMaxWidth().heightIn(min = IntakeSpacing.TouchTarget).padding(top = 8.dp),
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

    val kcalError = QuickAddLimits.problem(QuickAddField.KCAL, kcal)
    val proteinError = QuickAddLimits.problem(QuickAddField.PROTEIN, protein)
    val carbsError = QuickAddLimits.problem(QuickAddField.CARBS, carbs)
    val fatError = QuickAddLimits.problem(QuickAddField.FAT, fat)
    val anyError = listOf(kcalError, proteinError, carbsError, fatError).any { it != null }

    Column(
        modifier = Modifier.padding(IntakeSpacing.ScreenGutter).verticalScroll(rememberScrollState()),
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
            NumberField(kcal, { kcal = it }, "Calories", Modifier.weight(1f), error = kcalError)
            NumberField(protein, { protein = it }, "Protein g", Modifier.weight(1f), error = proteinError)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberField(carbs, { carbs = it }, "Carbs g", Modifier.weight(1f), error = carbsError)
            NumberField(fat, { fat = it }, "Fat g", Modifier.weight(1f), error = fatError)
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
            enabled = !anyError && kcal.toDoubleOrNull()?.let { it > 0 } == true,
            modifier = Modifier.fillMaxWidth().heightIn(min = IntakeSpacing.TouchTarget),
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
        modifier = Modifier.padding(IntakeSpacing.ScreenGutter).verticalScroll(rememberScrollState()),
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
            LineButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
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
    error: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { next -> onValueChange(next.filter { it.isDigit() || it == '.' }) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        isError = error != null,
        supportingText = if (error != null) {
            { Text(error) }
        } else {
            null
        },
        modifier = modifier,
    )
}

/**
 * One of the pills in the top row (`.pill`): `card` fill, ink when selected, 44 dp tall. [soft] is the meal picker, [ai] adds the
 * AI chip (Photo estimates by AI, so it carries the label), [trailing] a small chevron.
 */
@Composable
private fun SourcePill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    selected: Boolean,
    onClick: () -> Unit,
    trailing: androidx.compose.ui.graphics.vector.ImageVector? = null,
    soft: Boolean = false,
    ai: Boolean = false,
) {
    Surface(
        color = when {
            selected -> MaterialTheme.colorScheme.primary
            soft -> MaterialTheme.intake.soft
            else -> MaterialTheme.colorScheme.surface
        },
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        shape = IntakeShape.Pill,
        modifier = Modifier
            .heightIn(min = IntakeSpacing.TouchTarget)
            .clip(IntakeShape.Pill)
            .clickable(onClick = onClick)
            .semantics { role = Role.RadioButton; this.selected = selected },
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(IconSize.Small))
            Text(label, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            if (ai) IntakeChip("AI", tone = ChipTone.Ai, small = true)
            if (trailing != null) Icon(trailing, contentDescription = null, modifier = Modifier.size(IconSize.Small))
        }
    }
}
