package com.zeroindex.intake.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import com.zeroindex.intake.data.settings.ThemeMode

/**
 * Material 3 roles for the Ink and Lime palette (DESIGN-V6 a.1). `primary` is the ink, so every default filled button, selected
 * chip and switch is a black pill; the lime is `primaryContainer` (the one thing to look at) and the extra tokens are in [IntakeColors].
 *
 * Deviation from the brief, on purpose: `tertiary` is the *warning* pair (amber), not the AI pair. The screens that already use
 * `tertiary` use it for "over", "record" and "may contain"; the AI pair is `MaterialTheme.intake.ai` and is applied per screen.
 */
private val LightColors = lightColorScheme(
    primary = InkLight,
    onPrimary = InkOnLight,
    primaryContainer = Accent,
    onPrimaryContainer = OnAccent,
    inversePrimary = Accent,
    secondary = MutedLight,
    onSecondary = InkOnLight,
    secondaryContainer = SoftLight,
    onSecondaryContainer = InkLight,
    tertiary = WarnLight,
    onTertiary = InkOnLight,
    tertiaryContainer = WarnBgLight,
    onTertiaryContainer = WarnLight,
    background = BgLight,
    onBackground = InkLight,
    surface = CardLight,
    onSurface = InkLight,
    surfaceVariant = SoftLight,
    onSurfaceVariant = MutedLight,
    surfaceTint = CardLight,
    inverseSurface = InkLight,
    inverseOnSurface = InkOnLight,
    error = BadLight,
    onError = InkOnLight,
    errorContainer = BadBgLight,
    onErrorContainer = BadLight,
    outline = MutedLight,
    outlineVariant = LineLight,
    scrim = Scrim,
    surfaceBright = CardLight,
    surfaceDim = BgLight,
    surfaceContainerLowest = CardLight,
    surfaceContainerLow = CardLight,
    surfaceContainer = CardLight,
    surfaceContainerHigh = CardLight,
    surfaceContainerHighest = SoftLight,
)

private val DarkColors = darkColorScheme(
    primary = InkDark,
    onPrimary = InkOnDark,
    primaryContainer = Accent,
    onPrimaryContainer = OnAccent,
    inversePrimary = Accent,
    secondary = MutedDark,
    onSecondary = InkOnDark,
    secondaryContainer = SoftDark,
    onSecondaryContainer = InkDark,
    tertiary = WarnDark,
    onTertiary = InkOnDark,
    tertiaryContainer = WarnBgDark,
    onTertiaryContainer = WarnDark,
    background = BgDark,
    onBackground = InkDark,
    surface = CardDark,
    onSurface = InkDark,
    surfaceVariant = SoftDark,
    onSurfaceVariant = MutedDark,
    surfaceTint = CardDark,
    // Snackbars: in dark, ink is near-white, which would put the lime action on white. The dock grey carries it instead.
    inverseSurface = DockDark,
    inverseOnSurface = InkDark,
    error = BadDark,
    onError = InkOnDark,
    errorContainer = BadBgDark,
    onErrorContainer = BadDark,
    outline = MutedDark,
    outlineVariant = LineDark,
    scrim = Scrim,
    surfaceBright = CardDark,
    surfaceDim = BgDark,
    surfaceContainerLowest = CardDark,
    surfaceContainerLow = CardDark,
    surfaceContainer = CardDark,
    surfaceContainerHigh = CardDark,
    surfaceContainerHighest = SoftDark,
)

/** "Use wallpaper colours" (default off): only the neutral surfaces and ink follow the wallpaper; status, macro and accent stay fixed. */
private fun ColorScheme.withFixedMeaning(fixed: ColorScheme): ColorScheme = copy(
    primaryContainer = fixed.primaryContainer,
    onPrimaryContainer = fixed.onPrimaryContainer,
    inversePrimary = fixed.inversePrimary,
    tertiary = fixed.tertiary,
    onTertiary = fixed.onTertiary,
    tertiaryContainer = fixed.tertiaryContainer,
    onTertiaryContainer = fixed.onTertiaryContainer,
    error = fixed.error,
    onError = fixed.onError,
    errorContainer = fixed.errorContainer,
    onErrorContainer = fixed.onErrorContainer,
    scrim = fixed.scrim,
)

/**
 * SPEC section 3.6: dark and light both fully supported, following the system setting unless overridden in Settings.
 *
 * Dynamic colour defaults **off**: the palette in design/mockup-v6 is the intended look, and wallpaper colour would override the
 * status and macro colours that carry meaning. The setting is still there for anyone who prefers it.
 */
@Composable
fun IntakeTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val fixed = if (darkTheme) DarkColors else LightColors
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            (if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)).withFixedMeaning(fixed)
        }
        else -> fixed
    }

    CompositionLocalProvider(LocalIntakeColors provides if (darkTheme) IntakeColorsDark else IntakeColorsLight) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = IntakeTypography,
            shapes = IntakeShapes,
            content = content,
        )
    }
}
