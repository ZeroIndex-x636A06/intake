package com.zeroindex.intake.ui.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.toggleable
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zeroindex.intake.domain.motion.MotionRules

/*
 * Reusable motion building blocks. Every one of them
 *  - reads LocalReduceMotion and falls back to "no motion, final state",
 *  - reads animated values inside graphicsLayer / draw lambdas so only the layer redraws,
 *  - never loops while off-screen.
 * Documented in docs/DESIGN-MOTION.md.
 */

// ---------------------------------------------------------------------------------------------
// Press feedback

/**
 * Scales the element to 0.97 while a finger is down on it. Put it on a primary button or a tappable
 * card: `Button(modifier = Modifier.pressScale())`. It watches the pointer in the Initial pass and
 * never consumes anything, so the element's own click still works.
 */
@Composable
fun Modifier.pressScale(pressedScale: Float = Motion.PressScale): Modifier {
    val reduce = LocalReduceMotion.current
    if (reduce) return this
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = tween(Motion.DurationShort, easing = Motion.Standard),
        label = "pressScale",
    )
    return this
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                pressed = true
                waitForUpOrCancellation(pass = PointerEventPass.Initial)
                pressed = false
            }
        }
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
}

// ---------------------------------------------------------------------------------------------
// Visibility tracking, used to pause endless animations

/** Reports whether any part of this element is inside the window. Scrolled away or clipped = false. */
@Composable
fun rememberOnScreenState(): Pair<Boolean, Modifier> {
    var visible by remember { mutableStateOf(true) }
    val tracker = Modifier.onGloballyPositioned { visible = !it.boundsInWindow().isEmpty }
    return visible to tracker
}

/**
 * A 0..[periodTo] phase that loops forever, but only while [running]. It stops (and keeps its value)
 * the moment [running] turns false, so nothing animates for a screen the person cannot see.
 */
@Composable
fun rememberLoopingPhase(running: Boolean, periodMillis: Int, to: Float): androidx.compose.runtime.State<Float> {
    val phase = remember { Animatable(0f) }
    LaunchedEffect(running) {
        if (running) {
            phase.snapTo(phase.value % to)
            phase.animateTo(
                targetValue = phase.value + to,
                animationSpec = infiniteRepeatable(tween(periodMillis, easing = LinearEasing)),
            )
        }
    }
    return phase.asState()
}

// ---------------------------------------------------------------------------------------------
// Shimmer skeletons

/**
 * Turns the element into a skeleton block: a quiet fill with a soft highlight sliding across it.
 * Static when reduce-motion is on, and paused while the block is off-screen.
 */
@Composable
fun Modifier.shimmer(shape: Shape = RoundedCornerShape(8.dp)): Modifier {
    val reduce = LocalReduceMotion.current
    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)
    val (onScreen, tracker) = rememberOnScreenState()
    val phase = rememberLoopingPhase(running = !reduce && onScreen, periodMillis = 1_300, to = 1f)
    return this
        .then(tracker)
        .clip(shape)
        .background(base)
        .drawWithContent {
            drawContent()
            if (!reduce) {
                val band = size.width * 0.6f
                val x = -band + (size.width + band) * (phase.value % 1f)
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, highlight, Color.Transparent),
                        startX = x,
                        endX = x + band,
                    ),
                )
            }
        }
}

/** One skeleton line. [fraction] of the width, [height] tall. */
@Composable
fun SkeletonLine(modifier: Modifier = Modifier, fraction: Float = 1f, height: Dp = 14.dp) {
    Box(modifier.fillMaxWidth(fraction).height(height).shimmer(RoundedCornerShape(6.dp)))
}

/**
 * Placeholder rows for a list that is loading. One accessibility node ("Loading"), not a pile of
 * empty boxes for a screen reader to walk through.
 */
@Composable
fun SkeletonList(
    modifier: Modifier = Modifier,
    rows: Int = 4,
    rowHeight: Dp = 56.dp,
    label: String = "Loading",
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = label },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        repeat(rows) { i ->
            Box(
                Modifier
                    .fillMaxWidth(if (i % 2 == 0) 1f else 0.92f)
                    .height(rowHeight)
                    .shimmer(RoundedCornerShape(14.dp)),
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// List stagger

/** Remembers, for one list, which items have already played their entrance. Survives scroll-back. */
class StaggerGate {
    private val seen = HashSet<Any>()
    internal fun firstTime(key: Any): Boolean = seen.add(key)
}

@Composable
fun rememberStaggerGate(): StaggerGate = remember { StaggerGate() }

/**
 * Fade and slide-up on an item's first appearance: at most the first eight items, 30 ms apart
 * (see [MotionRules.staggerDelayMs]). Items that scroll back in, or recompose, do not replay,
 * because [gate] has already seen their [key].
 */
@Composable
fun Modifier.staggerIn(gate: StaggerGate, key: Any, index: Int): Modifier {
    val reduce = LocalReduceMotion.current
    val delay = MotionRules.staggerDelayMs(index)
    val animate = remember { !reduce && delay != null && gate.firstTime(key) }
    if (!animate) {
        return this
    }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(
            1f,
            tween(Motion.DurationMedium, delayMillis = delay ?: 0, easing = Motion.EmphasizedDecelerate),
        )
    }
    return this.graphicsLayer {
        val p = progress.value
        alpha = p
        translationY = (1f - p) * 14.dp.toPx()
    }
}

// ---------------------------------------------------------------------------------------------
// Count-up text

/**
 * A number that counts up to [value] the first time it shows and glides to each new value after.
 * Screen readers get the final text at once ([spoken], or the formatted value) and never hear the
 * in-between numbers.
 */
@Composable
fun CountUpText(
    value: Double,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.displaySmall,
    color: Color = MaterialTheme.colorScheme.onSurface,
    decimals: Int = 0,
    prefix: String = "",
    suffix: String = "",
    spoken: String? = null,
) {
    val reduce = LocalReduceMotion.current
    val shown = remember { Animatable(if (reduce) value.toFloat() else 0f) }
    LaunchedEffect(value, reduce) {
        if (reduce) shown.snapTo(value.toFloat())
        else shown.animateTo(value.toFloat(), tween(Motion.DurationLong + 250, easing = Motion.EmphasizedDecelerate))
    }
    val final = prefix + MotionRules.countUpText(value, 1f, decimals) + suffix
    Text(
        text = prefix + MotionRules.countUpText(shown.value.toDouble(), 1f, decimals) + suffix,
        style = style,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Clip,
        modifier = modifier.clearAndSetSemantics { contentDescription = spoken ?: final },
    )
}

// ---------------------------------------------------------------------------------------------
// Expanding sections

/** A chevron that turns half a turn when [expanded]. */
@Composable
fun ExpandChevron(expanded: Boolean, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    val reduce = LocalReduceMotion.current
    val turn by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = Motion.standardTween(reduce),
        label = "chevron",
    )
    Icon(
        imageVector = Icons.Filled.ExpandMore,
        contentDescription = null,
        tint = tint,
        modifier = modifier.size(24.dp).graphicsLayer { rotationZ = turn },
    )
}

/**
 * Progressive disclosure: a 48 dp header row with a chevron, content that grows with
 * `animateContentSize`. Collapsed by default so the screen leads with the few numbers that matter.
 */
@Composable
fun MoreDetail(
    title: String,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
    content: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    val reduce = LocalReduceMotion.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (reduce) Modifier else Modifier.animateContentSize(animationSpec = tween(Motion.DurationMedium, easing = Motion.Standard))),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .toggleable(value = expanded, role = Role.Button, onValueChange = { expanded = it })
                .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            ExpandChevron(expanded, tint = MaterialTheme.colorScheme.primary)
        }
        if (expanded) {
            Column(Modifier.padding(bottom = 4.dp)) { content() }
        }
    }
}
