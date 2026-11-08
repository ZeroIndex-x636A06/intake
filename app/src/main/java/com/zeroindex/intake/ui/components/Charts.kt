package com.zeroindex.intake.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.zeroindex.intake.ui.theme.HeatFull
import com.zeroindex.intake.ui.theme.HeatPartial
import com.zeroindex.intake.ui.theme.IntakeShape
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** One (x, y) sample. x is usually a local day index. */
data class ChartPoint(val x: Double, val y: Double)

/**
 * Raw points with a smoothed line over them (SPEC §11). Drawn on a Canvas
 * rather than a chart library: the spec allows either, and this keeps the
 * dependency surface small and the rendering exactly as described.
 *
 * The mockup gives the plot faint horizontal gridlines and a baseline, draws
 * the raw weigh-ins as clay dots ringed in the surface colour so they stay
 * legible where they overlap, and reserves the primary colour for the trend.
 * That split matters: the trend is the number you act on, the dots are the
 * noise it is smoothing away.
 *
 * The chart is summarised in [description] for TalkBack, since a Canvas says
 * nothing on its own.
 */
@Composable
fun TrendLineChart(
    raw: List<ChartPoint>,
    trend: List<ChartPoint>,
    description: String,
    modifier: Modifier = Modifier,
    rawColor: Color = MaterialTheme.colorScheme.tertiary,
    trendColor: Color = MaterialTheme.colorScheme.primary,
) {
    if (raw.isEmpty() && trend.isEmpty()) {
        ChartPlaceholder("Nothing to plot yet", modifier)
        return
    }

    val all = raw + trend
    val minX = all.minOf { it.x }
    val maxX = max(all.maxOf { it.x }, minX + 1.0)
    // Pad the y range so the line is never flush against the edge.
    val rawMinY = all.minOf { it.y }
    val rawMaxY = all.maxOf { it.y }
    val pad = max((rawMaxY - rawMinY) * 0.12, 0.3)
    val minY = rawMinY - pad
    val maxY = rawMaxY + pad

    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val ringColor = MaterialTheme.colorScheme.surface

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(150.dp)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        // Four gridlines and a baseline, as in the mockup's repeating gradient.
        val rows = 4
        repeat(rows) { index ->
            val y = size.height / rows * index
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx(),
            )
        }
        drawLine(
            color = gridColor,
            start = Offset(0f, size.height),
            end = Offset(size.width, size.height),
            strokeWidth = 1.dp.toPx(),
        )

        fun px(p: ChartPoint) = Offset(
            x = ((p.x - minX) / (maxX - minX) * size.width).toFloat(),
            y = (size.height - (p.y - minY) / (maxY - minY) * size.height).toFloat(),
        )

        if (trend.size >= 2) {
            val path = Path()
            trend.forEachIndexed { index, point ->
                val o = px(point)
                if (index == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
            }
            drawPath(
                path = path,
                color = trendColor,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
            )
        }

        // Dots last so they sit above the trend line, each with a surface ring.
        raw.forEach { point ->
            val centre = px(point)
            drawCircle(color = ringColor, radius = 5.5.dp.toPx(), center = centre)
            drawCircle(color = rawColor, radius = 3.5.dp.toPx(), center = centre)
        }
    }
}

/**
 * Calories against target, with the target as a labelled dashed reference line
 * (SPEC §11). Bars over target are drawn in [overColor] and also labelled in
 * the summary, so colour is not the only signal.
 */
@Composable
fun TargetBarChart(
    values: List<Double>,
    target: Double?,
    description: String,
    modifier: Modifier = Modifier,
    barColor: Color = HeatFull,
    overColor: Color = MaterialTheme.colorScheme.tertiary,
) {
    if (values.isEmpty()) {
        ChartPlaceholder("No days logged in this range", modifier)
        return
    }

    val maxValue = max(values.max(), target ?: 0.0) * 1.1
    val referenceColor = MaterialTheme.colorScheme.tertiary

    Column(modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clearAndSetSemantics { contentDescription = description },
        ) {
            if (maxValue <= 0.0) return@Canvas
            val slot = size.width / values.size
            val barWidth = min(slot * 0.65f, 16.dp.toPx())
            val radius = 5.dp.toPx()

            values.forEachIndexed { index, value ->
                if (value <= 0.0) return@forEachIndexed
                val height = (value / maxValue * size.height).toFloat()
                val left = slot * index + (slot - barWidth) / 2f
                val color = if (target != null && value > target) overColor else barColor
                // The mockup rounds only the top of each bar: draw it fully
                // rounded, then square off everything below the corner radius.
                drawRoundRect(
                    color = color,
                    topLeft = Offset(left, size.height - height),
                    size = Size(barWidth, height),
                    cornerRadius = CornerRadius(radius),
                )
                if (height > radius) {
                    drawRect(
                        color = color,
                        topLeft = Offset(left, size.height - height + radius),
                        size = Size(barWidth, height - radius),
                    )
                }
            }

            if (target != null && target > 0.0) {
                val y = (size.height - target / maxValue * size.height).toFloat()
                drawLine(
                    color = referenceColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(8.dp.toPx(), 6.dp.toPx()),
                    ),
                )
            }
        }
        if (target != null && target > 0.0) {
            Text(
                text = "${"%,d".format(target.roundToInt())} kcal target",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
    }
}

/**
 * A calendar heatmap of days logged (SPEC §11). Deliberately not a streak:
 * the spec is explicit that a counter resetting to zero on one missed day makes
 * people quit, so the summary above it reads "18 of the last 21 days".
 *
 * [todayIndex] gets the mockup's outline so the current day is findable without
 * counting back from the end.
 */
@Composable
fun AdherenceHeatmap(
    /** One entry per day, oldest first; null means nothing logged. */
    days: List<Double?>,
    target: Double?,
    modifier: Modifier = Modifier,
    todayIndex: Int = days.lastIndex,
) {
    val empty = MaterialTheme.colorScheme.surfaceVariant
    val outline = MaterialTheme.colorScheme.primary

    val logged = days.count { it != null && it > 0 }
    val description = "$logged of ${days.size} days logged."

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        days.chunked(7).forEachIndexed { weekIndex, week ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                week.forEachIndexed { dayIndex, value ->
                    val absolute = weekIndex * 7 + dayIndex
                    val color = when {
                        value == null || value <= 0.0 -> empty
                        target == null -> HeatFull
                        // Logged but well short of target reads as a partial day.
                        value < target * 0.6 -> HeatPartial
                        else -> HeatFull
                    }
                    Surface(
                        color = color,
                        shape = IntakeShape.Heat,
                        border = if (absolute == todayIndex) {
                            androidx.compose.foundation.BorderStroke(2.dp, outline)
                        } else {
                            null
                        },
                        modifier = Modifier.weight(1f).height(22.dp),
                    ) {}
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * The mockup's `.chart-legend`: what each mark means, since the charts carry no
 * axis labels.
 */
@Composable
fun ChartLegend(
    entries: List<LegendEntry>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        entries.forEach { entry ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (entry.isLine) {
                    Box(Modifier.width(14.dp).height(2.dp).background(entry.color))
                } else {
                    Box(Modifier.size(8.dp).background(entry.color, CircleShape))
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    text = entry.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** One mark in a [ChartLegend]: a dot for samples, a rule for a derived line. */
data class LegendEntry(val label: String, val color: Color, val isLine: Boolean = false)

@Composable
private fun ChartPlaceholder(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().height(110.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Shared y-axis-free summary used under the charts. */
internal fun Double.asKcal(): String = "${roundToInt()} kcal"
