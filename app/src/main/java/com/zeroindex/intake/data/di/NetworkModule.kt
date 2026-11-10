package com.zeroindex.intake.data.di

import com.zeroindex.intake.BuildConfig
import com.zeroindex.intake.data.ai.OpenRouterClient
import com.zeroindex.intake.data.remote.openfoodfacts.OffContributionService
import com.zeroindex.intake.data.remote.openfoodfacts.OffSearchService
import com.zeroindex.intake.data.remote.openfoodfacts.OffUserAgent
import com.zeroindex.intake.data.remote.openfoodfacts.OpenFoodFactsService
import com.zeroindex.intake.data.remote.usda.UsdaService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** The HTTP client for every Open Food Facts call: the shared one plus the User-Agent they ask for. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class OffHttp

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    @OffHttp
    fun provideOffHttp(client: OkHttpClient): OkHttpClient {
        val userAgent = OffUserAgent.forVersion(BuildConfig.VERSION_NAME)
        return client.newBuilder()
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("User-Agent", userAgent).build())
            }
            .build()
    }

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
     * AI calls are not conveniences on a short fuse like the lookups above, so they get their own client.
     * The timeouts here are only the ceiling: [OpenRouterClient] derives a client per request from the
     * feature's [com.zeroindex.intake.domain.ai.AiTimeout] (45 s for text, up to 120 s for photos and web search).
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
    fun provideOpenFoodFactsService(@OffHttp client: OkHttpClient, json: Json): OpenFoodFactsService =
        Retrofit.Builder()
            .baseUrl(OpenFoodFactsService.BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(OpenFoodFactsService::class.java)

    @Provides
    @Singleton
    fun provideOffSearchService(@OffHttp client: OkHttpClient, json: Json): OffSearchService =
        Retrofit.Builder()
            .baseUrl(OffSearchService.BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(OffSearchService::class.java)

    @Provides
    @Singleton
    fun provideOffContributionService(@OffHttp client: OkHttpClient, json: Json): OffContributionService =
        Retrofit.Builder()
            .baseUrl(OffContributionService.BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(OffContributionService::class.java)
}
