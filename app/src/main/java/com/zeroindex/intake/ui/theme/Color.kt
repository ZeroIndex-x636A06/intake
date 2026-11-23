package com.zeroindex.intake.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * "Ink and Lime" (DESIGN-V6 a.1). Every hex value in the app lives in this file; everything else reads a colour role from
 * [androidx.compose.material3.MaterialTheme.colorScheme] or a token from [IntakeColors] (`MaterialTheme.intake`).
 *
 * The CSS custom property each value comes from (design/mockup-v6/source/intake.css) is named so the two stay comparable.
 */

// ---- Light (.phone) ----
internal val BgLight = Color(0xFFF3F4EF) // --bg
internal val CardLight = Color(0xFFFFFFFF) // --card
internal val SoftLight = Color(0xFFECEEE7) // --soft
internal val InkLight = Color(0xFF111514) // --ink
internal val InkOnLight = Color(0xFFFFFFFF) // --ink-on
internal val MutedLight = Color(0xFF566060) // --muted
internal val LineLight = Color(0xFFE2E5DE) // --line
internal val DockLight = Color(0xFF111514) // --dock
internal val HeroLight = Color(0xFF111514) // .card.inkc (= ink)
internal val OnHeroLight = Color(0xFFFFFFFF)

// ---- Dark (.phone.dark) ----
internal val BgDark = Color(0xFF0C0F0E)
internal val CardDark = Color(0xFF171B1A)
internal val SoftDark = Color(0xFF212725)
internal val InkDark = Color(0xFFEEF1EC)
internal val InkOnDark = Color(0xFF0C0F0E)
internal val MutedDark = Color(0xFFA3ACA9)
internal val LineDark = Color(0xFF29302D)
internal val DockDark = Color(0xFF232927)

/** PROPOSED in the brief, approved (owner decision 9): a dark hero card in dark theme, not an inverted ink one. */
internal val HeroDark = Color(0xFF1B211F)
internal val OnHeroDark = Color(0xFFEEF1EC)

// ---- Same in both themes ----
/** --accent, the lime. Never used as text on a light surface. */
internal val Accent = Color(0xFFD4F25A)
internal val OnAccent = Color(0xFF111514)
internal val Scrim = Color(0x7A0A0C0B) // rgba(10,12,11,.48)

/** Inactive dock tab (`.dock .tab`). */
internal val DockInactive = Color(0xFFC5CBC8)

// Macro and body-signal colours (decisions 10/11: use the mockup's, light and dark).
internal val ProteinLight = Color(0xFF3D6BF5)
internal val CarbLight = Color(0xFFE07A12)
internal val FatLight = Color(0xFF8B5CF6)
internal val WaterLight = Color(0xFF1A9BD7)
internal val SleepLight = Color(0xFF5B5BD6)
internal val HeartLight = Color(0xFFD64545)
internal val ProteinDark = Color(0xFF86A2FF)
internal val CarbDark = Color(0xFFFFA54F)
internal val FatDark = Color(0xFFBBA0FF)
internal val WaterDark = Color(0xFF56C6F5)
internal val SleepDark = Color(0xFFA3A3FF)
internal val HeartDark = Color(0xFFFF8C8C)

// Status language: one pair per meaning.
internal val OkLight = Color(0xFF1A6E42)
internal val OkBgLight = Color(0xFFDFF3E7)
internal val WarnLight = Color(0xFF7E4C00)
internal val WarnBgLight = Color(0xFFFFEFCC)
internal val BadLight = Color(0xFFA8231A)
internal val BadBgLight = Color(0xFFFCE4E1)
internal val AiLight = Color(0xFF4E33C9)
internal val AiBgLight = Color(0xFFECE8FE)
internal val OkDark = Color(0xFF6BD9A3)
internal val OkBgDark = Color(0xFF12301F)
internal val WarnDark = Color(0xFFF7BE55)
internal val WarnBgDark = Color(0xFF33270F)
internal val BadDark = Color(0xFFFF9384)
internal val BadBgDark = Color(0xFF3B1714)
internal val AiDark = Color(0xFFBDAEFF)
internal val AiBgDark = Color(0xFF241D46)

internal val IntakeColorsLight = IntakeColors(
    accent = Accent, onAccent = OnAccent, dock = DockLight, dockInactive = DockInactive, scrim = Scrim,
    hero = HeroLight, onHero = OnHeroLight, soft = SoftLight,
    protein = ProteinLight, carb = CarbLight, fat = FatLight, water = WaterLight, sleep = SleepLight, heart = HeartLight,
    ok = OkLight, okBg = OkBgLight, warn = WarnLight, warnBg = WarnBgLight,
    bad = BadLight, badBg = BadBgLight, ai = AiLight, aiBg = AiBgLight,
)

internal val IntakeColorsDark = IntakeColors(
    accent = Accent, onAccent = OnAccent, dock = DockDark, dockInactive = DockInactive, scrim = Scrim,
    hero = HeroDark, onHero = OnHeroDark, soft = SoftDark,
    protein = ProteinDark, carb = CarbDark, fat = FatDark, water = WaterDark, sleep = SleepDark, heart = HeartDark,
    ok = OkDark, okBg = OkBgDark, warn = WarnDark, warnBg = WarnBgDark,
    bad = BadDark, badBg = BadBgDark, ai = AiDark, aiBg = AiBgDark,
)

// ---------------------------------------------------------------------------------------------------------------
// Names the screens already import. They stay so no screen needs an edit; each now reads the active theme's token,
// so it is the light value in light and the dark value in dark. New code should use `MaterialTheme.intake.*` directly.
// ---------------------------------------------------------------------------------------------------------------

/** Macro accents, fixed per theme so a colour means one thing: protein blue, carbs orange, fat violet. */
val ProteinAccent: Color @Composable @ReadOnlyComposable get() = LocalIntakeColors.current.protein
val CarbAccent: Color @Composable @ReadOnlyComposable get() = LocalIntakeColors.current.carb
val FatAccent: Color @Composable @ReadOnlyComposable get() = LocalIntakeColors.current.fat

/**
 * The allergen palette. "Contains" is `bad` (red on pale red) and "May contain" is `warn` (amber on pale amber): they stay
 * visibly different (SPEC section 7.3) and neither is ever softened to a neutral.
 */
val AllergenRed: Color @Composable @ReadOnlyComposable get() = LocalIntakeColors.current.bad
val AllergenAmber: Color @Composable @ReadOnlyComposable get() = LocalIntakeColors.current.warn
val AllergenRedBg: Color @Composable @ReadOnlyComposable get() = LocalIntakeColors.current.badBg
val AllergenAmberBg: Color @Composable @ReadOnlyComposable get() = LocalIntakeColors.current.warnBg

/** Adherence and chart fills: on target = `ok`, partial = `ok` at 40%. */
val HeatFull: Color @Composable @ReadOnlyComposable get() = LocalIntakeColors.current.ok
val HeatPartial: Color @Composable @ReadOnlyComposable get() = LocalIntakeColors.current.ok.copy(alpha = 0.4f)

/** Plate calculator: large plates `protein`, small `carb`, the bar `muted` (a.1). */
val PlateLarge: Color @Composable @ReadOnlyComposable get() = LocalIntakeColors.current.protein
val PlateSmall: Color @Composable @ReadOnlyComposable get() = LocalIntakeColors.current.carb
val BarSteel: Color @Composable @ReadOnlyComposable get() = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant

/** The water bar and hydration wave. */
val WaterAccent: Color @Composable @ReadOnlyComposable get() = LocalIntakeColors.current.water
