package com.zeroindex.intake.domain.barcode

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BarcodesTest {

    @Test
    fun checkDigitsAreValidatedForEveryLength() {
        assertThat(Barcodes.hasValidCheckDigit("3017620422003")).isTrue() // EAN-13
        assertThat(Barcodes.hasValidCheckDigit("894700010137")).isTrue() // UPC-A
        assertThat(Barcodes.hasValidCheckDigit("96385074")).isTrue() // EAN-8
        assertThat(Barcodes.hasValidCheckDigit("3017620422004")).isFalse()
        assertThat(Barcodes.hasValidCheckDigit("12345")).isFalse()
        assertThat(Barcodes.hasValidCheckDigit("30176204220a3")).isFalse()
    }

    @Test
    fun upcAGetsALeadingZeroToMatchOpenFoodFacts() {
        assertThat(Barcodes.normalize("894700010137")).isEqualTo("0894700010137")
        assertThat(Barcodes.normalize("0894700010137")).isEqualTo("0894700010137")
        assertThat(Barcodes.normalize("96385074")).isEqualTo("96385074")
    }

    @Test
    fun typedNumbersMayHaveSpacesAndDashes() {
        assertThat(Barcodes.normalize("3017 6204-22003")).isEqualTo("3017620422003")
    }

    @Test
    fun aMisreadOrMistypedDigitIsRejected() {
        assertThat(Barcodes.normalize("3017620422004")).isNull()
        assertThat(Barcodes.normalize("")).isNull()
        assertThat(Barcodes.normalize("1234567")).isNull()
    }

    @Test
    fun upcEExpandsToTheUpcAItStandsFor() {
        // The worked example from the UPC-E specification: 04252614 <-> 042100005264.
        assertThat(Barcodes.expandUpcE("04252614")).isEqualTo("042100005264")
        assertThat(Barcodes.normalize("04252614", isUpcE = true)).isEqualTo("0042100005264")
        assertThat(Barcodes.expandUpcE("24252614")).isNull() // only number systems 0 and 1
    }

    @Test
    fun eightDigitsAreAnEan8UnlessTheScannerSaidUpcE() {
        assertThat(Barcodes.normalize("96385074", isUpcE = false)).isEqualTo("96385074")
    }

    @Test
    fun lookupFormsIncludeTheOldTwelveDigitSpelling() {
        assertThat(Barcodes.lookupForms("0894700010137")).containsExactly("0894700010137", "894700010137").inOrder()
        assertThat(Barcodes.lookupForms("3017620422003")).containsExactly("3017620422003")
    }

    @Test
    fun firstValidSkipsReadsWithABadCheckDigit() {
        val reads = listOf(
            ScannedRead("3017620422004", isUpcE = false),
            ScannedRead("894700010137", isUpcE = false),
        )
        assertThat(Barcodes.firstValid(reads)).isEqualTo("0894700010137")
        assertThat(Barcodes.firstValid(listOf(ScannedRead("1", false)))).isNull()
    }
}
