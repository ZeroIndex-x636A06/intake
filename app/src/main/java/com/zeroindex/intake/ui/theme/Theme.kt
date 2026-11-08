package com.zeroindex.intake.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.zeroindex.intake.data.settings.ThemeMode

private val LightColors = lightColorScheme(
    primary = Green40,
    onPrimary = Color.White,
    primaryContainer = PaleLight,
    onPrimaryContainer = Green40,
    secondary = Sand40,
    onSecondary = Color.White,
    secondaryContainer = SandLight,
    onSecondaryContainer = Sand40,
    tertiary = Clay40,
    onTertiary = Color.White,
    tertiaryContainer = ClayLight,
    onTertiaryContainer = Clay40,
    background = BgLight,
    onBackground = OnLight,
    surface = SurfaceLight,
    onSurface = OnLight,
    surfaceVariant = VariantLight,
    onSurfaceVariant = SubLight,
    surfaceContainerLowest = SurfaceLight,
    surfaceContainerLow = BgLight,
    surfaceContainer = VariantLight,
    outline = SubLight,
    outlineVariant = OutlineLight,
    error = AllergenRed,
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Green80,
    onPrimary = Green10,
    primaryContainer = PaleDark,
    onPrimaryContainer = Green80,
    secondary = Sand80,
    onSecondary = Sand10,
    secondaryContainer = SandDark,
    onSecondaryContainer = Sand80,
    tertiary = Clay80,
    onTertiary = Clay10,
    tertiaryContainer = ClayDark,
    onTertiaryContainer = Clay80,
    background = BgDark,
    onBackground = OnDark,
    surface = SurfaceDark,
    onSurface = OnDark,
    surfaceVariant = VariantDark,
    onSurfaceVariant = SubDark,
    surfaceContainerLowest = BgDark,
    surfaceContainerLow = SurfaceDark,
    surfaceContainer = VariantDark,
    outline = SubDark,
    outlineVariant = OutlineDark,
    error = AllergenRed,
    onError = Color.White,
)

/**
 * SPEC §3.6: dark and light both fully supported, following the system setting
 * unless overridden in Settings.
 *
 * Dynamic colour now defaults **off**. The palette in design/intake-mockup.html
 * is the intended look, and wallpaper-derived colour overrides exactly the
 * greens and clays that carry meaning here. The setting is still there for
 * anyone who prefers it.
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

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = IntakeTypography,
        shapes = IntakeShapes,
        content = content,
    )
}
