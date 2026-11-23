package com.zeroindex.intake.wear

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.Text

/**
 * Ink and Lime on the watch (docs/briefs/DESIGN-V6.md, group F): dark ground, lime for the one action, Space Grotesk for
 * numbers and Inter for reading text. The watch keeps its own small token set and does not depend on the phone's ui/theme.
 * Values are the dark-theme values from the brief's colour table, plus the round face `#0B0D0C` and button `#262C2A` from the
 * Watch mockup.
 */
internal object WatchTokens {
    val Ground = Color(0xFF0B0D0C)
    val Surface = Color(0xFF262C2A)
    val Ink = Color(0xFFEEF1EC)
    val Muted = Color(0xFFA9B1AE)
    val Lime = Color(0xFFD4F25A)
    val OnLime = Color(0xFF111514)
    val Bad = Color(0xFFFF9384)
    val BadBg = Color(0xFF3B1714)
    val Protein = Color(0xFF86A2FF)
    val Water = Color(0xFF56C6F5)
}

/**
 * The M3 mapping: `primary` is lime (filled buttons), `secondaryContainer` is the neutral button fill (tonal buttons),
 * `errorContainer` is the danger pair.
 */
internal val WatchColorScheme = ColorScheme(
    primary = WatchTokens.Lime,
    onPrimary = WatchTokens.OnLime,
    primaryContainer = WatchTokens.Lime,
    onPrimaryContainer = WatchTokens.OnLime,
    secondaryContainer = WatchTokens.Surface,
    onSecondaryContainer = WatchTokens.Ink,
    errorContainer = WatchTokens.BadBg,
    onErrorContainer = WatchTokens.Bad,
    background = WatchTokens.Ground,
    onBackground = WatchTokens.Ink,
    onSurfaceVariant = WatchTokens.Muted,
)

// Bundled OFL 1.1 variable fonts (same files as the phone app; licence texts in assets/licences).
private fun spaceGrotesk(weight: Int) = Font(
    resId = R.font.space_grotesk_variable,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

private fun inter(weight: Int) = Font(
    resId = R.font.inter_variable,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

internal val SpaceGroteskFamily = FontFamily(spaceGrotesk(500), spaceGrotesk(600), spaceGrotesk(700))
internal val InterFamily = FontFamily(inter(400), inter(500), inter(600), inter(700))

/**
 * Text in the watch type: numbers (`numeric = true`) in Space Grotesk, everything else in Inter. Wraps the wear
 * `Text` so every call site picks its family the same way.
 */
@Composable
internal fun WText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 12.sp,
    fontWeight: FontWeight = FontWeight.Normal,
    color: Color = Color.Unspecified,
    numeric: Boolean = false,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontFamily = if (numeric) SpaceGroteskFamily else InterFamily,
        fontSize = fontSize,
        fontWeight = fontWeight,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = overflow,
    )
}
