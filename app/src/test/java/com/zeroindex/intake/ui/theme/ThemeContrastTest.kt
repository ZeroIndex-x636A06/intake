package com.zeroindex.intake.ui.theme

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/**
 * The status pairs and the text-on-surface pairs of the Ink and Lime palette must read at body size (WCAG AA, 4.5:1) in both
 * themes: Fits / May contain / Contains / AI chips, ink and muted text on the ground and on cards, the text on lime, the hero
 * card and the dock. Pure arithmetic on the token values; no Android classes.
 */
class ThemeContrastTest {

    private fun channel(c: Float): Double = if (c <= 0.03928f) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)

    private fun luminance(c: Color): Double =
        0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)

    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun check(theme: String, pairs: List<Triple<String, Color, Color>>) {
        val low = pairs.filter { (_, fg, bg) -> contrast(fg, bg) < 4.5 }
            .map { (name, fg, bg) -> "$theme $name %.2f".format(contrast(fg, bg)) }
        assertWithMessage("Pairs below 4.5:1: $low").that(low).isEmpty()
    }

    @Test
    fun `light theme pairs reach 4_5 to 1`() {
        val c = IntakeColorsLight
        check(
            "light",
            listOf(
                Triple("ink on bg", InkLight, BgLight),
                Triple("ink on card", InkLight, CardLight),
                Triple("ink on soft", InkLight, SoftLight),
                Triple("muted on bg", MutedLight, BgLight),
                Triple("muted on card", MutedLight, CardLight),
                Triple("muted on soft", MutedLight, SoftLight),
                Triple("inkOn on ink", InkOnLight, InkLight),
                Triple("onAccent on accent", c.onAccent, c.accent),
                Triple("onHero on hero", c.onHero, c.hero),
                Triple("dock inactive on dock", c.dockInactive, c.dock),
                Triple("accent on dock", c.accent, c.dock),
                Triple("ok on okBg", c.ok, c.okBg),
                Triple("warn on warnBg", c.warn, c.warnBg),
                Triple("bad on badBg", c.bad, c.badBg),
                Triple("ai on aiBg", c.ai, c.aiBg),
                Triple("ok on card", c.ok, CardLight),
                Triple("warn on card", c.warn, CardLight),
                Triple("bad on card", c.bad, CardLight),
            ),
        )
    }

    @Test
    fun `dark theme pairs reach 4_5 to 1`() {
        val c = IntakeColorsDark
        check(
            "dark",
            listOf(
                Triple("ink on bg", InkDark, BgDark),
                Triple("ink on card", InkDark, CardDark),
                Triple("ink on soft", InkDark, SoftDark),
                Triple("muted on bg", MutedDark, BgDark),
                Triple("muted on card", MutedDark, CardDark),
                Triple("muted on soft", MutedDark, SoftDark),
                Triple("inkOn on ink", InkOnDark, InkDark),
                Triple("onAccent on accent", c.onAccent, c.accent),
                Triple("onHero on hero", c.onHero, c.hero),
                Triple("dock inactive on dock", c.dockInactive, c.dock),
                Triple("accent on dock", c.accent, c.dock),
                Triple("ok on okBg", c.ok, c.okBg),
                Triple("warn on warnBg", c.warn, c.warnBg),
                Triple("bad on badBg", c.bad, c.badBg),
                Triple("ai on aiBg", c.ai, c.aiBg),
                Triple("ok on card", c.ok, CardDark),
                Triple("warn on card", c.warn, CardDark),
                Triple("bad on card", c.bad, CardDark),
                Triple("accent on hero", c.accent, c.hero),
            ),
        )
    }

    @Test
    fun `contains and may contain stay visibly different`() {
        // SPEC 7.3: red and amber are different hues in both themes, never the same pair.
        listOf(IntakeColorsLight, IntakeColorsDark).forEach { c ->
            assertWithMessage("bad and warn text must differ").that(c.bad).isNotEqualTo(c.warn)
            assertWithMessage("bad and warn fills must differ").that(c.badBg).isNotEqualTo(c.warnBg)
        }
    }
}
