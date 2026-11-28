package com.zeroindex.intake.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeroindex.intake.ui.motion.LocalReduceMotion
import com.zeroindex.intake.ui.motion.Motion
import com.zeroindex.intake.ui.theme.IconSize
import com.zeroindex.intake.ui.theme.IntakeElevation
import com.zeroindex.intake.ui.theme.IntakeIcons
import com.zeroindex.intake.ui.theme.IntakeShape
import com.zeroindex.intake.ui.theme.IntakeSpacing
import com.zeroindex.intake.ui.theme.intake

/** One tab of a [DockBar]: its label (also the accessibility label) and its line icon. */
class DockTab(val label: String, val icon: ImageVector)

/** Colours of the dock, from the theme tokens: the `dock` fill, the lime active tab, the pale inactive tab. */
internal class DockColors(val container: Color, val selected: Color, val unselected: Color)

@Composable
internal fun dockColors(): DockColors {
    val c = MaterialTheme.intake
    return DockColors(container = c.dock, selected = c.accent, unselected = c.dockInactive)
}

/** The dock's shadow (`0 14 36 rgba(0,0,0,.22)`), the only strong shadow in the app. */
internal fun Modifier.dockShadow(): Modifier = shadow(
    elevation = IntakeElevation.Dock,
    shape = IntakeShape.Pill,
    ambientColor = Color.Black.copy(alpha = 0.22f),
    spotColor = Color.Black.copy(alpha = 0.22f),
)

/**
 * One dock tab: a 22 dp icon over an 11 sp label, 60 x 52 at least, the active one in lime (200 ms recolour). The label starts at
 * its normal size and steps down while it does not fit (a larger font scale), down to [MinLabelSp]; it is never cut mid-word.
 * Touch is handled by the owner ([FloatingPillBar] for the slide gesture, [DockBar] for taps); this describes the item to
 * accessibility services and gives them a click action.
 */
@Composable
internal fun DockItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    highlighted: Boolean,
    colors: DockColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduce = LocalReduceMotion.current
    val density = LocalDensity.current
    var labelSize by remember(label, density.fontScale) { mutableFloatStateOf(LabelSp) }
    val tint by animateColorAsState(
        targetValue = if (highlighted) colors.selected else colors.unselected,
        animationSpec = Motion.standardTween(reduce, Motion.DurationShort),
        label = "dock-item-tint",
    )
    Column(
        modifier = modifier
            .heightIn(min = 52.dp)
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
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(IconSize.Default))
        Text(
            text = label,
            color = tint,
            fontSize = labelSize.sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (it.hasVisualOverflow && labelSize > MinLabelSp) labelSize -= 0.5f },
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

private const val LabelSp = 11f
private const val MinLabelSp = 8f

/**
 * The centre + (`.dock .fab`): a 56 dp lime circle with a 28 dp plus in `onAccent`. While the Quick log sheet is open
 * ([open]) the plus becomes an X, and the dock stays visible above the scrim. Phase 1b wires [onClick]; until then the shell
 * passes a no-op.
 */
@Composable
fun DockFab(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    open: Boolean = false,
) {
    val c = MaterialTheme.intake
    Box(
        modifier = modifier
            .size(56.dp)
            .clip(IntakeShape.Pill)
            .background(c.accent, IntakeShape.Pill)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (open) IntakeIcons.Close else IntakeIcons.Plus,
            contentDescription = null,
            tint = c.onAccent,
            modifier = Modifier.size(IconSize.Large),
        )
    }
}

/**
 * The flat five-slot dock of the redesign (DESIGN-V6 b.4): a pill in the `dock` colour with the tabs spread evenly and a
 * lime [DockFab] in the middle ([fabIndex] slots in; default the middle). No drag-to-select and no moving highlight: a tap
 * selects, the active tab turns lime. With [vertical] it is the wide-screen rail, FAB at the top. [compact] is the 60 dp
 * landscape height.
 *
 * [onSelect] gets the index into [tabs] (not counting the FAB), including for the already-selected tab, which the caller turns
 * into "pop to this tab's root". Nothing in the app uses this yet: Phase 1b swaps it in for [FloatingPillBar].
 */
@Composable
fun DockBar(
    tabs: List<DockTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onFab: () -> Unit,
    fabLabel: String,
    modifier: Modifier = Modifier,
    fabOpen: Boolean = false,
    fabIndex: Int = tabs.size / 2,
    vertical: Boolean = false,
    compact: Boolean = false,
) {
    val colors = dockColors()
    val shell = modifier
        .dockShadow()
        .background(colors.container, IntakeShape.Pill)
    val item: @Composable (Int, Modifier) -> Unit = { index, base ->
        val tab = tabs[index]
        DockItem(
            label = tab.label,
            icon = tab.icon,
            selected = index == selectedIndex,
            highlighted = index == selectedIndex,
            colors = colors,
            onClick = { onSelect(index) },
            modifier = base
                // Clip before the click so the press ripple is round.
                .clip(IntakeShape.Pill)
                .clickable(role = Role.Tab, onClickLabel = tab.label) { onSelect(index) },
        )
    }
    if (vertical) {
        Column(
            shell.padding(horizontal = 6.dp, vertical = 10.dp).selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            DockFab(onFab, fabLabel, open = fabOpen)
            tabs.indices.forEach { i -> item(i, Modifier.widthIn(min = 64.dp).heightIn(min = 60.dp)) }
        }
    } else {
        Row(
            shell
                .heightIn(min = if (compact) IntakeSpacing.DockHeightCompact else IntakeSpacing.DockHeight)
                .padding(horizontal = 10.dp)
                .selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            tabs.indices.forEach { i ->
                if (i == fabIndex) DockFab(onFab, fabLabel, open = fabOpen)
                item(i, Modifier.widthIn(min = 60.dp).weight(1f))
            }
            if (fabIndex >= tabs.size) DockFab(onFab, fabLabel, open = fabOpen)
        }
    }
}
