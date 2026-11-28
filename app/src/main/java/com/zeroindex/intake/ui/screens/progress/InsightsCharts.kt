package com.zeroindex.intake.ui.screens.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zeroindex.intake.domain.charts.ChartMath
import com.zeroindex.intake.domain.time.DateText
import com.zeroindex.intake.domain.units.KcalFormat
import com.zeroindex.intake.domain.units.UnitConverter
import com.zeroindex.intake.ui.components.ChartPoint
import com.zeroindex.intake.ui.motion.drawPathPartial
import com.zeroindex.intake.ui.motion.rememberDrawIn
import com.zeroindex.intake.ui.theme.intake
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.min

/*
 * Insights charts (DESIGN-V6 b.7). Phase 1 owns `ui/components/Charts.kt`; these are the Insights versions drawn in the new
 * language: ink trend line over grey dots with a lime end marker, rounded 5/2 dp bars, a dashed ink target, `soft` bar
 * tracks, and macro/sleep/heart colours from the tokens. The numbers fed in are exactly the ones the screen computed before.
 */

/** A bar with 5 dp rounded top corners and 2 dp bottom corners (the mockup's `border-radius: 5px 5px 2px 2px`). */
private fun DrawScope.roundedBar(color: Color, left: Float, top: Float, width: Float, height: Float) {
    if (height <= 0f || width <= 0f) return
    val top5 = CornerRadius(5.dp.toPx())
    val bottom2 = CornerRadius(2.dp.toPx())
    val path = Path().apply {
        addRoundRect(
            RoundRect(
                left = left, top = top, right = left + width, bottom = top + height,
                topLeftCornerRadius = top5, topRightCornerRadius = top5,
                bottomRightCornerRadius = bottom2, bottomLeftCornerRadius = bottom2,
            ),
        )
    }
    drawPath(path, color)
}

/**
 * The weight trend: faint grey dots for each weigh-in, the smoothed trend as an ink line, and a lime marker ringed in ink on the
 * latest trend value. Scrub horizontally for a day's value (a vertical drag still scrolls the page). Same maths and the same
 * scrub readout as the shared trend chart; only the marks and colours differ.
 */
@Composable
fun InsightsTrendChart(
    raw: List<ChartPoint>,
    trend: List<ChartPoint>,
    description: String,
    modifier: Modifier = Modifier,
    valueSuffix: String = "",
    formatValue: (Double) -> String = { UnitConverter.round(it, 1).toString() },
    formatY: (Double, Int) -> String = { v, decimals -> "%.${decimals}f".format(v) },
    gapDays: Int? = ChartMath.GAP_DAYS,
    formatX: (Double) -> String = { DateText.mediumWithWeekday(ChartMath.localDate(it.toLong(), ZoneId.systemDefault())) },
    minYSpan: Double = 0.0,
    maxTicks: Int = 4,
    dateLabels: Boolean = false,
    plotHeight: Dp = 150.dp,
    endMarker: Boolean = true,
) {
    if (raw.isEmpty() && trend.isEmpty()) {
        Box(modifier.fillMaxWidth().height(110.dp), contentAlignment = Alignment.Center) {
            Text("Nothing to plot yet", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val sortedRaw = remember(raw) { raw.sortedBy { it.x } }
    val sortedTrend = remember(trend) { trend.sortedBy { it.x } }
    val trendXs = remember(sortedTrend) { sortedTrend.map { it.x } }
    val all = sortedRaw + sortedTrend
    val minX = all.minOf { it.x }
    val maxX = max(all.maxOf { it.x }, minX + 1.0)
    val (minY, maxY) = ChartMath.yRange(all.minOf { it.y }, all.maxOf { it.y }, minYSpan)
    val ticks = remember(minY, maxY) { ChartMath.niceTicks(minY, maxY, maxTicks) }
    val tickText = remember(ticks, formatY) {
        val decimals = ChartMath.tickDecimals(ticks)
        ticks.map { formatY(it, decimals) }
    }

    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val ink = MaterialTheme.colorScheme.onSurface
    val dotColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    val markerFill = MaterialTheme.intake.accent
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = labelColor)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val drawIn = rememberDrawIn()

    val gutterPx = remember(tickText, labelStyle) {
        (tickText.maxOfOrNull { measurer.measure(it, labelStyle, maxLines = 1, softWrap = false).size.width } ?: 0) +
            with(density) { 6.dp.toPx() }
    }
    var scrubIndex by remember(sortedRaw) { mutableStateOf<Int?>(null) }
    val xs = remember(sortedRaw) { sortedRaw.map { it.x } }
    val selected = scrubIndex?.let { sortedRaw.getOrNull(it) }
    val readout = selected?.let { p ->
        buildString {
            append(formatX(p.x)).append("  ").append(formatValue(p.y))
            if (valueSuffix.isNotEmpty()) append(" ").append(valueSuffix)
            if (sortedTrend.isNotEmpty() && sortedTrend != sortedRaw) {
                ChartMath.nearestIndex(trendXs, p.x)?.let { i ->
                    append("  ·  trend ").append(formatValue(sortedTrend[i].y))
                    if (valueSuffix.isNotEmpty()) append(" ").append(valueSuffix)
                }
            }
            p.source?.let { append("  ·  ").append(it) }
        }
    }

    Column(modifier.fillMaxWidth()) {
        Text(
            text = readout ?: "Drag across the chart to read a day",
            style = MaterialTheme.typography.labelMedium,
            color = if (readout != null) MaterialTheme.colorScheme.onSurface else labelColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().height(22.dp),
        )
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(plotHeight)
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
            // Keep the marker and dots inside the canvas edge.
            val inset = 8.dp.toPx()

            fun px(p: ChartPoint) = Offset(
                x = (plotLeft + inset + (p.x - minX) / (maxX - minX) * (plotW - 2 * inset)).toFloat(),
                y = (size.height - (p.y - minY) / (maxY - minY) * size.height).toFloat(),
            )

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

            // Raw weigh-ins sit behind the trend as quiet dots.
            sortedRaw.forEach { point ->
                val centre = px(point)
                if (centre.x <= plotLeft + plotW * drawIn.value + 1f) drawCircle(dotColor, radius = 2.5.dp.toPx(), center = centre)
            }

            if (sortedTrend.size >= 2) {
                val runs = if (gapDays == null) {
                    listOf(0..sortedTrend.lastIndex)
                } else {
                    ChartMath.runs(sortedTrend.map { it.x }, gapDays * ChartMath.DAY_MS)
                }
                clipRect(right = plotLeft + plotW * drawIn.value) {
                    runs.zipWithNext().forEach { (before, after) ->
                        drawLine(
                            color = ink.copy(alpha = 0.28f),
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
                        drawPath(path, ink, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }
            }

            if (endMarker && sortedTrend.isNotEmpty() && drawIn.value >= 1f) {
                val c = px(sortedTrend.last())
                drawCircle(ink, radius = 7.dp.toPx(), center = c)
                drawCircle(markerFill, radius = 5.dp.toPx(), center = c)
            }

            if (selected != null) {
                val c = px(selected)
                drawLine(labelColor.copy(alpha = 0.7f), Offset(c.x, 0f), Offset(c.x, size.height), 1.5.dp.toPx())
                drawCircle(ink, radius = 6.dp.toPx(), center = c)
                drawCircle(markerFill, radius = 4.dp.toPx(), center = c)
            }
        }

        if (dateLabels && sortedRaw.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(start = with(density) { gutterPx.toDp() }, top = 2.dp)) {
                Text(formatX(minX), style = labelStyle, maxLines = 1)
                Spacer(Modifier.weight(1f))
                Text(formatX(maxX), style = labelStyle, maxLines = 1)
            }
        }
    }
}

/**
 * Calories per day against the target: `soft` bars with rounded tops, `carb` (orange) on days over target, and the target as a
 * dashed ink line. Over-target days are also counted in [description], so colour is not the only signal.
 */
@Composable
fun CaloriesBarChart(
    values: List<Double>,
    target: Double?,
    description: String,
    modifier: Modifier = Modifier,
    plotHeight: Dp = 90.dp,
) {
    if (values.isEmpty()) {
        Box(modifier.fillMaxWidth().height(110.dp), contentAlignment = Alignment.Center) {
            Text(
                "No days logged in this range",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    val maxValue = max(values.max(), target ?: 0.0) * 1.1
    val barColor = MaterialTheme.intake.soft
    val overColor = MaterialTheme.intake.carb
    val ink = MaterialTheme.colorScheme.onSurface
    val grow = rememberDrawIn(600)

    Column(modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(plotHeight)
                .clearAndSetSemantics { contentDescription = description },
        ) {
            if (maxValue <= 0.0) return@Canvas
            val slot = size.width / values.size
            val gap = if (slot > 12.dp.toPx()) 5.dp.toPx() else slot * 0.2f
            val barWidth = min(slot - gap, 44.dp.toPx()).coerceAtLeast(1f)
            values.forEachIndexed { index, value ->
                if (value <= 0.0) return@forEachIndexed
                val h = (value / maxValue * size.height).toFloat() * grow.value
                val left = slot * index + (slot - barWidth) / 2f
                val color = if (target != null && value > target) overColor else barColor
                roundedBar(color, left, size.height - h, barWidth, h)
            }
            if (target != null && target > 0.0) {
                val y = (size.height - target / maxValue * size.height).toFloat()
                drawLine(
                    color = ink,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())),
                )
            }
        }
        if (target != null && target > 0.0) {
            Text(
                text = "${KcalFormat.format(target)} kcal target",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
    }
}

/** What the marks in [CaloriesBarChart] and [JumpOverlayChart] mean. Each entry is a dot, a dashed rule, or a rule. */
enum class LegendMark { Dot, DotOutlined, Dashed, Line }

data class InsightsLegendEntry(val label: String, val color: Color, val mark: LegendMark = LegendMark.Dot)

@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
fun InsightsLegend(entries: List<InsightsLegendEntry>, modifier: Modifier = Modifier) {
    // Wraps instead of squeezing at large font sizes.
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        entries.forEach { entry ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (entry.mark) {
                    LegendMark.Dot -> Box(Modifier.size(8.dp).background(entry.color, CircleShape))
                    LegendMark.DotOutlined -> Box(
                        Modifier.size(8.dp).background(entry.color, CircleShape)
                            .border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant, CircleShape),
                    )
                    LegendMark.Line -> Box(Modifier.width(14.dp).height(2.dp).background(entry.color))
                    LegendMark.Dashed -> Canvas(Modifier.width(14.dp).height(2.dp)) {
                        drawLine(
                            color = entry.color,
                            start = Offset(0f, size.height / 2f),
                            end = Offset(size.width, size.height / 2f),
                            strokeWidth = 2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx())),
                        )
                    }
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    text = entry.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** A row of small rounded bars (sleep, steps). The last bar can be lime with an ink outline, as on the mockup's steps tile. */
@Composable
fun MiniBars(
    values: List<Double>,
    color: Color,
    description: String,
    modifier: Modifier = Modifier,
    highlightLast: Boolean = false,
    height: Dp = 56.dp,
) {
    val accent = MaterialTheme.intake.accent
    val ink = MaterialTheme.colorScheme.onSurface
    val grow = rememberDrawIn(600)
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        if (values.isEmpty()) return@Canvas
        val peak = values.max().takeIf { it > 0.0 } ?: return@Canvas
        val gap = 5.dp.toPx()
        val barWidth = ((size.width - gap * (values.size - 1)) / values.size).coerceAtLeast(1f)
        values.forEachIndexed { i, v ->
            val h = (v / peak * size.height).toFloat().coerceAtLeast(2.dp.toPx()) * grow.value
            val left = i * (barWidth + gap)
            val last = highlightLast && i == values.lastIndex
            roundedBar(if (last) accent else color, left, size.height - h, barWidth, h)
            if (last) {
                drawRoundRect(
                    color = ink,
                    topLeft = Offset(left + 0.75.dp.toPx(), size.height - h + 0.75.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(barWidth - 1.5.dp.toPx(), h - 1.5.dp.toPx()),
                    cornerRadius = CornerRadius(4.dp.toPx()),
                    style = Stroke(1.5.dp.toPx()),
                )
            }
        }
    }
}

/** A small rounded line with no axes (resting heart rate). */
@Composable
fun MiniLine(
    values: List<Double>,
    color: Color,
    description: String,
    modifier: Modifier = Modifier,
    height: Dp = 56.dp,
) {
    val drawIn = rememberDrawIn()
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        if (values.size < 2) return@Canvas
        val lo = values.min()
        val hi = values.max()
        val span = (hi - lo).takeIf { it > 0.0 } ?: 1.0
        val pad = 4.dp.toPx()
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = pad + i.toFloat() / (values.size - 1) * (size.width - 2 * pad)
            val y = pad + (1f - ((v - lo) / span).toFloat()) * (size.height - 2 * pad)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPathPartial(path, color, 2.5.dp.toPx(), drawIn.value)
    }
}

/**
 * Days logged as a grid of round marks, seven to a row: filled ink = logged, pale = logged but under 60% of the target, soft =
 * nothing logged, and a ring round today's mark. Today is never "short": a partial day is still in progress.
 */
@Composable
fun DaysLoggedDots(
    days: List<Double?>,
    target: Double?,
    modifier: Modifier = Modifier,
    todayIndex: Int = days.lastIndex,
) {
    val full = MaterialTheme.colorScheme.onSurface
    val partial = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
    val empty = MaterialTheme.colorScheme.outlineVariant
    val description = "${days.count { it != null && it > 0 }} of ${days.size} days logged."
    Column(
        modifier = modifier
            .widthIn(max = 220.dp)
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        days.chunked(7).forEachIndexed { week, row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEachIndexed { d, value ->
                    val index = week * 7 + d
                    val color = when {
                        value == null || value <= 0.0 -> empty
                        target == null -> full
                        index == todayIndex -> full
                        value < target * 0.6 -> partial
                        else -> full
                    }
                    val dot = Modifier.weight(1f).aspectRatio(1f)
                    if (index == todayIndex) {
                        Box(dot.border(1.5.dp, full, CircleShape).padding(2.dp).background(color, CircleShape))
                    } else {
                        Box(dot.background(color, CircleShape))
                    }
                }
                repeat(7 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * Weekly training volume as rounded `soft` bars and the best vertical as an ink line with dots. Each series is scaled to its
 * own range (they do not share a unit), exactly as the shared overlay chart does; [description] says it in words.
 */
@Composable
fun JumpOverlayChart(
    bars: List<Double>,
    line: List<Double?>,
    description: String,
    modifier: Modifier = Modifier,
) {
    if (bars.isEmpty()) {
        Box(modifier.fillMaxWidth().height(110.dp), contentAlignment = Alignment.Center) {
            Text(
                "Log a jump test and a few sessions to compare them",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val barColor = MaterialTheme.intake.soft
    val lineColor = MaterialTheme.colorScheme.onSurface
    val ring = MaterialTheme.colorScheme.surface
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
        val gap = if (slot > 12.dp.toPx()) 5.dp.toPx() else slot * 0.2f
        val barWidth = min(slot - gap, 40.dp.toPx()).coerceAtLeast(1f)
        bars.forEachIndexed { i, v ->
            if (v <= 0) return@forEachIndexed
            val h = (v / maxBar * size.height * 0.85).toFloat() * drawIn.value
            roundedBar(barColor, slot * i + (slot - barWidth) / 2f, size.height - h, barWidth, h)
        }
        fun y(v: Double) = (size.height - (v - (minLine - pad)) / ((maxLine + pad) - (minLine - pad)) * size.height).toFloat()
        val centres = line.mapIndexedNotNull { i, v -> v?.let { Offset(slot * i + slot / 2f, y(it)) } }
        if (centres.size >= 2) {
            val path = Path()
            centres.forEachIndexed { i, o -> if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y) }
            drawPathPartial(path, lineColor, 2.dp.toPx(), drawIn.value)
        }
        centres.forEach { o ->
            if (o.x <= size.width * drawIn.value + 1f) {
                drawCircle(ring, radius = 5.5.dp.toPx(), center = o)
                drawCircle(lineColor, radius = 3.5.dp.toPx(), center = o)
            }
        }
    }
}
