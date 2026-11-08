package com.zeroindex.intake.data.di

import com.zeroindex.intake.data.ai.OpenRouterClient
import com.zeroindex.intake.data.remote.openfoodfacts.OpenFoodFactsService
import com.zeroindex.intake.data.remote.usda.UsdaService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttp(): OkHttpClient = OkHttpClient.Builder()
        // Short timeouts on purpose: these three calls are conveniences, and
        // SPEC §3.1 requires them to degrade to manual entry quickly rather
        // than leave the user watching a spinner.
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    /**
     * AI calls are not conveniences on a short fuse like the lookups above: a
     * vision estimate takes seconds and a search-and-extract run can take a
     * minute, so they get their own client with patient timeouts.
     */
    @Provides
    @Singleton
    fun provideOpenRouterClient(client: OkHttpClient, json: Json): OpenRouterClient = OpenRouterClient(
        http = client.newBuilder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(150, TimeUnit.SECONDS)
            .callTimeout(180, TimeUnit.SECONDS)
            .build(),
        json = json,
    )

    @Provides
    @Singleton
    fun provideUsdaService(client: OkHttpClient, json: Json): UsdaService =
        Retrofit.Builder()
            .baseUrl(UsdaService.BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(UsdaService::class.java)

    @Provides
    @Singleton
    fun provideOpenFoodFactsService(client: OkHttpClient, json: Json): OpenFoodFactsService =
        Retrofit.Builder()
            .baseUrl(OpenFoodFactsService.BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(OpenFoodFactsService::class.java)
}
