package com.zeroindex.intake.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
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
import androidx.compose.ui.unit.dp
import com.zeroindex.intake.ui.theme.IntakeShape
import com.zeroindex.intake.ui.theme.IntakeSpacing

/**
 * The floating navigation bar, in the flat "dock" look (DESIGN-V6 a.5, b.4): a pill in the solid `dock` colour with an icon
 * over a label per tab and the active tab in lime. No blur, no glass and no moving highlight. With [vertical] it is the tablet
 * rail, the same pill stood on end.
 *
 * Touch is still handled once for the whole bar rather than per item, so a finger can go down on one tab and slide across the
 * others: the tab under the finger turns lime, each new tab crossed gives a light haptic tick, and lifting commits the tab under
 * the finger. A tap is the same gesture without the slide. The slide is an enhancement only: each item also exposes a click
 * action to accessibility services, so TalkBack's double-tap and switch access select tabs normally. (Phase 1b replaces this
 * with the static five-slot [DockBar]; the gesture stays here until then.)
 *
 * A touch that starts inside the system back-gesture strip at a screen edge never slides (the system owns it); it can still be
 * a tap.
 *
 * [onSelect] is called with the item index, including for the already-selected item, which the caller turns into "pop to this
 * tab's root". [backdrop] is accepted for source compatibility and ignored: the dock is a solid fill now.
 */
@Composable
fun FloatingPillBar(
    destinations: List<TopLevelDestination>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    vertical: Boolean = false,
    backdrop: PillBackdrop? = null,
) {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val view = LocalView.current
    val gestureInsets = WindowInsets.systemGestures
    val colors = dockColors()
    val count = destinations.size
    val currentOnSelect by rememberUpdatedState(onSelect)

    // Item rectangles and the touch surface's origin, both in root coordinates; the gesture subtracts one from the other,
    // so padding and rounding never matter.
    val itemRects = remember(count) { mutableStateListOf<Rect>().apply { repeat(count) { add(Rect.Zero) } } }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var hover by remember { mutableIntStateOf(PillSlideTracker.NONE) }
    val tracker = remember(density) { PillSlideTracker(hysteresisPx = with(density) { 6.dp.toPx() }) }

    val shownIndex = if (hover != PillSlideTracker.NONE) hover else selectedIndex

    Box(
        modifier = modifier
            .dockShadow()
            .background(colors.container, IntakeShape.Pill)
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
                        // Also runs when the system takes the gesture over (a back swipe): nothing is committed and the
                        // lime returns to the selected tab.
                        tracker.cancel()
                        hover = PillSlideTracker.NONE
                    }
                }
            },
    ) {
        val item: @Composable (Int, TopLevelDestination, Modifier) -> Unit = { index, destination, base ->
            DockItem(
                label = stringResource(destination.labelRes),
                icon = destination.icon,
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
                Modifier.padding(horizontal = 6.dp, vertical = 10.dp).selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                destinations.forEachIndexed { index, destination ->
                    // Grows with its label: a larger font scale widens the rail rather than cutting a word.
                    item(index, destination, Modifier.widthIn(min = 64.dp).heightIn(min = 60.dp))
                }
            }
        } else {
            Row(
                Modifier.heightIn(min = IntakeSpacing.DockHeight).padding(horizontal = 10.dp).selectableGroup(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                destinations.forEachIndexed { index, destination ->
                    item(index, destination, Modifier.weight(1f))
                }
            }
        }
    }
}
