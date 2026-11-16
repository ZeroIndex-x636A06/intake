package com.zeroindex.intake.domain.units

import com.google.common.truth.Truth.assertThat
import java.util.Locale
import org.junit.After
import org.junit.Before
import org.junit.Test

class KcalFormatTest {
    private var saved: Locale? = null

    @Before
    fun useUs() {
        saved = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After
    fun restore() {
        saved?.let { Locale.setDefault(it) }
    }

    @Test
    fun thousandsAreGroupedEverywhereKcalIsShown() {
        assertThat(KcalFormat.format(2393)).isEqualTo("2,393")
        assertThat(KcalFormat.format(1357)).isEqualTo("1,357")
        assertThat(KcalFormat.format(2260)).isEqualTo("2,260")
        assertThat(KcalFormat.format(596)).isEqualTo("596")
        assertThat(KcalFormat.format(0)).isEqualTo("0")
    }

    @Test
    fun aDoubleRoundsToTheWholeKcalFirst() {
        assertThat(KcalFormat.format(2392.6)).isEqualTo("2,393")
        assertThat(KcalFormat.format(2392.4)).isEqualTo("2,392")
    }

    @Test
    fun negativeValuesKeepTheirSign() {
        assertThat(KcalFormat.format(-1250)).isEqualTo("-1,250")
    }

    @Test
    fun doubleTotalsFromMealsAndFoodsAreGroupedToo() {
        // Today's meal cards pass Double totals (QA Q5a-14: "1476 kcal" should read "1,476 kcal").
        assertThat(KcalFormat.format(1476.0)).isEqualTo("1,476")
        assertThat(KcalFormat.format(1187.4)).isEqualTo("1,187")
        assertThat(KcalFormat.format(122.6)).isEqualTo("123")
    }
}
