package com.zeroindex.intake.ui.screens.backup

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.data.db.entity.SyncConflictEntity
import org.junit.Test

class SyncConflictChangesTest {
    @Test
    fun `only real field differences are listed, not bookkeeping`() {
        val c = SyncConflictEntity(
            tableName = "food_log",
            rowId = "r",
            keptJson = """{"id":"r","grams":150.0,"note":"rice","updatedAt":2,"createdAt":1}""",
            otherJson = """{"id":"r","grams":200.0,"note":"rice","updatedAt":3,"createdAt":1}""",
            otherSource = "this device",
            detectedAt = 0, createdAt = 0, updatedAt = 0,
        )
        assertThat(SyncViewModel.changes(c)).containsExactly(FieldChange("grams", "150.0", "200.0"))
    }
}
