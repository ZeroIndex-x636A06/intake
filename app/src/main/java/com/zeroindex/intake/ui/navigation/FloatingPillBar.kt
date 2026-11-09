package com.zeroindex.intake.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeroindex.intake.ui.motion.LocalReduceMotion
import com.zeroindex.intake.ui.motion.Motion
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * The floating navigation pill (SPEC-v3): a dark, fully rounded bar above the content with an icon
 * over a label per tab, and a lighter inner pill under the selected one that springs between tabs.
 * With [vertical] it is the tablet rail, the same pill stood on end.
 *
 * Touch is handled once for the whole pill rather than per item, so a finger can go down on one
 * tab and slide across the others: the highlight follows it, each new tab crossed gives a light
 * haptic tick, and lifting commits the tab under the finger. A tap is the same gesture without
 * the slide. The slide is an enhancement only: each item also exposes a click action to
 * accessibility services, so TalkBack's double-tap and switch access select tabs normally.
 *
 * A touch that starts inside the system back-gesture strip at a screen edge never slides (the
 * system owns it); it can still be a tap.
 *
 * [onSelect] is called with the item index, including for the already-selected item, which the
 * caller turns into "pop to this tab's root".
 */
@Composable
fun FloatingPillBar(
    destinations: List<TopLevelDestination>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    vertical: Boolean = false,
    /** The content region's recording, for a frosted-glass background (API 31+); null keeps the plain fill. */
    backdrop: PillBackdrop? = null,
) {
    val reduce = LocalReduceMotion.current
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val view = LocalView.current
    val gestureInsets = WindowInsets.systemGestures
    val blurOn = backdrop != null && PillBackdrop.supported
    val colors = pillColors(frosted = blurOn)
    val blurLayer = androidx.compose.ui.graphics.rememberGraphicsLayer()
    val count = destinations.size
    val currentOnSelect by rememberUpdatedState(onSelect)

    // Item rectangles and the touch surface's origin, both in root coordinates; the gesture and
    // the indicator subtract one from the other, so padding and rounding never matter.
    val itemRects = remember(count) { mutableStateListOf<Rect>().apply { repeat(count) { add(Rect.Zero) } } }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var hover by remember { mutableIntStateOf(PillSlideTracker.NONE) }
    val tracker = remember(density) { PillSlideTracker(hysteresisPx = with(density) { 6.dp.toPx() }) }

    val shownIndex = if (hover != PillSlideTracker.NONE) hover else selectedIndex
    val target = itemRects.getOrNull(shownIndex)?.takeIf { it.width > 0f }
    val targetStart = target?.let { (if (vertical) it.top - origin.y else it.left - origin.x) } ?: 0f
    val targetLength = target?.let { if (vertical) it.height else it.width } ?: 0f
    val crossStart = target?.let { if (vertical) it.left - origin.x else it.top - origin.y } ?: 0f
    val crossLength = target?.let { if (vertical) it.width else it.height } ?: 0f

    val start = remember { Animatable(0f) }
    val length = remember { Animatable(0f) }
    var placed by remember { mutableStateOf(false) }
    LaunchedEffect(targetStart, targetLength, reduce) {
        if (!placed || reduce) {
            start.snapTo(targetStart)
            length.snapTo(targetLength)
            placed = targetLength > 0f
        } else {
            launch { start.animateTo(targetStart, Motion.pillSpring(reduce)) }
            launch { length.animateTo(targetLength, Motion.pillSpring(reduce)) }
        }
    }

    Box(
        modifier = modifier
            .shadow(
                elevation = 12.dp,
                shape = CircleShape,
                ambientColor = Color.Black.copy(alpha = 0.30f),
                spotColor = Color.Black.copy(alpha = 0.30f),
            )
            .then(
                if (blurOn && backdrop != null) {
                    // The blurred backdrop first, the translucent tint over it.
                    Modifier.pillBlurBehind(
                        backdrop = backdrop,
                        blurLayer = blurLayer,
                        shape = CircleShape,
                        radiusPx = with(density) { 20.dp.toPx() },
                        pillOrigin = { origin },
                    )
                } else {
                    Modifier
                },
            )
            .background(colors.container, CircleShape)
            .border(1.dp, colors.border, CircleShape)
            .onGloballyPositioned { origin = it.positionInRoot() }
            .pointerInput(count, vertical, tracker) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val axis: (Offset) -> Float = { if (vertical) it.y else it.x }
                    val bounds = {
                        itemRects.map { r ->
                            if (vertical) ItemBounds(r.top - origin.y, r.bottom - origin.y)
                            else ItemBounds(r.left - origin.x, r.right - origin.x)
                        }
                    }
                    // Only a horizontal bar can collide with the edge back gesture.
                    val inGestureZone = !vertical && PillHitTest.isInSystemGestureZone(
                        xInWindow = origin.x + down.position.x,
                        windowWidth = view.width.toFloat(),
                        leftInset = gestureInsets.getLeft(this@pointerInput, layoutDirection).toFloat(),
                        rightInset = gestureInsets.getRight(this@pointerInput, layoutDirection).toFloat(),
                    )
                    try {
                        hover = tracker.down(axis(down.position), bounds()).hoverIndex
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null || change.isConsumed) {
                                tracker.cancel()
                                break
                            }
                            if (change.changedToUp()) {
                                tracker.up()?.let { currentOnSelect(it) }
                                break
                            }
                            if (!inGestureZone) {
                                val update = tracker.move(axis(change.position), bounds())
                                hover = update.hoverIndex
                                if (update.tick) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            }
                        }
                    } finally {
                        // Also runs when the system takes the gesture over (a back swipe): nothing
                        // is committed and the highlight returns to the selected tab.
                        tracker.cancel()
                        hover = PillSlideTracker.NONE
                    }
                }
            },
    ) {
        // The sliding highlight sits behind the items.
        if (placed && length.value > 0f) {
            Box(
                Modifier
                    .offset {
                        if (vertical) IntOffset(crossStart.roundToInt(), start.value.roundToInt())
                        else IntOffset(start.value.roundToInt(), crossStart.roundToInt())
                    }
                    .size(
                        width = with(density) { (if (vertical) crossLength else length.value).toDp() },
                        height = with(density) { (if (vertical) length.value else crossLength).toDp() },
                    )
                    .background(colors.indicator, CircleShape),
            )
        }
        val item: @Composable (Int, TopLevelDestination, Modifier) -> Unit = { index, destination, base ->
            PillItem(
                destination = destination,
                selected = index == selectedIndex,
                highlighted = index == shownIndex,
                colors = colors,
                onClick = { currentOnSelect(index) },
                modifier = base.onGloballyPositioned {
                    if (index < itemRects.size) {
                        itemRects[index] = Rect(
                            it.positionInRoot(),
                            Size(it.size.width.toFloat(), it.size.height.toFloat()),
                        )
                    }
                },
            )
        }
        if (vertical) {
            Column(
                Modifier.padding(6.dp).selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                destinations.forEachIndexed { index, destination ->
                    item(index, destination, Modifier.size(width = 64.dp, height = 60.dp))
                }
            }
        } else {
            Row(Modifier.padding(6.dp).selectableGroup()) {
                destinations.forEachIndexed { index, destination ->
                    item(index, destination, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PillItem(
    destination: TopLevelDestination,
    selected: Boolean,
    highlighted: Boolean,
    colors: PillColors,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val reduce = LocalReduceMotion.current
    val label = stringResource(destination.labelRes)
    val tint by animateColorAsState(
        targetValue = if (highlighted) colors.selected else colors.unselected,
        animationSpec = Motion.standardTween(reduce, Motion.DurationShort),
        label = "pill-item-tint",
    )
    Column(
        modifier = modifier
            .heightIn(min = 56.dp)
            // Touch is the pill's job; this only describes the item to accessibility services and
            // gives them a click action, so TalkBack double-tap and switch access work.
            .semantics(mergeDescendants = true) {
                role = Role.Tab
                this.selected = selected
                contentDescription = label
                onClick(label = label) {
                    onClick()
                    true
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(imageVector = destination.icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        Text(
            text = label,
            color = tint,
            fontSize = 11.sp,
            maxLines = 1,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/** Colours for the pill, all taken from the active colour scheme. */
internal class PillColors(
    val container: Color,
    val border: Color,
    val indicator: Color,
    val selected: Color,
    val unselected: Color,
)

/**
 * Dark theme: the surface colour, a hairline to lift it off the background, the primary tint for
 * the selected tab. Light theme: the pill is dark (the inverse surface) with light content, which
 * is what the reference shows. The theme is judged from the surface's luminance so a forced theme
 * and wallpaper colours are both handled.
 */
@Composable
internal fun pillColors(frosted: Boolean = false): PillColors {
    val scheme = MaterialTheme.colorScheme
    // Frosted: enough tint to keep the icons legible over any content, thin enough for the blur to show.
    val tint = if (frosted) 0.62f else 0.94f
    return if (scheme.surface.luminance() < 0.5f) {
        PillColors(
            container = scheme.surface.copy(alpha = tint),
            border = scheme.outlineVariant,
            indicator = scheme.onSurface.copy(alpha = 0.14f),
            selected = scheme.primary,
            unselected = scheme.onSurfaceVariant,
        )
    } else {
        PillColors(
            container = scheme.inverseSurface.copy(alpha = tint),
            border = Color.Transparent,
            indicator = scheme.inverseOnSurface.copy(alpha = 0.18f),
            selected = scheme.inversePrimary,
            unselected = scheme.inverseOnSurface.copy(alpha = 0.66f),
        )
    }
}
