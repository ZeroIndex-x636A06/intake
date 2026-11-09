package com.zeroindex.intake.data.backup

import com.zeroindex.intake.data.db.dao.ExportDao
import com.zeroindex.intake.data.db.entity.Audited
import com.zeroindex.intake.data.db.entity.AiCorrectionEntity
import com.zeroindex.intake.data.db.entity.AiInsightEntity
import com.zeroindex.intake.data.db.entity.AllergenAliasEntity
import com.zeroindex.intake.data.db.entity.AllergenDefinitionEntity
import com.zeroindex.intake.data.db.entity.AllergenProfileEntity
import com.zeroindex.intake.data.db.entity.BackupRunEntity
import com.zeroindex.intake.data.db.entity.BodyMetricEntity
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
import kotlinx.serialization.KSerializer

/**
 * One table in a backup: how to read it whole, write it back, and encode it.
 * [naturalKey] is set where a table has a unique column besides its id, so
 * a row from another device for the same thing (one health summary per day)
 * merges with the local row instead of colliding with it.
 */
class BackupTable<T : Audited>(
    val name: String,
    val serializer: KSerializer<T>,
    val read: suspend () -> List<T>,
    val write: suspend (List<T>) -> Unit,
    val naturalKey: ((T) -> String)? = null,
    val withId: ((T, String) -> T)? = null,
)

/**
 * Every table, parents before children so foreign keys hold on restore.
 * The order is the schema's foreign-key graph, topologically sorted.
 */
fun backupTables(dao: ExportDao): List<BackupTable<out Audited>> = listOf(
    BackupTable("ai_correction", AiCorrectionEntity.serializer(), dao::aiCorrection, dao::upsertAiCorrection),
    BackupTable("ai_insight", AiInsightEntity.serializer(), dao::aiInsight, dao::upsertAiInsight),
    BackupTable("allergen_alias", AllergenAliasEntity.serializer(), dao::allergenAlias, dao::upsertAllergenAlias),
    BackupTable("allergen_definition", AllergenDefinitionEntity.serializer(), dao::allergenDefinition, dao::upsertAllergenDefinition),
    BackupTable("allergen_profile", AllergenProfileEntity.serializer(), dao::allergenProfile, dao::upsertAllergenProfile),
    BackupTable("backup_run", BackupRunEntity.serializer(), dao::backupRun, dao::upsertBackupRun),
    BackupTable("body_metric", BodyMetricEntity.serializer(), dao::bodyMetric, dao::upsertBodyMetric),
    BackupTable("meal_plan", MealPlanEntity.serializer(), dao::mealPlan, dao::upsertMealPlan),
    BackupTable("plan_week", PlanWeekEntity.serializer(), dao::planWeek, dao::upsertPlanWeek),
    BackupTable("eat_out_option", EatOutOptionEntity.serializer(), dao::eatOutOption, dao::upsertEatOutOption),
    BackupTable("exercise", ExerciseEntity.serializer(), dao::exercise, dao::upsertExercise),
    BackupTable("food", FoodEntity.serializer(), dao::food, dao::upsertFood),
    BackupTable("food_log", FoodLogEntity.serializer(), dao::foodLog, dao::upsertFoodLog),
    BackupTable("health_daily_summary", HealthDailySummaryEntity.serializer(), dao::healthDailySummary, dao::upsertHealthDailySummary,
        naturalKey = { it.dayStartMillis.toString() }, withId = { row, id -> row.copy(id = id) }),
    BackupTable("hydration_log", HydrationLogEntity.serializer(), dao::hydrationLog, dao::upsertHydrationLog),
    BackupTable("hydration_preset", HydrationPresetEntity.serializer(), dao::hydrationPreset, dao::upsertHydrationPreset),
    BackupTable("meal_template", MealTemplateEntity.serializer(), dao::mealTemplate, dao::upsertMealTemplate),
    BackupTable("meal_template_item", MealTemplateItemEntity.serializer(), dao::mealTemplateItem, dao::upsertMealTemplateItem),
    BackupTable("menu_source", MenuSourceEntity.serializer(), dao::menuSource, dao::upsertMenuSource),
    BackupTable("menu_item", MenuItemEntity.serializer(), dao::menuItem, dao::upsertMenuItem),
    BackupTable("model_benchmark", ModelBenchmarkEntity.serializer(), dao::modelBenchmark, dao::upsertModelBenchmark),
    BackupTable("nutrition_target", NutritionTargetEntity.serializer(), dao::nutritionTarget, dao::upsertNutritionTarget),
    BackupTable("performance_metric", PerformanceMetricEntity.serializer(), dao::performanceMetric, dao::upsertPerformanceMetric),
    BackupTable("recipe", RecipeEntity.serializer(), dao::recipe, dao::upsertRecipe),
    BackupTable("plan_week_recipe", PlanWeekRecipeEntity.serializer(), dao::planWeekRecipe, dao::upsertPlanWeekRecipe),
    BackupTable("program", ProgramEntity.serializer(), dao::program, dao::upsertProgram),
    BackupTable("program_day", ProgramDayEntity.serializer(), dao::programDay, dao::upsertProgramDay),
    BackupTable("progression_rule", ProgressionRuleEntity.serializer(), dao::progressionRule, dao::upsertProgressionRule),
    BackupTable("program_exercise", ProgramExerciseEntity.serializer(), dao::programExercise, dao::upsertProgramExercise),
    BackupTable("recipe_ingredient", RecipeIngredientEntity.serializer(), dao::recipeIngredient, dao::upsertRecipeIngredient),
    BackupTable("workout_session", WorkoutSessionEntity.serializer(), dao::workoutSession, dao::upsertWorkoutSession),
    BackupTable("set_log", SetLogEntity.serializer(), dao::setLog, dao::upsertSetLog),
    BackupTable("shopping_list_item", ShoppingListItemEntity.serializer(), dao::shoppingListItem, dao::upsertShoppingListItem),
    BackupTable("tdee_estimate", TdeeEstimateEntity.serializer(), dao::tdeeEstimate, dao::upsertTdeeEstimate),
)
