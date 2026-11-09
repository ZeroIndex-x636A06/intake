package com.zeroindex.intake.ui.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// ---------------------------------------------------------------------------------------------
// Pulse and flash

/**
 * Plays a short scale pulse (1 -> 1.04 -> 1, 350 ms) each time [trigger] increases. The first value
 * is the baseline: nothing plays when the screen opens.
 */
@Composable
fun Modifier.pulseOn(trigger: Int, peak: Float = 1.04f): Modifier {
    val reduce = LocalReduceMotion.current
    val scale = remember { Animatable(1f) }
    val first = remember { intArrayOf(trigger) }
    LaunchedEffect(trigger) {
        if (trigger == first[0] || reduce) return@LaunchedEffect
        scale.animateTo(peak, tween(120, easing = Motion.Standard))
        scale.animateTo(1f, tween(230, easing = Motion.EmphasizedDecelerate))
    }
    return this.graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}

/** A brief colour wash behind the content (about 700 ms) each time [trigger] increases: a personal record. */
@Composable
fun Modifier.flashOn(trigger: Int, color: Color): Modifier {
    val reduce = LocalReduceMotion.current
    val alpha = remember { Animatable(0f) }
    val first = remember { intArrayOf(trigger) }
    LaunchedEffect(trigger) {
        if (trigger == first[0] || reduce) return@LaunchedEffect
        alpha.snapTo(0.55f)
        alpha.animateTo(0f, tween(700, easing = Motion.Standard))
    }
    return this.drawBehind {
        val a = alpha.value
        if (a > 0f) drawRect(color.copy(alpha = a))
    }
}

// ---------------------------------------------------------------------------------------------
// Check mark helpers

/** The tick, scaled into a square of [side] at [topLeft]; [fraction] of its length is drawn. */
private fun DrawScope.drawTick(topLeft: Offset, side: Float, fraction: Float, color: Color, strokePx: Float) {
    if (fraction <= 0f) return
    val path = Path().apply {
        moveTo(topLeft.x + side * 0.24f, topLeft.y + side * 0.54f)
        lineTo(topLeft.x + side * 0.43f, topLeft.y + side * 0.72f)
        lineTo(topLeft.x + side * 0.76f, topLeft.y + side * 0.32f)
    }
    val measure = PathMeasure().apply { setPath(path, false) }
    val partial = Path()
    measure.getSegment(0f, measure.length * fraction.coerceIn(0f, 1f), partial, true)
    drawPath(partial, color, style = Stroke(strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/**
 * A round check box whose fill grows from the centre and whose tick draws itself (about 250 ms).
 * 48 dp tall hit area is the caller's job (wrap it in a toggleable or IconButton); this is the picture.
 */
@Composable
fun AnimatedCheck(
    checked: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    fill: Color,
    outline: Color,
    tick: Color,
) {
    val reduce = LocalReduceMotion.current
    val progress = remember { Animatable(if (checked) 1f else 0f) }
    LaunchedEffect(checked, reduce) {
        val target = if (checked) 1f else 0f
        if (reduce) progress.snapTo(target) else progress.animateTo(target, tween(Motion.DurationMedium, easing = Motion.EmphasizedDecelerate))
    }
    Canvas(modifier.size(size)) {
        val p = progress.value
        val stroke = 2.dp.toPx()
        val r = this.size.minDimension / 2f
        drawCircle(outline.copy(alpha = outline.alpha * (1f - p)), radius = r - stroke / 2f, style = Stroke(stroke))
        if (p > 0f) drawCircle(fill, radius = r * p)
        drawTick(Offset.Zero, this.size.minDimension, ((p - 0.35f) / 0.65f), tick, 2.2.dp.toPx())
    }
}

// ---------------------------------------------------------------------------------------------
// Celebration burst

/**
 * The once-a-day protein burst: a tick that draws itself inside a ring of 14 particles flying
 * outwards, about 600 ms. Plays each time [trigger] increases (the first value is the baseline).
 * Lay it over the ring; it takes no clicks and has no accessibility node.
 */
@Composable
fun BurstOverlay(trigger: Int, color: Color, modifier: Modifier = Modifier) {
    val reduce = LocalReduceMotion.current
    val progress = remember { Animatable(1f) }
    val first = remember { intArrayOf(trigger) }
    LaunchedEffect(trigger) {
        if (trigger == first[0] || reduce) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(1f, tween(BURST_MILLIS, easing = LinearEasing))
    }
    Canvas(modifier.fillMaxSize()) {
        val p = progress.value
        if (p >= 1f) return@Canvas
        val centre = Offset(size.width / 2f, size.height / 2f)
        val reach = min(size.width, size.height) / 2f
        val eased = 1f - (1f - p) * (1f - p) * (1f - p)
        val fade = if (p < 0.6f) 1f else 1f - (p - 0.6f) / 0.4f
        for (i in 0 until PARTICLES) {
            val angle = (2.0 * PI * i / PARTICLES).toFloat() + (i % 2) * 0.12f
            val dist = reach * (0.45f + 0.5f * eased) * (if (i % 2 == 0) 1f else 0.82f)
            val dot = (if (i % 3 == 0) 4.5.dp else 3.dp).toPx() * (1f - 0.5f * p)
            drawCircle(
                color = color.copy(alpha = fade),
                radius = dot,
                center = Offset(centre.x + cos(angle) * dist, centre.y + sin(angle) * dist),
            )
        }
        val side = reach * 0.9f
        drawTick(
            topLeft = Offset(centre.x - side / 2f, centre.y - side / 2f),
            side = side,
            fraction = min(1f, p / 0.45f),
            color = color.copy(alpha = fade),
            strokePx = 4.dp.toPx(),
        )
    }
}

private const val BURST_MILLIS = 600
private const val PARTICLES = 14

// ---------------------------------------------------------------------------------------------
// Water wave

/**
 * Hydration fill: a rounded tank whose water rises to [fraction] and whose leading edge is a gentle
 * sine wave. The wave phase only runs while the tank is on screen and reduce-motion is off; the fill
 * level itself still animates (or snaps) so the number is always honest.
 */
@Composable
fun WaterWave(
    fraction: Float,
    water: Color,
    track: Color,
    modifier: Modifier = Modifier,
    height: Dp = 22.dp,
) {
    val reduce = LocalReduceMotion.current
    val level = remember { Animatable(0f) }
    LaunchedEffect(fraction, reduce) {
        val target = fraction.coerceIn(0f, 1f)
        if (reduce) level.snapTo(target) else level.animateTo(target, tween(Motion.DurationLong + 250, easing = Motion.EmphasizedDecelerate))
    }
    val (onScreen, tracker) = rememberOnScreenState()
    val partial = fraction > 0f && fraction < 1f
    val phase = rememberLoopingPhase(running = !reduce && onScreen && partial, periodMillis = 2_600, to = (2.0 * PI).toFloat())
    val percent = (fraction.coerceIn(0f, 1f) * 100).toInt()
    Box(
        modifier
            .then(tracker)
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .semantics(mergeDescendants = false) { stateDescription = "$percent percent of today's water target" }
            .drawBehind {
                drawRect(track)
                val w = size.width
                val h = size.height
                val x = w * level.value
                if (x <= 0f) return@drawBehind
                if (level.value >= 0.995f) {
                    drawRect(water)
                    return@drawBehind
                }
                val amp = min(h * 0.16f, 3.5.dp.toPx())
                drawWave(x, h, amp, phase.value, water.copy(alpha = 0.45f), shift = 1.7f)
                drawWave(x, h, amp, phase.value, water, shift = 0f)
            },
    )
}

private fun DrawScope.drawWave(edgeX: Float, h: Float, amp: Float, phase: Float, color: Color, shift: Float) {
    val path = Path()
    path.moveTo(0f, 0f)
    val steps = 24
    for (i in 0..steps) {
        val y = h * i / steps
        val x = edgeX + amp * sin(y / h * 2f * PI.toFloat() * 1.25f + phase + shift)
        path.lineTo(x, y)
    }
    path.lineTo(0f, h)
    path.close()
    drawPath(path, color)
}

// ---------------------------------------------------------------------------------------------
// Rest timer ring

/**
 * The circular countdown for rest. [remainingFraction] is 1 at the start of the rest and 0 at the end;
 * it glides between whole-second updates so the arc moves smoothly. [content] sits in the middle.
 */
@Composable
fun RestRing(
    remainingFraction: Float,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    stroke: Dp = 8.dp,
    color: Color,
    track: Color,
    description: String,
    content: @Composable () -> Unit,
) {
    val reduce = LocalReduceMotion.current
    val shown = remember { Animatable(remainingFraction) }
    LaunchedEffect(remainingFraction, reduce) {
        if (reduce) shown.snapTo(remainingFraction) else shown.animateTo(remainingFraction, tween(1_000, easing = LinearEasing))
    }
    Box(
        modifier.size(size).clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val s = stroke.toPx()
            val inset = s / 2f
            val arc = Size(this.size.width - s, this.size.height - s)
            drawArc(track, -90f, 360f, false, Offset(inset, inset), arc, style = Stroke(s, cap = StrokeCap.Round))
            val sweep = 360f * shown.value.coerceIn(0f, 1f)
            if (sweep > 0f) {
                drawArc(color, -90f, sweep, false, Offset(inset, inset), arc, style = Stroke(s, cap = StrokeCap.Round))
            }
        }
        content()
    }
}

// ---------------------------------------------------------------------------------------------
// Chart draw-in

/** 0 -> 1 over about 800 ms the first time a chart shows; already 1 with reduce-motion. */
@Composable
fun rememberDrawIn(millis: Int = 800): androidx.compose.runtime.State<Float> {
    val reduce = LocalReduceMotion.current
    val progress = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(reduce) {
        if (reduce) progress.snapTo(1f) else progress.animateTo(1f, tween(millis, easing = Motion.EmphasizedDecelerate))
    }
    return progress.asState()
}

/** Strokes the first [fraction] of [path]'s length: the line draws itself in. */
fun DrawScope.drawPathPartial(path: Path, color: Color, strokeWidthPx: Float, fraction: Float) {
    if (fraction <= 0f) return
    if (fraction >= 1f) {
        drawPath(path, color, style = Stroke(strokeWidthPx, cap = StrokeCap.Round, join = StrokeJoin.Round))
        return
    }
    val measure = PathMeasure().apply { setPath(path, false) }
    val partial = Path()
    measure.getSegment(0f, measure.length * fraction, partial, true)
    drawPath(partial, color, style = Stroke(strokeWidthPx, cap = StrokeCap.Round, join = StrokeJoin.Round))
}
