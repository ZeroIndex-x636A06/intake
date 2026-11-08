package com.zeroindex.intake.data.ai

import android.content.Context
import com.zeroindex.intake.data.db.dao.AiInsightDao
import com.zeroindex.intake.domain.ai.AiTask
import com.zeroindex.intake.domain.ai.BenchmarkScore
import com.zeroindex.intake.domain.ai.ModelInfo
import com.zeroindex.intake.domain.time.Clock
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

data class CatalogState(
    val models: List<ModelInfo> = emptyList(),
    val fetchedAt: Long? = null,
    val refreshing: Boolean = false,
    val error: String? = null,
)

/**
 * The live OpenRouter model list, cached on disk.
 *
 * Read from OpenRouter rather than baked into the app, because prices and
 * models change month to month - a hardcoded "cheapest" is wrong within a
 * season. The cache keeps the picker usable offline, and the list needs no
 * key, so it can show what each task would cost before the user commits.
 */
@Singleton
class ModelCatalogRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OpenRouterClient,
    private val insightDao: AiInsightDao,
    private val json: Json,
    private val clock: Clock,
) {
    private companion object {
        const val MAX_AGE_MS = 24 * 60 * 60 * 1000L
    }

    private val file: File get() = File(context.filesDir, "ai/openrouter-models.json")
    private val mutex = Mutex()
    private val _state = MutableStateFlow(CatalogState())
    val state: StateFlow<CatalogState> = _state.asStateFlow()

    fun observeBenchmarks(): Flow<List<BenchmarkScore>> = insightDao.observeBenchmarks().map { rows ->
        rows.mapNotNull { row ->
            val task = runCatching { AiTask.valueOf(row.task) }.getOrNull() ?: return@mapNotNull null
            BenchmarkScore(task, row.modelId, row.accuracy, row.jsonValidity, row.fixtures, row.medianLatencyMs, row.costUsdPerRequest)
        }
    }

    suspend fun benchmarks(): List<BenchmarkScore> = insightDao.allBenchmarks().mapNotNull { row ->
        runCatching { AiTask.valueOf(row.task) }.getOrNull()
            ?.let { BenchmarkScore(it, row.modelId, row.accuracy, row.jsonValidity, row.fixtures, row.medianLatencyMs, row.costUsdPerRequest) }
    }

    /** Loads the cache, then refreshes from the network if it is stale. */
    suspend fun ensureLoaded(force: Boolean = false): CatalogState = mutex.withLock {
        if (_state.value.models.isEmpty()) loadCache()
        val age = _state.value.fetchedAt?.let { clock.nowMillis() - it }
        if (force || age == null || age > MAX_AGE_MS) fetch()
        _state.value
    }

    private suspend fun loadCache() = withContext(Dispatchers.IO) {
        runCatching { json.decodeFromString(CatalogCache.serializer(), file.readText()) }
            .onSuccess { cache -> _state.update { CatalogState(cache.data.map(::toInfo), cache.fetchedAt) } }
    }

    private suspend fun fetch() {
        _state.update { it.copy(refreshing = true, error = null) }
        runCatching { client.models() }
            .onSuccess { models ->
                val now = clock.nowMillis()
                withContext(Dispatchers.IO) {
                    file.parentFile?.mkdirs()
                    file.writeText(json.encodeToString(CatalogCache.serializer(), CatalogCache(now, models)))
                }
                _state.value = CatalogState(models.map(::toInfo), now)
            }
            .onFailure { e ->
                _state.update {
                    it.copy(refreshing = false, error = (e as? AiError)?.message ?: "Could not load the model list.")
                }
            }
    }

    private fun toInfo(m: OrModel): ModelInfo {
        fun perMillion(v: String?) = (v?.toDoubleOrNull() ?: 0.0) * 1_000_000
        return ModelInfo(
            id = m.id,
            name = m.name,
            description = m.description,
            contextLength = m.contextLength ?: 0,
            promptPerMillion = perMillion(m.pricing.prompt),
            completionPerMillion = perMillion(m.pricing.completion),
            perImage = m.pricing.image?.toDoubleOrNull() ?: 0.0,
            perRequest = m.pricing.request?.toDoubleOrNull() ?: 0.0,
            acceptsImages = "image" in m.architecture.inputModalities,
            acceptsFiles = "file" in m.architecture.inputModalities,
            outputsText = m.architecture.outputModalities.isEmpty() || "text" in m.architecture.outputModalities,
            structuredOutputs = "structured_outputs" in m.supportedParameters,
            tools = "tools" in m.supportedParameters,
            createdAt = m.created,
        )
    }
}
