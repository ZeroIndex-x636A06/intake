package com.zeroindex.intake.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.zeroindex.intake.data.db.entity.ShoppingListItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ShoppingListDao {
    @Upsert suspend fun upsert(item: ShoppingListItemEntity)
    @Upsert suspend fun upsertAll(items: List<ShoppingListItemEntity>)

    @Query(
        """
        SELECT * FROM shopping_list_item
        WHERE planWeekId = :planWeekId AND deletedAt IS NULL
        ORDER BY isPantryStaple, category, name
        """,
    )
    fun observeForWeek(planWeekId: String): Flow<List<ShoppingListItemEntity>>

    @Query(
        """
        SELECT * FROM shopping_list_item
        WHERE planWeekId = :planWeekId AND deletedAt IS NULL
        ORDER BY isPantryStaple, category, name
        """,
    )
    suspend fun forWeek(planWeekId: String): List<ShoppingListItemEntity>

    /** Ids of every row for the week, soft-deleted ones included: generation must not resurrect a removal. */
    @Query("SELECT id FROM shopping_list_item WHERE planWeekId = :planWeekId")
    suspend fun idsForWeek(planWeekId: String): List<String>

    @Query("SELECT * FROM shopping_list_item WHERE deletedAt IS NULL")
    suspend fun all(): List<ShoppingListItemEntity>

    @Query("UPDATE shopping_list_item SET isChecked = :checked, updatedAt = :now WHERE id = :id")
    suspend fun setChecked(id: String, checked: Boolean, now: Long)

    /** SPEC §9: check state resets when the week changes. */
    @Query(
        "UPDATE shopping_list_item SET isChecked = 0, updatedAt = :now WHERE planWeekId = :planWeekId AND deletedAt IS NULL",
    )
    suspend fun resetChecks(planWeekId: String, now: Long)

    @Query("UPDATE shopping_list_item SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)
}
