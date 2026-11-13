@file:OptIn(ExperimentalMaterial3Api::class)

package com.zeroindex.intake.ui.screens.drinks

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zeroindex.intake.data.db.entity.FoodEntity
import com.zeroindex.intake.domain.drinks.DrinkCategory
import com.zeroindex.intake.domain.drinks.DrinkSize
import com.zeroindex.intake.domain.drinks.DrinkSizes
import com.zeroindex.intake.domain.units.VolumeUnit
import com.zeroindex.intake.ui.addfood.RowAllergen
import com.zeroindex.intake.ui.addfood.RowAllergenChip
import com.zeroindex.intake.ui.allergen.AllergenWarningSheet
import com.zeroindex.intake.ui.components.ChoiceChip
import com.zeroindex.intake.ui.components.EmptyState
import com.zeroindex.intake.ui.components.IntakeCard
import com.zeroindex.intake.ui.components.SectionLine
import com.zeroindex.intake.ui.screens.today.components.icon
import com.zeroindex.intake.ui.theme.IntakeShape
import com.zeroindex.intake.ui.theme.WaterAccent
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The Drinks page, one calm list: today's total against the water target in a compact strip; one search field (over
 * every source Log food uses: this phone, the offline USDA database, Open Food Facts, the USDA API; drinks only);
 * category chips that filter the list; "My drinks" and "Recent" as quick-tap cards (a tap logs the last size, a long
 * press opens the options); the drinks; and today's drinks with a remove. Choosing a drink opens one bottom sheet with
 * the size chips plus a custom amount, then Customise and Log. A drink is a food: logging writes the water entry and the
 * food-log entry (Drinks slot) together, and the allergen check runs first. Settings for all of it (target, sizes, drink
 * values, units) live in Settings > Water and drinks, never here.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DrinksScreen(
    contentPadding: PaddingValues,
    onOpenFood: (String) -> Unit,
    /** Opens the customise screen for this drink (or edits it, for a saved "My drink"). */
    onCustomise: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** Words carried over from Log food ("coke"), so the search starts with them. */
    initialQuery: String = "",
    viewModel: DrinksViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val unit = state.volumeUnit
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    LaunchedEffect(query) { viewModel.setQuery(query) }
    val search = state.search
    val listState = rememberLazyListState()

    val selected = state.selected
    val sizes = remember(unit, state.presets) { DrinkSizes.buttons(unit, state.presets.map { DrinkSize(unit.format(it.volumeMl), it.volumeMl) }) }
    val time = remember { DateTimeFormatter.ofPattern("h:mm a") }
    val cat = state.category

    // The quick cards follow the category chip: a drink of another category is hidden, not removed.
    fun inCategory(f: FoodEntity) = cat == null || f.drinkCategory == cat.name
    val mine = state.myDrinks.filter { inCategory(it.food) }
    val mineIds = state.myDrinks.map { it.food.id }.toSet()
    val recent = state.recent.filter { inCategory(it.row.food) && it.row.food.id !in mineIds }
    val lastSizes = state.recent.associate { it.row.food.id to it.lastMl }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 4.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
            start = 16.dp,
            end = 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "summary") { SummaryStrip(state, unit) }

        item(key = "search") {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search drinks") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, contentDescription = "Clear the search") } },
                singleLine = true,
                shape = IntakeShape.Field,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (query.isBlank()) {
            item(key = "categories") {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChip("All", cat == null, { viewModel.selectCategory(null) })
                    DrinkCategory.entries.forEach { c -> ChoiceChip(c.label, c == cat, { viewModel.selectCategory(c) }) }
                }
            }

            if (mine.isNotEmpty()) {
                item(key = "my") { QuickRow("My drinks", mine.map { QuickItem(it.food, lastSizes[it.food.id] ?: it.food.servingSizeGrams) }, unit, viewModel::log, viewModel::select) }
            }
            if (recent.isNotEmpty()) {
                item(key = "recent") { QuickRow("Recent", recent.map { QuickItem(it.row.food, it.lastMl) }, unit, viewModel::log, viewModel::select) }
            }
            if (mine.isNotEmpty() || recent.isNotEmpty()) {
                item(key = "hint") {
                    Text("Tap to log the last size. Press and hold for options.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (state.drinks.isEmpty()) {
                item(key = "none") {
                    EmptyState(
                        title = if (cat == null) "No drinks yet" else "No ${cat.label.lowercase()} drinks yet",
                        body = "Drinks you find with the search, or scan, land in their category here.",
                    )
                }
            } else {
                items(state.drinks, key = { "cat-${it.food.id}" }) { row ->
                    DrinkRow(
                        food = row.food,
                        allergen = row.allergen,
                        selected = row.food.id == selected?.id,
                        onSelect = { viewModel.select(row.food.id) },
                        onInfo = { onOpenFood(row.food.id) },
                    )
                }
            }
        } else {
            fun isChosen(row: DrinkSearchRow) = state.selectedFromSearch && selected != null &&
                (row.food.id == selected.id || (row.food.barcode != null && row.food.barcode == selected.barcode))
            val section: androidx.compose.foundation.lazy.LazyListScope.(String, List<DrinkSearchRow>) -> Unit = { title, rows ->
                if (rows.isNotEmpty()) {
                    item(key = "h-$title") { SectionLine(title) }
                    items(rows, key = { it.key }) { row ->
                        DrinkRow(
                            food = row.food,
                            allergen = row.allergen,
                            selected = isChosen(row),
                            onSelect = { viewModel.choose(row) },
                            // The nutrition label is for a drink that is on the phone already.
                            onInfo = if (row.origin == DrinkOrigin.MINE || row.origin == DrinkOrigin.USDA_ONLINE) ({ onOpenFood(row.food.id) }) else null,
                        )
                    }
                }
            }
            section("ON THIS PHONE", search.mine)
            section("FOOD DATABASE (USDA, WORKS OFFLINE)", search.usda)
            when (search.offStatus) {
                OffDrinksStatus.HIDDEN -> Unit
                OffDrinksStatus.LOADING -> item(key = "off-loading") {
                    Text("Searching Open Food Facts for drinks…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OffDrinksStatus.DONE -> section("PACKAGED DRINKS (OPEN FOOD FACTS)", search.off)
                OffDrinksStatus.FAILED -> item(key = "off-failed") {
                    Text("Open Food Facts could not be reached. The sources above still work.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (search.hasUsdaKey) {
                item(key = "usda-online") {
                    when (val s = search.onlineStatus) {
                        OnlineDrinksStatus.Idle -> OutlinedButton(onClick = viewModel::searchUsdaOnline, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Also search USDA online (your key)") }
                        OnlineDrinksStatus.Loading -> Text("Searching USDA…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OnlineDrinksStatus.Done -> Unit
                        OnlineDrinksStatus.Nothing -> Text("USDA online had no drinks for that.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        is OnlineDrinksStatus.Failed -> Text(s.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                section("USDA ONLINE (YOUR KEY), SAVED TO THIS PHONE", search.online)
            }
            // The words on screen have been searched (not still waiting on the debounce) and nothing is a drink.
            if (search.query.trim() == query.trim() && search.isEmpty && search.offStatus != OffDrinksStatus.LOADING) {
                item(key = "search-none") {
                    EmptyState(
                        title = "No drink matches",
                        body = "Only drinks are listed here. Foods, soups and sauces are in Log food.",
                    )
                }
            }
        }

        item(key = "today-head") { SectionLine("LOGGED TODAY", note = if (state.entries.isEmpty()) null else "${state.entries.size} drink${if (state.entries.size == 1) "" else "s"}") }
        if (state.entries.isEmpty()) {
            item(key = "today-none") { Text("Nothing logged yet today.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(state.entries, key = { it.log.id }) { e ->
                IntakeCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(e.log.drinkType.icon(), contentDescription = null, tint = WaterAccent, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(e.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                buildString {
                                    append(unit.format(e.log.volumeMl))
                                    append(" · ").append(time.format(Instant.ofEpochMilli(e.log.loggedAt).atZone(ZoneId.systemDefault())))
                                    e.kcal?.let { append(" · ${it.roundToInt()} kcal") }
                                    if (e.log.caffeineMg >= 1) append(" · ${e.log.caffeineMg.roundToInt()} mg caffeine")
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { viewModel.remove(e.log.id) }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Filled.RemoveCircleOutline, contentDescription = "Remove ${e.name}, ${unit.format(e.log.volumeMl)}")
                        }
                    }
                }
            }
        }
    }

    if (selected != null) {
        val allergen = (state.myDrinks.map { it.food.id to it.allergen } + state.recent.map { it.row.food.id to it.row.allergen } +
            state.drinks.map { it.food.id to it.allergen } + (search.mine + search.usda + search.off + search.online).map { it.food.id to it.allergen })
            .firstOrNull { it.first == selected.id }?.second
        DrinkSheet(
            drink = selected,
            allergen = allergen,
            unit = unit,
            sizes = sizes,
            startMl = lastSizes[selected.id] ?: selected.servingSizeGrams.takeIf { it > 0 } ?: sizes.first().ml,
            onDismiss = viewModel::clearSelection,
            onCustomise = { id -> viewModel.clearSelection(); onCustomise(id) },
            onDelete = { viewModel.deleteMyDrink(selected); viewModel.clearSelection() },
            onLog = { ml -> viewModel.log(selected, ml); viewModel.clearSelection() },
        )
    }

    state.pending?.let { p ->
        AllergenWarningSheet(
            foodName = p.food.name,
            scan = p.scan,
            ingredientText = p.food.allergenTags.takeIf { it.isNotBlank() },
            foodDetail = "${unit.format(p.ml)} · ${(p.food.kcalPer100g * p.ml / 100.0).roundToInt()} kcal",
            onDismiss = viewModel::dismissPending,
            onLogAnyway = viewModel::confirmPending,
        )
    }
}

private fun kcalOf(food: FoodEntity, ml: Double): Int = (food.kcalPer100g * ml / 100.0).roundToInt()

/** Today against the target in one compact strip, with the day's caffeine, sugar, calories and alcohol on one line under it. */
@Composable
private fun SummaryStrip(state: DrinksUiState, unit: VolumeUnit) {
    val day = state.day
    IntakeCard {
        if (day == null) {
            Text("Reading today's drinks…", style = MaterialTheme.typography.titleMedium)
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(unit.formatNumber(day.consumedMl), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                Text(" of ${unit.format(day.target.totalMl)}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 2.dp).weight(1f))
                Text("${(day.progress * 100).roundToInt().coerceAtLeast(0)}%", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
            LinearProgressIndicator(
                progress = { day.progress.coerceIn(0f, 1f) },
                color = WaterAccent,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 6.dp),
            )
            Text(
                "${day.totals.caffeineMg.roundToInt()} mg caffeine · ${state.sugarG.roundToInt()} g sugar · ${state.kcal.roundToInt()} kcal · " +
                    "%.1f".format(java.util.Locale.US, day.totals.standardDrinks).removeSuffix(".0") + " std alcohol",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
            day.notes.forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp)) }
        }
    }
}

private data class QuickItem(val food: FoodEntity, val ml: Double)

/** A short label over a row of quick-tap cards: one tap logs the card's size, a long press opens the options. */
@Composable
private fun QuickRow(
    title: String,
    items: List<QuickItem>,
    unit: VolumeUnit,
    onLog: (FoodEntity, Double) -> Unit,
    onOptions: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items.forEach { QuickCard(it, unit, onLog = { onLog(it.food, it.ml) }, onOptions = { onOptions(it.food.id) }) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuickCard(item: QuickItem, unit: VolumeUnit, onLog: () -> Unit, onOptions: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        color = scheme.surface,
        shape = IntakeShape.Card,
        border = BorderStroke(1.dp, scheme.outlineVariant),
        modifier = Modifier
            .width(156.dp)
            .heightIn(min = 64.dp)
            .combinedClickable(onClick = onLog, onLongClick = onOptions, onClickLabel = "Log ${unit.format(item.ml)} of ${item.food.name}", onLongClickLabel = "Options"),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.Center) {
            Text(item.food.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${unit.format(item.ml)} · ${kcalOf(item.food, item.ml)} kcal",
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** One drink, from the list or the search. Drinks are liquids: its numbers read per 100 ml, whatever the source. */
@Composable
private fun DrinkRow(food: FoodEntity, allergen: RowAllergen?, selected: Boolean, onSelect: () -> Unit, onInfo: (() -> Unit)?) {
    val f = food
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .selectable(selected = selected, role = Role.Button, onClick = onSelect)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
            Text(f.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            Text(
                buildString {
                    f.brand?.takeIf { it.isNotBlank() }?.let { append("$it · ") }
                    append("${f.kcalPer100g.roundToInt()} kcal / 100 ml")
                    if ((f.caffeineMgPer100ml ?: 0.0) > 0) append(" · ${f.caffeineMgPer100ml!!.roundToInt()} mg caffeine")
                    if (f.sugarPer100g >= 0.5) append(" · ${f.sugarPer100g.roundToInt()} g sugar")
                    if ((f.abvPercent ?: 0.0) > 0) append(" · ${f.abvPercent!!.roundToInt()}% alcohol")
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            allergen?.let { a -> Box(Modifier.padding(top = 4.dp)) { RowAllergenChip(a) } }
        }
        if (onInfo != null) {
            IconButton(onClick = onInfo, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.Info, contentDescription = "Nutrition label for ${f.name}", modifier = Modifier.size(20.dp))
            }
        }
    }
}

/**
 * The one sheet a chosen drink opens: its size chips plus a custom amount, then Customise (Edit for a saved drink) and Log.
 * What the chosen size comes to (calories, sugar, caffeine) is shown above the buttons.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DrinkSheet(
    drink: FoodEntity,
    allergen: RowAllergen?,
    unit: VolumeUnit,
    sizes: List<DrinkSize>,
    startMl: Double,
    onDismiss: () -> Unit,
    onCustomise: (String) -> Unit,
    onDelete: () -> Unit,
    onLog: (Double) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var ml by remember(drink.id) { mutableStateOf(startMl) }
    var custom by remember(drink.id) { mutableStateOf(false) }
    var text by remember(drink.id) { mutableStateOf("") }
    val saved = drink.customDrink != null
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(drink.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            allergen?.let { RowAllergenChip(it) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                sizes.forEach { s -> ChoiceChip(s.label, !custom && abs(ml - s.ml) < 1.0, { custom = false; ml = s.ml }) }
                ChoiceChip("Custom amount", custom, { custom = true })
            }
            if (custom) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { v ->
                        text = v.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(7)
                        text.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 && it.isFinite() }?.let { ml = DrinkSizes.toMl(unit, it) }
                    },
                    label = { Text("Amount (${unit.label})") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = IntakeShape.Field,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(
                buildString {
                    append(unit.format(ml)).append(" · ").append(kcalOf(drink, ml)).append(" kcal")
                    if (drink.sugarPer100g * ml / 100.0 >= 0.5) append(" · ${(drink.sugarPer100g * ml / 100.0).roundToInt()} g sugar")
                    val caffeine = (drink.caffeineMgPer100ml ?: 0.0) * ml / 100.0
                    if (caffeine >= 1) append(" · ${caffeine.roundToInt()} mg caffeine")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { onCustomise(drink.id) }, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text(if (saved) "Edit" else "Customise") }
                Button(onClick = { onLog(ml) }, enabled = ml > 0, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("Log") }
            }
            if (saved) {
                TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Delete this drink") }
            }
        }
    }
}
