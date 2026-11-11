package com.zeroindex.intake.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.zeroindex.intake.data.db.entity.MenuItemEntity
import com.zeroindex.intake.data.db.entity.MenuSourceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MenuDao {
    @Upsert suspend fun upsertSource(source: MenuSourceEntity)
    @Upsert suspend fun upsertSources(sources: List<MenuSourceEntity>)
    @Upsert suspend fun upsertItems(items: List<MenuItemEntity>)

    @Query("SELECT * FROM menu_source WHERE deletedAt IS NULL ORDER BY restaurantName")
    fun observeSources(): Flow<List<MenuSourceEntity>>

    @Query("SELECT * FROM menu_source WHERE deletedAt IS NULL ORDER BY restaurantName")
    suspend fun sources(): List<MenuSourceEntity>

    @Query("SELECT * FROM menu_source WHERE id = :id AND deletedAt IS NULL")
    suspend fun source(id: String): MenuSourceEntity?

    @Query("UPDATE menu_source SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDeleteSource(id: String, now: Long)

    @Query("SELECT * FROM menu_item WHERE sourceId = :sourceId AND deletedAt IS NULL ORDER BY name")
    fun observeItemsOf(sourceId: String): Flow<List<MenuItemEntity>>

    @Query("SELECT * FROM menu_item WHERE deletedAt IS NULL ORDER BY restaurantName, name")
    suspend fun allItems(): List<MenuItemEntity>

    @Query("SELECT * FROM menu_item WHERE id = :id AND deletedAt IS NULL")
    suspend fun item(id: String): MenuItemEntity?

    /** Items the local engine cleared, for Plan B to rank offline. */
    @Query(
        "SELECT * FROM menu_item WHERE deletedAt IS NULL AND allergenStatus = 'CLEAR' ORDER BY restaurantName, name",
    )
    suspend fun clearItems(): List<MenuItemEntity>

    /** A re-import replaces the previous run's items rather than duplicating them. */
    @Query("UPDATE menu_item SET deletedAt = :now, updatedAt = :now WHERE sourceId = :sourceId AND deletedAt IS NULL")
    suspend fun softDeleteItemsOf(sourceId: String, now: Long)
}
