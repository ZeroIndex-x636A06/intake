package com.zeroindex.intake.data.di

import android.content.Context
import androidx.room.Room
import com.zeroindex.intake.data.db.ALL
import com.zeroindex.intake.data.db.IntakeDatabase
import com.zeroindex.intake.data.db.dao.AiCorrectionDao
import com.zeroindex.intake.data.db.dao.AiInsightDao
import com.zeroindex.intake.data.db.dao.AllergenDao
import com.zeroindex.intake.data.db.dao.BodyMetricDao
import com.zeroindex.intake.data.db.dao.ExerciseDao
import com.zeroindex.intake.data.db.dao.ExportDao
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
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): IntakeDatabase =
        Room.databaseBuilder(context, IntakeDatabase::class.java, IntakeDatabase.NAME)
            .addCallback(IntakeDatabase.seedCallback())
            .addMigrations(*ALL)
            // No fallbackToDestructiveMigration, by policy (SPEC §4).
            .build()

    @Provides fun provideFoodDao(db: IntakeDatabase): FoodDao = db.foodDao()
    @Provides fun provideFoodLogDao(db: IntakeDatabase): FoodLogDao = db.foodLogDao()
    @Provides fun provideRecipeDao(db: IntakeDatabase): RecipeDao = db.recipeDao()
    @Provides fun provideMealTemplateDao(db: IntakeDatabase): MealTemplateDao = db.mealTemplateDao()
    @Provides fun provideBodyMetricDao(db: IntakeDatabase): BodyMetricDao = db.bodyMetricDao()
    @Provides fun provideNutritionTargetDao(db: IntakeDatabase): NutritionTargetDao =
        db.nutritionTargetDao()
    @Provides fun provideTdeeEstimateDao(db: IntakeDatabase): TdeeEstimateDao = db.tdeeEstimateDao()
    @Provides fun provideExerciseDao(db: IntakeDatabase): ExerciseDao = db.exerciseDao()
    @Provides fun provideProgramDao(db: IntakeDatabase): ProgramDao = db.programDao()
    @Provides fun provideProgressionRuleDao(db: IntakeDatabase): ProgressionRuleDao =
        db.progressionRuleDao()
    @Provides fun provideWorkoutDao(db: IntakeDatabase): WorkoutDao = db.workoutDao()
    @Provides fun providePlanDao(db: IntakeDatabase): PlanDao = db.planDao()
    @Provides fun provideShoppingListDao(db: IntakeDatabase): ShoppingListDao = db.shoppingListDao()
    @Provides fun provideAllergenDao(db: IntakeDatabase): AllergenDao = db.allergenDao()
    @Provides fun provideAiCorrectionDao(db: IntakeDatabase): AiCorrectionDao = db.aiCorrectionDao()
    @Provides fun provideHydrationDao(db: IntakeDatabase): HydrationDao = db.hydrationDao()
    @Provides fun providePerformanceDao(db: IntakeDatabase): PerformanceDao = db.performanceDao()
    @Provides fun provideMenuDao(db: IntakeDatabase): MenuDao = db.menuDao()
    @Provides fun provideAiInsightDao(db: IntakeDatabase): AiInsightDao = db.aiInsightDao()
    @Provides fun provideHealthSummaryDao(db: IntakeDatabase): HealthSummaryDao = db.healthSummaryDao()
    @Provides fun provideExportDao(db: IntakeDatabase): ExportDao = db.exportDao()
}
