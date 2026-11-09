package com.zeroindex.intake.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.zeroindex.intake.data.db.entity.EatOutOptionEntity
import com.zeroindex.intake.data.db.entity.MealPlanEntity
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

    // ---- plans -----------------------------------------------------------------------------

    @Upsert suspend fun upsertPlan(plan: MealPlanEntity)
    @Upsert suspend fun upsertPlans(plans: List<MealPlanEntity>)

    @Query("SELECT * FROM meal_plan WHERE deletedAt IS NULL ORDER BY isActive DESC, name COLLATE NOCASE")
    fun observePlans(): Flow<List<MealPlanEntity>>

    @Query("SELECT * FROM meal_plan WHERE deletedAt IS NULL ORDER BY isActive DESC, name COLLATE NOCASE")
    suspend fun plans(): List<MealPlanEntity>

    @Query("SELECT * FROM meal_plan WHERE deletedAt IS NULL AND isActive = 1 LIMIT 1")
    fun observeActivePlan(): Flow<MealPlanEntity?>

    @Query("SELECT * FROM meal_plan WHERE deletedAt IS NULL AND isActive = 1 LIMIT 1")
    suspend fun activePlan(): MealPlanEntity?

    @Query("SELECT * FROM meal_plan WHERE id = :id AND deletedAt IS NULL")
    suspend fun plan(id: String): MealPlanEntity?

    @Query("SELECT * FROM meal_plan WHERE deletedAt IS NULL AND name = :name COLLATE NOCASE LIMIT 1")
    suspend fun planByName(name: String): MealPlanEntity?

    @Query("UPDATE meal_plan SET isActive = 0, updatedAt = :now WHERE isActive = 1 AND id != :exceptId")
    suspend fun deactivateOthers(exceptId: String, now: Long)

    @Query("UPDATE meal_plan SET deletedAt = :now, updatedAt = :now, isActive = 0 WHERE id = :id")
    suspend fun softDeletePlan(id: String, now: Long)

    // ---- weeks of one plan -------------------------------------------------------------------

    @Query("SELECT * FROM plan_week WHERE deletedAt IS NULL AND planId = :planId ORDER BY weekNumber")
    fun observeWeeks(planId: String): Flow<List<PlanWeekEntity>>

    @Query("SELECT * FROM plan_week WHERE deletedAt IS NULL AND planId = :planId ORDER BY weekNumber")
    suspend fun weeksOf(planId: String): List<PlanWeekEntity>

    @Query("SELECT * FROM plan_week WHERE deletedAt IS NULL ORDER BY planId, weekNumber")
    suspend fun allWeeks(): List<PlanWeekEntity>

    @Query("SELECT * FROM plan_week WHERE deletedAt IS NULL AND planId = :planId AND weekNumber = :weekNumber LIMIT 1")
    fun observeWeek(planId: String, weekNumber: Int): Flow<PlanWeekEntity?>

    @Query("SELECT * FROM plan_week WHERE deletedAt IS NULL AND planId = :planId AND weekNumber = :weekNumber LIMIT 1")
    suspend fun week(planId: String, weekNumber: Int): PlanWeekEntity?

    /** Weeks with no plan: rows that arrived from a schema-2 backup or peer. */
    @Query("SELECT * FROM plan_week WHERE planId = '' AND deletedAt IS NULL")
    suspend fun orphanWeeks(): List<PlanWeekEntity>

    @Query("SELECT * FROM meal_plan WHERE id = :id")
    suspend fun planIncludingArchived(id: String): MealPlanEntity?

    /** Includes soft-deleted weeks: an import reuses a removed week's row instead of adding a twin. */
    @Query("SELECT * FROM plan_week WHERE planId = :planId")
    suspend fun weeksIncludingRemoved(planId: String): List<PlanWeekEntity>

    @Query("SELECT * FROM plan_week_recipe WHERE planWeekId = :planWeekId AND deletedAt IS NULL")
    fun observeWeekRecipes(planWeekId: String): Flow<List<PlanWeekRecipeEntity>>

    @Query("SELECT * FROM plan_week_recipe WHERE planWeekId = :planWeekId AND deletedAt IS NULL")
    suspend fun weekRecipes(planWeekId: String): List<PlanWeekRecipeEntity>

    @Query("SELECT * FROM plan_week_recipe WHERE id = :id")
    suspend fun weekRecipeById(id: String): PlanWeekRecipeEntity?

    @Query("SELECT * FROM plan_week_recipe WHERE planWeekId = :planWeekId")
    suspend fun weekRecipesIncludingRemoved(planWeekId: String): List<PlanWeekRecipeEntity>

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
