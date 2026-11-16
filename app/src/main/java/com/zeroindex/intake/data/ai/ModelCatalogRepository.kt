package com.zeroindex.intake.data.ai

import android.content.Context
import com.zeroindex.intake.data.db.dao.AiInsightDao
import com.zeroindex.intake.domain.ai.AiTask
import com.zeroindex.intake.domain.ai.BenchmarkScore
import com.zeroindex.intake.domain.ai.ModelInfo
import com.zeroindex.intake.domain.ai.benchmark.EndpointInfo
import com.zeroindex.intake.domain.ai.benchmark.EndpointKnowledge
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
    private val zdrFile: File get() = File(context.filesDir, "ai/openrouter-zdr.json")
    private val endpointMutex = Mutex()
    private val _endpoints = MutableStateFlow(EndpointKnowledge())

    /** Where models can be served, as far as it has been looked up. See [ensureEndpoints]. */
    val endpoints: StateFlow<EndpointKnowledge> = _endpoints.asStateFlow()
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

    /**
     * Looks up where [modelIds] can be served, so a benchmark never spends a slot on a model that has no provider
     * meeting the privacy setting. Two public, key-free OpenRouter lists: each model's own endpoints (which
     * parameters it accepts, whether it is up) and, when [needZdr], the zero-data-retention list (the only public
     * per-endpoint data policy). The zero-retention list is cached a day. A failed lookup leaves that part
     * unknown, and unknown never excludes a model.
     */
    suspend fun ensureEndpoints(modelIds: Collection<String>, needZdr: Boolean) = endpointMutex.withLock {
        if (needZdr && _endpoints.value.zdr == null) {
            val zdr = loadZdr()
            if (zdr != null) _endpoints.update { it.copy(zdr = zdr) }
        }
        val missing = modelIds.filter { it !in _endpoints.value.perModel }
        for (id in missing) {
            val found = runCatching { client.endpointsOf(id) }.getOrNull() ?: continue
            val infos = found.map { it.toInfo(id) }
            _endpoints.update { it.copy(perModel = it.perModel + (id to infos)) }
        }
    }

    private suspend fun loadZdr(): List<EndpointInfo>? {
        val now = clock.nowMillis()
        val cached = withContext(Dispatchers.IO) {
            runCatching { json.decodeFromString(ZdrCache.serializer(), zdrFile.readText()) }.getOrNull()
        }
        if (cached != null && now - cached.fetchedAt < MAX_AGE_MS) return cached.data.map { it.toInfo(it.modelId) }
        val fresh = runCatching { client.zdrEndpoints() }.getOrNull()
            ?: return cached?.data?.map { it.toInfo(it.modelId) }
        val trimmed = fresh.map { OrEndpoint(it.modelId, it.providerName, it.tag, it.supportedParameters, it.status) }
        withContext(Dispatchers.IO) {
            runCatching {
                zdrFile.parentFile?.mkdirs()
                zdrFile.writeText(json.encodeToString(ZdrCache.serializer(), ZdrCache(now, trimmed)))
            }
        }
        return trimmed.map { it.toInfo(it.modelId) }
    }

    private fun OrEndpoint.toInfo(model: String) =
        EndpointInfo(modelId = model, tag = tag, supportedParameters = supportedParameters.toSet(), status = status)

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
            responseFormat = "response_format" in m.supportedParameters,
            acceptsTemperature = m.supportedParameters.isEmpty() || "temperature" in m.supportedParameters,
            tools = "tools" in m.supportedParameters,
            createdAt = m.created,
        )
    }
}
