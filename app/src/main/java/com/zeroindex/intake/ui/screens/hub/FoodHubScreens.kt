package com.zeroindex.intake.ui.screens.hub

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import com.zeroindex.intake.ui.components.ChipTone
import com.zeroindex.intake.ui.components.DangerButton
import com.zeroindex.intake.ui.components.GhostButton
import com.zeroindex.intake.ui.components.HeroCard
import com.zeroindex.intake.ui.components.IconCircleButton
import com.zeroindex.intake.ui.components.IconCircleStyle
import com.zeroindex.intake.ui.components.IconTile
import com.zeroindex.intake.ui.components.IconTileStyle
import com.zeroindex.intake.ui.components.IntakeChip
import com.zeroindex.intake.ui.components.SubCopy
import com.zeroindex.intake.ui.components.heroMuted
import com.zeroindex.intake.ui.motion.pressScale
import com.zeroindex.intake.ui.screens.plan.RecipeCard
import com.zeroindex.intake.ui.theme.IntakeIcons
import com.zeroindex.intake.ui.theme.IntakeShape
import com.zeroindex.intake.ui.theme.IntakeSpacing
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.zeroindex.intake.data.repository.MealPlanRepository
import com.zeroindex.intake.data.swap.SwapEntry
import com.zeroindex.intake.data.swap.SwapLogResult
import com.zeroindex.intake.data.swap.SwapRepository
import com.zeroindex.intake.data.swap.SwapSource
import com.zeroindex.intake.domain.model.MealSlot
import com.zeroindex.intake.ui.AppMessage
import com.zeroindex.intake.ui.AppMessages
import com.zeroindex.intake.ui.allergen.AllergenChip
import com.zeroindex.intake.ui.components.EmptyState
import com.zeroindex.intake.ui.components.IntakeCard
import com.zeroindex.intake.ui.components.SectionLine
import com.zeroindex.intake.ui.motion.rememberStaggerGate
import com.zeroindex.intake.ui.motion.staggerIn
import com.zeroindex.intake.ui.screens.plan.RecipeLibraryViewModel
import com.zeroindex.intake.ui.screens.plan.RecipeRow
import com.zeroindex.intake.ui.screens.today.SlotPickerSheet
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The meal slot a "Log food" from a screen with no slot of its own starts in: by the clock. */
fun defaultMealSlot(hourOfDay: Int): MealSlot = when (hourOfDay) {
    in 4..10 -> MealSlot.BREAKFAST
    in 11..14 -> MealSlot.LUNCH
    in 15..20 -> MealSlot.DINNER
    else -> MealSlot.SNACK
}

/** Nutrition > Recipes: what you have saved, plus the optional AI finder. */
@Composable
fun RecipesScreen(
    contentPadding: PaddingValues,
    onOpenRecipe: (String) -> Unit,
    onOpenHunter: () -> Unit,
    onOpenImport: () -> Unit,
    onOpenNew: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecipeLibraryViewModel = hiltViewModel(),
) {
    val loadedRecipes by viewModel.recipesOrNull.collectAsStateWithLifecycle()
    val recipes = loadedRecipes.orEmpty()
    val verdicts by viewModel.verdicts.collectAsStateWithLifecycle()
    val avoids by viewModel.avoidsAnything.collectAsStateWithLifecycle()
    val estimatedKcal by viewModel.estimatedKcal.collectAsStateWithLifecycle()
    val gate = rememberStaggerGate()
    // Two cards a row; one column when the text is large (font scale 1.5).
    val columns = if (LocalDensity.current.fontScale >= 1.3f) 1 else 2
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = hubPadding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(IntakeSpacing.StackGap),
    ) {
        item(key = "heading") { SubCopy("Saved recipes log a serving in one tap.") }
        // Recipe Hunter is the one dark card: the optional AI finder, labelled as AI.
        item(key = "hunter") {
            HeroCard(
                modifier = Modifier.clickable(role = Role.Button, onClickLabel = "Open Recipe Hunter", onClick = onOpenHunter),
                contentPadding = PaddingValues(IntakeSpacing.CardPadHero),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconTile(IntakeIcons.SearchPlus, style = IconTileStyle.Lime)
                    Spacer(Modifier.weight(1f))
                    IntakeChip("Optional AI", tone = ChipTone.Ai, icon = IntakeIcons.Sparkle, small = true)
                }
                Spacer(Modifier.height(10.dp))
                Text("Recipe Hunter", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Finds recipes that fit your macros and checks them for your allergens",
                    style = MaterialTheme.typography.bodyMedium,
                    color = heroMuted(),
                )
            }
        }
        item(key = "add-import") {
            Row(horizontalArrangement = Arrangement.spacedBy(IntakeSpacing.GridGap2), modifier = Modifier.height(IntrinsicSize.Max)) {
                RecipeActionTile(
                    "Add a recipe", "Type in the name, ingredients and steps. Macros add up as you go",
                    IntakeIcons.Pencil, onOpenNew, Modifier.weight(1f).fillMaxHeight(),
                )
                RecipeActionTile(
                    "Import from a link", "Paste a recipe link. Intake reads the recipe data on that page, with no AI",
                    IntakeIcons.Link, onOpenImport, Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
        item(key = "mine-head") { SectionLine("My recipes", note = if (loadedRecipes == null) "" else "${recipes.size} saved") }
        if (loadedRecipes != null && recipes.isEmpty()) {
            item(key = "no-recipes") {
                EmptyState(
                    title = "No recipes yet",
                    body = "Add one by hand or import one from a link. Saved recipes log a serving in one tap.",
                    icon = IntakeIcons.Book,
                    action = {
                        Button(onClick = onOpenNew) { Text("Add a recipe") }
                    },
                )
            }
        }
        itemsIndexed(recipes.chunked(columns), key = { _, row -> row.first().id }) { index, row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(IntakeSpacing.GridGap2),
                modifier = Modifier.height(IntrinsicSize.Max).staggerIn(gate, row.first().id, index),
            ) {
                row.forEach { recipe ->
                    RecipeCard(
                        recipe,
                        onClick = { onOpenRecipe(recipe.id) },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        allergen = verdicts[recipe.id] ?: com.zeroindex.intake.ui.addfood.RowAllergen.Checking.takeIf { avoids },
                        estimatedKcal = estimatedKcal[recipe.id],
                    )
                }
                if (row.size < columns) Spacer(Modifier.weight((columns - row.size).toFloat()))
            }
        }
    }
}

/** `.card` tile with an icon, a title and one line: the Add / Import pair above My recipes. */
@Composable
private fun RecipeActionTile(title: String, body: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    IntakeCard(
        modifier = modifier.pressScale().clickable(role = Role.Button, onClick = onClick),
        shape = IntakeShape.TileLarge,
        contentPadding = PaddingValues(IntakeSpacing.CardPadTile),
    ) {
        IconTile(icon)
        Spacer(Modifier.height(8.dp))
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Saved eat-out options with today's allergen verdict on each, read once per change. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class EatingOutViewModel @Inject constructor(
    private val plans: MealPlanRepository,
    private val swap: SwapRepository,
    private val messages: AppMessages,
) : ViewModel() {
    /** Null until the first read finishes. */
    val options: StateFlow<List<SwapEntry>?> = plans.observeEatOutOptions()
        .mapLatest { swap.savedEatOut(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun remove(id: String) { viewModelScope.launch { plans.deleteEatOutOption(id) } }

    /** A flagged saved option waiting on the standard warning sheet, with the meal it was picked for. */
    private val _pending = kotlinx.coroutines.flow.MutableStateFlow<Pair<com.zeroindex.intake.data.swap.PendingSwapWarning, MealSlot>?>(null)
    val pending: StateFlow<Pair<com.zeroindex.intake.data.swap.PendingSwapWarning, MealSlot>?> = _pending

    fun dismissPending() { _pending.value = null }

    /** "Log it anyway" on the warning sheet. */
    fun confirmPending(entries: List<SwapEntry>) {
        val (p, slot) = _pending.value ?: return
        _pending.value = null
        val entry = entries.firstOrNull { it.option.key == p.key } ?: return
        choose(entry, slot, override = true)
    }

    /**
     * Logs a saved option to [slot], through the same check Swap uses: a "contains" or "may contain" option opens the
     * standard warning sheet first and is logged only after "Log it anyway".
     */
    fun choose(entry: SwapEntry, slot: MealSlot, override: Boolean = false) {
        viewModelScope.launch {
            when (val r = swap.choose(entry, slot, override)) {
                is SwapLogResult.Blocked -> _pending.value =
                    com.zeroindex.intake.data.swap.PendingSwapWarning(entry.option.key, entry.option.title, swap.warningScan(entry)) to slot
                is SwapLogResult.NeedsWarning -> _pending.value =
                    com.zeroindex.intake.data.swap.PendingSwapWarning(entry.option.key, r.title, r.scan) to slot
                is SwapLogResult.Logged -> messages.post(AppMessage(r.text, actionLabel = "Undo", onAction = r.undo))
            }
        }
    }
}

/**
 * Nutrition > Eating out: the options saved for when the plan does not apply (they work with no signal, and each carries
 * its allergen verdict), the restaurant menu import, and "Find healthier options nearby" (imported menus first, then an
 * optional AI search on tap). Swap is still the main home of nearby search, ranked against the planned meal.
 */
@Composable
fun EatingOutScreen(
    contentPadding: PaddingValues,
    onOpenMenuImport: () -> Unit,
    onOpenAiSettings: () -> Unit,
    onOpenRadiusSetting: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EatingOutViewModel = hiltViewModel(),
) {
    val healthierBlock = rememberHealthierBlock(onOpenAiSettings, onOpenRadiusSetting)
    val options by viewModel.options.collectAsStateWithLifecycle()
    var removing by remember { mutableStateOf<SwapEntry?>(null) }
    var choosing by remember { mutableStateOf<SwapEntry?>(null) }
    HubScreen(
        subtitle = "Saved options, restaurant menus and healthier options near you.",
        contentPadding = contentPadding,
        modifier = modifier,
        items = listOf(
            HubItem(
                title = "Menu import",
                description = "Bring in restaurant menus, checked against your allergens",
                icon = IntakeIcons.MapPin,
                onClick = onOpenMenuImport,
            ),
        ),
        after = {
            healthierBlock()
            item(key = "saved-head") { SectionLine("Saved eat-out options", note = "works offline") }
            val list = options
            when {
                list == null -> item(key = "loading") { Text("Reading your saved options…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                list.isEmpty() -> item(key = "none") {
                    EmptyState(
                        title = "Nothing saved yet",
                        body = "Save an option from Swap's nearby search, or import a plan that has eat-out options. They stay on this phone and work with no signal.",
                    )
                }
                else -> itemsIndexed(list, key = { _, e -> e.option.key }) { _, e ->
                    val src = e.source as? SwapSource.EatOut
                    // The card logs the option to a meal you pick; the bin icon removes it and stops there.
                    IntakeCard(Modifier.clickable(onClickLabel = "Log ${e.option.title}") { choosing = e }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(src?.option?.venueName ?: e.option.title, style = MaterialTheme.typography.titleMedium)
                                Text(e.option.title, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "${e.option.macros.kcal.roundToInt()} kcal · P ${e.option.macros.protein.roundToInt()} · C ${e.option.macros.carbs.roundToInt()} · F ${e.option.macros.fat.roundToInt()} · estimate",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                src?.option?.cautionNote?.takeIf { it.isNotBlank() }?.let {
                                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            IconCircleButton(IntakeIcons.Trash, "Remove ${e.option.title}", onClick = { removing = e }, style = IconCircleStyle.Soft)
                        }
                        Column(Modifier.padding(top = 6.dp)) {
                            AllergenChip(e.option.allergen, label = if (e.option.allergen == com.zeroindex.intake.domain.swap.SwapAllergen.CLEAR) "Fits your allergens" else e.option.allergenReason)
                        }
                    }
                }
            }
        },
    )
    choosing?.let { e ->
        // The option's name (the venue, as the card headline), not its description of what to order (QA Q5a-19).
        SlotPickerSheet(
            title = "Log ${(e.source as? SwapSource.EatOut)?.option?.venueName ?: e.option.title} to",
            onDismiss = { choosing = null },
            onPick = { slot -> choosing = null; viewModel.choose(e, slot) },
        )
    }
    val pendingWarning by viewModel.pending.collectAsStateWithLifecycle()
    pendingWarning?.let { (p, _) ->
        com.zeroindex.intake.ui.allergen.AllergenWarningSheet(
            foodName = p.title, scan = p.scan,
            onDismiss = viewModel::dismissPending, onLogAnyway = { viewModel.confirmPending(options.orEmpty()) },
        )
    }
    removing?.let { e ->
        val src = e.source as? SwapSource.EatOut
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove this saved option?") },
            text = { Text("${src?.option?.venueName ?: ""}: ${e.option.title}. It is hidden from Swap and this list. Nothing is deleted from your history.") },
            confirmButton = { DangerButton(onClick = { removing = null; src?.option?.id?.let(viewModel::remove) }) { Text("Remove") } },
            dismissButton = { GhostButton(onClick = { removing = null }) { Text("Cancel") } },
        )
    }
}
