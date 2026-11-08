package com.zeroindex.intake.ui.theme

import androidx.compose.ui.graphics.Color

// Palette lifted from design/intake-mockup.html. The CSS custom property each
// one corresponds to is named so the two stay comparable.

// --primary / --pale
internal val Green40 = Color(0xFF1F5D50)
internal val Green80 = Color(0xFF8FD6C4)
internal val PaleLight = Color(0xFFE1F1EB)
internal val PaleDark = Color(0xFF263D37)
internal val Green10 = Color(0xFF00201A)
internal val Green90 = Color(0xFFABF2DF)

// --secondary / --sand
internal val Sand40 = Color(0xFF6C5D2F)
internal val Sand80 = Color(0xFFD9C78E)
internal val SandLight = Color(0xFFF4EFD9)
internal val SandDark = Color(0xFF363222)
internal val Sand10 = Color(0xFF231B00)

// --clay / --clayp
internal val Clay40 = Color(0xFF8F4C38)
internal val Clay80 = Color(0xFFFFB5A0)
internal val ClayLight = Color(0xFFF8E6DF)
internal val ClayDark = Color(0xFF472B27)
internal val Clay10 = Color(0xFF3A0B01)

// --bgc / --surface / --variant / --fg / --sub / --bd
internal val BgLight = Color(0xFFFFFBFF)
internal val SurfaceLight = Color(0xFFFFFFFF)
internal val VariantLight = Color(0xFFF5F2F7)
internal val OnLight = Color(0xFF1C1D20)
internal val SubLight = Color(0xFF65676E)
internal val OutlineLight = Color(0xFFE8E5EC)

internal val BgDark = Color(0xFF121318)
internal val SurfaceDark = Color(0xFF1A1B21)
internal val VariantDark = Color(0xFF1E1F25)
internal val OnDark = Color(0xFFF1F0F5)
internal val SubDark = Color(0xFFA6A6AE)
internal val OutlineDark = Color(0xFF34353C)

/** Macro accents, fixed in both themes so a colour means one thing. */
val ProteinAccent = Color(0xFF2E7D6B)
val CarbAccent = Color(0xFFB08415)
val FatAccent = Color(0xFF9A5540)

/**
 * The allergen palette is deliberately its own thing rather than the Material
 * error role: this is the one place in the app that stops the user, and the
 * mockup gives it a deeper, less alarming-but-more-serious red than M3's.
 * SPEC §7.3 wants "contains" and "may contain" visibly different.
 */
val AllergenRed = Color(0xFF9F3026)
val AllergenAmber = Color(0xFF825014)

/** Adherence heatmap fills: on target, under target, nothing logged. */
val HeatFull = Color(0xFF83B9A6)
val HeatPartial = Color(0xFFB8D7CA)

/** Plate calculator: large plates read as primary, small ones as carb amber. */
val PlateLarge = Color(0xFF2E7D6B)
val PlateSmall = Color(0xFFB08415)
val BarSteel = Color(0xFF747C78)
