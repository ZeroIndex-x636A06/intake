package com.zeroindex.intake.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The colour tokens the Material 3 [androidx.compose.material3.ColorScheme] has no slot for (DESIGN-V6 a.1). Provided by
 * [IntakeTheme]; read with `MaterialTheme.intake`.
 *
 * What each token is, and the ColorScheme role that carries the rest of the palette:
 *  - bg = `background`, card = `surface`, soft = [soft] (also `surfaceVariant`), ink = `primary`, inkOn = `onPrimary`,
 *    muted = `onSurfaceVariant`, line = `outlineVariant`.
 *  - [accent] / [onAccent] = the lime and the ink on it (also `primaryContainer` / `onPrimaryContainer`).
 *  - [hero] / [onHero] = the dark hero card (ink in light, a separate near-black in dark).
 *  - status pairs: [ok] Fits, [warn] May contain / Check the label / Not checked, [bad] Contains / Blocked, [ai] every AI label.
 */
@Immutable
class IntakeColors(
    val accent: Color,
    val onAccent: Color,
    val dock: Color,
    /** Inactive dock tab. */
    val dockInactive: Color,
    val scrim: Color,
    val hero: Color,
    val onHero: Color,
    val soft: Color,
    val protein: Color,
    val carb: Color,
    val fat: Color,
    val water: Color,
    val sleep: Color,
    val heart: Color,
    val ok: Color,
    val okBg: Color,
    val warn: Color,
    val warnBg: Color,
    val bad: Color,
    val badBg: Color,
    val ai: Color,
    val aiBg: Color,
)

val LocalIntakeColors = staticCompositionLocalOf<IntakeColors> { error("no IntakeTheme") }

val MaterialTheme.intake: IntakeColors
    @Composable @ReadOnlyComposable get() = LocalIntakeColors.current

/** Spacing tokens (a.3), in dp. */
object IntakeSpacing {
    val ScreenGutter = 20.dp
    val StackGap = 12.dp
    val GridGap2 = 12.dp
    val GridGap3 = 10.dp
    val RowGap = 8.dp
    val CardPad = 18.dp
    val CardPadHero = 20.dp
    val CardPadTile = 14.dp
    val CardPadCompact = 12.dp
    val ListRowPadV = 13.dp
    val ListRowPadH = 18.dp
    val ListRowMinHeight = 56.dp
    val IconGap = 12.dp
    val TopBarTop = 20.dp
    val TopBarBottom = 12.dp
    val SectionTop = 10.dp
    val SectionSide = 4.dp
    val DockInset = 16.dp
    val DockBottom = 20.dp
    val DockHeight = 70.dp
    val DockHeightCompact = 60.dp
    val LivePillBottom = 102.dp
    /** Smallest hit area of any button, chip or row (a.3). */
    val TouchTarget = 44.dp
}

/** Elevation tokens (a.5). Cards, tiles, sheets and dialogs have none. */
object IntakeElevation {
    val Dock = 12.dp
    val Live = 8.dp
    val SelectedSegment = 1.dp
    val Knob = 1.dp
}
