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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import com.zeroindex.intake.domain.charts.ChartMath
import com.zeroindex.intake.domain.units.UnitConverter
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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
import com.zeroindex.intake.ui.motion.drawPathPartial
import com.zeroindex.intake.ui.motion.rememberDrawIn
import com.zeroindex.intake.ui.theme.HeatFull
import com.zeroindex.intake.ui.theme.HeatPartial
import com.zeroindex.intake.ui.theme.IntakeShape
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * One (x, y) sample. x is epoch millis on the Progress charts. [source] is shown in the scrub tooltip when known.
 */
data class ChartPoint(val x: Double, val y: Double, val source: String? = null)

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
 * The y axis is labelled in [formatY] (the user's unit). A trend line is not drawn solid across more than [gapDays] days
 * with no data: it breaks there, and a faint dashed bridge shows that the two ends are not a measured stretch.
 *
 * Press and drag horizontally to scrub: a guide line, the nearest raw point highlighted, and a tooltip with the date,
 * the value ([formatValue], then [valueSuffix]) and the point's source. A vertical drag still scrolls the page.
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
    valueSuffix: String = "",
    formatValue: (Double) -> String = { UnitConverter.round(it, 1).toString() },
    formatY: (Double, Int) -> String = { v, decimals -> "%.${decimals}f".format(v) },
    gapDays: Int? = ChartMath.GAP_DAYS,
    formatX: (Double) -> String = { ChartMath.localDate(it.toLong(), ZoneId.systemDefault()).format(TOOLTIP_DATE) },
) {
    if (raw.isEmpty() && trend.isEmpty()) {
        ChartPlaceholder("Nothing to plot yet", modifier)
        return
    }

    val sortedRaw = remember(raw) { raw.sortedBy { it.x } }
    val sortedTrend = remember(trend) { trend.sortedBy { it.x } }
    val all = sortedRaw + sortedTrend
    val minX = all.minOf { it.x }
    val maxX = max(all.maxOf { it.x }, minX + 1.0)
    // Pad the y range so the line is never flush against the edge.
    val rawMinY = all.minOf { it.y }
    val rawMaxY = all.maxOf { it.y }
    val pad = max((rawMaxY - rawMinY) * 0.12, 0.3)
    val minY = rawMinY - pad
    val maxY = rawMaxY + pad
    val ticks = remember(minY, maxY) { ChartMath.niceTicks(minY, maxY, 4) }
    val tickText = remember(ticks, formatY) {
        val decimals = ChartMath.tickDecimals(ticks)
        ticks.map { formatY(it, decimals) }
    }

    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val ringColor = MaterialTheme.colorScheme.surface
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val tooltipBg = MaterialTheme.colorScheme.inverseSurface
    val tooltipFg = MaterialTheme.colorScheme.inverseOnSurface
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = labelColor)
    val tipStyle = MaterialTheme.typography.labelMedium.copy(color = tooltipFg)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    // The trend line draws itself in on first show; the dots pop in as it passes them.
    val drawIn = rememberDrawIn()

    // Room on the left for the axis labels, measured so a long label ("1,250") is never clipped.
    val gutterPx = remember(tickText, labelStyle) {
        (tickText.maxOfOrNull { measurer.measure(it, labelStyle, maxLines = 1, softWrap = false).size.width } ?: 0) +
            with(density) { 6.dp.toPx() }
    }
    var scrubIndex by remember(sortedRaw) { mutableStateOf<Int?>(null) }
    val xs = remember(sortedRaw) { sortedRaw.map { it.x } }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(150.dp)
            .clearAndSetSemantics { contentDescription = description }
            .pointerInput(xs, minX, maxX, gutterPx) {
                if (xs.isEmpty()) return@pointerInput
                fun update(px: Float) {
                    val plotW = (size.width - gutterPx).coerceAtLeast(1f)
                    val dataX = minX + ((px - gutterPx) / plotW).coerceIn(0f, 1f) * (maxX - minX)
                    val index = ChartMath.nearestIndex(xs, dataX)
                    if (index != scrubIndex) {
                        scrubIndex = index
                        if (index != null) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // Only a horizontal move claims the gesture; a vertical one is left for the page to scroll.
                    val moved = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                    if (moved != null) {
                        update(moved.position.x)
                        horizontalDrag(moved.id) { change ->
                            change.consume()
                            update(change.position.x)
                        }
                    }
                    scrubIndex = null
                }
            },
    ) {
        val plotLeft = gutterPx
        val plotW = size.width - plotLeft

        fun px(p: ChartPoint) = Offset(
            x = (plotLeft + (p.x - minX) / (maxX - minX) * plotW).toFloat(),
            y = (size.height - (p.y - minY) / (maxY - minY) * size.height).toFloat(),
        )

        // Gridlines at the labelled values, and a baseline, as in the mockup's repeating gradient.
        ticks.forEachIndexed { i, tick ->
            val y = (size.height - (tick - minY) / (maxY - minY) * size.height).toFloat()
            drawLine(gridColor, Offset(plotLeft, y), Offset(size.width, y), 1.dp.toPx())
            val layout = measurer.measure(tickText[i], labelStyle, maxLines = 1, softWrap = false)
            drawText(
                layout,
                topLeft = Offset(
                    plotLeft - 6.dp.toPx() - layout.size.width,
                    (y - layout.size.height / 2f).coerceIn(0f, size.height - layout.size.height),
                ),
            )
        }
        drawLine(gridColor, Offset(plotLeft, size.height), Offset(size.width, size.height), 1.dp.toPx())

        if (sortedTrend.size >= 2) {
            val runs = if (gapDays == null) {
                listOf(0..sortedTrend.lastIndex)
            } else {
                ChartMath.runs(sortedTrend.map { it.x }, gapDays * ChartMath.DAY_MS)
            }
            clipRect(right = plotLeft + plotW * drawIn.value) {
                // A long stretch with no data is bridged only by a faint dashed line.
                runs.zipWithNext().forEach { (before, after) ->
                    drawLine(
                        color = trendColor.copy(alpha = 0.28f),
                        start = px(sortedTrend[before.last]),
                        end = px(sortedTrend[after.first]),
                        strokeWidth = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 6.dp.toPx())),
                    )
                }
                runs.forEach { run ->
                    if (run.first == run.last) return@forEach
                    val path = Path()
                    run.forEachIndexed { n, i ->
                        val o = px(sortedTrend[i])
                        if (n == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
                    }
                    drawPath(path, trendColor, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        }

        // Dots last so they sit above the trend line, each with a surface ring.
        sortedRaw.forEach { point ->
            val centre = px(point)
            val reached = centre.x <= plotLeft + plotW * drawIn.value + 1f
            if (reached) {
                drawCircle(color = ringColor, radius = 5.5.dp.toPx(), center = centre)
                drawCircle(color = rawColor, radius = 3.5.dp.toPx(), center = centre)
            }
        }

        // Scrub: a guide line, the nearest dot enlarged, and the tooltip.
        val selected = scrubIndex?.let { sortedRaw.getOrNull(it) }
        if (selected != null) {
            val c = px(selected)
            drawLine(labelColor.copy(alpha = 0.7f), Offset(c.x, 0f), Offset(c.x, size.height), 1.5.dp.toPx())
            drawCircle(ringColor, radius = 8.dp.toPx(), center = c)
            drawCircle(rawColor, radius = 6.dp.toPx(), center = c)

            val valueLine = formatValue(selected.y) + if (valueSuffix.isEmpty()) "" else " $valueSuffix"
            val text = buildString {
                append(formatX(selected.x)).append("\n").append(valueLine)
                if (selected.source != null) append(" · ").append(selected.source)
            }
            val layout = measurer.measure(text, tipStyle, maxLines = 2, softWrap = false)
            val padH = 8.dp.toPx()
            val padV = 5.dp.toPx()
            val boxW = layout.size.width + padH * 2
            val boxH = layout.size.height + padV * 2
            val left = (c.x - boxW / 2f).coerceIn(0f, (size.width - boxW).coerceAtLeast(0f))
            // Above the point when there is room, else below it, so the finger does not hide the tooltip.
            val wanted = if (c.y - boxH - 12.dp.toPx() >= 0f) c.y - boxH - 12.dp.toPx() else c.y + 12.dp.toPx()
            val top = wanted.coerceIn(0f, (size.height - boxH).coerceAtLeast(0f))
            drawRoundRect(tooltipBg, Offset(left, top), Size(boxW, boxH), CornerRadius(8.dp.toPx()))
            drawText(layout, topLeft = Offset(left + padH, top + padV))
        }
    }
}

private val TOOLTIP_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM yyyy")

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
    val grow = rememberDrawIn(600)

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
                val height = (value / maxValue * size.height).toFloat() * grow.value
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
 * Two series that do not share a unit on one plot (amendment §2): bars for
 * weekly training volume, a line with dots for jump height. Each is scaled to
 * its own range, so the chart shows whether they move together - which is the
 * question - without pretending pounds and inches share an axis. The legend
 * carries each series' real range, and [description] says it in words.
 */
@Composable
fun OverlayChart(
    bars: List<Double>,
    line: List<Double?>,
    description: String,
    modifier: Modifier = Modifier,
    barColor: Color = HeatPartial,
    lineColor: Color = MaterialTheme.colorScheme.tertiary,
) {
    if (bars.isEmpty()) {
        ChartPlaceholder("Log a jump test and a few sessions to compare them", modifier)
        return
    }
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val ringColor = MaterialTheme.colorScheme.surface
    val maxBar = bars.maxOrNull()?.takeIf { it > 0 } ?: 1.0
    val points = line.filterNotNull()
    val minLine = points.minOrNull() ?: 0.0
    val maxLine = points.maxOrNull() ?: 1.0
    val pad = max((maxLine - minLine) * 0.2, 1.0)
    val drawIn = rememberDrawIn()

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(140.dp)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        drawLine(gridColor, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
        val slot = size.width / bars.size
        val barWidth = min(slot * 0.6f, 18.dp.toPx())
        bars.forEachIndexed { i, v ->
            if (v <= 0) return@forEachIndexed
            val h = (v / maxBar * size.height * 0.85).toFloat() * drawIn.value
            drawRoundRect(
                color = barColor,
                topLeft = Offset(slot * i + (slot - barWidth) / 2f, size.height - h),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(4.dp.toPx()),
            )
        }
        fun y(v: Double) = (size.height - (v - (minLine - pad)) / ((maxLine + pad) - (minLine - pad)) * size.height).toFloat()
        val centres = line.mapIndexedNotNull { i, v -> v?.let { Offset(slot * i + slot / 2f, y(it)) } }
        if (centres.size >= 2) {
            val path = Path()
            centres.forEachIndexed { i, o -> if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y) }
            drawPathPartial(path, lineColor, 2.5.dp.toPx(), drawIn.value)
        }
        centres.forEach { o ->
            if (o.x <= size.width * drawIn.value + 1f) {
                drawCircle(ringColor, radius = 5.5.dp.toPx(), center = o)
                drawCircle(lineColor, radius = 3.5.dp.toPx(), center = o)
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
