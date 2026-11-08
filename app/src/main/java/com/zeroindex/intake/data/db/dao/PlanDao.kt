package com.zeroindex.intake.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.zeroindex.intake.data.db.entity.EatOutOptionEntity
import com.zeroindex.intake.data.db.entity.PlanWeekEntity
import com.zeroindex.intake.data.db.entity.PlanWeekRecipeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlanDao {
    @Upsert suspend fun upsertWeek(week: PlanWeekEntity)
    @Upsert suspend fun upsertWeeks(weeks: List<PlanWeekEntity>)
    @Upsert suspend fun upsertWeekRecipe(link: PlanWeekRecipeEntity)
    @Upsert suspend fun upsertWeekRecipes(links: List<PlanWeekRecipeEntity>)
    @Upsert suspend fun upsertEatOutOption(option: EatOutOptionEntity)
    @Upsert suspend fun upsertEatOutOptions(options: List<EatOutOptionEntity>)

    @Query("SELECT * FROM plan_week WHERE deletedAt IS NULL ORDER BY weekNumber")
    fun observeWeeks(): Flow<List<PlanWeekEntity>>

    @Query("SELECT * FROM plan_week WHERE deletedAt IS NULL ORDER BY weekNumber")
    suspend fun allWeeks(): List<PlanWeekEntity>

    @Query("SELECT * FROM plan_week WHERE deletedAt IS NULL AND weekNumber = :weekNumber LIMIT 1")
    fun observeWeek(weekNumber: Int): Flow<PlanWeekEntity?>

    @Query("SELECT * FROM plan_week WHERE deletedAt IS NULL AND weekNumber = :weekNumber LIMIT 1")
    suspend fun week(weekNumber: Int): PlanWeekEntity?

    @Query("SELECT * FROM plan_week_recipe WHERE planWeekId = :planWeekId AND deletedAt IS NULL")
    fun observeWeekRecipes(planWeekId: String): Flow<List<PlanWeekRecipeEntity>>

    @Query("SELECT * FROM plan_week_recipe WHERE planWeekId = :planWeekId AND deletedAt IS NULL")
    suspend fun weekRecipes(planWeekId: String): List<PlanWeekRecipeEntity>

    @Query("SELECT * FROM plan_week_recipe WHERE deletedAt IS NULL")
    suspend fun allWeekRecipes(): List<PlanWeekRecipeEntity>

    /**
     * SPEC §9: eat-out options for the current week, plus the ones that apply to
     * every week (null planWeekId). Local only - this is used with no signal.
     */
    @Query(
        """
        SELECT * FROM eat_out_option
        WHERE deletedAt IS NULL AND (planWeekId = :planWeekId OR planWeekId IS NULL)
        ORDER BY venueName
        """,
    )
    fun observeEatOutOptions(planWeekId: String?): Flow<List<EatOutOptionEntity>>

    @Query("SELECT * FROM eat_out_option WHERE deletedAt IS NULL ORDER BY venueName")
    fun observeAllEatOutOptions(): Flow<List<EatOutOptionEntity>>

    @Query("SELECT * FROM eat_out_option WHERE deletedAt IS NULL ORDER BY venueName")
    suspend fun allEatOutOptions(): List<EatOutOptionEntity>

    @Query("UPDATE plan_week SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDeleteWeek(id: String, now: Long)

    @Query("UPDATE eat_out_option SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDeleteEatOutOption(id: String, now: Long)
}
