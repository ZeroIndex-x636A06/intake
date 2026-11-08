package com.zeroindex.intake.ui.screens.progress

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.zeroindex.intake.domain.model.TdeeConfidence
import com.zeroindex.intake.domain.units.UnitConverter
import com.zeroindex.intake.ui.components.AdherenceHeatmap
import com.zeroindex.intake.ui.components.ChartPoint
import com.zeroindex.intake.ui.components.ScreenHeading
import com.zeroindex.intake.ui.components.TargetBarChart
import com.zeroindex.intake.ui.components.TrendLineChart
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun ProgressScreen(
    contentPadding: PaddingValues,
    onAddMeasurement: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProgressViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val unit = state.settings.weightUnit

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
        item(key = "heading") { ScreenHeading("Progress") }

        item(key = "ranges") {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ProgressRange.entries.forEach { range ->
                    FilterChip(
                        selected = state.range == range,
                        onClick = { viewModel.selectRange(range) },
                        label = { Text(range.label) },
                    )
                }
            }
        }

        item(key = "weight") {
            SectionCard(title = "Weight") {
                // SPEC §8: the trend line is what is shown, with raw points
                // behind it rather than instead of it.
                TrendLineChart(
                    raw = state.metrics.map {
                        ChartPoint(it.measuredAt.toDouble(), unit.fromKg(it.weightKg))
                    },
                    trend = state.trend.map {
                        ChartPoint(it.dayIndex.toDouble() * 86_400_000.0, unit.fromKg(it.trendKg))
                    },
                    description = weightDescription(state, unit.label),
                )
                Spacer(Modifier.height(8.dp))
                val trendWeight = state.trendWeightKg
                val weekly = state.weeklyChangeKg
                Text(
                    text = buildString {
                        if (trendWeight != null) {
                            append("Trend ${format(unit.fromKg(trendWeight))} ${unit.label}")
                        } else {
                            append("No weigh-ins in this range")
                        }
                        if (weekly != null && abs(weekly) > 0.005) {
                            val direction = if (weekly < 0) "down" else "up"
                            append(
                                ", $direction ${format(abs(unit.fromKg(weekly)))} " +
                                    "${unit.label} a week",
                            )
                        }
                        append(".")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onAddMeasurement,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) { Text("Add a weigh-in") }
            }
        }

        item(key = "summary") { WeeklySummaryCard(state, viewModel) }

        item(key = "adherence") {
            SectionCard(title = "Logging") {
                // SPEC §11: never a streak that resets to zero on one bad day.
                Text(
                    text = "${state.daysLogged} of the last ${state.daysInWindow} days logged.",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(12.dp))
                AdherenceHeatmap(
                    days = state.dailyKcal.takeLast(28),
                    target = state.target?.kcal?.toDouble(),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Calories against target",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                TargetBarChart(
                    values = state.dailyKcal.map { it ?: 0.0 },
                    target = state.target?.kcal?.toDouble(),
                    description = intakeDescription(state),
                )
            }
        }

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
}

@Composable
private fun WeeklySummaryCard(state: ProgressUiState, viewModel: ProgressViewModel) {
    val tdee = state.tdee
    val unit = state.settings.weightUnit

    SectionCard(title = "This week") {
        val average = state.averageIntake
        SummaryRow(
            "Average intake",
            if (average != null) "${average.roundToInt()} kcal" else "Not enough logged",
        )
        val weekly = state.weeklyChangeKg
        SummaryRow(
            "Average weight change",
            if (weekly != null) {
                "${format(unit.fromKg(weekly))} ${unit.label} a week"
            } else {
                "Needs two weigh-ins"
            },
        )

        // SPEC §8: when data is too sparse, say so plainly rather than showing a
        // confident number.
        SummaryRow(
            "Estimated maintenance",
            if (tdee == null) {
                "Needs 14 days of weigh-ins and logs"
            } else {
                "${tdee.estimatedTdee.roundToInt()} kcal · ${tdee.confidence.describe()}"
            },
        )
        state.proteinHitRate?.let {
            SummaryRow("Protein target hit", "${(it * 100).roundToInt()}% of logged days")
        }

        if (tdee != null && tdee.confidence == TdeeConfidence.LOW) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Treat that number as provisional: there is not yet enough overlap " +
                    "between weigh-ins and logged days to measure maintenance properly.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = viewModel::recalculateTdee,
            enabled = !state.isRecalculating,
            modifier = Modifier.fillMaxWidth().height(50.dp),
        ) {
            Text(if (state.isRecalculating) "Recalculating..." else "Recalculate now")
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
            selector(metric)?.let { ChartPoint(metric.measuredAt.toDouble(), unit.fromCm(it)) }
        }
        if (points.size < 2) return@forEach
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp),
        )
        TrendLineChart(
            raw = points,
            trend = points,
            description = "$label over time, in $suffix. " +
                "Latest ${format(points.last().y)} $suffix.",
            modifier = Modifier.height(120.dp),
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f),
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 10.dp),
            )
            content()
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
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
