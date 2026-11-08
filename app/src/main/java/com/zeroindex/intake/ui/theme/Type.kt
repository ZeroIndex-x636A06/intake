package com.zeroindex.intake.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.zeroindex.intake.R

/**
 * Typography from design/intake-mockup.html: Manrope for anything structural or
 * numeric, DM Sans for prose. Both are bundled as variable fonts so the app
 * needs no network to render correctly (SPEC §3.1) - weights come from the
 * `wght` axis rather than separate files.
 *
 * The mockup's heavy Manrope with negative tracking is what gives the numbers
 * their presence, so the weights and letter spacing here are deliberate and
 * worth preserving when editing.
 */
private fun manrope(weight: Int) = Font(
    resId = R.font.manrope_variable,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

private fun dmSans(weight: Int) = Font(
    resId = R.font.dm_sans_variable,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Manrope = FontFamily(manrope(400), manrope(500), manrope(600), manrope(700), manrope(800))
val DmSans = FontFamily(dmSans(400), dmSans(500), dmSans(600), dmSans(700))

/**
 * Sizes track the mockup, with one deliberate change: body text is floored at
 * 12sp and labels at 11sp. The mockup's 10-11px reads fine in a browser at
 * 390px, but Android applies the user's font scale on top and anything smaller
 * fails the accessibility bar SPEC §3.7 sets. The hierarchy is unchanged.
 */
internal val IntakeTypography = Typography(
    // Big numbers: the macro ring centre, weight, headline stats.
    displayLarge = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 40.sp,
        lineHeight = 44.sp,
        letterSpacing = (-2).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 34.sp,
        lineHeight = 39.sp,
        letterSpacing = (-1.7).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 28.sp,
        lineHeight = 33.sp,
        letterSpacing = (-1.1).sp,
    ),
    // Cook mode's one-step-at-a-time heading.
    headlineLarge = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 32.sp,
        lineHeight = 37.sp,
        letterSpacing = (-1.6).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 26.sp,
        lineHeight = 31.sp,
        letterSpacing = (-1).sp,
    ),
    // Screen titles ("Progress", "Train", "Add food").
    headlineSmall = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 22.sp,
        lineHeight = 27.sp,
        letterSpacing = (-0.9).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 19.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.6).sp,
    ),
    // Section headings and card titles.
    titleMedium = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleSmall = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = DmSans,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = DmSans,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = DmSans,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = DmSans,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        lineHeight = 17.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = DmSans,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = DmSans,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        lineHeight = 15.sp,
    ),
)

/** The mockup's uppercase micro-label: `.label` and `.warn-top`. */
val EyebrowStyle = TextStyle(
    fontFamily = DmSans,
    fontWeight = FontWeight.ExtraBold,
    fontSize = 11.sp,
    lineHeight = 15.sp,
    letterSpacing = 1.1.sp,
)
