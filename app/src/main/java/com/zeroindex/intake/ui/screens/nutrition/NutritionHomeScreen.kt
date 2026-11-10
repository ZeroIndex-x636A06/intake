package com.zeroindex.intake.ui.screens.nutrition

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zeroindex.intake.data.db.entity.AllergenStatus
import com.zeroindex.intake.domain.nutrition.DashRow
import com.zeroindex.intake.domain.nutrition.DashboardModel
import com.zeroindex.intake.domain.nutrition.NutritionDashboard
import com.zeroindex.intake.domain.swap.SwapAllergen
import com.zeroindex.intake.ui.addfood.slotWord
import com.zeroindex.intake.ui.allergen.AllergenChip
import com.zeroindex.intake.ui.allergen.AllergenStrip
import com.zeroindex.intake.ui.components.ActionTile
import com.zeroindex.intake.ui.components.AnimatedBar
import com.zeroindex.intake.ui.components.EmptyState
import com.zeroindex.intake.ui.components.Eyebrow
import com.zeroindex.intake.ui.components.IntakeCard
import com.zeroindex.intake.ui.components.Segmented
import com.zeroindex.intake.ui.components.rememberSweepFraction
import com.zeroindex.intake.ui.motion.MoreDetail
import com.zeroindex.intake.ui.onboarding.TabTip
import com.zeroindex.intake.ui.onboarding.TabTipCopy
import com.zeroindex.intake.domain.onboarding.TipKey
import com.zeroindex.intake.ui.screens.today.NextMealUi
import com.zeroindex.intake.ui.theme.CarbAccent
import com.zeroindex.intake.ui.theme.FatAccent
import com.zeroindex.intake.ui.theme.IntakeShape
import com.zeroindex.intake.ui.theme.ProteinAccent
import com.zeroindex.intake.ui.theme.WaterAccent
import kotlin.math.roundToInt

/** Where each control on the Nutrition tab leads. Each is a different screen; nothing here configures anything. */
class NutritionActions(
    val onOpenAllergens: () -> Unit,
    /** Opens Log food on the named mode (an `AddFoodTab` name) for the meal slot named (a `MealSlot` name). */
    val onLogFood: (mode: String, slot: String) -> Unit,
    val onSwap: (slot: String?) -> Unit,
    val onOpenDrinks: () -> Unit,
    val onOpenMealPlans: () -> Unit,
    val onOpenRecipes: () -> Unit,
    val onOpenMealTemplates: () -> Unit,
    val onOpenEatingOut: () -> Unit,
)

/**
 * The Nutrition tab root (IA 5a, Option A): the allergen strip; Search, Scan and Photo; today's planned meal with Swap and
 * Log it; the nutrition dashboard (calorie ring, macros, fibre, saturated fat, sugar, salt or sodium, and "More nutrients"),
 * where a value no logged food listed reads "not listed" and never 0; the Drinks row; and the four sections Plan, Recipes,
 * Meals and Eating out. The content clears the status bar and the pill through [contentPadding].
 */
@Composable
fun NutritionHomeScreen(
    contentPadding: PaddingValues,
    actions: NutritionActions,
    modifier: Modifier = Modifier,
    viewModel: NutritionHomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var section by rememberSaveable { mutableIntStateOf(0) }
    val slot = viewModel.currentSlot().name

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 4.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
            start = 16.dp,
            end = 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "strip") { AllergenStrip(onClick = actions.onOpenAllergens) }

        item(key = "tip") {
            TabTip(key = TipKey.FOOD, body = TabTipCopy.FOOD, actionLabel = "Log food", onAction = { actions.onLogFood("SEARCH", slot) })
        }

        item(key = "tiles") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionTile("Search", Icons.Filled.Search, { actions.onLogFood("SEARCH", slot) }, Modifier.weight(1f))
                ActionTile("Scan", Icons.Filled.QrCodeScanner, { actions.onLogFood("BARCODE", slot) }, Modifier.weight(1f))
                ActionTile("Photo", Icons.Filled.CameraAlt, { actions.onLogFood("PHOTO", slot) }, Modifier.weight(1f))
            }
        }

        item(key = "planned") {
            PlannedMealCard(
                next = state.next,
                ready = state.ready,
                onSwap = actions.onSwap,
                onLogIt = viewModel::logNextMeal,
                onOpenPlan = actions.onOpenMealPlans,
                fallbackSlot = slot,
            )
        }

        item(key = "dashboard") { DashboardCard(state.dashboard, state.ready, state.hydration, state.settings.volumeUnit) }

        item(key = "drinks") {
            val day = state.hydration
            val unit = state.settings.volumeUnit
            NavCard(
                title = "Drinks",
                body = if (day == null) {
                    "Log a drink, with sizes and categories"
                } else {
                    "${unit.formatNumber(day.consumedMl)} of ${unit.format(day.target.totalMl)} today" +
                        (state.drinkNames.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "")
                },
                icon = { Icon(Icons.Filled.WaterDrop, contentDescription = null, tint = WaterAccent) },
                onClick = actions.onOpenDrinks,
            )
        }

        item(key = "switch") {
            Segmented(listOf("Plan", "Recipes", "Meals", "Eating out"), section, { section = it }, Modifier.fillMaxWidth())
        }
        item(key = "section") {
            val s = state.sections
            when (section) {
                0 -> NavCard(
                    title = s.planName ?: "Meal plan",
                    body = if (s.planName == null) {
                        "No plan yet. Import one, write one or start from a template. Plans of any length, shopping lists, import and export."
                    } else {
                        listOfNotNull(s.weekLine, s.weekDish?.let { "this week: $it" }, "shopping list, import and export").joinToString(" · ")
                    },
                    onClick = actions.onOpenMealPlans,
                )
                1 -> NavCard(
                    title = "Recipes",
                    body = if (s.recipeCount == 0) {
                        "No recipes yet. Add one, import one from a link or let Recipe Hunter find one."
                    } else {
                        "${s.recipeCount} saved · ${s.recipeNames.joinToString(", ")}" + if (s.recipeCount > 3) " and more" else ""
                    },
                    onClick = actions.onOpenRecipes,
                )
                2 -> NavCard(
                    title = "Meals",
                    body = if (s.templateCount == 0) {
                        "No saved meals yet. Save a meal you eat often and log it in one tap, or from an NFC tag."
                    } else {
                        "${s.templateCount} saved · ${s.templateNames.joinToString(", ")}" + if (s.templateCount > 3) " and more" else ""
                    },
                    onClick = actions.onOpenMealTemplates,
                )
                else -> NavCard(
                    title = "Eating out",
                    body = (if (s.eatOutCount == 0) "No saved options yet. " else "${s.eatOutCount} saved option${if (s.eatOutCount == 1) "" else "s"}. ") +
                        "Saved options and restaurant menus. Nearby search is in Swap.",
                    onClick = actions.onOpenEatingOut,
                )
            }
        }
    }
}

@Composable
private fun NavCard(title: String, body: String, onClick: () -> Unit, icon: (@Composable () -> Unit)? = null) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface,
        shape = IntakeShape.Card,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            if (icon != null) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = IntakeShape.Tile, modifier = Modifier.size(48.dp)) {
                    Box(contentAlignment = Alignment.Center) { icon() }
                }
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PlannedMealCard(
    next: NextMealUi?,
    ready: Boolean,
    fallbackSlot: String,
    onSwap: (String?) -> Unit,
    onLogIt: (NextMealUi) -> Unit,
    onOpenPlan: () -> Unit,
) {
    IntakeCard {
        if (next == null) {
            Eyebrow("Today's ${slotWord(com.zeroindex.intake.domain.model.MealSlot.valueOf(fallbackSlot)).lowercase()}", color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.size(8.dp))
            if (!ready) {
                Text("Reading your plan…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text("Nothing planned for now", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                Text(
                    "A meal plan puts the day's meal here with Log it. Swap also finds something else when you cannot eat it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onOpenPlan, shape = IntakeShape.Button, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("Open meal plans") }
                    OutlinedButton(onClick = { onSwap(fallbackSlot) }, shape = IntakeShape.Button, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                        Icon(Icons.Filled.SwapHoriz, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Swap")
                    }
                }
            }
            return@IntakeCard
        }
        val m = next.content.macros.perServing
        Eyebrow("Today's ${slotWord(next.slot).lowercase()}", color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.size(8.dp))
        Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), shape = IntakeShape.InnerCard, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text("${slotWord(next.slot)} · planned".uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    val verdict = when (next.content.allergenStatus) {
                        AllergenStatus.CONTAINS -> SwapAllergen.CONTAINS
                        AllergenStatus.MAY_CONTAIN -> SwapAllergen.MAY_CONTAIN
                        AllergenStatus.CHECK_LABEL, AllergenStatus.UNKNOWN -> SwapAllergen.CHECK_LABEL
                        else -> SwapAllergen.CLEAR
                    }
                    AllergenChip(verdict, next.content.allergenTerms.firstOrNull())
                }
                Text(next.content.recipe.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 4.dp))
                Text(
                    if (next.content.macros.isComplete) {
                        "${m.kcal.roundToInt()} kcal · P ${m.protein.roundToInt()} · C ${m.carbs.roundToInt()} · F ${m.fat.roundToInt()}"
                    } else {
                        "Macros incomplete. Open the plan to confirm."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onSwap(next.slot.name) }, shape = IntakeShape.Button, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                        Icon(Icons.Filled.SwapHoriz, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Swap")
                    }
                    Button(
                        onClick = { if (next.content.macros.isComplete) onLogIt(next) else onOpenPlan() },
                        shape = IntakeShape.Button,
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                    ) { Text(if (next.content.macros.isComplete) "Log it" else "Open plan") }
                }
            }
        }
    }
}

@Composable
private fun DashboardCard(
    model: DashboardModel?,
    ready: Boolean,
    hydration: com.zeroindex.intake.data.repository.HydrationDay?,
    unit: com.zeroindex.intake.domain.units.VolumeUnit,
) {
    IntakeCard {
        if (model == null || !ready) {
            Text("Reading today…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@IntakeCard
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            CalorieRingView(model)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val colors = listOf(ProteinAccent, CarbAccent, FatAccent)
                model.macros.forEachIndexed { i, row -> MacroBar(row, colors[i]) }
            }
        }
        Spacer(Modifier.size(12.dp))
        // Fibre, saturated fat, sugar and salt or sodium: reference limits where a limit exists, "not listed" when unknown.
        model.others.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                pair.forEach { row -> Box(Modifier.weight(1f)) { SmallBar(row) } }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        if (model.entries == 0) {
            Text("Nothing logged yet today. Search, scan or photograph what you eat.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        }
        model.notes.forEach {
            Text("$it (that total is a floor).", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
        }
        Text(
            "Fibre, saturated fat and salt are measured against common reference limits, not a personal target.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
        MoreDetail(title = "More nutrients") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                val t = hydration?.totals
                ValueLine("Caffeine from drinks", t?.let { "${it.caffeineMg.roundToInt()} mg" } ?: "not listed")
                ValueLine("Alcohol from drinks", t?.let { "%.1f".format(java.util.Locale.US, it.standardDrinks).removeSuffix(".0") + " standard drinks" } ?: "not listed")
                hydration?.let { ValueLine("Water counted", "${unit.format(it.consumedMl)} of ${unit.format(it.target.totalMl)}") }
                Text(
                    "Vitamins and minerals: not listed. Intake keeps energy, the macros, fibre, sugar, saturated fat and sodium for every food, so it cannot add them up yet.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ValueLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CalorieRingView(model: DashboardModel) {
    val c = model.calories
    val sweep by rememberSweepFraction(c.fraction)
    val track = MaterialTheme.colorScheme.surfaceVariant
    val color = if (c.over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val big = when {
        c.left == null -> "${c.eaten}"
        c.over -> "${-c.left}"
        else -> "${c.left}"
    }
    val small = when {
        c.left == null -> "kcal eaten"
        c.over -> "kcal over"
        else -> "kcal left"
    }
    Box(
        Modifier.size(132.dp).semantics { contentDescription = "$big $small" + (c.target?.let { ", of $it, ${c.eaten} eaten" } ?: "") },
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(progress = { 1f }, color = track, strokeWidth = 14.dp, modifier = Modifier.size(132.dp), trackColor = Color.Transparent)
        CircularProgressIndicator(progress = { sweep }, color = color, strokeWidth = 14.dp, modifier = Modifier.size(132.dp), trackColor = Color.Transparent)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(big, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
            Text(small, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MacroBar(row: DashRow, color: Color) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f), shape = IntakeShape.Tile) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(row.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text("${row.value?.roundToInt() ?: 0}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = color)
                Text(" / ${row.target?.roundToInt() ?: "—"} g", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 1.dp))
            }
            Spacer(Modifier.size(5.dp))
            AnimatedBar(row.fraction, color, MaterialTheme.colorScheme.surfaceVariant, height = 6.dp)
        }
    }
}

@Composable
private fun SmallBar(row: DashRow) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(row.label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            val listed = row.listed
            Text(
                NutritionDashboard.valueText(row),
                style = if (listed) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (listed) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.size(4.dp))
        AnimatedBar(row.fraction, CarbAccent.copy(alpha = 0.7f), MaterialTheme.colorScheme.surfaceVariant, height = 5.dp)
    }
}
