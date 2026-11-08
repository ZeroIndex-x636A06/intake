package com.zeroindex.intake.domain.backup

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.data.backup.CsvCell
import org.junit.Test

class CsvCellTest {
    @Test
    fun `cells are quoted only when they need it`() {
        assertThat(CsvCell.escape("Chicken thigh")).isEqualTo("Chicken thigh")
        assertThat(CsvCell.escape("Rice, white")).isEqualTo("\"Rice, white\"")
        assertThat(CsvCell.escape("6\" sub")).isEqualTo("\"6\"\" sub\"")
        assertThat(CsvCell.escape("two\nlines")).isEqualTo("\"two\nlines\"")
    }
}
