package com.zeroindex.intake.ui.screens.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import com.zeroindex.intake.ui.components.EmptyState
import com.zeroindex.intake.ui.motion.SkeletonList
import com.zeroindex.intake.ui.motion.pressScale
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.zeroindex.intake.domain.model.TdeeConfidence
import com.zeroindex.intake.domain.units.UnitConverter
import com.zeroindex.intake.ui.adaptive.LocalWindowSizeClass
import com.zeroindex.intake.ui.adaptive.isExpanded
import com.zeroindex.intake.ui.components.AdherenceHeatmap
import com.zeroindex.intake.domain.charts.ChartMath
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.zeroindex.intake.domain.health.DayPoint
import com.zeroindex.intake.domain.health.HealthSeries
import com.zeroindex.intake.ui.components.ChartLegend
import com.zeroindex.intake.ui.components.ChartPoint
import com.zeroindex.intake.ui.components.ChoiceChip
import com.zeroindex.intake.ui.components.ConfidenceLine
import com.zeroindex.intake.ui.components.Eyebrow
import com.zeroindex.intake.ui.components.LegendEntry
import com.zeroindex.intake.ui.components.OverlayChart
import com.zeroindex.intake.ui.components.SectionCard
import com.zeroindex.intake.ui.components.SubHead
import com.zeroindex.intake.ui.components.SummaryTile
import com.zeroindex.intake.ui.components.TargetBarChart
import com.zeroindex.intake.ui.components.TrendLineChart
import com.zeroindex.intake.ui.theme.HeatFull
import com.zeroindex.intake.ui.theme.HeatPartial
import com.zeroindex.intake.ui.theme.IntakeShape
import kotlin.math.abs
import com.zeroindex.intake.ui.screens.hub.HubItem
import com.zeroindex.intake.ui.screens.hub.HubList
import kotlin.math.roundToInt

@Composable
fun ProgressScreen(
    contentPadding: PaddingValues,
    onAddMeasurement: () -> Unit,
    onAddJumpTest: () -> Unit,
    onOpenTrainingStats: () -> Unit,
    onOpenCheckIn: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProgressViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (LocalWindowSizeClass.current.isExpanded) {
        // Amendment §3, mockup `tabletProgress()`: heading and range selector
        // span the top; weight and adherence sit on the left, intake and the
        // weekly check-in on the right, and the cards the mockup does not show
        // (jump output, measurements, photos) follow in the column with room.
        // Each column scrolls on its own.
        val columnPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 24.dp)
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(top = contentPadding.calculateTopPadding() + 12.dp, start = 16.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SubHead(
                    "Progress",
                    "Patterns over time, without the noise.",
                    Modifier.weight(1f),
                )
                RangeSelector(state.range, viewModel::selectRange, Modifier.weight(1f))
            }
            com.zeroindex.intake.ui.onboarding.TabTip(
                key = com.zeroindex.intake.domain.onboarding.TipKey.PROGRESS,
                body = com.zeroindex.intake.ui.onboarding.TabTipCopy.PROGRESS,
                actionLabel = "Log weight",
                onAction = onAddMeasurement,
            )
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentPadding = columnPadding,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item(key = "log-weight") { LogWeightButton(onAddMeasurement) }
                    if (!state.loaded) {
                        item(key = "loading") { SkeletonList(rows = 2, rowHeight = 150.dp, label = "Loading your progress") }
                    } else {
                        item(key = "weight") { WeightCard(state, onAddMeasurement) }
                        healthItem(state)
                        item(key = "adherence") { AdherenceCard(state) }
                        detailItems(state)
                    }
                }
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentPadding = columnPadding,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item(key = "calories") { CaloriesCard(state) }
                    item(key = "summary") { WeeklySummaryCard(state, viewModel) }
                    item(key = "performance") {
                        if (state.settings.jumpTests) PerformanceCard(state = state, onAddJump = onAddJumpTest)
                    }
                    item(key = "reports") {
                        ProgressLinks(onOpenTrainingStats, onOpenCheckIn)
                    }
                }
            }
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxWidth(),
            contentPadding = PaddingValues(
                top = contentPadding.calculateTopPadding() + 12.dp,
                bottom = contentPadding.calculateBottomPadding() + 24.dp,
                start = 16.dp,
                end = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "tip") {
                com.zeroindex.intake.ui.onboarding.TabTip(
                    key = com.zeroindex.intake.domain.onboarding.TipKey.PROGRESS,
                    body = com.zeroindex.intake.ui.onboarding.TabTipCopy.PROGRESS,
                    actionLabel = "Log weight",
                    onAction = onAddMeasurement,
                )
            }
            // The one obvious action on this screen.
            item(key = "log-weight") { LogWeightButton(onAddMeasurement) }
            item(key = "ranges") { RangeSelector(state.range, viewModel::selectRange) }
            if (!state.loaded) {
                // Shimmer placeholders while the charts' data loads, instead of empty cards.
                item(key = "loading") { SkeletonList(rows = 3, rowHeight = 150.dp, label = "Loading your progress") }
            } else {
                item(key = "weight") { WeightCard(state, onAddMeasurement) }
                healthItem(state)
                item(key = "summary") { WeeklySummaryCard(state, viewModel) }
                item(key = "adherence") { AdherenceCard(state) }
                item(key = "calories") { CaloriesCard(state) }
                item(key = "performance") {
                    if (state.settings.jumpTests) PerformanceCard(state = state, onAddJump = onAddJumpTest)
                }
                detailItems(state)
            }
            item(key = "reports") {
                ProgressLinks(onOpenTrainingStats, onOpenCheckIn)
            }
        }
    }
}

/** The rest of the Progress hub: the reports and data sources that have their own screens. */
@Composable
private fun ProgressLinks(
    onOpenTrainingStats: () -> Unit,
    onOpenCheckIn: () -> Unit,
) {
    HubList(
        listOf(
            HubItem(
                title = "Weekly check-in",
                description = "The week's summary, suggestions to apply, and a PDF to export or print",
                icon = Icons.Filled.Checklist,
                onClick = onOpenCheckIn,
            ),
            HubItem(
                title = "Training stats",
                description = "Workout history, volume per muscle and personal records",
                icon = Icons.Filled.BarChart,
                onClick = onOpenTrainingStats,
            ),
        ),
    )
}

/**
 * The mockup's `.range`: equal-width segments across the full width, so the
 * ranges read as one control rather than a scrolling list of chips.
 */
@Composable
private fun RangeSelector(
    selected: ProgressRange,
    onSelect: (ProgressRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        ProgressRange.entries.forEach { range ->
            ChoiceChip(
                label = range.label,
                selected = selected == range,
                onClick = { onSelect(range) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun WeightCard(state: ProgressUiState, onAddMeasurement: () -> Unit) {
    val unit = state.settings.weightUnit
    SectionCard(title = "Weight trend", caption = state.range.caption) {
        // The headline figure is the latest raw weigh-in, and says so with its date; the smoothed trend value is
        // named beside it, because the two differ and the line in the chart is the trend.
        state.metrics.lastOrNull()?.let { latest ->
            val zone = ZoneId.systemDefault()
            val date = ChartMath.localDate(latest.measuredAt, zone).format(HEADLINE_DATE)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = format(unit.fromKg(latest.weightKg)),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = "${unit.label} · latest weigh-in, $date",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 3.dp),
                )
            }
            state.trendWeightKg?.let { trendKg ->
                Text(
                    text = "Trend ${format(unit.fromKg(trendKg))} ${unit.label}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        // SPEC §8: the trend line is what is shown, with raw points
        // behind it rather than instead of it.
        // One message when there is nothing to draw; the Log weight button above the card is the way in.
        if (state.metrics.isEmpty()) {
            EmptyState(body = "No weigh-ins in this range yet. Log your weight and the trend line appears here.")
            return@SectionCard
        }
        // Dots and trend share one time base: real instants, the trend at the start of its local day (not UTC midnight).
        val zone = ZoneId.systemDefault()
        TrendLineChart(
            raw = state.metrics.map {
                ChartPoint(it.measuredAt.toDouble(), unit.fromKg(it.weightKg), ChartMath.sourceLabel(it.source))
            },
            trend = state.trend.map {
                ChartPoint(ChartMath.localDayStartMillis(it.dayIndex, zone).toDouble(), unit.fromKg(it.trendKg))
            },
            description = weightDescription(state, unit.label),
            valueSuffix = unit.label,
        )
        val weekly = state.weeklyChangeKg
        ChartLegend(
            entries = listOf(
                LegendEntry("Daily weigh-in", MaterialTheme.colorScheme.tertiary),
                LegendEntry(
                    label = if (weekly != null && abs(weekly) > 0.005) {
                        val signed = unit.fromKg(weekly)
                        val arrow = if (weekly < 0) "−" else "+"
                        "Smoothed trend · $arrow${format(abs(signed))} ${unit.label}/week"
                    } else {
                        "Smoothed trend"
                    },
                    color = MaterialTheme.colorScheme.primary,
                    isLine = true,
                ),
            ),
        )
    }
}

/**
 * Sleep, steps and resting heart rate over the selected range, from Health Connect. The whole card is absent when none of
 * the three has a day with data (no permission, no watch): days with no reading are left out of the lines, not drawn as zero.
 */
private fun LazyListScope.healthItem(state: ProgressUiState) {
    val sleep = HealthSeries.sleepHours(state.health)
    val steps = HealthSeries.steps(state.health)
    val resting = HealthSeries.restingHeartRate(state.health)
    if (sleep.isEmpty() && steps.isEmpty() && resting.isEmpty()) return
    item(key = "health") {
        SectionCard(title = "Sleep, steps and heart rate", caption = "from Health Connect · ${state.range.caption}") {
            HealthSeriesChart("Sleep", sleep, "h a night") { "%.1f".format(it) }
            HealthSeriesChart("Steps", steps, "a day", { v, d -> "%,.${d}f".format(v) }) { "%,d".format(it.roundToInt()) }
            HealthSeriesChart("Resting heart rate", resting, "bpm") { it.roundToInt().toString() }
        }
    }
}

@Composable
private fun HealthSeriesChart(
    label: String,
    points: List<DayPoint>,
    unit: String,
    formatY: (Double, Int) -> String = { v, d -> "%.${d}f".format(v) },
    show: (Double) -> String,
) {
    if (points.isEmpty()) return
    val average = points.map { it.value }.average()
    Eyebrow(text = label, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
    Text(
        text = "${show(average)} $unit on average · latest ${show(points.last().value)} · ${points.size} ${if (points.size == 1) "day" else "days"} with data",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (points.size >= 2) {
        val chart = points.map { ChartPoint(it.dayStartMillis.toDouble(), it.value, "Health Connect") }
        TrendLineChart(
            raw = chart,
            trend = chart,
            description = "$label over time, from Health Connect. Average ${show(average)} $unit, latest ${show(points.last().value)}.",
            modifier = Modifier.height(120.dp),
            valueSuffix = unit,
            formatValue = show,
            formatY = formatY,
        )
    }
}

/** Progress's primary action. Everything else on the screen is something to read. */
@Composable
private fun LogWeightButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = IntakeShape.Button,
        modifier = Modifier.fillMaxWidth().height(52.dp).pressScale(),
    ) { Text("Log weight") }
}

@Composable
private fun AdherenceCard(state: ProgressUiState) {
    SectionCard(
        title = "Days logged",
        caption = "last ${minOf(state.daysInWindow, 28)} days",
    ) {
        // SPEC §11: never a streak that resets to zero on one bad day.
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "${state.daysLogged}",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = "of the last ${state.daysInWindow} days logged",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }
        Spacer(Modifier.height(11.dp))
        AdherenceHeatmap(
            days = state.dailyKcal.takeLast(28),
            target = state.target?.kcal?.toDouble(),
        )
    }
}

@Composable
private fun CaloriesCard(state: ProgressUiState) {
    SectionCard(title = "Calories vs target", caption = state.range.caption) {
        TargetBarChart(
            values = state.dailyKcal.map { it ?: 0.0 },
            target = state.target?.kcal?.toDouble(),
            description = intakeDescription(state),
        )
        ChartLegend(
            entries = listOfNotNull(
                LegendEntry("Calories logged", HeatFull),
                state.target?.let {
                    LegendEntry(
                        label = "Target",
                        color = MaterialTheme.colorScheme.tertiary,
                        isLine = true,
                    )
                },
            ),
        )
    }
}

/** Body measurements and progress photos, which only appear once there is data for them. */
private fun LazyListScope.detailItems(state: ProgressUiState) {
    if (state.metrics.any { it.hasMeasurements() }) {
        item(key = "measurements") {
            SectionCard(title = "Measurements") {
                MeasurementLines(state)
            }
        }
    }

    if (state.photos.isNotEmpty()) {
        item(key = "photos") {
            SectionCard(title = "Progress photos") {
                LazyHorizontalGrid(
                    rows = GridCells.Fixed(1),
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(items = state.photos, key = { it.id }) { metric ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.aspectRatio(0.75f),
                        ) {
                            AsyncImage(
                                model = metric.photoUri,
                                contentDescription = "Progress photo",
                                contentScale = ContentScale.Crop,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The mockup's week summary: four figures as a two-by-two grid of tiles rather
 * than a list of label/value rows. Each tile is a number you can read at a
 * glance; the confidence qualifier sits underneath where it applies to all of
 * them.
 */
@Composable
private fun WeeklySummaryCard(state: ProgressUiState, viewModel: ProgressViewModel) {
    val tdee = state.tdee
    val unit = state.settings.weightUnit
    val weekly = state.weeklyChangeKg

    SectionCard(title = "This week", caption = state.range.caption) {
        val tiles = listOf(
            (state.averageIntake?.let { "${"%,d".format(it.roundToInt())}" } ?: "—")
                to "avg kcal / day",
            (
                weekly?.let {
                    val signed = unit.fromKg(it)
                    "${if (it < 0) "−" else "+"}${format(abs(signed))} ${unit.label}"
                } ?: "—"
                ) to "avg weight change",
            // SPEC §8: when data is too sparse, say so plainly rather than
            // showing a confident number.
            (tdee?.let { "≈${"%,d".format(it.estimatedTdee.roundToInt())}" } ?: "—")
                to "maintenance kcal",
            (state.proteinHitRate?.let { "${(it * 100).roundToInt()}%" } ?: "—")
                to "protein target hit",
        )

        tiles.chunked(2).forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                pair.forEach { (value, caption) ->
                    SummaryTile(
                        value = value,
                        caption = caption,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        ConfidenceLine(
            text = when {
                tdee == null -> "Needs 14 days of weigh-ins and logs"
                else -> "${tdee.confidence.describe().replaceFirstChar { it.uppercase() }} · " +
                    "based on your intake and weight history"
            },
        )

        if (tdee != null && tdee.confidence == TdeeConfidence.LOW) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Treat that maintenance figure as provisional: there is not yet enough " +
                    "overlap between weigh-ins and logged days to measure it properly.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = viewModel::recalculateTdee,
            enabled = !state.isRecalculating,
            shape = IntakeShape.Button,
            modifier = Modifier.fillMaxWidth().height(50.dp),
        ) {
            Text(if (state.isRecalculating) "Recalculating…" else "Recalculate now")
        }
    }
}

@Composable
private fun MeasurementLines(state: ProgressUiState) {
    val unit = state.settings.lengthUnit
    val sites = listOf<Triple<String, (com.zeroindex.intake.data.db.entity.BodyMetricEntity) -> Double?, String>>(
        Triple("Waist", { it.waistCm }, unit.label),
        Triple("Chest", { it.chestCm }, unit.label),
        Triple("Arm", { it.armCm }, unit.label),
        Triple("Thigh", { it.thighCm }, unit.label),
        Triple("Hip", { it.hipCm }, unit.label),
    )

    sites.forEach { (label, selector, suffix) ->
        val points = state.metrics.mapNotNull { metric ->
            selector(metric)?.let { ChartPoint(metric.measuredAt.toDouble(), unit.fromCm(it), ChartMath.sourceLabel(metric.source)) }
        }
        if (points.size < 2) return@forEach
        Eyebrow(
            text = label,
            modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
        )
        TrendLineChart(
            raw = points,
            trend = points,
            description = "$label over time, in $suffix. " +
                "Latest ${format(points.last().y)} $suffix.",
            modifier = Modifier.height(120.dp),
            valueSuffix = suffix,
            gapDays = null,
        )
    }
}

private fun com.zeroindex.intake.data.db.entity.BodyMetricEntity.hasMeasurements(): Boolean =
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
            append(
                ", $direction ${format(abs(state.settings.weightUnit.fromKg(weekly)))} " +
                    "$unitLabel per week",
            )
        }
        append(", from ${state.metrics.size} weigh-ins.")
    }
}

private fun intakeDescription(state: ProgressUiState): String {
    val logged = state.dailyKcal.count { it != null && it > 0 }
    val target = state.target?.kcal
    val over = state.dailyKcal.count { it != null && target != null && it > target }
    return buildString {
        append("Calories per day across $logged logged days")
        if (target != null) append(", target $target kcal, $over days over")
        append(".")
    }
}

private fun format(value: Double): String = UnitConverter.round(value, 1).toString()

private val HEADLINE_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")

/**
 * Amendment §2: does more training volume come with a better jump, or a
 * worse one? Weekly tonnage as bars, best vertical that week as the line.
 */
@Composable
private fun PerformanceCard(state: ProgressUiState, onAddJump: () -> Unit) {
    val weeks = state.performance
    val length = state.settings.lengthUnit
    val weight = state.settings.weightUnit
    val jumps = weeks.mapNotNull { it.bestVerticalCm }
    val latest = jumps.lastOrNull()

    SectionCard(title = "Explosive output", caption = "weekly") {
        if (latest != null) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = format(length.fromCm(latest)),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = "${length.label} · latest vertical",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 3.dp),
                )
            }
        }
        OverlayChart(
            bars = weeks.map { weight.fromKg(it.tonnageKg) },
            line = weeks.map { w -> w.bestVerticalCm?.let(length::fromCm) },
            description = buildString {
                append("Weekly training volume and best vertical jump over ${weeks.size} weeks. ")
                if (jumps.size >= 2) {
                    append(
                        "Jump went from ${format(length.fromCm(jumps.first()))} to " +
                            "${format(length.fromCm(jumps.last()))} ${length.label}.",
                    )
                } else {
                    append("Not enough jump tests to show a trend yet.")
                }
            },
        )
        val maxTonnage = weeks.maxOfOrNull { it.tonnageKg } ?: 0.0
        ChartLegend(
            entries = listOf(
                LegendEntry(
                    "Weekly volume (to ${"%,d".format(weight.fromKg(maxTonnage).roundToInt())} ${weight.label})",
                    HeatPartial,
                ),
                LegendEntry("Best vertical (${length.label})", MaterialTheme.colorScheme.tertiary, isLine = true),
            ),
        )
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = onAddJump,
            shape = IntakeShape.Button,
            modifier = Modifier.fillMaxWidth().height(50.dp),
        ) { Text("Log a jump test") }
    }
}
