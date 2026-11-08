package com.zeroindex.intake.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.zeroindex.intake.data.db.dao.AiCorrectionDao
import com.zeroindex.intake.data.db.dao.AiInsightDao
import com.zeroindex.intake.data.db.dao.AllergenDao
import com.zeroindex.intake.data.db.dao.BodyMetricDao
import com.zeroindex.intake.data.db.dao.ExerciseDao
import com.zeroindex.intake.data.db.dao.ExportDao
import com.zeroindex.intake.data.db.dao.SyncConflictDao
import com.zeroindex.intake.data.db.dao.FoodDao
import com.zeroindex.intake.data.db.dao.FoodLogDao
import com.zeroindex.intake.data.db.dao.HealthSummaryDao
import com.zeroindex.intake.data.db.dao.HydrationDao
import com.zeroindex.intake.data.db.dao.MealTemplateDao
import com.zeroindex.intake.data.db.dao.MenuDao
import com.zeroindex.intake.data.db.dao.NutritionTargetDao
import com.zeroindex.intake.data.db.dao.PerformanceDao
import com.zeroindex.intake.data.db.dao.PlanDao
import com.zeroindex.intake.data.db.dao.ProgramDao
import com.zeroindex.intake.data.db.dao.ProgressionRuleDao
import com.zeroindex.intake.data.db.dao.RecipeDao
import com.zeroindex.intake.data.db.dao.ShoppingListDao
import com.zeroindex.intake.data.db.dao.TdeeEstimateDao
import com.zeroindex.intake.data.db.dao.WorkoutDao
import com.zeroindex.intake.data.db.entity.AiCorrectionEntity
import com.zeroindex.intake.data.db.entity.AiInsightEntity
import com.zeroindex.intake.data.db.entity.AllergenAliasEntity
import com.zeroindex.intake.data.db.entity.AllergenProfileEntity
import com.zeroindex.intake.data.db.entity.BackupRunEntity
import com.zeroindex.intake.data.db.entity.SyncConflictEntity
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
 * The single source of truth for the whole app (SPEC §1). Everything the UI
 * shows is observed off this database; nothing polls.
 */
@Database(
    version = IntakeDatabase.VERSION,
    exportSchema = true,
    entities = [
        FoodEntity::class,
        FoodLogEntity::class,
        RecipeEntity::class,
        RecipeIngredientEntity::class,
        MealTemplateEntity::class,
        MealTemplateItemEntity::class,
        BodyMetricEntity::class,
        NutritionTargetEntity::class,
        TdeeEstimateEntity::class,
        ExerciseEntity::class,
        ProgramEntity::class,
        ProgramDayEntity::class,
        ProgramExerciseEntity::class,
        ProgressionRuleEntity::class,
        WorkoutSessionEntity::class,
        SetLogEntity::class,
        PlanWeekEntity::class,
        PlanWeekRecipeEntity::class,
        EatOutOptionEntity::class,
        ShoppingListItemEntity::class,
        AllergenProfileEntity::class,
        AllergenAliasEntity::class,
        AiCorrectionEntity::class,
        // Version 2 (SPEC amendments).
        HydrationLogEntity::class,
        HydrationPresetEntity::class,
        PerformanceMetricEntity::class,
        MenuSourceEntity::class,
        MenuItemEntity::class,
        AiInsightEntity::class,
        ModelBenchmarkEntity::class,
        HealthDailySummaryEntity::class,
        BackupRunEntity::class,
        SyncConflictEntity::class,
    ],
)
abstract class IntakeDatabase : RoomDatabase() {

    abstract fun foodDao(): FoodDao
    abstract fun foodLogDao(): FoodLogDao
    abstract fun recipeDao(): RecipeDao
    abstract fun mealTemplateDao(): MealTemplateDao
    abstract fun bodyMetricDao(): BodyMetricDao
    abstract fun nutritionTargetDao(): NutritionTargetDao
    abstract fun tdeeEstimateDao(): TdeeEstimateDao
    abstract fun exerciseDao(): ExerciseDao
    abstract fun programDao(): ProgramDao
    abstract fun progressionRuleDao(): ProgressionRuleDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun planDao(): PlanDao
    abstract fun shoppingListDao(): ShoppingListDao
    abstract fun allergenDao(): AllergenDao
    abstract fun aiCorrectionDao(): AiCorrectionDao
    abstract fun hydrationDao(): HydrationDao
    abstract fun performanceDao(): PerformanceDao
    abstract fun menuDao(): MenuDao
    abstract fun aiInsightDao(): AiInsightDao
    abstract fun healthSummaryDao(): HealthSummaryDao
    abstract fun exportDao(): ExportDao
    abstract fun syncConflictDao(): SyncConflictDao

    companion object {
        const val VERSION = 2
        const val NAME = "intake.db"

        /** Seeds the allergen alias table the moment the database is created. */
        fun seedCallback(nowProvider: () -> Long = System::currentTimeMillis): Callback =
            object : Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    AllergenSeed.seed(db, nowProvider())
                }
            }
    }
}
