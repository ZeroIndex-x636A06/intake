package com.zeroindex.intake.ui.screens.train

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zeroindex.intake.ui.components.IconTile
import com.zeroindex.intake.ui.components.IconTileStyle
import com.zeroindex.intake.ui.theme.IconSize
import com.zeroindex.intake.ui.theme.IntakeIcons
import com.zeroindex.intake.ui.theme.IntakeShape
import com.zeroindex.intake.ui.theme.IntakeSpacing

/**
 * Small building blocks of the Train screens that the shared components do not have yet (a `.li` row with its own padding, the
 * Train tile). Kept private to the Train package for the integrator to promote.
 */

/** Side padding of a Train list or form screen (`screenGutter`), with [bottom] under it for the dock. */
internal fun trainPadding(bottom: Dp, top: Dp = 6.dp) =
    PaddingValues(start = IntakeSpacing.ScreenGutter, end = IntakeSpacing.ScreenGutter, top = top, bottom = bottom)

/**
 * `.li`: one row inside a [com.zeroindex.intake.ui.components.ListCard]. Optional [icon] tile, title over [subtitle], then
 * [trailing] (a chip, a button or a chevron). 56 dp minimum, 13/18 dp padding. The whole row is the touch target when
 * [onClick] is set.
 */
@Composable
internal fun TrainRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = IntakeSpacing.ListRowMinHeight)
            .let { if (onClick != null) it.clickable(role = Role.Button, onClickLabel = onClickLabel ?: title, onClick = onClick) else it }
            .padding(horizontal = IntakeSpacing.ListRowPadH, vertical = IntakeSpacing.ListRowPadV),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            IconTile(icon)
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            if (!subtitle.isNullOrEmpty()) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(10.dp))
            trailing()
        }
    }
}

/** The trailing chevron of a tappable [TrainRow]. */
@Composable
internal fun RowChevronIcon() {
    Icon(
        IntakeIcons.ChevronRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(IconSize.Default),
    )
}

/** The Train tile (`.tile`): a card with a small icon tile on top, a 13.5 sp title and a muted line. The whole tile taps. */
@Composable
internal fun TrainTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconStyle: IconTileStyle = IconTileStyle.Soft,
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = IntakeShape.TileLarge,
        modifier = modifier,
    ) {
        Column(Modifier.padding(IntakeSpacing.CardPadTile), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            IconTile(icon, small = true, style = iconStyle)
            Spacer(Modifier.size(8.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Two [TrainTile]s side by side at equal height; a one-element row leaves the second slot empty. */
@Composable
internal fun TileGrid(tiles: List<@Composable (Modifier) -> Unit>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(IntakeSpacing.GridGap2)) {
        tiles.chunked(2).forEach { pair ->
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(IntakeSpacing.GridGap2),
            ) {
                pair.forEach { tile -> tile(Modifier.weight(1f).fillMaxHeight()) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** `.search`: a `card`-filled pill with no outline, for the Exercises and Videos search boxes. */
@Composable
internal fun searchFieldColors() = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surface,
    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
    disabledContainerColor = MaterialTheme.colorScheme.surface,
    focusedBorderColor = MaterialTheme.colorScheme.onSurface,
    unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
    unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
)
