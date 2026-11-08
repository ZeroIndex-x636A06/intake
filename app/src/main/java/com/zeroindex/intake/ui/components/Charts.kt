package com.zeroindex.intake.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
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
 * The chart is summarised in [description] for TalkBack, since a Canvas says
 * nothing on its own.
 */
@Composable
fun TrendLineChart(
    raw: List<ChartPoint>,
    trend: List<ChartPoint>,
    description: String,
    modifier: Modifier = Modifier,
    rawColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
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

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        fun px(p: ChartPoint) = Offset(
            x = ((p.x - minX) / (maxX - minX) * size.width).toFloat(),
            y = (size.height - (p.y - minY) / (maxY - minY) * size.height).toFloat(),
        )

        raw.forEach { point ->
            drawCircle(color = rawColor, radius = 3.dp.toPx(), center = px(point))
        }

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
    }
}

/**
 * Calories against target, with the target as a reference line (SPEC §11).
 * Bars over target are drawn in [overColor] and also labelled in the summary,
 * so colour is not the only signal.
 */
@Composable
fun TargetBarChart(
    values: List<Double>,
    target: Double?,
    description: String,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
    overColor: Color = MaterialTheme.colorScheme.tertiary,
) {
    if (values.isEmpty()) {
        ChartPlaceholder("No days logged in this range", modifier)
        return
    }

    val maxValue = max(values.max(), target ?: 0.0) * 1.1
    val referenceColor = MaterialTheme.colorScheme.onSurfaceVariant

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(150.dp)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        if (maxValue <= 0.0) return@Canvas
        val slot = size.width / values.size
        val barWidth = min(slot * 0.65f, 18.dp.toPx())

        values.forEachIndexed { index, value ->
            if (value <= 0.0) return@forEachIndexed
            val height = (value / maxValue * size.height).toFloat()
            val left = slot * index + (slot - barWidth) / 2f
            drawRoundRect(
                color = if (target != null && value > target) overColor else barColor,
                topLeft = Offset(left, size.height - height),
                size = androidx.compose.ui.geometry.Size(barWidth, height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
            )
        }

        if (target != null && target > 0.0) {
            val y = (size.height - target / maxValue * size.height).toFloat()
            drawLine(
                color = referenceColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(
                    floatArrayOf(8.dp.toPx(), 6.dp.toPx()),
                ),
            )
        }
    }
}

/**
 * A calendar heatmap of days logged (SPEC §11). Deliberately not a streak:
 * the spec is explicit that a counter resetting to zero on one missed day makes
 * people quit, so the summary above it reads "18 of the last 21 days".
 */
@Composable
fun AdherenceHeatmap(
    /** One entry per day, oldest first; null means nothing logged. */
    days: List<Double?>,
    target: Double?,
    modifier: Modifier = Modifier,
) {
    val empty = MaterialTheme.colorScheme.surfaceVariant
    val partial = MaterialTheme.colorScheme.primary.copy(alpha = 0.40f)
    val full = MaterialTheme.colorScheme.primary
    val over = MaterialTheme.colorScheme.tertiary

    val logged = days.count { it != null && it > 0 }
    val description = "$logged of ${days.size} days logged."

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = description },
    ) {
        days.chunked(7).forEach { week ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                week.forEach { value ->
                    val color = when {
                        value == null || value <= 0.0 -> empty
                        target == null -> full
                        value > target * 1.1 -> over
                        value < target * 0.6 -> partial
                        else -> full
                    }
                    Surface(
                        color = color,
                        shape = RoundedCornerShape(3.dp),
                        modifier = Modifier.size(22.dp),
                    ) {}
                }
                repeat(7 - week.size) { Spacer(Modifier.size(22.dp)) }
            }
        }
    }
}

@Composable
private fun ChartPlaceholder(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().height(120.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Shared y-axis-free summary used under the charts. */
internal fun Double.asKcal(): String = "${roundToInt()} kcal"
