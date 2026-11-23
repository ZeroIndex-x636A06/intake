package com.zeroindex.intake.ui.quicklog

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zeroindex.intake.R
import com.zeroindex.intake.domain.model.MealSlot
import com.zeroindex.intake.ui.components.ChipTone
import com.zeroindex.intake.ui.components.IntakeChip
import com.zeroindex.intake.ui.motion.LocalReduceMotion
import com.zeroindex.intake.ui.motion.Motion
import com.zeroindex.intake.ui.screens.hub.defaultMealSlot
import com.zeroindex.intake.ui.theme.IconSize
import com.zeroindex.intake.ui.theme.IntakeIcons
import com.zeroindex.intake.ui.theme.IntakeShape
import com.zeroindex.intake.ui.theme.IntakeSpacing
import com.zeroindex.intake.ui.theme.intake
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** The line icon of a tile (DESIGN-V6 a.6: the mockup's own paths, already in [IntakeIcons]). */
private fun QuickTile.icon(): ImageVector = when (this) {
    QuickTile.FOOD -> IntakeIcons.ForkKnife
    QuickTile.DRINK -> IntakeIcons.CupFilled
    QuickTile.SCAN -> IntakeIcons.Scan
    QuickTile.PHOTO -> IntakeIcons.Camera
    QuickTile.QUICK_CALORIES -> IntakeIcons.Flame
    QuickTile.MEAL_TEMPLATE -> IntakeIcons.Clipboard
    QuickTile.WEIGHT -> IntakeIcons.Scale
    QuickTile.MEASUREMENTS -> IntakeIcons.Tape
    QuickTile.PROGRESS_PHOTO -> IntakeIcons.User
    QuickTile.START_WORKOUT -> IntakeIcons.Dumbbell
    QuickTile.JUMP_TEST -> IntakeIcons.ArrowUpBar
    QuickTile.NFC_TAG -> IntakeIcons.Signal
}

/**
 * The Quick log sheet behind the dock's centre + (owner decision 1, DESIGN-V6 b.5): "Log something", a 3-column grid of tiles
 * that each open an existing flow, and "Water · one tap" with the saved sizes. It is an overlay in the app shell, not a modal
 * window, so the dock stays visible above the scrim (the + turns into an X). Tiles whose feature is off are not drawn.
 *
 * [dockClearance] is the room the sheet leaves at its bottom for the dock (its height, its gap and the navigation bar).
 */
@Composable
fun QuickLogSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onTile: (QuickTile) -> Unit,
    dockClearance: Dp,
    modifier: Modifier = Modifier,
    viewModel: QuickLogViewModel = hiltViewModel(),
) {
    val reduce = LocalReduceMotion.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler(enabled = visible, onBack = onDismiss)
    Box(modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(Motion.standardTween(reduce, Motion.DurationMedium)),
            exit = fadeOut(Motion.standardTween(reduce, Motion.DurationShort)),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.intake.scrim)
                    // Tapping the scrim closes the sheet; it is not announced as a control of its own (the X and Back do that).
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            )
        }
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(if (reduce) snap() else tween(Motion.DurationLong, easing = Motion.EmphasizedDecelerate)) { it },
            exit = slideOutVertically(Motion.standardTween(reduce, Motion.DurationMedium)) { it },
        ) {
            SheetBody(
                state = state,
                dockClearance = dockClearance,
                onTile = onTile,
                onWater = viewModel::logWater,
                onDismiss = onDismiss,
            )
        }
    }
}

@Composable
private fun SheetBody(
    state: QuickLogUiState,
    dockClearance: Dp,
    onTile: (QuickTile) -> Unit,
    onWater: (WaterSize) -> Unit,
    onDismiss: () -> Unit,
) {
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val fontScale = LocalDensity.current.fontScale
    // Three columns as drawn; two once the text is large enough that a label would be cut (font scale 1.3 and up).
    val columns = if (fontScale >= 1.3f) 2 else 3
    val tiles = remember(state.context) { QuickLogPolicy.visibleTiles(state.context) }
    val contextLine = remember { slotAndTime() }
    Column(
        Modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            // Never taller than the room above the dock, so a landscape phone scrolls the sheet instead of clipping it.
            .heightIn(max = screenHeight - 24.dp)
            .background(MaterialTheme.colorScheme.surface, IntakeShape.Sheet)
            // Swallow taps on the sheet itself so they do not reach the scrim.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = dockClearance + 12.dp),
    ) {
        // Grabber: 40 x 5, `line`, centred, 16 below.
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .size(width = 40.dp, height = 5.dp)
                .background(MaterialTheme.colorScheme.outlineVariant, IntakeShape.Pill),
        )
        Row(
            Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                stringResource(R.string.quick_log_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f, fill = false).semantics { heading() },
            )
            Text(
                contextLine,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(IntakeSpacing.GridGap3)) {
            tiles.chunked(columns).forEach { rowTiles ->
                Row(horizontalArrangement = Arrangement.spacedBy(IntakeSpacing.GridGap3)) {
                    rowTiles.forEach { tile -> QuickTileButton(tile, onClick = { onTile(tile) }, modifier = Modifier.weight(1f)) }
                    // Keep the last row's tiles the same width as the rest.
                    repeat(columns - rowTiles.size) { Box(Modifier.weight(1f)) }
                }
            }
        }
        if (state.waterSizes.isNotEmpty()) {
            Text(
                stringResource(R.string.quick_log_water_heading),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            )
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(IntakeSpacing.RowGap),
            ) {
                state.waterSizes.forEachIndexed { index, size ->
                    WaterPill(size, showDrop = index == 0, onClick = { onWater(size); onDismiss() })
                }
            }
        }
    }
}

@Composable
private fun QuickTileButton(tile: QuickTile, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.intake
    val (bg, fg) = when (tile.look) {
        QuickTileLook.Soft -> c.soft to MaterialTheme.colorScheme.onSurface
        QuickTileLook.Lime -> c.accent to c.onAccent
        QuickTileLook.Ink -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
    }
    val label = stringResource(tile.labelRes)
    Column(
        modifier
            .heightIn(min = 96.dp)
            .clip(IntakeShape.TileLarge)
            .background(bg, IntakeShape.TileLarge)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .padding(IntakeSpacing.CardPadTile),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(tile.icon(), contentDescription = null, tint = fg, modifier = Modifier.size(IconSize.Default))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, color = fg, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f, fill = false))
            if (QuickLogPolicy.showsAiChip(tile)) IntakeChip(text = stringResource(R.string.quick_log_ai), tone = ChipTone.Ai, small = true)
        }
    }
}

@Composable
private fun WaterPill(size: WaterSize, showDrop: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .heightIn(min = IntakeSpacing.TouchTarget)
            .clip(IntakeShape.Pill)
            .background(MaterialTheme.intake.soft, IntakeShape.Pill)
            .clickable(role = Role.Button, onClickLabel = size.label, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (showDrop) {
            Icon(
                IntakeIcons.Drop, contentDescription = null, tint = MaterialTheme.intake.water, modifier = Modifier.size(IconSize.Small),
            )
        }
        Text(size.label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
    }
}

/** "Lunch · 12:40": the meal the clock says it is, and the time, so the food tiles' target is no surprise. */
private fun slotAndTime(now: LocalTime = LocalTime.now()): String {
    val slot = defaultMealSlot(now.hour)
    val name = when (slot) {
        MealSlot.BREAKFAST -> "Breakfast"
        MealSlot.LUNCH -> "Lunch"
        MealSlot.DINNER -> "Dinner"
        else -> "Snack"
    }
    return name + " · " + DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(now)
}
