package com.zeroindex.intake.domain.food

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ServingTextTest {

    @Test fun `a bare weight becomes one serving with its grams`() {
        assertThat(ServingText.of("43g", 43.0).full).isEqualTo("1 serving (43 g)")
        assertThat(ServingText.of("43 g", 43.0).full).isEqualTo("1 serving (43 g)")
    }

    @Test fun `no label at all is one serving`() {
        assertThat(ServingText.of(null, 30.0).full).isEqualTo("1 serving (30 g)")
        assertThat(ServingText.of("  ", 30.0).full).isEqualTo("1 serving (30 g)")
    }

    @Test fun `a label with a count and a unit stays as it is`() {
        assertThat(ServingText.of("1 packet (43 g)", 43.0).full).isEqualTo("1 packet (43 g)")
        assertThat(ServingText.of("3 oz (85 g)", 85.0).full).isEqualTo("3 oz (85 g)")
    }

    @Test fun `the grams come from the serving size, not from the label's bracket`() {
        assertThat(ServingText.of("1 packet (40 g)", 43.0).full).isEqualTo("1 packet (43 g)")
    }

    @Test fun `a fractional count of servings is shown with its grams`() {
        assertThat(ServingText.of("43g", 43.0, servings = 1.5).full).isEqualTo("1.5 servings (64.5 g)")
    }

    @Test fun `a count other than one is pluralised`() {
        assertThat(ServingText.of("1 packet (43 g)", 43.0, servings = 2.0).full).isEqualTo("2 packets (86 g)")
        assertThat(ServingText.of("3 oz (85 g)", 85.0, servings = 2.0).full).isEqualTo("6 oz (170 g)")
        assertThat(ServingText.of("43g", 43.0, servings = 2.0).full).isEqualTo("2 servings (86 g)")
    }

    @Test fun `a plural label becomes singular for one`() {
        // The label's two pieces weigh 30 g, so half a serving is one piece of 15 g.
        assertThat(ServingText.of("2 pieces (30 g)", 30.0, servings = 0.5).full).isEqualTo("1 piece (15 g)")
        assertThat(ServingText.of("2 pieces (30 g)", 30.0, servings = 2.0).full).isEqualTo("4 pieces (60 g)")
    }

    @Test fun `words ending in s, x, ch and consonant y pluralise correctly`() {
        assertThat(ServingText.of("1 glass (250 ml)", 250.0, servings = 2.0).count).isEqualTo("2 glasses")
        assertThat(ServingText.of("1 box (40 g)", 40.0, servings = 2.0).count).isEqualTo("2 boxes")
        assertThat(ServingText.of("1 bunch (50 g)", 50.0, servings = 2.0).count).isEqualTo("2 bunches")
        assertThat(ServingText.of("1 berry (5 g)", 5.0, servings = 2.0).count).isEqualTo("2 berries")
    }

    @Test fun `a multi-word unit pluralises its last word`() {
        assertThat(ServingText.of("1 medium banana (118 g)", 118.0, servings = 2.0).full).isEqualTo("2 medium bananas (236 g)")
    }

    @Test fun `a fraction in the label is read as a count`() {
        assertThat(ServingText.of("1/2 cup (60 g)", 60.0).count).isEqualTo("0.5 cups")
    }

    @Test fun `the phrase exposes the count and the grams separately`() {
        val p = ServingText.of("43g", 43.0, servings = 1.5)
        assertThat(p.count).isEqualTo("1.5 servings")
        assertThat(p.grams).isWithin(0.001).of(64.5)
    }

    @Test fun `grams are rounded to one decimal and no decimals are shown for whole numbers`() {
        assertThat(ServingText.of("43g", 43.0, servings = 1.3).full).isEqualTo("1.3 servings (55.9 g)")
        assertThat(ServingText.of(null, 33.33, servings = 1.0).full).isEqualTo("1 serving (33.3 g)")
    }

    @Test fun `zero or unreadable input does not crash`() {
        assertThat(ServingText.of("0 g", 0.0).full).isEqualTo("1 serving (0 g)")
        assertThat(ServingText.of("43g", Double.NaN).full).isEqualTo("1 serving (0 g)")
    }
}
