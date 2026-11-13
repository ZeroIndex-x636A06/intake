package com.zeroindex.intake.ui.screens.nutrition

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zeroindex.intake.data.db.entity.AllergenStatus
import com.zeroindex.intake.domain.model.MealSlot
import com.zeroindex.intake.domain.nutrition.DashRow
import com.zeroindex.intake.domain.nutrition.DashboardModel
import com.zeroindex.intake.domain.nutrition.MacroRingSplit
import com.zeroindex.intake.domain.nutrition.MacroShares
import com.zeroindex.intake.domain.nutrition.NutritionDashboard
import com.zeroindex.intake.domain.nutrition.RingLabelLayout
import com.zeroindex.intake.domain.onboarding.TipKey
import com.zeroindex.intake.domain.swap.SwapAllergen
import com.zeroindex.intake.ui.allergen.AllergenChip
import com.zeroindex.intake.ui.allergen.AllergenStrip
import com.zeroindex.intake.ui.components.AnimatedBar
import com.zeroindex.intake.ui.components.IntakeCard
import com.zeroindex.intake.ui.components.MacroSplitBar
import com.zeroindex.intake.ui.components.rememberSweepFraction
import com.zeroindex.intake.ui.motion.MoreDetail
import com.zeroindex.intake.ui.onboarding.TabTip
import com.zeroindex.intake.ui.onboarding.TabTipCopy
import com.zeroindex.intake.ui.screens.today.NextMealUi
import com.zeroindex.intake.ui.theme.CarbAccent
import com.zeroindex.intake.ui.theme.FatAccent
import com.zeroindex.intake.ui.theme.IntakeShape
import com.zeroindex.intake.ui.theme.ProteinAccent
import kotlin.math.roundToInt

/** Where each control on the Nutrition tab leads. Each is a different screen; nothing here configures anything. */
class NutritionActions(
    val onOpenAllergens: () -> Unit,
    /**
     * Opens Log food on the named mode (an `AddFoodTab` name) for the meal slot named (a `MealSlot` name), stamped at
     * [atMillis]: the chosen day's time, so logging from a past day lands on that day.
     */
    val onLogFood: (mode: String, slot: String, atMillis: Long) -> Unit,
    /** Opens the per-meal screen for a `MealSlot` name on a local day (`LocalDate.toEpochDay`). */
    val onOpenMeal: (slot: String, epochDay: Long) -> Unit,
    val onSwap: (slot: String?) -> Unit,
    val onOpenMealPlans: () -> Unit,
    val onOpenRecipes: () -> Unit,
    val onOpenMealTemplates: () -> Unit,
    /** The shopping list built from chosen recipes and meal templates (works with no meal plan). */
    val onOpenShopping: () -> Unit,
    val onOpenEatingOut: () -> Unit,
    /** The pushed "More" list: Plan, Recipes, Meals, Eating out and Drinks. */
    val onOpenMore: () -> Unit,
)

/**
 * The Nutrition tab root (round 3, "Nutrition tab v3"): the compact allergen chip; the overview card (calorie ring with a
 * hold-to-split button, macro cards, fibre, saturated fat, sugar and salt or sodium, and "More nutrients"), where a value
 * no logged food listed reads "not listed" and never 0; the Meals card (Breakfast, Lunch, Dinner and Snacks, each
 * with a "+" that opens Log food with Search, Scan and Photo); and a row to the pushed More list. The content clears the
 * status bar and the pill through [contentPadding].
 */
@Composable
fun NutritionHomeScreen(
    contentPadding: PaddingValues,
    actions: NutritionActions,
    modifier: Modifier = Modifier,
    viewModel: NutritionHomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val slot = viewModel.currentSlot()
    var pickingDate by rememberSaveable { mutableStateOf(false) }

    if (pickingDate) {
        DayPickerDialog(
            date = state.date,
            today = state.today,
            onPick = { viewModel.showPickerDate(it); pickingDate = false },
            onDismiss = { pickingDate = false },
        )
    }

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
        item(key = "day") {
            DaySwitcher(
                label = state.dateLabel,
                date = state.date,
                isToday = state.isToday,
                canGoNext = state.canGoNext,
                onPrevious = viewModel::showPreviousDay,
                onNext = viewModel::showNextDay,
                onToday = viewModel::showToday,
                onPick = { pickingDate = true },
            )
        }

        item(key = "strip") { AllergenStrip(onClick = actions.onOpenAllergens, compact = true) }

        item(key = "tip") {
            TabTip(key = TipKey.FOOD, body = TabTipCopy.FOOD, actionLabel = "Log food", onAction = { actions.onLogFood("SEARCH", slot.name, viewModel.loggingMillis(slot)) })
        }

        item(key = "overview") { OverviewCard(state.dashboard, state.ready, state.hydration, state.settings.volumeUnit, state.isToday) }

        item(key = "meals") {
            MealsCard(
                state = state,
                actions = actions,
                onLogIt = viewModel::logNextMeal,
                onOpenMeal = { actions.onOpenMeal(it.name, state.date.toEpochDay()) },
                onAddFood = { actions.onLogFood("SEARCH", it.name, viewModel.loggingMillis(it)) },
            )
        }

        item(key = "more") {
            Surface(
                onClick = actions.onOpenMore,
                color = MaterialTheme.colorScheme.surface,
                shape = CircleShape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) {
                Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "More: plan, recipes, eating out, nutrients",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ---- Overview card ----

private val RingSize = 124.dp
private val RingStroke = 12.dp
/** The ring's gap to its percentage labels, and the room the box keeps for them on each side (a "100%" label, the gap, a margin). */
private val RingLabelGap = 6.dp
private val RingLabelRoomX = 40.dp
private val RingLabelRoomY = 26.dp
/** The ring plus the room its percentage labels need outside it, so they never reach the text beside it. */
private val RingBoxWidth = RingSize + RingLabelRoomX * 2
private val RingBoxHeight = RingSize + RingLabelRoomY * 2

@Composable
private fun OverviewCard(
    model: DashboardModel?,
    ready: Boolean,
    hydration: com.zeroindex.intake.data.repository.HydrationDay?,
    unit: com.zeroindex.intake.domain.units.VolumeUnit,
    isToday: Boolean,
) {
    IntakeCard {
        if (model == null || !ready) {
            Text(if (isToday) "Reading today…" else "Reading that day…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@IntakeCard
        }
        var held by remember { mutableStateOf(false) }
        val grams = model.macros.associate { it.id to (it.value ?: 0.0) }
        val protein = grams["protein"] ?: 0.0
        val carbs = grams["carbs"] ?: 0.0
        val fat = grams["fat"] ?: 0.0
        val shares = MacroShares.of(protein, carbs, fat)
        // Holding with nothing eaten shows nothing extra.
        val showSplit = held && shares != null

        Box(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                CalorieRingView(model, showSplit, protein, carbs, fat)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    model.calories.target?.let {
                        Text("of ${java.text.NumberFormat.getIntegerInstance().format(it)} kcal", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("${model.calories.eaten} eaten", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold)
                }
            }
            SplitButton(held = held, onHeld = { held = it }, modifier = Modifier.align(Alignment.TopEnd).offset(x = 8.dp, y = (-8).dp))
        }

        MacroCards(model, showSplit, shares)

        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(8.dp))
        // Fibre, saturated fat, sugar and salt or sodium: reference limits where a limit exists, "not listed" when unknown.
        model.others.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                pair.forEach { row -> Box(Modifier.weight(1f)) { SmallBar(row) } }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        model.notes.forEach {
            Text("$it (that total is a floor).", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
        }
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
                Text(
                    "Fibre, saturated fat and salt are measured against common reference limits, not a personal target.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * The round pie button in the card's corner. Pressed and held it reports `true` through [onHeld]; released it reports
 * `false`. A screen reader's activation toggles it instead, since it cannot hold.
 */
@Composable
private fun SplitButton(held: Boolean, onHeld: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    val ring = MaterialTheme.colorScheme.primary
    Box(
        modifier
            .size(48.dp)
            .semantics {
                contentDescription = "Hold to show macro split"
                onClick(label = if (held) "Hide macro split" else "Show macro split") { onHeld(!held); true }
            }
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onHeld(true)
                    try {
                        tryAwaitRelease()
                    } finally {
                        onHeld(false)
                    }
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = CircleShape,
            border = BorderStroke(if (held) 2.dp else 1.dp, if (held) ring else MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.size(34.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.PieChart, contentDescription = null, tint = if (held) ring else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun CalorieRingView(model: DashboardModel, showSplit: Boolean, protein: Double, carbs: Double, fat: Double) {
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
    val segments = if (showSplit) MacroRingSplit.segments(protein, carbs, fat, sweep) else emptyList()
    val colors = mapOf(MacroRingSplit.Macro.PROTEIN to ProteinAccent, MacroRingSplit.Macro.CARBS to CarbAccent, MacroRingSplit.Macro.FAT to FatAccent)
    Box(
        Modifier.size(RingBoxWidth, RingBoxHeight).semantics { contentDescription = "$big $small" + (c.target?.let { ", of $it, ${c.eaten} eaten" } ?: "") },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(RingSize)) {
            val stroke = RingStroke.toPx()
            val topLeft = Offset(stroke / 2, stroke / 2)
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            if (segments.isNotEmpty()) {
                segments.forEach {
                    drawArc(colors.getValue(it.macro), -90f + it.startDegrees, it.sweepDegrees, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Butt))
                }
            } else if (sweep > 0f) {
                drawArc(color, -90f, 360f * sweep, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        // Each percentage sits just outside the ring, at the middle of its segment. The label is measured and placed so
        // its nearest edge is RingLabelGap clear of the ring's outer edge (RingLabelLayout), then nudged outward if it
        // would touch another label.
        if (segments.isNotEmpty()) {
            val measurer = rememberTextMeasurer()
            val density = LocalDensity.current
            val style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold)
            val texts = segments.map { "${it.pct}%" }
            val sizes = texts.map {
                val px = measurer.measure(it, style, maxLines = 1, softWrap = false).size
                with(density) { RingLabelLayout.Size(px.width.toDp().value.toDouble(), px.height.toDp().value.toDouble()) }
            }
            val placed = RingLabelLayout.place(segments.map { it.midDegrees }, sizes, RingSize.value / 2.0, RingLabelGap.value.toDouble())
            segments.forEachIndexed { i, seg ->
                Text(
                    texts[i],
                    style = style,
                    color = colors.getValue(seg.macro),
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.offset(x = placed[i].centerX.dp, y = placed[i].centerY.dp),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(big, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
            Text(small, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MacroCards(model: DashboardModel, showSplit: Boolean, shares: MacroShares.Shares?) {
    val colors = listOf(ProteinAccent, CarbAccent, FatAccent)
    val g = model.macros.map { it.value ?: 0.0 }
    val kcal = MacroRingSplit.kcal(g[0], g[1], g[2])
    val kcals = listOf(kcal.protein, kcal.carbs, kcal.fat)
    val pcts = shares?.let { listOf(it.proteinPct, it.carbPct, it.fatPct) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        model.macros.forEachIndexed { i, row ->
            Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f), shape = IntakeShape.Tile, modifier = Modifier.weight(1f)) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 9.dp)) {
                    Text(row.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${row.value?.roundToInt() ?: 0}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                    Text("/ ${row.target?.roundToInt() ?: "—"} g", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(5.dp))
                    AnimatedBar(row.fraction, colors[i], MaterialTheme.colorScheme.surfaceVariant, height = 3.dp)
                    Spacer(Modifier.height(5.dp))
                    Text("${kcals[i]} kcal", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (showSplit && pcts != null) {
                        Text("${pcts[i]}% of kcal", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, color = colors[i])
                    }
                }
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
private fun SmallBar(row: DashRow) {
    val barColor = when (row.id) {
        "fiber", "fibre" -> ProteinAccent
        "sugar" -> FatAccent
        else -> CarbAccent
    }
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
        Spacer(Modifier.height(4.dp))
        AnimatedBar(row.fraction, barColor, MaterialTheme.colorScheme.surfaceVariant, height = 3.dp)
    }
}

// ---- Meals card ----

@Composable
private fun MealsCard(
    state: NutritionHomeState,
    actions: NutritionActions,
    onLogIt: (NextMealUi) -> Unit,
    onOpenMeal: (MealSlot) -> Unit,
    onAddFood: (MealSlot) -> Unit,
) {
    IntakeCard {
        state.slots.forEach { row ->
            val planned = state.next?.takeIf { it.slot == row.slot && !row.logged }
            MealRow(
                emoji = slotEmoji(row.slot),
                name = slotLabel(row.slot),
                // The row opens the per-meal screen; the "+" goes straight to Log food for that meal and day.
                onClick = { onOpenMeal(row.slot) },
                onAdd = { onAddFood(row.slot) },
                addDescription = "Log food for ${slotLabel(row.slot).lowercase()}",
            ) {
                when {
                    row.logged -> {
                        Row {
                            Text(foodsLine(row.foods), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            Text(" · ${row.kcal} kcal", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
                        }
                        MacroSplitBar(row.protein, row.carbs, row.fat, Modifier.fillMaxWidth(0.7f).padding(top = 5.dp), showLabels = false, barHeight = 5.dp)
                    }
                    planned != null -> PlannedLine(planned, actions, onLogIt)
                    else -> Text("Nothing yet", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun PlannedLine(next: NextMealUi, actions: NutritionActions, onLogIt: (NextMealUi) -> Unit) {
    val complete = next.content.macros.isComplete
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Planned: ${next.content.recipe.name}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Text(" · ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextAction(if (complete) "Log it" else "Open plan") { if (complete) onLogIt(next) else actions.onOpenMealPlans() }
        Spacer(Modifier.size(12.dp))
        TextAction("Swap") { actions.onSwap(next.slot.name) }
    }
    val verdict = when (next.content.allergenStatus) {
        AllergenStatus.CONTAINS -> SwapAllergen.CONTAINS
        AllergenStatus.MAY_CONTAIN -> SwapAllergen.MAY_CONTAIN
        AllergenStatus.CHECK_LABEL, AllergenStatus.UNKNOWN -> SwapAllergen.CHECK_LABEL
        else -> SwapAllergen.CLEAR
    }
    if (verdict != SwapAllergen.CLEAR) {
        Box(Modifier.padding(top = 4.dp)) { AllergenChip(verdict, next.content.allergenTerms.firstOrNull()) }
    }
}

@Composable
private fun TextAction(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.clickable(onClick = onClick).heightIn(min = 36.dp).padding(horizontal = 2.dp, vertical = 8.dp),
    )
}

@Composable
private fun MealRow(
    emoji: String,
    name: String,
    onClick: () -> Unit,
    onAdd: () -> Unit,
    addDescription: String,
    subtitle: @Composable () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = IntakeShape.Tile, modifier = Modifier.size(44.dp)) {
            Box(contentAlignment = Alignment.Center) { Text(emoji, style = MaterialTheme.typography.titleMedium) }
        }
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
            subtitle()
        }
        IconButton(onClick = onAdd, modifier = Modifier.size(48.dp)) {
            Surface(
                color = Color.Transparent,
                shape = CircleShape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.size(34.dp).semantics { contentDescription = addDescription },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

private fun slotLabel(slot: MealSlot): String = when (slot) {
    MealSlot.BREAKFAST -> "Breakfast"
    MealSlot.LUNCH -> "Lunch"
    MealSlot.DINNER -> "Dinner"
    MealSlot.SNACK -> "Snacks"
    MealSlot.DRINKS -> "Drinks"
}

private fun slotEmoji(slot: MealSlot): String = when (slot) {
    MealSlot.BREAKFAST -> "\uD83C\uDF05"
    MealSlot.LUNCH -> "\u2600\uFE0F"
    MealSlot.DINNER -> "\uD83C\uDF19"
    MealSlot.SNACK -> "\uD83C\uDF4E"
    MealSlot.DRINKS -> "\uD83E\uDD64"
}

/** "Oatmeal, banana" for the first two foods, then "+N" for the rest; the Text ellipsises anything still too long. */
internal fun foodsLine(foods: List<String>): String {
    val shown = foods.distinct().take(2).joinToString(", ")
    val rest = foods.distinct().size - 2
    return if (rest > 0) "$shown +$rest" else shown
}
