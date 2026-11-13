package com.zeroindex.intake.ui.navigation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The back motion must be the push motion played in reverse: same distance, same duration, same alpha
 * change, opposite direction. Pinned on the plain numbers in [NavSpecs], so a later edit cannot drift one side.
 */
class NavTransitionsTest {

    private fun NavSlide.reversed() = NavSlide(
        fromOffset = toOffset,
        toOffset = fromOffset,
        fromAlpha = toAlpha,
        toAlpha = fromAlpha,
        durationMillis = durationMillis,
    )

    @Test
    fun `back the top screen is the push enter played in reverse`() {
        assertThat(NavSpecs.popOut).isEqualTo(NavSpecs.push.reversed())
    }

    @Test
    fun `back the previous screen is the push exit played in reverse`() {
        assertThat(NavSpecs.pop).isEqualTo(NavSpecs.pushUnder.reversed())
    }

    @Test
    fun `push and back use one duration`() {
        assertThat(NavSpecs.popOut.durationMillis).isEqualTo(NavSpecs.push.durationMillis)
        assertThat(NavSpecs.pop.durationMillis).isEqualTo(NavSpecs.pushUnder.durationMillis)
        assertThat(NavSpecs.push.durationMillis).isEqualTo(NavSpecs.SLIDE_MILLIS)
    }

    @Test
    fun `a push comes in from the right and the screen under it moves a quarter of a width left and dims`() {
        assertThat(NavSpecs.push.fromOffset).isEqualTo(1f)
        assertThat(NavSpecs.push.toOffset).isEqualTo(0f)
        assertThat(NavSpecs.pushUnder.fromOffset).isEqualTo(0f)
        assertThat(NavSpecs.pushUnder.toOffset).isEqualTo(-0.25f)
        assertThat(NavSpecs.pushUnder.toAlpha).isLessThan(1f)
        assertThat(NavSpecs.pushUnder.toAlpha).isGreaterThan(0.5f)
    }

    @Test
    fun `a tab switch is a cross-fade that is quicker than a slide`() {
        assertThat(NavSpecs.TAB_FADE_MILLIS).isLessThan(NavSpecs.SLIDE_MILLIS)
    }
}
