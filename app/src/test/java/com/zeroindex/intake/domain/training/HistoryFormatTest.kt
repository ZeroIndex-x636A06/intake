package com.zeroindex.intake.domain.training

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HistoryFormatTest {
    @Test fun `large volumes get a thousands separator and no decimal`() {
        assertThat(HistoryFormat.weight(10309.6, "lb")).isEqualTo("10,310 lb")
        assertThat(HistoryFormat.weight(9432.0, "lb")).isEqualTo("9,432 lb")
        assertThat(HistoryFormat.weight(1000.0, "kg")).isEqualTo("1,000 kg")
    }

    @Test fun `weights under a thousand keep one decimal only when they have one`() {
        assertThat(HistoryFormat.weight(160.0, "lb")).isEqualTo("160 lb")
        assertThat(HistoryFormat.weight(62.5, "kg")).isEqualTo("62.5 kg")
        assertThat(HistoryFormat.weight(197.34, "lb")).isEqualTo("197.3 lb")
        assertThat(HistoryFormat.weight(0.0, "lb")).isEqualTo("0 lb")
    }

    @Test fun `record counts and the sessions heading agree in number`() {
        assertThat(HistoryFormat.records(1)).isEqualTo("1 record")
        assertThat(HistoryFormat.records(2)).isEqualTo("2 records")
        assertThat(HistoryFormat.sessionsHeading(3, 1)).isEqualTo("Last 3 sessions · 1 record so far")
        assertThat(HistoryFormat.sessionsHeading(1, 0)).isEqualTo("Last session")
        assertThat(HistoryFormat.sessionsHeading(2, 4)).isEqualTo("Last 2 sessions · 4 records so far")
    }
}
