package com.zeroindex.intake.ui.theme

import androidx.compose.ui.graphics.Color

// Fallback palette for devices without dynamic colour. Green reads as "on
// target", amber as "may contain", red as "contains" - see SPEC §7.3. Status
// colour is never the only signal (SPEC §3.7), so these are decoration.
internal val Green40 = Color(0xFF1F5D50)
internal val Green80 = Color(0xFF8FD6C4)
internal val Green90 = Color(0xFFABF2DF)
internal val Green10 = Color(0xFF00201A)
internal val Green30 = Color(0xFF00473B)

internal val Sand40 = Color(0xFF6C5D2F)
internal val Sand80 = Color(0xFFD9C78E)
internal val Sand10 = Color(0xFF231B00)
internal val Sand30 = Color(0xFF524619)

internal val Clay40 = Color(0xFF8F4C38)
internal val Clay80 = Color(0xFFFFB5A0)
internal val Clay10 = Color(0xFF3A0B01)
internal val Clay30 = Color(0xFF723523)

internal val NeutralLightBg = Color(0xFFFFFBFF)
internal val NeutralLightSurface = Color(0xFFF5F2F7)
internal val NeutralLightOn = Color(0xFF1B1B1F)
internal val NeutralDarkBg = Color(0xFF121318)
internal val NeutralDarkSurface = Color(0xFF1E1F25)
internal val NeutralDarkOn = Color(0xFFE4E2E6)

/** Macro accents, used by the macro ring and the charts. */
val ProteinAccent = Color(0xFF2E7D6B)
val CarbAccent = Color(0xFFB08415)
val FatAccent = Color(0xFF9A5540)
