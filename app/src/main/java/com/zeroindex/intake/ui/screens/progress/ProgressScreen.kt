package com.zeroindex.intake.ui.screens.progress

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.zeroindex.intake.data.db.entity.BodyMetricEntity
import com.zeroindex.intake.domain.charts.ChartMath
import com.zeroindex.intake.domain.health.DayPoint
import com.zeroindex.intake.domain.health.HealthSeries
import com.zeroindex.intake.domain.model.TdeeConfidence
import com.zeroindex.intake.domain.time.DateText
import com.zeroindex.intake.domain.units.KcalFormat
import com.zeroindex.intake.domain.units.LengthUnit
import com.zeroindex.intake.domain.units.UnitConverter
import com.zeroindex.intake.ui.adaptive.LocalWindowSizeClass
import com.zeroindex.intake.ui.adaptive.isExpanded
import com.zeroindex.intake.ui.components.AccentCard
import com.zeroindex.intake.ui.components.ChartPoint
import com.zeroindex.intake.ui.components.EmptyState
import com.zeroindex.intake.ui.components.Eyebrow
import com.zeroindex.intake.ui.components.HeroCard
import com.zeroindex.intake.ui.components.IconTile
import com.zeroindex.intake.ui.components.IconTileStyle
import com.zeroindex.intake.ui.components.IntakeCard
import com.zeroindex.intake.ui.components.IntakeChip
import com.zeroindex.intake.ui.components.RowChevron
import com.zeroindex.intake.ui.components.SoftButton
import com.zeroindex.intake.ui.components.SubCopy
import com.zeroindex.intake.ui.components.heroMuted
import com.zeroindex.intake.ui.motion.MoreDetail
import com.zeroindex.intake.ui.motion.SkeletonList
import com.zeroindex.intake.ui.theme.IconSize
import com.zeroindex.intake.ui.theme.IntakeIcons
import com.zeroindex.intake.ui.theme.IntakeShape
import com.zeroindex.intake.ui.theme.IntakeSpacing
import com.zeroindex.intake.ui.theme.NumberSmall
import com.zeroindex.intake.ui.theme.intake
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

private val InsightsGap = 14.dp
private const val HEART_RATE = "Resting heart rate"

/**
 * Insights (the Progress tab root, DESIGN-V6 Insights): the weekly check-in as the lime hero, two link tiles, the range
 * selector, then the weight trend, Health Connect tiles, calories, measurements, photos, averages with the maintenance tile,
 * training and jump tests. Every figure is the one the screen computed before the redesign.
 *
 * [onOpenPhotos] opens the Progress photos screen (`Route.ProgressPhotos`); the host passes it.
 */
@Composable
fun ProgressScreen(
    contentPadding: PaddingValues,
    onAddMeasurement: () -> Unit,
    onAddJumpTest: () -> Unit,
    onOpenTrainingStats: () -> Unit,
    onOpenCheckIn: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenPhotos: () -> Unit = {},
    viewModel: ProgressViewModel = hiltViewModel(),
    trainingModel: TrainingStatsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val training by trainingModel.uiState.collectAsStateWithLifecycle()
    val startInset = contentPadding.calculateStartPadding(LayoutDirection.Ltr)
    val endInset = contentPadding.calculateEndPadding(LayoutDirection.Ltr)
    val hasHistory by viewModel.hasHistory.collectAsStateWithLifecycle()
    val gutter = IntakeSpacing.ScreenGutter
    val tip: @Composable () -> Unit = {
        com.zeroindex.intake.ui.onboarding.TabTip(
            key = com.zeroindex.intake.domain.onboarding.TipKey.PROGRESS,
            body = com.zeroindex.intake.ui.onboarding.TabTipCopy.PROGRESS,
            actionLabel = "Log weight",
            onAction = onAddMeasurement,
            hasHistory = hasHistory,
        )
    }
    if (LocalWindowSizeClass.current.isExpanded) {
        // Heading and range selector span the top; two columns scroll on their own.
        val columnPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 24.dp)
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(top = contentPadding.calculateTopPadding() + 4.dp, start = gutter + startInset, end = gutter + endInset),
            verticalArrangement = Arrangement.spacedBy(InsightsGap),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(InsightsGap)) {
                Text(
                    "Patterns over time, without the noise.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                RangeSegments(state.range, viewModel::selectRange, Modifier.weight(1f))
            }
            tip()
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(InsightsGap)) {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentPadding = columnPadding,
                    verticalArrangement = Arrangement.spacedBy(InsightsGap),
                ) {
                    if (!state.loaded) {
                        item(key = "loading") { SkeletonList(rows = 2, rowHeight = 150.dp, label = "Loading your progress") }
                    } else {
                        weightItem(state)
                        healthItems(state)
                        measurementsItem(state)
                        photosItem(state, onOpenPhotos)
                    }
                }
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentPadding = columnPadding,
                    verticalArrangement = Arrangement.spacedBy(InsightsGap),
                ) {
                    item(key = "checkin") { CheckInHero(onOpenCheckIn) }
                    item(key = "tiles") { LinkTiles(onOpenTrainingStats, onAddMeasurement) }
                    if (state.loaded) {
                        caloriesItem(state)
                        averagesItem(state, viewModel::recalculateTdee)
                        item(key = "training") { TrainingStatsCard(training, onOpenLog = null) }
                        if (state.settings.jumpTests) item(key = "performance") { PerformanceCard(state, onAddJumpTest) }
                    }
                }
            }
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxWidth(),
            contentPadding = PaddingValues(
                top = contentPadding.calculateTopPadding() + 4.dp,
                bottom = contentPadding.calculateBottomPadding() + 24.dp,
                start = gutter + startInset,
                end = gutter + endInset,
            ),
            verticalArrangement = Arrangement.spacedBy(InsightsGap),
        ) {
            item(key = "sub") { SubCopy("Patterns over time, without the noise.", Modifier.padding(0.dp)) }
            item(key = "tip") { tip() }
            item(key = "checkin") { CheckInHero(onOpenCheckIn) }
            item(key = "tiles") { LinkTiles(onOpenTrainingStats, onAddMeasurement) }
            item(key = "ranges") { RangeSegments(state.range, viewModel::selectRange) }
            if (!state.loaded) {
                item(key = "loading") { SkeletonList(rows = 3, rowHeight = 150.dp, label = "Loading your progress") }
            } else {
                weightItem(state)
                healthItems(state)
                caloriesItem(state)
                measurementsItem(state)
                photosItem(state, onOpenPhotos)
                averagesItem(state, viewModel::recalculateTdee)
                item(key = "training") { TrainingStatsCard(training, onOpenLog = null) }
                if (state.settings.jumpTests) item(key = "performance") { PerformanceCard(state, onAddJumpTest) }
            }
        }
    }
}

/** `.card.lime` link: the weekly check-in is the one thing on this tab that asks for attention. */
@Composable
private fun CheckInHero(onOpen: () -> Unit) {
    AccentCard(modifier = Modifier.clickable(role = Role.Button, onClick = onOpen)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Weekly check-in", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Icon(IntakeIcons.ChevronRight, contentDescription = null, modifier = Modifier.size(IconSize.Default))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "The week's summary, suggestions to apply, and a PDF to export or print",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.intake.onAccent.copy(alpha = 0.75f),
        )
    }
}

@Composable
private fun LinkTiles(onTrainingStats: () -> Unit, onLogWeight: () -> Unit) {
    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(IntakeSpacing.GridGap2)) {
        LinkTile(IntakeIcons.Dumbbell, IconTileStyle.Soft, "Training stats", "Records, volume, est. max", onTrainingStats, Modifier.weight(1f).fillMaxHeight())
        LinkTile(IntakeIcons.Plus, IconTileStyle.Ink, "Log weight", "and measurements", onLogWeight, Modifier.weight(1f).fillMaxHeight())
    }
}

@Composable
private fun LinkTile(
    icon: ImageVector,
    style: IconTileStyle,
    title: String,
    sub: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = IntakeShape.Card,
        modifier = modifier,
    ) {
        Column(Modifier.padding(IntakeSpacing.CardPad), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            IconTile(icon, style = style, small = true)
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** `.seg` with equal-width items: 1M 3M 6M 1Y All read as one control across the full width. */
@Composable
private fun RangeSegments(selected: ProgressRange, onSelect: (ProgressRange) -> Unit, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.intake.soft, shape = IntakeShape.Pill, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            ProgressRange.entries.forEach { range ->
                val on = selected == range
                Surface(
                    color = if (on) MaterialTheme.colorScheme.surface else Color.Transparent,
                    contentColor = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    shape = IntakeShape.Pill,
                    shadowElevation = if (on) 1.dp else 0.dp,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 40.dp)
                        .clip(IntakeShape.Pill)
                        .clickable(role = Role.RadioButton, onClickLabel = range.label) { onSelect(range) },
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 4.dp)) {
                        Text(range.label, style = MaterialTheme.typography.titleSmall, maxLines = 1, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

/** A card header: the eyebrow on the left and the window the figures cover on the right. */
@Composable
private fun StatHeader(title: String, caption: String?) {
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Eyebrow(title, Modifier.weight(1f))
        if (caption != null) {
            Text(caption, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private val BigNumber = TextStyle(fontSize = 28.sp, lineHeight = 32.sp)
private val TileNumber = TextStyle(fontSize = 22.sp, lineHeight = 26.sp)

private fun LazyListScope.weightItem(state: ProgressUiState) {
    item(key = "weight") { WeightCard(state) }
}

@Composable
private fun WeightCard(state: ProgressUiState) {
    val unit = state.settings.weightUnit
    IntakeCard {
        StatHeader("Weight trend", state.range.caption)
        val weekly = state.weeklyChangeKg
        // The headline figure is the latest raw weigh-in, with its date; the smoothed trend is named beside it.
        state.metrics.lastOrNull()?.let { latest ->
            val zone = ZoneId.systemDefault()
            val date = DateText.medium(ChartMath.localDate(latest.measuredAt, zone))
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = format(unit.fromKg(latest.weightKg)),
                            style = MaterialTheme.typography.headlineMedium.merge(BigNumber),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = "${unit.label} · latest weigh-in, $date",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                    state.trendWeightKg?.let { trendKg ->
                        Text(
                            text = "Trend ${format(unit.fromKg(trendKg))} ${unit.label}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                if (weekly != null && abs(weekly) > 0.005) {
                    val arrow = if (weekly < 0) "−" else "+"
                    IntakeChip("$arrow${format(abs(unit.fromKg(weekly)))} ${unit.label}/week")
                }
            }
        }
        if (state.metrics.isEmpty()) {
            EmptyState(
                body = "No weigh-ins in this range yet. Log your weight and the trend line appears here.",
                icon = IntakeIcons.LineChart,
            )
            return@IntakeCard
        }
        Spacer(Modifier.height(8.dp))
        val zone = ZoneId.systemDefault()
        InsightsTrendChart(
            raw = state.metrics.map { ChartPoint(it.measuredAt.toDouble(), unit.fromKg(it.weightKg), ChartMath.sourceLabel(it.source)) },
            trend = state.trend.map { ChartPoint(ChartMath.localDayStartMillis(it.dayIndex, zone).toDouble(), unit.fromKg(it.trendKg)) },
            description = weightDescription(state, unit.label),
            valueSuffix = unit.label,
            maxTicks = 6,
            dateLabels = true,
        )
        InsightsLegend(
            listOf(
                InsightsLegendEntry("Daily weigh-in", MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)),
                InsightsLegendEntry("Smoothed trend", MaterialTheme.colorScheme.onSurface, LegendMark.Line),
            ),
            Modifier.padding(top = 8.dp),
        )
    }
}

/**
 * Sleep, steps and resting heart rate from Health Connect, plus days logged, as a bento of small tiles. The Health tiles are
 * absent when none of the three has a day with data (no permission, no watch): days with no reading are left out, not drawn as
 * zero. Days logged always shows.
 */
private fun LazyListScope.healthItems(state: ProgressUiState) {
    item(key = "bento") { Bento(state) }
    val hasHealth = HealthSeries.sleepHours(state.health).isNotEmpty() ||
        HealthSeries.steps(state.health).isNotEmpty() ||
        HealthSeries.restingHeartRate(state.health).isNotEmpty()
    if (!hasHealth) return
    item(key = "bento-caption") {
        Text(
            "Sleep, steps and heart rate from Health Connect · ${state.range.caption}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun Bento(state: ProgressUiState) {
    val sleep = HealthSeries.sleepHours(state.health)
    val steps = HealthSeries.steps(state.health)
    val resting = HealthSeries.restingHeartRate(state.health)
    val colors = MaterialTheme.intake
    val ink = MaterialTheme.colorScheme.onSurface
    val tiles = buildList<@Composable (Modifier) -> Unit> {
        if (sleep.isNotEmpty()) {
            add { m -> HealthTile("Sleep", sleep, "h a night", { "%.1f".format(it) }, m) { v, d -> MiniBars(v, colors.sleep, d) } }
        }
        if (steps.isNotEmpty()) {
            add { m -> HealthTile("Steps", steps, "a day", { "%,d".format(it.roundToInt()) }, m) { v, d -> MiniBars(v, ink, d, highlightLast = true) } }
        }
        if (resting.isNotEmpty()) {
            add { m -> HealthTile(HEART_RATE, resting, "bpm", { it.roundToInt().toString() }, m) { v, d -> MiniLine(v, colors.heart, d) } }
        }
        add { m -> DaysLoggedTile(state, m) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(IntakeSpacing.GridGap2)) {
        tiles.chunked(2).forEach { pair ->
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(IntakeSpacing.GridGap2)) {
                pair.forEach { tile -> tile(Modifier.weight(1f).fillMaxHeight()) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HealthTile(
    label: String,
    points: List<DayPoint>,
    unit: String,
    show: (Double) -> String,
    modifier: Modifier,
    chart: @Composable (List<Double>, String) -> Unit,
) {
    val average = points.map { it.value }.average()
    val days = "${points.size} ${if (points.size == 1) "day" else "days"} with data"
    val description = "$label over time, from Health Connect. Average ${show(average)} $unit, latest ${show(points.last().value)}."
    IntakeCard(modifier = modifier, contentPadding = PaddingValues(16.dp)) {
        Eyebrow(label)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(show(average), style = MaterialTheme.typography.headlineMedium.merge(TileNumber))
            Spacer(Modifier.width(4.dp))
            Text(unit, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 3.dp))
        }
        Text(
            "average · latest ${show(points.last().value)} · $days",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        // Bars show the newest days; the heart-rate line shows the whole range.
        val values = points.map { it.value }
        chart(if (label == HEART_RATE) values else values.takeLast(14), description)
    }
}

@Composable
private fun DaysLoggedTile(state: ProgressUiState, modifier: Modifier) {
    IntakeCard(modifier = modifier, contentPadding = PaddingValues(16.dp)) {
        Eyebrow("Days logged")
        Spacer(Modifier.height(6.dp))
        // SPEC §11: never a streak that resets to zero on one bad day.
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${state.daysLogged}", style = MaterialTheme.typography.headlineMedium.merge(TileNumber))
            Spacer(Modifier.width(4.dp))
            Text(
                "of the last ${state.daysInWindow} days logged",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 3.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        DaysLoggedDots(days = state.adherence, target = state.target?.kcal?.toDouble())
        Text(
            "Filled: logged. Pale: under 60% of your target. Grey: nothing logged. Ring: today.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

private fun LazyListScope.caloriesItem(state: ProgressUiState) {
    item(key = "calories") {
        IntakeCard {
            StatHeader("Calories vs target", state.range.caption)
            // No logged day in the range: say so rather than drawing an empty chart.
            if (state.dailyKcal.all { it == null }) {
                EmptyState(
                    body = "No meals logged in this range yet. Log food and each day's calories appear here against your target.",
                    icon = IntakeIcons.ForkKnife,
                )
                return@IntakeCard
            }
            CaloriesBarChart(
                values = state.dailyKcal.map { it ?: 0.0 },
                target = state.target?.kcal?.toDouble(),
                description = intakeDescription(state),
            )
            val c = MaterialTheme.intake
            InsightsLegend(
                listOfNotNull(
                    InsightsLegendEntry("Calories logged", c.soft, LegendMark.DotOutlined),
                    state.target?.let { InsightsLegendEntry("Target", MaterialTheme.colorScheme.onSurface, LegendMark.Dashed) },
                    state.target?.let { InsightsLegendEntry("Over", c.carb) },
                ),
                Modifier.padding(top = 8.dp),
            )
        }
    }
}

private data class Site(val label: String, val points: List<ChartPoint>)

private fun LazyListScope.measurementsItem(state: ProgressUiState) {
    if (state.metrics.none { it.hasMeasurements() }) return
    item(key = "measurements") { MeasurementsCard(state) }
}

@Composable
private fun MeasurementsCard(state: ProgressUiState) {
    val unit = state.settings.lengthUnit
    val selectors = listOf<Pair<String, (BodyMetricEntity) -> Double?>>(
        "Waist" to { it.waistCm }, "Chest" to { it.chestCm }, "Arm" to { it.armCm }, "Thigh" to { it.thighCm }, "Hip" to { it.hipCm },
    )
    val sites = selectors.mapNotNull { (label, selector) ->
        val points = state.metrics.mapNotNull { m ->
            selector(m)?.let { ChartPoint(m.measuredAt.toDouble(), unit.fromCm(it), ChartMath.sourceLabel(m.source)) }
        }.sortedBy { it.x }
        if (points.isEmpty()) null else Site(label, points)
    }
    IntakeCard {
        StatHeader("Measurements", state.range.caption)
        sites.forEach { site ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(site.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text("${format(site.points.last().y)} ${unit.label}", style = MaterialTheme.typography.titleMedium)
                if (site.points.size >= 2) {
                    Spacer(Modifier.width(8.dp))
                    IntakeChip(deltaText(site.points.last().y - site.points.first().y))
                }
            }
        }
        // A site needs two readings to draw a line.
        val drawn = sites.filter { it.points.size >= 2 }
        if (drawn.isEmpty()) {
            EmptyState(
                body = "No measurements in this range yet. Two readings of a site, such as your waist, draw its line here.",
                icon = IntakeIcons.Tape,
            )
        } else {
            MoreDetail(title = "Show the charts") {
                drawn.forEach { site ->
                    Eyebrow(site.label, Modifier.padding(top = 10.dp, bottom = 2.dp))
                    InsightsTrendChart(
                        raw = site.points,
                        trend = site.points,
                        description = "${site.label} over time, in ${if (unit == LengthUnit.INCHES) "inches" else "centimetres"}. " +
                            "Latest ${format(site.points.last().y)} ${unit.label}.",
                        valueSuffix = unit.label,
                        gapDays = null,
                        // A flat-ish tape reading is wobble, not a trend: the axis is at least 5 cm or 2 in tall.
                        minYSpan = if (unit == LengthUnit.INCHES) 2.0 else 5.0,
                        maxTicks = 5,
                        dateLabels = true,
                        plotHeight = 120.dp,
                        endMarker = false,
                    )
                }
            }
        }
    }
}

private fun deltaText(d: Double): String {
    val r = UnitConverter.round(abs(d), 2)
    return when {
        r == 0.0 -> "±0"
        d < 0 -> "−$r"
        else -> "+$r"
    }
}

private fun LazyListScope.photosItem(state: ProgressUiState, onOpen: () -> Unit) {
    item(key = "photos") {
        IntakeCard(modifier = Modifier.clickable(role = Role.Button, onClick = onOpen)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Surface(color = MaterialTheme.intake.soft, shape = IntakeShape.Thumb, modifier = Modifier.size(width = 56.dp, height = 72.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(IntakeIcons.User, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(IconSize.Default))
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text("Progress photos", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = if (state.photoCount > 0) {
                            "${state.photoCount} ${if (state.photoCount == 1) "photo" else "photos"}" +
                                (state.photoNewestAt?.let { " · latest ${DateText.medium(it)}" } ?: "")
                        } else {
                            "No photos yet. Photos are stored on this phone only."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                RowChevron()
            }
            if (state.photos.isNotEmpty()) {
                Row(Modifier.padding(top = 12.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.photos.forEach { metric ->
                        AsyncImage(
                            model = metric.photoUri,
                            contentDescription = "Progress photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(width = 90.dp, height = 120.dp).clip(IntakeShape.Thumb),
                        )
                    }
                }
            }
        }
    }
}

/** Weekly averages beside the dark maintenance tile; stacked in one column when the font is large. */
private fun LazyListScope.averagesItem(state: ProgressUiState, onRecalculate: () -> Unit) {
    item(key = "summary") { AveragesRow(state, onRecalculate) }
}

@Composable
private fun AveragesRow(state: ProgressUiState, onRecalculate: () -> Unit) {
    val tdee = state.tdee
    val unit = state.settings.weightUnit
    val weekly = state.weeklyChangeKg
    val stacked = LocalDensity.current.fontScale > 1.3f
    val averages: @Composable (Modifier) -> Unit = { m ->
        IntakeCard(modifier = m, contentPadding = PaddingValues(16.dp)) {
            StatHeader("Averages", state.range.caption)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AverageLine(state.averageIntake?.let { "%,d".format(it.roundToInt()) } ?: "—", "avg kcal / day")
                AverageLine(
                    weekly?.let { "${if (it < 0) "−" else "+"}${format(abs(unit.fromKg(it)))} ${unit.label}" } ?: "—",
                    "avg weight change",
                )
                AverageLine(state.proteinHitRate?.let { "${(it * 100).roundToInt()}%" } ?: "—", "protein target hit, ${state.range.caption}")
            }
        }
    }
    val maintenance: @Composable (Modifier) -> Unit = { m ->
        HeroCard(modifier = m, contentPadding = PaddingValues(16.dp)) {
            Eyebrow("Maintenance", color = heroMuted())
            Spacer(Modifier.height(6.dp))
            Text(
                // SPEC §8: when data is too sparse, say so plainly rather than showing a confident number.
                tdee?.let { "≈${"%,d".format(it.estimatedTdee.roundToInt())}" } ?: "—",
                style = MaterialTheme.typography.headlineMedium,
            )
            Text("maintenance kcal (latest estimate)", style = MaterialTheme.typography.labelSmall, color = heroMuted())
            Spacer(Modifier.height(4.dp))
            Text(
                if (tdee == null) {
                    "Needs 14 days of weigh-ins and logs"
                } else {
                    "${tdee.confidence.describe().replaceFirstChar { it.uppercase() }} · based on your intake and weight history"
                },
                style = MaterialTheme.typography.labelSmall,
                color = heroMuted(),
            )
            Spacer(Modifier.height(12.dp))
            val on = MaterialTheme.intake.onHero
            Button(
                onClick = onRecalculate,
                enabled = !state.isRecalculating,
                shape = IntakeShape.Button,
                colors = ButtonDefaults.buttonColors(
                    containerColor = on.copy(alpha = 0.12f),
                    contentColor = on,
                    disabledContainerColor = on.copy(alpha = 0.06f),
                    disabledContentColor = on.copy(alpha = 0.5f),
                ),
                modifier = Modifier.fillMaxWidth().heightIn(min = IntakeSpacing.TouchTarget),
            ) {
                Text(if (state.isRecalculating) "Recalculating…" else "Recalculate now", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(IntakeSpacing.GridGap2)) {
        if (stacked) {
            averages(Modifier)
            maintenance(Modifier)
        } else {
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(IntakeSpacing.GridGap2)) {
                averages(Modifier.weight(1f).fillMaxHeight())
                maintenance(Modifier.weight(1f).fillMaxHeight())
            }
        }
        if (tdee != null && tdee.confidence == TdeeConfidence.LOW) {
            Text(
                text = "Treat that maintenance figure as provisional: there is not yet enough " +
                    "overlap between weigh-ins and logged days to measure it properly.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun AverageLine(value: String, caption: String) {
    Column {
        Text(value, style = NumberSmall, color = MaterialTheme.colorScheme.onSurface)
        Text(caption, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun BodyMetricEntity.hasMeasurements(): Boolean =
    waistCm != null || chestCm != null || armCm != null || thighCm != null || hipCm != null

private fun TdeeConfidence.describe(): String = when (this) {
    TdeeConfidence.LOW -> "low confidence"
    TdeeConfidence.MEDIUM -> "medium confidence"
    TdeeConfidence.HIGH -> "high confidence"
}

private fun weightDescription(state: ProgressUiState, unitLabel: String): String {
    val trend = state.trendWeightKg ?: return "No weigh-ins in this range."
    val weekly = state.weeklyChangeKg
    return buildString {
        append("Weight trend ${format(state.settings.weightUnit.fromKg(trend))} $unitLabel")
        if (weekly != null) {
            val direction = if (weekly < 0) "down" else "up"
            append(", $direction ${format(abs(state.settings.weightUnit.fromKg(weekly)))} $unitLabel per week")
        }
        append(", from ${state.metrics.size} weigh-ins.")
    }
}

private fun intakeDescription(state: ProgressUiState): String {
    val stats = state.intakeStats
    val target = state.target?.kcal
    return buildString {
        append("Calories per day across ${stats.loggedDays} logged days")
        if (target != null) append(", target ${KcalFormat.format(target)} kcal, ${stats.daysOverTarget} days over")
        append(".")
    }
}

private fun format(value: Double): String = UnitConverter.round(value, 1).toString()

/**
 * Amendment §2: does more training volume come with a better jump, or a worse one? Weekly tonnage as bars, best vertical that
 * week as the line.
 */
@Composable
private fun PerformanceCard(state: ProgressUiState, onAddJump: () -> Unit) {
    val weeks = state.performance
    val length = state.settings.lengthUnit
    val weight = state.settings.weightUnit
    val jumps = weeks.mapNotNull { it.bestVerticalCm }
    val latest = jumps.lastOrNull()

    IntakeCard {
        Row(Modifier.padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconTile(IntakeIcons.ArrowUpBar, small = true)
            Column(Modifier.weight(1f)) {
                Text("Jump tests", style = MaterialTheme.typography.titleMedium)
                Text("weekly, ${state.range.caption}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (jumps.isEmpty()) {
            EmptyState(
                body = "No jump tests in this range yet. Log one and your best vertical shows here next to your weekly training volume.",
            )
            SoftButton(onClick = onAddJump, modifier = Modifier.fillMaxWidth()) { Text("Log a jump test") }
            return@IntakeCard
        }
        if (latest != null) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(format(length.fromCm(latest)), style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.width(5.dp))
                Text(
                    "${length.label} · latest vertical",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 3.dp),
                )
            }
        }
        JumpOverlayChart(
            bars = weeks.map { weight.fromKg(it.tonnageKg) },
            line = weeks.map { w -> w.bestVerticalCm?.let(length::fromCm) },
            description = buildString {
                append("Weekly training volume and best vertical jump over ${weeks.size} weeks. ")
                if (jumps.size >= 2) {
                    append("Jump went from ${format(length.fromCm(jumps.first()))} to ${format(length.fromCm(jumps.last()))} ${length.label}.")
                } else {
                    append("Not enough jump tests to show a trend yet.")
                }
            },
            modifier = Modifier.padding(top = 8.dp),
        )
        val maxTonnage = weeks.maxOfOrNull { it.tonnageKg } ?: 0.0
        InsightsLegend(
            listOf(
                InsightsLegendEntry(
                    "Weekly volume (to ${"%,d".format(weight.fromKg(maxTonnage).roundToInt())} ${weight.label})",
                    MaterialTheme.intake.soft,
                    LegendMark.DotOutlined,
                ),
                InsightsLegendEntry("Best vertical (${length.label})", MaterialTheme.colorScheme.onSurface, LegendMark.Line),
            ),
            Modifier.padding(top = 8.dp),
        )
        Spacer(Modifier.height(10.dp))
        SoftButton(onClick = onAddJump, modifier = Modifier.fillMaxWidth()) { Text("Log a jump test") }
    }
}
