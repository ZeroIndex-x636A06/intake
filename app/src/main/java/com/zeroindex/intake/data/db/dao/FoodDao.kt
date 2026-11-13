package com.zeroindex.intake.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.zeroindex.intake.data.db.entity.FoodEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodDao {

    @Upsert suspend fun upsert(food: FoodEntity)
    @Upsert suspend fun upsertAll(foods: List<FoodEntity>)

    @Query("SELECT * FROM food WHERE id = :id AND deletedAt IS NULL")
    suspend fun findById(id: String): FoodEntity?

    @Query("SELECT * FROM food WHERE barcode = :barcode AND deletedAt IS NULL LIMIT 1")
    suspend fun findByBarcode(barcode: String): FoodEntity?

    /**
     * SPEC §5: Recents is the default tab and shows the 30 most-logged foods,
     * because in practice logging is mostly repetition.
     */
    @Query(
        """
        SELECT * FROM food
        WHERE deletedAt IS NULL AND timesLogged > 0
        ORDER BY timesLogged DESC, updatedAt DESC
        LIMIT :limit
        """,
    )
    fun observeMostLogged(limit: Int = 30): Flow<List<FoodEntity>>

    @Query("SELECT * FROM food WHERE deletedAt IS NULL AND isFavorite = 1 ORDER BY name")
    fun observeFavorites(): Flow<List<FoodEntity>>

    @Query("SELECT * FROM food WHERE deletedAt IS NULL ORDER BY name")
    suspend fun all(): List<FoodEntity>

    /** Local matches come first in the add sheet, ahead of any remote result (SPEC §5.1). */
    @Query(
        """
        SELECT * FROM food
        WHERE deletedAt IS NULL
          AND (name LIKE '%' || :query || '%' OR brand LIKE '%' || :query || '%')
        ORDER BY timesLogged DESC, name
        LIMIT :limit
        """,
    )
    fun searchByName(query: String, limit: Int = 50): Flow<List<FoodEntity>>

    @Query("UPDATE food SET timesLogged = timesLogged + 1, updatedAt = :now WHERE id = :id")
    suspend fun incrementTimesLogged(id: String, now: Long)

    @Query("UPDATE food SET isFavorite = :favorite, updatedAt = :now WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean, now: Long)

    @Query("UPDATE food SET offContributedAt = :at, updatedAt = :at WHERE id = :id")
    suspend fun markContributed(id: String, at: Long)

    @Query("SELECT * FROM food WHERE deletedAt IS NULL ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<FoodEntity>>

    /** Undo of a soft delete. */
    @Query("UPDATE food SET deletedAt = NULL, updatedAt = :now WHERE id = :id")
    suspend fun restore(id: String, now: Long)

    /** SPEC §3.8: soft delete only. */
    @Query("UPDATE food SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    @Query("SELECT COUNT(*) FROM food WHERE deletedAt IS NULL")
    fun observeCount(): Flow<Int>

    /** True if the id exists at all, deleted or not: a seeded drink the user removed must not come back. */
    @Query("SELECT COUNT(*) FROM food WHERE id = :id")
    suspend fun idCount(id: String): Int

    /** Every drink, or one category's, most-used first, then the plainest name (so Water comes before Sparkling water). */
    /** The saved custom drinks ("My drinks"), most used first. */
    @Query("SELECT * FROM food WHERE deletedAt IS NULL AND customDrink IS NOT NULL ORDER BY timesLogged DESC, name")
    fun observeMyDrinks(): Flow<List<FoodEntity>>

    @Query(
        """
        SELECT * FROM food
        WHERE deletedAt IS NULL AND drinkCategory IS NOT NULL AND (:category IS NULL OR drinkCategory = :category)
        ORDER BY timesLogged DESC, LENGTH(name), name
        """,
    )
    fun observeDrinks(category: String?): Flow<List<FoodEntity>>

    @Query(
        """
        SELECT * FROM food
        WHERE deletedAt IS NULL AND drinkCategory IS NOT NULL
          AND (name LIKE '%' || :query || '%' OR brand LIKE '%' || :query || '%')
        ORDER BY timesLogged DESC, name
        LIMIT :limit
        """,
    )
    fun searchDrinks(query: String, limit: Int = 50): Flow<List<FoodEntity>>
}
