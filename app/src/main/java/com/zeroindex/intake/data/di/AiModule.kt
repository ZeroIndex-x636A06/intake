package com.zeroindex.intake.data.di

import com.zeroindex.intake.data.ai.AiEnvironment
import com.zeroindex.intake.data.ai.DefaultAiEnvironment
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AiModule {
    @Binds
    @Singleton
    abstract fun bindAiEnvironment(impl: DefaultAiEnvironment): AiEnvironment
}
