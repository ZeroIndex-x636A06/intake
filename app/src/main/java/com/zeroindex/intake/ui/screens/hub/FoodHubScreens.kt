package com.zeroindex.intake.ui.screens.hub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.zeroindex.intake.data.repository.MealPlanRepository
import com.zeroindex.intake.data.swap.SwapEntry
import com.zeroindex.intake.data.swap.SwapRepository
import com.zeroindex.intake.data.swap.SwapSource
import com.zeroindex.intake.domain.model.MealSlot
import com.zeroindex.intake.ui.allergen.AllergenChip
import com.zeroindex.intake.ui.components.EmptyState
import com.zeroindex.intake.ui.components.IntakeCard
import com.zeroindex.intake.ui.components.SectionLine
import com.zeroindex.intake.ui.motion.rememberStaggerGate
import com.zeroindex.intake.ui.motion.staggerIn
import com.zeroindex.intake.ui.screens.plan.RecipeLibraryViewModel
import com.zeroindex.intake.ui.screens.plan.RecipeRow
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
    val recipes by viewModel.recipes.collectAsStateWithLifecycle()
    val gate = rememberStaggerGate()
    HubScreen(
        title = "Recipes",
        subtitle = "Saved recipes log a serving in one tap.",
        contentPadding = contentPadding,
        modifier = modifier,
        items = listOf(
            HubItem(
                title = "Add a recipe",
                description = "Type in the name, ingredients and steps. Macros add up as you go",
                icon = Icons.Filled.Edit,
                onClick = onOpenNew,
            ),
            HubItem(
                title = "Import from a link",
                description = "Paste a recipe page. Read straight off the page, no AI",
                icon = Icons.Filled.Link,
                onClick = onOpenImport,
            ),
            HubItem(
                title = "Recipe Hunter",
                description = "Finds recipes that fit your macros and checks them for your allergens",
                icon = Icons.Filled.AutoAwesome,
                onClick = onOpenHunter,
                tag = "Optional AI",
            ),
        ),
        after = {
            item(key = "mine-head") { SectionLine("My recipes", note = "${recipes.size} saved") }
            if (recipes.isEmpty()) {
                item(key = "no-recipes") {
                    EmptyState(
                        title = "No recipes yet",
                        body = "Add one by hand or import one from a link. Saved recipes log a serving in one tap.",
                        action = {
                            Button(onClick = onOpenNew, modifier = Modifier.heightIn(min = 48.dp)) { Text("Add a recipe") }
                        },
                    )
                }
            }
            itemsIndexed(recipes, key = { _, r -> r.id }) { index, recipe ->
                RecipeRow(recipe, onClick = { onOpenRecipe(recipe.id) }, modifier = Modifier.staggerIn(gate, recipe.id, index))
            }
        },
    )
}

/** Saved eat-out options with today's allergen verdict on each, read once per change. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class EatingOutViewModel @Inject constructor(
    private val plans: MealPlanRepository,
    swap: SwapRepository,
) : ViewModel() {
    /** Null until the first read finishes. */
    val options: StateFlow<List<SwapEntry>?> = plans.observeEatOutOptions()
        .mapLatest { swap.savedEatOut(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun remove(id: String) { viewModelScope.launch { plans.deleteEatOutOption(id) } }
}

/**
 * Nutrition > Eating out: the options saved for when the plan does not apply (they work with no signal, and each carries
 * its allergen verdict) and the restaurant menu import. Searching for places nearby is not here: it is in Swap, on tap.
 */
@Composable
fun EatingOutScreen(
    contentPadding: PaddingValues,
    onOpenMenuImport: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EatingOutViewModel = hiltViewModel(),
) {
    val options by viewModel.options.collectAsStateWithLifecycle()
    var removing by remember { mutableStateOf<SwapEntry?>(null) }
    HubScreen(
        title = "Eating out",
        subtitle = "Saved options and restaurant menus. Nearby search is in Swap.",
        contentPadding = contentPadding,
        modifier = modifier,
        items = listOf(
            HubItem(
                title = "Menu import",
                description = "Bring in restaurant menus, checked against your allergens",
                icon = Icons.Filled.Storefront,
                onClick = onOpenMenuImport,
            ),
        ),
        after = {
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
                    IntakeCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(src?.option?.venueName ?: e.option.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
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
                            IconButton(onClick = { removing = e }) { Icon(Icons.Filled.Delete, contentDescription = "Remove ${e.option.title}") }
                        }
                        Column(Modifier.padding(top = 6.dp)) {
                            AllergenChip(e.option.allergen, label = if (e.option.allergen == com.zeroindex.intake.domain.swap.SwapAllergen.CLEAR) "No allergens found" else e.option.allergenReason)
                        }
                    }
                }
            }
        },
    )
    removing?.let { e ->
        val src = e.source as? SwapSource.EatOut
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove this saved option?") },
            text = { Text("${src?.option?.venueName ?: ""}: ${e.option.title}. It is hidden from Swap and this list. Nothing is deleted from your history.") },
            confirmButton = { Button(onClick = { removing = null; src?.option?.id?.let(viewModel::remove) }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } },
        )
    }
}
