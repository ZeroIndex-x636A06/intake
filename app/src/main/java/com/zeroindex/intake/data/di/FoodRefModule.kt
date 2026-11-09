package com.zeroindex.intake.data.di

import android.content.Context
import androidx.room.Room
import com.zeroindex.intake.data.foodref.FoodRefDatabase
import com.zeroindex.intake.data.foodref.RefFoodDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** The bundled USDA reference database: a separate file from the user's `intake.db`. */
@Module
@InstallIn(SingletonComponent::class)
object FoodRefModule {

    @Provides
    @Singleton
    fun provideFoodRefDatabase(@ApplicationContext context: Context): FoodRefDatabase =
        Room.databaseBuilder(context, FoodRefDatabase::class.java, FoodRefDatabase.NAME)
            .createFromAsset(FoodRefDatabase.ASSET)
            // Reference data only. When a later app ships a newer asset (a higher
            // FoodRefDatabase.VERSION), re-copying it is the migration. The user's own
            // data lives in intake.db, where destructive migration is never used.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideRefFoodDao(db: FoodRefDatabase): RefFoodDao = db.refFoodDao()
}
