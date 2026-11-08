package com.zeroindex.intake.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.zeroindex.intake.data.db.entity.RecipeEntity
import com.zeroindex.intake.data.db.entity.RecipeIngredientEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeDao {
    @Upsert suspend fun upsertRecipe(recipe: RecipeEntity)
    @Upsert suspend fun upsertRecipes(recipes: List<RecipeEntity>)
    @Upsert suspend fun upsertIngredients(ingredients: List<RecipeIngredientEntity>)

    @Query("SELECT * FROM recipe WHERE id = :id AND deletedAt IS NULL")
    suspend fun findById(id: String): RecipeEntity?

    @Query("SELECT * FROM recipe WHERE deletedAt IS NULL ORDER BY name")
    fun observeAll(): Flow<List<RecipeEntity>>

    @Query("SELECT * FROM recipe WHERE deletedAt IS NULL ORDER BY name")
    suspend fun all(): List<RecipeEntity>

    @Query("SELECT * FROM recipe WHERE deletedAt IS NULL AND sourceWeekNumber = :weekNumber")
    fun observeForWeek(weekNumber: Int): Flow<List<RecipeEntity>>

    @Query(
        "SELECT * FROM recipe_ingredient WHERE recipeId = :recipeId AND deletedAt IS NULL ORDER BY displayOrder",
    )
    fun observeIngredients(recipeId: String): Flow<List<RecipeIngredientEntity>>

    @Query(
        "SELECT * FROM recipe_ingredient WHERE recipeId = :recipeId AND deletedAt IS NULL ORDER BY displayOrder",
    )
    suspend fun ingredientsOf(recipeId: String): List<RecipeIngredientEntity>

    @Query("SELECT * FROM recipe_ingredient WHERE deletedAt IS NULL")
    suspend fun allIngredients(): List<RecipeIngredientEntity>

    @Query("UPDATE recipe SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDeleteRecipe(id: String, now: Long)

    @Query("UPDATE recipe_ingredient SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDeleteIngredient(id: String, now: Long)
}
