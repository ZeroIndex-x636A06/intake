package com.zeroindex.intake.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.zeroindex.intake.data.db.entity.AiCorrectionEntity
import com.zeroindex.intake.data.db.entity.AiInsightEntity
import com.zeroindex.intake.data.db.entity.AllergenAliasEntity
import com.zeroindex.intake.data.db.entity.AllergenDefinitionEntity
import com.zeroindex.intake.data.db.entity.AllergenProfileEntity
import com.zeroindex.intake.data.db.entity.BackupRunEntity
import com.zeroindex.intake.data.db.entity.BodyMetricEntity
import com.zeroindex.intake.data.db.entity.CoachSuggestionEntity
import com.zeroindex.intake.data.db.entity.EatOutOptionEntity
import com.zeroindex.intake.data.db.entity.ExerciseEntity
import com.zeroindex.intake.data.db.entity.FoodEntity
import com.zeroindex.intake.data.db.entity.FoodLogEntity
import com.zeroindex.intake.data.db.entity.HealthDailySummaryEntity
import com.zeroindex.intake.data.db.entity.HydrationLogEntity
import com.zeroindex.intake.data.db.entity.HydrationPresetEntity
import com.zeroindex.intake.data.db.entity.MealTemplateEntity
import com.zeroindex.intake.data.db.entity.MealTemplateItemEntity
import com.zeroindex.intake.data.db.entity.MenuItemEntity
import com.zeroindex.intake.data.db.entity.MenuSourceEntity
import com.zeroindex.intake.data.db.entity.ModelBenchmarkEntity
import com.zeroindex.intake.data.db.entity.NutritionTargetEntity
import com.zeroindex.intake.data.db.entity.PerformanceMetricEntity
import com.zeroindex.intake.data.db.entity.MealPlanEntity
import com.zeroindex.intake.data.db.entity.PlanWeekEntity
import com.zeroindex.intake.data.db.entity.PlanWeekRecipeEntity
import com.zeroindex.intake.data.db.entity.ProgramDayEntity
import com.zeroindex.intake.data.db.entity.ProgramEntity
import com.zeroindex.intake.data.db.entity.ProgramExerciseEntity
import com.zeroindex.intake.data.db.entity.ProgressionRuleEntity
import com.zeroindex.intake.data.db.entity.RecipeEntity
import com.zeroindex.intake.data.db.entity.RecipeIngredientEntity
import com.zeroindex.intake.data.db.entity.SetLogEntity
import com.zeroindex.intake.data.db.entity.ShoppingListItemEntity
import com.zeroindex.intake.data.db.entity.TdeeEstimateEntity
import com.zeroindex.intake.data.db.entity.WorkoutSessionEntity

/**
 * Whole tables, soft-deleted rows included, for backup and restore (SPEC §13:
 * the export must round-trip losslessly, and a deletion is data too).
 * Nothing else may use this: every other read filters on `deletedAt`.
 */
@Dao
interface ExportDao {
    @Query("SELECT * FROM ai_correction") suspend fun aiCorrection(): List<AiCorrectionEntity>
    @Upsert suspend fun upsertAiCorrection(rows: List<AiCorrectionEntity>)
    @Query("SELECT * FROM ai_insight") suspend fun aiInsight(): List<AiInsightEntity>
    @Upsert suspend fun upsertAiInsight(rows: List<AiInsightEntity>)
    @Query("SELECT * FROM allergen_alias") suspend fun allergenAlias(): List<AllergenAliasEntity>
    @Upsert suspend fun upsertAllergenAlias(rows: List<AllergenAliasEntity>)
    @Query("SELECT * FROM allergen_definition") suspend fun allergenDefinition(): List<AllergenDefinitionEntity>
    @Upsert suspend fun upsertAllergenDefinition(rows: List<AllergenDefinitionEntity>)
    @Query("SELECT * FROM allergen_profile") suspend fun allergenProfile(): List<AllergenProfileEntity>
    @Upsert suspend fun upsertAllergenProfile(rows: List<AllergenProfileEntity>)
    @Query("SELECT * FROM backup_run") suspend fun backupRun(): List<BackupRunEntity>
    @Upsert suspend fun upsertBackupRun(rows: List<BackupRunEntity>)
    @Query("SELECT * FROM body_metric") suspend fun bodyMetric(): List<BodyMetricEntity>
    @Upsert suspend fun upsertBodyMetric(rows: List<BodyMetricEntity>)
    @Query("SELECT * FROM meal_plan") suspend fun mealPlan(): List<MealPlanEntity>
    @Upsert suspend fun upsertMealPlan(rows: List<MealPlanEntity>)
    @Query("SELECT * FROM plan_week") suspend fun planWeek(): List<PlanWeekEntity>
    @Upsert suspend fun upsertPlanWeek(rows: List<PlanWeekEntity>)
    @Query("SELECT * FROM eat_out_option") suspend fun eatOutOption(): List<EatOutOptionEntity>
    @Upsert suspend fun upsertEatOutOption(rows: List<EatOutOptionEntity>)
    @Query("SELECT * FROM exercise") suspend fun exercise(): List<ExerciseEntity>
    @Upsert suspend fun upsertExercise(rows: List<ExerciseEntity>)
    @Query("SELECT * FROM food") suspend fun food(): List<FoodEntity>
    @Upsert suspend fun upsertFood(rows: List<FoodEntity>)
    @Query("SELECT * FROM food_log") suspend fun foodLog(): List<FoodLogEntity>
    @Upsert suspend fun upsertFoodLog(rows: List<FoodLogEntity>)
    @Query("SELECT * FROM health_daily_summary") suspend fun healthDailySummary(): List<HealthDailySummaryEntity>
    @Upsert suspend fun upsertHealthDailySummary(rows: List<HealthDailySummaryEntity>)
    @Query("SELECT * FROM hydration_log") suspend fun hydrationLog(): List<HydrationLogEntity>
    @Upsert suspend fun upsertHydrationLog(rows: List<HydrationLogEntity>)
    @Query("SELECT * FROM hydration_preset") suspend fun hydrationPreset(): List<HydrationPresetEntity>
    @Upsert suspend fun upsertHydrationPreset(rows: List<HydrationPresetEntity>)
    @Query("SELECT * FROM meal_template") suspend fun mealTemplate(): List<MealTemplateEntity>
    @Upsert suspend fun upsertMealTemplate(rows: List<MealTemplateEntity>)
    @Query("SELECT * FROM meal_template_item") suspend fun mealTemplateItem(): List<MealTemplateItemEntity>
    @Upsert suspend fun upsertMealTemplateItem(rows: List<MealTemplateItemEntity>)
    @Query("SELECT * FROM menu_source") suspend fun menuSource(): List<MenuSourceEntity>
    @Upsert suspend fun upsertMenuSource(rows: List<MenuSourceEntity>)
    @Query("SELECT * FROM menu_item") suspend fun menuItem(): List<MenuItemEntity>
    @Upsert suspend fun upsertMenuItem(rows: List<MenuItemEntity>)
    @Query("SELECT * FROM model_benchmark") suspend fun modelBenchmark(): List<ModelBenchmarkEntity>
    @Upsert suspend fun upsertModelBenchmark(rows: List<ModelBenchmarkEntity>)
    @Query("SELECT * FROM nutrition_target") suspend fun nutritionTarget(): List<NutritionTargetEntity>
    @Upsert suspend fun upsertNutritionTarget(rows: List<NutritionTargetEntity>)
    @Query("SELECT * FROM performance_metric") suspend fun performanceMetric(): List<PerformanceMetricEntity>
    @Upsert suspend fun upsertPerformanceMetric(rows: List<PerformanceMetricEntity>)
    @Query("SELECT * FROM recipe") suspend fun recipe(): List<RecipeEntity>
    @Upsert suspend fun upsertRecipe(rows: List<RecipeEntity>)
    @Query("SELECT * FROM plan_week_recipe") suspend fun planWeekRecipe(): List<PlanWeekRecipeEntity>
    @Upsert suspend fun upsertPlanWeekRecipe(rows: List<PlanWeekRecipeEntity>)
    @Query("SELECT * FROM program") suspend fun program(): List<ProgramEntity>
    @Upsert suspend fun upsertProgram(rows: List<ProgramEntity>)
    @Query("SELECT * FROM program_day") suspend fun programDay(): List<ProgramDayEntity>
    @Upsert suspend fun upsertProgramDay(rows: List<ProgramDayEntity>)
    @Query("SELECT * FROM progression_rule") suspend fun progressionRule(): List<ProgressionRuleEntity>
    @Upsert suspend fun upsertProgressionRule(rows: List<ProgressionRuleEntity>)
    @Query("SELECT * FROM program_exercise") suspend fun programExercise(): List<ProgramExerciseEntity>
    @Upsert suspend fun upsertProgramExercise(rows: List<ProgramExerciseEntity>)
    @Query("SELECT * FROM recipe_ingredient") suspend fun recipeIngredient(): List<RecipeIngredientEntity>
    @Upsert suspend fun upsertRecipeIngredient(rows: List<RecipeIngredientEntity>)
    @Query("SELECT * FROM workout_session") suspend fun workoutSession(): List<WorkoutSessionEntity>
    @Upsert suspend fun upsertWorkoutSession(rows: List<WorkoutSessionEntity>)
    @Query("SELECT * FROM set_log") suspend fun setLog(): List<SetLogEntity>
    @Upsert suspend fun upsertSetLog(rows: List<SetLogEntity>)
    @Query("SELECT * FROM shopping_list_item") suspend fun shoppingListItem(): List<ShoppingListItemEntity>
    @Upsert suspend fun upsertShoppingListItem(rows: List<ShoppingListItemEntity>)
    @Query("SELECT * FROM tdee_estimate") suspend fun tdeeEstimate(): List<TdeeEstimateEntity>
    @Upsert suspend fun upsertTdeeEstimate(rows: List<TdeeEstimateEntity>)
    @Query("SELECT * FROM coach_suggestion") suspend fun coachSuggestion(): List<CoachSuggestionEntity>
    @Upsert suspend fun upsertCoachSuggestion(rows: List<CoachSuggestionEntity>)
}
