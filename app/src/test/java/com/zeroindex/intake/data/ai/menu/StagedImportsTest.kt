package com.zeroindex.intake.data.ai.menu

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.data.db.entity.AllergenStatus
import com.zeroindex.intake.data.db.entity.MenuItemEntity
import com.zeroindex.intake.data.db.entity.MenuRunStatus
import com.zeroindex.intake.data.db.entity.MenuSourceEntity
import com.zeroindex.intake.data.db.entity.MenuSourceKind
import java.io.File
import org.junit.Test

class StagedImportsTest {
    private val source = MenuSourceEntity(id = "S1", restaurantName = "R", kind = MenuSourceKind.URL, location = "https://x", itemCount = 4, lastStatus = MenuRunStatus.OK, createdAt = 1, updatedAt = 1)

    private fun item(name: String, status: AllergenStatus) = MenuItemEntity(
        restaurantName = "R", name = name, kcal = 100.0, allergenStatus = status, sourceId = "S1", importedAt = 1, createdAt = 1, updatedAt = 1,
    )

    private val staged = listOf(
        StagedItem(item("Fries", AllergenStatus.CLEAR)),
        StagedItem(item("Shake", AllergenStatus.CONTAINS)),
        StagedItem(item("Burger", AllergenStatus.MAY_CONTAIN)),
    )

    @Test
    fun `staging ids round trip and are recognised`() {
        assertThat(StagedImports.stagingId("S1")).isEqualTo("staged:S1")
        assertThat(StagedImports.isStagingId("staged:S1")).isTrue()
        assertThat(StagedImports.isStagingId("S1")).isFalse()
        assertThat(StagedImports.isStagingId(null)).isFalse()
        assertThat(StagedImports.targetId("staged:S1")).isEqualTo("S1")
    }

    @Test
    fun `the DAO queries that hide staged rows use the same prefix`() {
        val dao = File("src/main/java/com/zeroindex/intake/data/db/dao/MenuDao.kt").readText()
        assertThat(dao).contains("NOT LIKE '${StagedImports.PREFIX}%'")
    }

    @Test
    fun `a finished import waits as To review and its source keeps its saved items count`() {
        val marked = StagedImports.markToReview(source, now = 5)
        assertThat(marked.lastStatus).isEqualTo(MenuRunStatus.TO_REVIEW)
        assertThat(StagedImports.needsReview(marked.lastStatus)).isTrue()
        assertThat(marked.itemCount).isEqualTo(4)
        val hidden = StagedImports.stagingSource(source, staged.size, 5)
        assertThat(hidden.id).isEqualTo("staged:S1")
        assertThat(hidden.enabled).isFalse()
        assertThat(hidden.itemCount).isEqualTo(3)
    }

    @Test
    fun `staged rows get fresh ids under the staging source so saved rows are never moved`() {
        val rows = StagedImports.toRows(source, staged, now = 5)
        assertThat(rows).hasSize(3)
        assertThat(rows.map { it.sourceId }.toSet()).containsExactly("staged:S1")
        assertThat(rows.map { it.id }.toSet()).hasSize(3)
        assertThat(rows.map { it.id }).containsNoneIn(staged.map { it.entity.id })
        assertThat(rows.all { it.deletedAt == null }).isTrue()
    }

    @Test
    fun `reopening restores every staged item for the real source with the default accept rule`() {
        val rows = StagedImports.toRows(source, staged, now = 5)
        val back = StagedImports.fromRows(source, rows)
        assertThat(back.map { it.entity.name }).containsExactly("Fries", "Shake", "Burger").inOrder()
        assertThat(back.map { it.entity.sourceId }.toSet()).containsExactly("S1")
        // An item that contains an allergen starts rejected; the rest start accepted. The person can change either.
        assertThat(back.map { it.accepted }).containsExactly(true, false, true).inOrder()
        assertThat(back.map { it.entity.allergenStatus }).containsExactly(AllergenStatus.CLEAR, AllergenStatus.CONTAINS, AllergenStatus.MAY_CONTAIN).inOrder()
    }

    @Test
    fun `accepting saves only the accepted items, to the real source`() {
        val items = listOf(staged[0], staged[1].copy(accepted = true), staged[2].copy(accepted = false))
        val saved = StagedImports.acceptedEntities(source, items)
        assertThat(saved.map { it.name }).containsExactly("Fries", "Shake")
        assertThat(saved.map { it.sourceId }.toSet()).containsExactly("S1")
    }

    @Test
    fun `discarding returns a source to how it read before`() {
        assertThat(StagedImports.statusAfterDiscard(savedItemCount = 4)).isEqualTo(MenuRunStatus.OK)
        assertThat(StagedImports.statusAfterDiscard(savedItemCount = 0)).isEqualTo(MenuRunStatus.NEVER_RUN)
    }

    @Test
    fun `only To review needs a review`() {
        MenuRunStatus.entries.filter { it != MenuRunStatus.TO_REVIEW }.forEach { assertThat(StagedImports.needsReview(it)).isFalse() }
    }
}
