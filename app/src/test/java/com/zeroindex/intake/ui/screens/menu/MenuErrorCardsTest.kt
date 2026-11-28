package com.zeroindex.intake.ui.screens.menu

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.data.db.entity.MenuRunStatus
import com.zeroindex.intake.data.db.entity.MenuSourceEntity
import com.zeroindex.intake.data.db.entity.MenuSourceKind
import org.junit.Test

class MenuErrorCardsTest {
    private val failed = MenuSourceEntity(
        restaurantName = "McDonald's", kind = MenuSourceKind.URL, location = "https://x.test/a.pdf",
        lastStatus = MenuRunStatus.FAILED, lastError = "x.test refused the download (HTTP 403).",
        createdAt = 1, updatedAt = 1,
    )

    @Test
    fun `the same failure is shown once, on the top card`() {
        assertThat(MenuErrorCards.sourceFailure(listOf(failed), failed.lastError, null)).isNull()
    }

    @Test
    fun `a source offered AI does not also get a failure card`() {
        assertThat(MenuErrorCards.sourceFailure(listOf(failed), null, failed.id)).isNull()
    }

    @Test
    fun `an older failure still shows once nothing else says it`() {
        assertThat(MenuErrorCards.sourceFailure(listOf(failed), null, null)).isEqualTo(failed)
        assertThat(MenuErrorCards.sourceFailure(listOf(failed), "Something else", null)).isEqualTo(failed)
    }
}
