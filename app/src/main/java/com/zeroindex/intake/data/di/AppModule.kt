package com.zeroindex.intake.data.di

import com.zeroindex.intake.data.ai.benchmark.AssetFixtureReader
import com.zeroindex.intake.data.ai.benchmark.FixtureReader
import com.zeroindex.intake.domain.time.Clock
import com.zeroindex.intake.domain.time.HybridClock
import com.zeroindex.intake.domain.time.SystemClock
import com.zeroindex.intake.data.health.HealthConnectSource
import com.zeroindex.intake.data.health.HealthDataSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.serialization.json.Json

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /** One hybrid clock for the whole app, so sync can advance it (amendment §3). */
    @Provides
    @Singleton
    fun provideHybridClock(): HybridClock = HybridClock(SystemClock())

    @Provides
    @Singleton
    fun provideClock(hybrid: HybridClock): Clock = hybrid

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
        prettyPrint = true
    }

    @Provides
    @Singleton
    fun provideFixtureReader(reader: AssetFixtureReader): FixtureReader = reader

    @Provides
    @Singleton
    fun provideHealthDataSource(source: HealthConnectSource): HealthDataSource = source
}
