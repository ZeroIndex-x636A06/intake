package com.zeroindex.intake.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.zeroindex.intake.data.db.entity.MealTemplateEntity
import com.zeroindex.intake.data.db.entity.MealTemplateItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MealTemplateDao {
    @Upsert suspend fun upsertTemplate(template: MealTemplateEntity)
    @Upsert suspend fun upsertItems(items: List<MealTemplateItemEntity>)

    @Query("SELECT * FROM meal_template WHERE deletedAt IS NULL ORDER BY name")
    fun observeAll(): Flow<List<MealTemplateEntity>>

    @Query("SELECT * FROM meal_template WHERE deletedAt IS NULL ORDER BY name")
    suspend fun all(): List<MealTemplateEntity>

    @Query(
        "SELECT * FROM meal_template_item WHERE mealTemplateId = :templateId AND deletedAt IS NULL ORDER BY displayOrder",
    )
    suspend fun itemsOf(templateId: String): List<MealTemplateItemEntity>

    @Query("SELECT * FROM meal_template_item WHERE deletedAt IS NULL")
    suspend fun allItems(): List<MealTemplateItemEntity>

    /** Editing a template replaces its items: the old rows are soft-deleted, never removed. */
    @Query("UPDATE meal_template_item SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDeleteItem(id: String, now: Long)

    @Query("UPDATE meal_template SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)
}
