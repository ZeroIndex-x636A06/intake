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
    primaryContainer = Green90,
    onPrimaryContainer = Green10,
    secondary = Sand40,
    onSecondary = Color.White,
    secondaryContainer = Sand80,
    onSecondaryContainer = Sand10,
    tertiary = Clay40,
    onTertiary = Color.White,
    tertiaryContainer = Clay80,
    onTertiaryContainer = Clay10,
    background = NeutralLightBg,
    onBackground = NeutralLightOn,
    surface = NeutralLightBg,
    onSurface = NeutralLightOn,
    surfaceVariant = NeutralLightSurface,
    onSurfaceVariant = Green30,
)

private val DarkColors = darkColorScheme(
    primary = Green80,
    onPrimary = Green10,
    primaryContainer = Green30,
    onPrimaryContainer = Green90,
    secondary = Sand80,
    onSecondary = Sand10,
    secondaryContainer = Sand30,
    onSecondaryContainer = Sand80,
    tertiary = Clay80,
    onTertiary = Clay10,
    tertiaryContainer = Clay30,
    onTertiaryContainer = Clay80,
    background = NeutralDarkBg,
    onBackground = NeutralDarkOn,
    surface = NeutralDarkBg,
    onSurface = NeutralDarkOn,
    surfaceVariant = NeutralDarkSurface,
    onSurfaceVariant = Green80,
)

/**
 * SPEC §3.6: dark and light both fully supported, following the system setting
 * unless the user has picked one in Settings. Dynamic colour is used where the
 * platform offers it, with the hand-tuned palette above as the fallback.
 */
@Composable
fun IntakeTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
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
        content = content,
    )
}
