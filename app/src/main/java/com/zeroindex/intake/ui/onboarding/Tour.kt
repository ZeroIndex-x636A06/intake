package com.zeroindex.intake.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateRectAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clearAndSetSemantics
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.runtime.withFrameNanos
import com.zeroindex.intake.domain.onboarding.BubblePlacement
import com.zeroindex.intake.domain.onboarding.PxRect
import com.zeroindex.intake.domain.onboarding.TourIds
import com.zeroindex.intake.domain.onboarding.TourPlan
import com.zeroindex.intake.domain.onboarding.TourStep
import com.zeroindex.intake.domain.onboarding.TourSteps
import com.zeroindex.intake.ui.motion.LocalReduceMotion
import com.zeroindex.intake.ui.motion.Motion
import com.zeroindex.intake.ui.theme.IntakeShape
import kotlin.math.min

/**
 * A reusable coach-mark tour. Screens mark the things worth pointing at with
 * [Modifier.tourTarget]; the [TourOverlay] dims everything else, cuts a rounded spotlight around
 * the current target and shows a bubble with Next and Skip.
 *
 * A target counts as "present" when it is composed (it has reported bounds) or when its screen says
 * so with [TourTargetsPresent], which is how a lazily composed item that is currently scrolled away
 * is still part of the tour. Steps whose target is missing are skipped, never shown empty.
 */
@Stable
class TourState {
    private val bounds = mutableStateMapOf<String, Rect>()
    private val declared = mutableStateMapOf<Any, Set<String>>()

    var running by mutableStateOf(false)
        private set
    var steps by mutableStateOf<List<TourStep>>(emptyList())
        private set
    var index by mutableIntStateOf(0)
        private set

    /** Scrolls the list that holds the targets by the given pixels; set by the screen that scrolls. */
    var scroller: (suspend (Float) -> Unit)? = null

    /** Called once when the tour ends, with true when it ran to the last step. */
    var onFinished: ((completed: Boolean) -> Unit)? = null

    /** The id of every target that exists right now. */
    val available: Set<String>
        get() = bounds.keys.toSet() + declared.values.flatten()

    fun boundsOf(id: String): Rect? = bounds[id]

    internal fun register(id: String, rect: Rect) {
        if (bounds[id] != rect) bounds[id] = rect
    }

    internal fun unregister(id: String) {
        bounds.remove(id)
    }

    internal fun declare(owner: Any, ids: Set<String>) {
        declared[owner] = ids
    }

    internal fun undeclare(owner: Any) {
        declared.remove(owner)
    }

    /** Starts the Today tour with whichever steps have a target. False when nothing can be shown. */
    fun start(all: List<TourStep> = TourSteps.today): Boolean {
        val shown = TourPlan.filter(all, available)
        if (shown.isEmpty()) return false
        steps = shown
        index = 0
        running = true
        return true
    }

    fun next() {
        val to = TourPlan.advance(index, steps.size)
        if (to == null) finish(completed = true) else index = to
    }

    fun back() {
        index = TourPlan.previous(index)
    }

    fun skip() = finish(completed = false)

    private fun finish(completed: Boolean) {
        if (!running) return
        running = false
        steps = emptyList()
        index = 0
        onFinished?.invoke(completed)
    }

    /** The current step could not be brought on screen: drop it and carry on with the rest. */
    internal fun dropCurrent() {
        if (!running) return
        val remaining = steps.toMutableList().also { if (index in it.indices) it.removeAt(index) }
        if (remaining.isEmpty()) {
            finish(completed = true)
        } else {
            steps = remaining
            index = index.coerceAtMost(remaining.lastIndex)
        }
    }

    /**
     * Scrolls until [id] sits between [topLimit] and [bottomLimit]. Returns false when the target
     * never appears (so the step is skipped). Without a scroller, a target that exists is accepted
     * where it is.
     */
    internal suspend fun bringIntoView(id: String, forward: Boolean, topLimit: Float, bottomLimit: Float): Boolean {
        repeat(MAX_SCROLL_STEPS) {
            val r = bounds[id]
            if (r == null) {
                val s = scroller ?: return id in declared.values.flatten().toSet()
                s(if (forward) SEARCH_STEP_PX else -SEARCH_STEP_PX)
                withFrameNanos { }
            } else {
                if (r.height > bottomLimit - topLimit) return true
                val dy = when {
                    r.bottom > bottomLimit -> r.bottom - bottomLimit + EDGE_PX
                    r.top < topLimit -> r.top - topLimit - EDGE_PX
                    else -> return true
                }
                val s = scroller ?: return true
                s(dy)
                withFrameNanos { }
                // A scroll that moved nothing (end of the list) leaves the target where it was.
                if (bounds[id] == r) return true
            }
        }
        return bounds[id] != null
    }

    private companion object {
        const val MAX_SCROLL_STEPS = 24
        const val SEARCH_STEP_PX = 700f
        const val EDGE_PX = 24f
    }
}

val LocalTourState = staticCompositionLocalOf<TourState?> { null }

/** Marks this element as something the tour can point at. Does nothing outside a tour host. */
fun Modifier.tourTarget(id: String): Modifier = composed {
    val tour = LocalTourState.current
    if (tour == null) {
        Modifier
    } else {
        DisposableEffect(tour, id) { onDispose { tour.unregister(id) } }
        Modifier.onGloballyPositioned { coordinates ->
            if (coordinates.isAttached) tour.register(id, coordinates.boundsInRoot())
        }
    }
}

/**
 * Says which targets this screen has even when they are not composed right now (a lazy list item
 * further down), so the tour does not drop them from its plan. Remove the id when the thing is gone.
 */
@Composable
fun TourTargetsPresent(ids: Set<String>) {
    val tour = LocalTourState.current ?: return
    val owner = remember { Any() }
    DisposableEffect(tour, ids) {
        tour.declare(owner, ids)
        onDispose { tour.undeclare(owner) }
    }
}

/** Lets the tour scroll this list to reach a target that is off screen. */
@Composable
fun TourScrollHook(state: LazyListState, reduceMotion: Boolean = LocalReduceMotion.current) {
    val tour = LocalTourState.current ?: return
    DisposableEffect(tour, state, reduceMotion) {
        tour.scroller = { dy ->
            if (reduceMotion) state.scrollBy(dy) else state.animateScrollBy(dy, Motion.standardTween(false, Motion.DurationLong))
        }
        onDispose { tour.scroller = null }
    }
}

/** Shown over the whole app. Put it last in the root `Box`, so it draws above the pill. */
@Composable
fun TourOverlay(tour: TourState, modifier: Modifier = Modifier) {
    val reduce = LocalReduceMotion.current
    BackHandler(enabled = tour.running) { tour.skip() }

    var container by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val topInset = WindowInsets.systemBars.getTop(density).toFloat()
    val bottomInset = WindowInsets.systemBars.getBottom(density).toFloat()
    val edge = with(density) { 12.dp.toPx() }

    // The rectangle currently spotlighted, once the step's target is on screen.
    var shown by remember { mutableStateOf<Rect?>(null) }
    var shownIndex by remember { mutableIntStateOf(-1) }
    var lastIndex by remember { mutableIntStateOf(0) }

    val step = tour.steps.getOrNull(tour.index)
    LaunchedEffect(tour.running, tour.index, tour.steps, container) {
        if (!tour.running || step == null || container == IntSize.Zero) return@LaunchedEffect
        val forward = tour.index >= lastIndex
        lastIndex = tour.index
        val pill = tour.boundsOf(TourIds.PILL)
        val bottomLimit = if (pill != null && pill.width > pill.height) pill.top - edge else container.height - bottomInset - edge
        val ok = tour.bringIntoView(step.targetId, forward, topInset + edge, bottomLimit)
        val r = tour.boundsOf(step.targetId)
        if (ok && r != null) {
            shown = r
            shownIndex = tour.index
        } else {
            tour.dropCurrent()
        }
    }

    AnimatedVisibility(
        visible = tour.running,
        // Plain fades in both modes: reduced motion only removes the spotlight's travel.
        enter = fadeIn(tween(if (reduce) Motion.DurationShort else Motion.DurationMedium)),
        exit = fadeOut(tween(if (reduce) Motion.DurationShort else Motion.DurationMedium)),
        modifier = modifier,
    ) {
        Box(Modifier.fillMaxSize().onSizeChanged { container = it }) {
            val target = shown
            if (target != null) {
                Scrim(target, reduce)
                Bubble(
                    tour = tour,
                    target = target,
                    ready = shownIndex == tour.index,
                    topInset = topInset,
                    bottomInset = bottomInset,
                )
            } else {
                Scrim(null, reduce)
            }
        }
    }
}

@Composable
private fun Scrim(target: Rect?, reduce: Boolean) {
    val density = LocalDensity.current
    val pad = with(density) { 8.dp.toPx() }
    val maxCorner = with(density) { 22.dp.toPx() }
    val scrim = Color.Black.copy(alpha = 0.62f)
    val ring = MaterialTheme.colorScheme.primaryContainer
    val padded = target?.let { Rect(it.left - pad, it.top - pad, it.right + pad, it.bottom + pad) }
    val animated by animateRectAsState(
        targetValue = padded ?: Rect.Zero,
        animationSpec = if (reduce) snap() else tween(Motion.DurationMedium, easing = Motion.Standard),
        label = "tour-spotlight",
    )
    Canvas(
        Modifier
            .fillMaxSize()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            // Taps on the dim area do nothing; the bubble's buttons are the way on. The scrim is
            // decoration for a screen reader and holds no focus.
            .pointerInput(Unit) { detectTapGestures { } }
            .clearAndSetSemantics { },
    ) {
        drawRect(scrim)
        if (padded != null) {
            val corner = CornerRadius(min(maxCorner, min(animated.width, animated.height) / 2f))
            drawRoundRect(
                color = Color.Black,
                topLeft = Offset(animated.left, animated.top),
                size = Size(animated.width, animated.height),
                cornerRadius = corner,
                blendMode = BlendMode.Clear,
            )
            drawRoundRect(
                color = ring,
                topLeft = Offset(animated.left, animated.top),
                size = Size(animated.width, animated.height),
                cornerRadius = corner,
                style = Stroke(width = 2.dp.toPx()),
            )
        }
    }
}

@Composable
private fun Bubble(
    tour: TourState,
    target: Rect,
    ready: Boolean,
    topInset: Float,
    bottomInset: Float,
) {
    val density = LocalDensity.current
    val margin = with(density) { 16.dp.toPx() }
    val gap = with(density) { 16.dp.toPx() }
    val maxWidth = with(density) { 340.dp.roundToPx() }

    Layout(
        content = { BubbleCard(tour, ready) },
        modifier = Modifier.fillMaxSize(),
    ) { measurables, constraints ->
        val placeable = measurables.first().measure(
            Constraints(maxWidth = min(maxWidth, constraints.maxWidth - 2 * margin.toInt()).coerceAtLeast(0)),
        )
        val spot = PxRect(target.left - 8f, target.top - 8f, target.right + 8f, target.bottom + 8f)
        val at = BubblePlacement.place(
            target = spot,
            screenWidth = constraints.maxWidth.toFloat(),
            screenHeight = constraints.maxHeight.toFloat(),
            bubbleWidth = placeable.width.toFloat(),
            bubbleHeight = placeable.height.toFloat(),
            gap = gap,
            margin = margin,
            topInset = topInset,
            bottomInset = bottomInset,
        )
        layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(at.x.toInt(), at.y.toInt()) }
    }
}

@Composable
private fun BubbleCard(tour: TourState, ready: Boolean) {
    val step = tour.steps.getOrNull(tour.index) ?: return
    val last = tour.index == tour.steps.lastIndex
    Surface(
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = IntakeShape.Card,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        // Read the bubble first, before anything behind it (traversal order), without blocking focus.
        modifier = Modifier.semantics { isTraversalGroup = true; traversalIndex = -1f },
    ) {
        Column(Modifier.padding(16.dp)) {
            Crossfade(
                targetState = step,
                animationSpec = tween(Motion.DurationShort),
                label = "tour-bubble-text",
                // Announced each time the step changes.
                modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            ) { s ->
                Column {
                    Text(
                        text = "Tour, ${TourPlan.label(tour.index, tour.steps.size)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(text = s.text, style = MaterialTheme.typography.bodyLarge)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TextButton(onClick = tour::skip, modifier = Modifier.heightIn(min = 48.dp)) { Text("Skip tour") }
                Spacer(Modifier.weight(1f))
                if (tour.index > 0) {
                    TextButton(onClick = tour::back, modifier = Modifier.heightIn(min = 48.dp)) { Text("Back") }
                }
                Button(
                    onClick = tour::next,
                    enabled = ready,
                    shape = IntakeShape.Button,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(if (last) "Done" else "Next") }
            }
        }
    }
}
