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
 * Typography (DESIGN-V6 a.2): Space Grotesk for titles and every number, Inter for everything read. Both are SIL OFL 1.1
 * variable fonts bundled in res/font (licences in design/fonts, credited under Settings > About and help), so the app needs no
 * network and no Google Play services to render correctly (SPEC section 3.1). Weights come from the `wght` axis.
 *
 * The files are subset to Latin and Latin Extended plus common symbols; Inter is pinned to its text optical size. Space Grotesk
 * spans 300-700 and Inter 400-700, so a request for a heavier weight (ExtraBold, Black) resolves to Bold.
 */
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

val SpaceGrotesk = FontFamily(spaceGrotesk(400), spaceGrotesk(500), spaceGrotesk(600), spaceGrotesk(700))
val Inter = FontFamily(inter(400), inter(500), inter(600), inter(700))

/**
 * Sizes track the mockup. Body text is floored at 12 sp and labels at 11 sp (the mockup's 11 and 11.5 are exactly at the floor),
 * because Android applies the user's font scale on top. Tracking is the mockup's em value times the size. All text is in sp.
 */
internal val IntakeTypography = Typography(
    // Hero number (Weekly "48").
    displayLarge = TextStyle(
        fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold,
        fontSize = 48.sp, lineHeight = 48.sp, letterSpacing = (-1.4).sp,
    ),
    // Big stat, onboarding and summary titles.
    displayMedium = TextStyle(
        fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold,
        fontSize = 34.sp, lineHeight = 37.sp, letterSpacing = (-0.85).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp, lineHeight = 31.sp, letterSpacing = (-0.84).sp,
    ),
    // `.title`: tab-root and screen titles.
    headlineLarge = TextStyle(
        fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp, lineHeight = 32.sp, letterSpacing = (-0.75).sp,
    ),
    // Stat numbers in tiles and cards.
    headlineMedium = TextStyle(
        fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp, lineHeight = 28.sp, letterSpacing = (-0.72).sp,
    ),
    // Sheet title, in-screen heading.
    headlineSmall = TextStyle(
        fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp, lineHeight = 26.sp, letterSpacing = (-0.44).sp,
    ),
    // `.h2`: section and card titles.
    titleLarge = TextStyle(
        fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp, lineHeight = 23.sp, letterSpacing = (-0.3).sp,
    ),
    // `.h3`: row title.
    titleMedium = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp, lineHeight = 20.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.SemiBold,
        fontSize = 13.5.sp, lineHeight = 18.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Normal,
        fontSize = 15.sp, lineHeight = 21.sp,
    ),
    // `.sub`.
    bodyMedium = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Normal,
        fontSize = 13.sp, lineHeight = 18.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 16.sp,
    ),
    // `.btn`.
    labelLarge = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.SemiBold,
        fontSize = 14.5.sp, lineHeight = 20.sp,
    ),
    // `.chip` and field labels.
    labelMedium = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.SemiBold,
        fontSize = 12.5.sp, lineHeight = 16.sp,
    ),
    // Dock tab label and small captions.
    labelSmall = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp, lineHeight = 15.sp,
    ),
)

/** `.eyebrow`: 11.5 sp, +0.08 em, uppercase (the caller uppercases the existing string, see Eyebrow). */
val EyebrowStyle = TextStyle(
    fontFamily = Inter,
    fontWeight = FontWeight.SemiBold,
    fontSize = 11.5.sp,
    lineHeight = 15.sp,
    letterSpacing = 0.9.sp,
)

/** `.num` 16 to 20: day-strip date, small stats. Not an M3 slot. */
val NumberSmall = TextStyle(
    fontFamily = SpaceGrotesk,
    fontWeight = FontWeight.SemiBold,
    fontSize = 18.sp,
    lineHeight = 22.sp,
    letterSpacing = (-0.54).sp,
    fontFeatureSettings = "tnum",
)

/** Numbers that change live (timers, set weights): tabular figures so the digits do not jitter. */
fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")
