package com.zeroindex.intake.ui.screens.hub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.unit.dp
import com.zeroindex.intake.ui.motion.MoreDetail
import com.zeroindex.intake.ui.motion.pressScale
import com.zeroindex.intake.ui.theme.IntakeShape
import com.zeroindex.intake.ui.components.EmptyState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zeroindex.intake.domain.model.MealSlot
import com.zeroindex.intake.ui.addfood.AddFoodSheet
import com.zeroindex.intake.ui.addfood.AddFoodTab
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Storage
import com.zeroindex.intake.ui.components.AccentNote
import com.zeroindex.intake.ui.components.SectionLine
import com.zeroindex.intake.ui.screens.plan.RecipeLibraryViewModel
import com.zeroindex.intake.ui.screens.plan.RecipeRow
import java.time.LocalTime

/** The meal slot a "Log food" from the hub starts in: by the clock, since the hub has no slot of its own. */
fun defaultMealSlot(hourOfDay: Int): MealSlot = when (hourOfDay) {
    in 4..10 -> MealSlot.BREAKFAST
    in 11..14 -> MealSlot.LUNCH
    in 15..20 -> MealSlot.DINNER
    else -> MealSlot.SNACK
}

/**
 * The Food tab root (SPEC-v3 IA): every food feature behind one short list. Log food opens the
 * Add food flow in place; the other rows open their own screens.
 */
@Composable
fun FoodHubScreen(
    contentPadding: PaddingValues,
    onOpenMealTemplates: () -> Unit,
    onOpenMealPlans: () -> Unit,
    onOpenRecipes: () -> Unit,
    onOpenEatingOut: () -> Unit,
    onOpenAllergens: () -> Unit,
    onOpenFoodScriptHelp: () -> Unit,
    onOpenAiSettings: () -> Unit,
    onOpenMyFoods: () -> Unit,
    onOpenFood: (String) -> Unit,
    onOpenFoodData: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var logging by rememberSaveable { mutableStateOf(false) }
    // Which tab Add food opens on: Search for "Log food", Barcode for "Scan a barcode".
    var scanning by rememberSaveable { mutableStateOf(false) }
    HubScreen(
        title = "Food",
        subtitle = "Log what you eat, plan your week, eat out safely.",
        contentPadding = contentPadding,
        modifier = modifier,
        tip = {
            com.zeroindex.intake.ui.onboarding.TabTip(
                key = com.zeroindex.intake.domain.onboarding.TipKey.FOOD,
                body = com.zeroindex.intake.ui.onboarding.TabTipCopy.FOOD,
                actionLabel = "Log food",
                onAction = { scanning = false; logging = true },
            )
        },
        before = {
            // The one obvious action on this screen.
            item(key = "log-food") {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Button(
                        onClick = { scanning = false; logging = true },
                        shape = IntakeShape.Button,
                        modifier = Modifier.fillMaxWidth().height(56.dp).pressScale(),
                    ) { Text("Log food") }
                    TextButton(
                        onClick = { scanning = true; logging = true },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Icon(Icons.Filled.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Scan a barcode instead")
                    }
                }
            }
        },
        items = listOf(
            HubItem(
                title = "Meal plans",
                description = "Plans of any length, shopping lists, import and export",
                icon = Icons.Filled.CalendarMonth,
                onClick = onOpenMealPlans,
            ),
            HubItem(
                title = "Recipes",
                description = "Your saved recipes and cook mode",
                icon = Icons.Filled.Restaurant,
                onClick = onOpenRecipes,
            ),
            HubItem(
                title = "Meal templates",
                description = "Saved meals that log in one tap, or from an NFC tag",
                icon = Icons.Filled.Nfc,
                onClick = onOpenMealTemplates,
            ),
            HubItem(
                title = "Eating out",
                description = "Plan B, saved options and restaurant menus",
                icon = Icons.Filled.Storefront,
                onClick = onOpenEatingOut,
            ),
            HubItem(
                title = "Allergens",
                description = "What you avoid, and the hidden-ingredient reference",
                icon = Icons.Filled.Warning,
                onClick = onOpenAllergens,
            ),
        ),
        after = {
            // Rarely needed, so folded away instead of lengthening the list.
            item(key = "more-food") {
                MoreDetail(title = "More food tools") {
                    HubList(
                        listOf(
                            HubItem(
                                title = "My foods",
                                description = "Every food on this phone: edit it or share it with Open Food Facts",
                                icon = Icons.Filled.Fastfood,
                                onClick = onOpenMyFoods,
                            ),
                            HubItem(
                                title = "Food database & credits",
                                description = "Offline USDA foods, Open Food Facts search and contributing, licences",
                                icon = Icons.Filled.Storage,
                                onClick = onOpenFoodData,
                            ),
                            HubItem(
                                title = "Food script help",
                                description = "Write a meal plan by hand, with the full reference",
                                icon = Icons.AutoMirrored.Filled.MenuBook,
                                onClick = onOpenFoodScriptHelp,
                            ),
                        ),
                    )
                }
            }
        },
    )
    if (logging) {
        val now = remember { System.currentTimeMillis() }
        AddFoodSheet(
            mealSlot = defaultMealSlot(LocalTime.now().hour),
            loggedAtMillis = now,
            onDismiss = { logging = false },
            onOpenAiSettings = onOpenAiSettings,
            initialTab = if (scanning) AddFoodTab.BARCODE else AddFoodTab.RECENTS,
            onOpenFood = { id ->
                logging = false
                onOpenFood(id)
            },
        )
    }
}

/** Food, Recipes: what you have saved, plus the optional AI finder. */
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
            items(recipes, key = { it.id }) { recipe -> RecipeRow(recipe, onClick = { onOpenRecipe(recipe.id) }) }
        },
    )
}

/** Food, Eating out: everything for when the plan does not apply. */
@Composable
fun EatingOutScreen(
    contentPadding: PaddingValues,
    onOpenPlanB: () -> Unit,
    onOpenMenuImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HubScreen(
        title = "Eating out",
        subtitle = "A safer choice when you cannot eat what you cooked.",
        contentPadding = contentPadding,
        modifier = modifier,
        items = listOf(
            HubItem(
                title = "Plan B",
                description = "Find a safer place to eat nearby. Saved options work offline",
                icon = Icons.Filled.RestaurantMenu,
                onClick = onOpenPlanB,
            ),
            HubItem(
                title = "Menu import",
                description = "Bring in restaurant menus, checked against your allergens",
                icon = Icons.Filled.Storefront,
                onClick = onOpenMenuImport,
            ),
        ),
    )
}

/** Food, Allergens: the profile you set and the reference you read. */
@Composable
fun AllergensScreen(
    contentPadding: PaddingValues,
    onOpenMine: () -> Unit,
    onOpenReference: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HubScreen(
        title = "Allergens",
        subtitle = "What you avoid, kept on this phone.",
        contentPadding = contentPadding,
        modifier = modifier,
        items = listOf(
            HubItem(
                title = "My allergens",
                description = "Pick what you avoid, add your own, and manage keywords and how strict to be",
                icon = Icons.Filled.Person,
                onClick = onOpenMine,
            ),
            HubItem(
                title = "Allergen reference",
                description = "The hidden-ingredient checklist and eat-out notes, offline",
                icon = Icons.Filled.Checklist,
                onClick = onOpenReference,
            ),
        ),
    )
}
