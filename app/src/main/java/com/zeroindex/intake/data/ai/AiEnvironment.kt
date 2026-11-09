package com.zeroindex.intake.data.ai

import com.zeroindex.intake.data.db.dao.AiUsageDao
import com.zeroindex.intake.data.db.entity.AiUsageEntity
import com.zeroindex.intake.data.keys.ApiKeyStore
import com.zeroindex.intake.data.settings.SettingsRepository
import com.zeroindex.intake.domain.ai.AiFeature
import com.zeroindex.intake.domain.ai.AiTask
import com.zeroindex.intake.domain.ai.ModelInfo
import com.zeroindex.intake.domain.ai.ModelSuggester
import com.zeroindex.intake.domain.ai.cost.AiUsageRecord
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** What the gateway needs to know about the person's choices, read fresh for every call. */
data class AiPolicy(
    /** The "Use AI features" master switch. */
    val enabled: Boolean,
    val monthlyBudgetUsd: Double,
    val privacy: AiPrivacy,
    /** Schedulable features whose own opt-in switch is on. */
    val scheduleOptIns: Set<AiFeature>,
)

/**
 * The gateway's view of the app: key, settings, model choice, prices and the usage log. An interface so
 * the policy the gateway enforces is tested on the JVM with a fake, without Android, DataStore or a key.
 */
interface AiEnvironment {
    fun openRouterKey(): String?

    /** A cheap, non-suspending read of the master switch, for deciding what to show. Never the hard gate. */
    fun enabledNow(): Boolean

    /** True in debug builds: a call that breaks the trigger rule throws instead of being quietly refused. */
    val strict: Boolean

    suspend fun policy(): AiPolicy
    suspend fun modelFor(task: AiTask): String
    suspend fun priceOf(modelId: String): ModelInfo?
    suspend fun spentSince(startMillis: Long): Double
    suspend fun record(record: AiUsageRecord, now: Long)
}

@Singleton
class DefaultAiEnvironment @Inject constructor(
    private val keys: ApiKeyStore,
    private val settings: SettingsRepository,
    private val catalog: ModelCatalogRepository,
    private val usage: AiUsageDao,
) : AiEnvironment {

    @Volatile private var enabledSnapshot = true

    init {
        // Keeps the switch readable without suspending; the real gate in policy() always reads the store.
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            settings.settings.collect { enabledSnapshot = it.aiEnabled }
        }
    }

    override fun openRouterKey(): String? = keys.openRouterKey()

    override fun enabledNow(): Boolean = enabledSnapshot

    override val strict: Boolean = com.zeroindex.intake.BuildConfig.DEBUG

    override suspend fun policy(): AiPolicy {
        val s = settings.settings.first()
        return AiPolicy(
            enabled = s.aiEnabled,
            monthlyBudgetUsd = s.aiMonthlyBudgetUsd,
            privacy = s.aiPrivacy,
            scheduleOptIns = buildSet {
                if (s.dailyInsightEnabled) add(AiFeature.DAILY_INSIGHT)
                if (s.coachAiReword) add(AiFeature.COACH_REWORD)
                if (s.menuAutoRefresh) add(AiFeature.MENU_IMPORT)
            },
        )
    }

    override suspend fun modelFor(task: AiTask): String {
        val prefs = settings.settings.first()
        prefs.aiModelOverrides[task]?.let { return it }
        val state = catalog.ensureLoaded()
        val suggestion = ModelSuggester.suggest(task, state.models, catalog.benchmarks(), prefs.aiAllowRetainingModels)
        return suggestion.suggested?.model?.id
            // Offline with no cached catalog: fall back to the first concrete
            // shortlist entry rather than refusing outright.
            ?: task.shortlist.firstOrNull { '*' !in it }
            ?: throw AiError.BadResponse("no model available for ${task.label}")
    }

    /** From the cached catalog only: pricing a finished call must never start a download. */
    override suspend fun priceOf(modelId: String): ModelInfo? = catalog.state.value.models.firstOrNull { it.id == modelId }

    override suspend fun spentSince(startMillis: Long): Double = usage.spendSince(startMillis)

    override suspend fun record(record: AiUsageRecord, now: Long) {
        usage.insert(
            AiUsageEntity(
                feature = record.feature.name,
                model = record.model,
                triggerKind = record.trigger.name,
                promptTokens = record.promptTokens,
                completionTokens = record.completionTokens,
                costUsd = record.costUsd,
                costReported = record.costReported,
                success = record.success,
                at = record.at,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }
}
