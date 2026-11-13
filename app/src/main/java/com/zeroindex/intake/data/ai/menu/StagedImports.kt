package com.zeroindex.intake.data.ai.menu

import com.zeroindex.intake.data.db.entity.AllergenStatus
import com.zeroindex.intake.data.db.entity.MenuItemEntity
import com.zeroindex.intake.data.db.entity.MenuRunStatus
import com.zeroindex.intake.data.db.entity.MenuSourceEntity
import com.zeroindex.intake.data.db.entity.newId

/**
 * Imports waiting for review. A menu import is read into staged items, and the person accepts or rejects them on a review
 * screen. Closing that screen (back, a swipe, an accidental tap outside) must lose nothing, so the staged items are kept
 * in the existing tables with no schema change:
 *
 *  - the source row keeps the status [MenuRunStatus.TO_REVIEW];
 *  - the staged items are menu item rows under a hidden "staging" source whose id is [stagingId] of the real source.
 *    The DAO queries that feed Log food, Swap and Plan B leave staging rows out, so nothing unreviewed is ever offered.
 *
 * Accepting saves the accepted items to the real source and soft deletes the staging rows; "Discard import" soft deletes
 * them without saving. Nothing is hard deleted.
 */
object StagedImports {
    /** The prefix of a staging source id. The DAO queries in `MenuDao` spell it out, and a test keeps them in step. */
    const val PREFIX = "staged:"

    fun stagingId(sourceId: String): String = PREFIX + sourceId

    fun isStagingId(id: String?): Boolean = id != null && id.startsWith(PREFIX)

    fun targetId(stagingId: String): String = stagingId.removePrefix(PREFIX)

    /** An item that contains an allergen starts as rejected; the person can still accept it. Same rule as [StagedItem]. */
    fun defaultAccepted(status: AllergenStatus): Boolean = status != AllergenStatus.CONTAINS

    fun needsReview(status: MenuRunStatus): Boolean = status == MenuRunStatus.TO_REVIEW

    /** The hidden source that holds [target]'s staged items. It is never enabled and never listed. */
    fun stagingSource(target: MenuSourceEntity, itemCount: Int, now: Long): MenuSourceEntity = target.copy(
        id = stagingId(target.id), enabled = false, lastStatus = MenuRunStatus.TO_REVIEW, lastError = null,
        itemCount = itemCount, createdAt = now, updatedAt = now, deletedAt = null,
    )

    /** The target as the sources list shows it while a review waits. Its saved items are untouched. */
    fun markToReview(target: MenuSourceEntity, now: Long): MenuSourceEntity =
        target.copy(lastStatus = MenuRunStatus.TO_REVIEW, lastError = null, updatedAt = now)

    /** Rows to store for [items]: fresh ids, so staging a refresh never moves the rows already saved for the source. */
    fun toRows(target: MenuSourceEntity, items: List<StagedItem>, now: Long): List<MenuItemEntity> =
        items.map { it.entity.copy(id = newId(), sourceId = stagingId(target.id), updatedAt = now, deletedAt = null) }

    /** The staged items again, as the review screen shows them, belonging to the real source. */
    fun fromRows(target: MenuSourceEntity, rows: List<MenuItemEntity>): List<StagedItem> =
        rows.map { StagedItem(it.copy(sourceId = target.id), accepted = defaultAccepted(it.allergenStatus)) }

    /** What to save when the person accepts: the accepted items only. */
    fun acceptedEntities(target: MenuSourceEntity, items: List<StagedItem>): List<MenuItemEntity> =
        items.filter { it.accepted }.map { it.entity.copy(sourceId = target.id) }

    /** How the source reads once its staged import is discarded: as saved before, or never run if nothing was ever saved. */
    fun statusAfterDiscard(savedItemCount: Int): MenuRunStatus =
        if (savedItemCount > 0) MenuRunStatus.OK else MenuRunStatus.NEVER_RUN
}
