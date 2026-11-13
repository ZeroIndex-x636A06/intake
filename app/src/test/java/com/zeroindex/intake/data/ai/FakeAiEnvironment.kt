package com.zeroindex.intake.data.ai

import com.zeroindex.intake.domain.ai.AiFeature
import com.zeroindex.intake.domain.ai.AiTask
import com.zeroindex.intake.domain.ai.ModelInfo
import com.zeroindex.intake.domain.ai.cost.AiUsageRecord

/** A stand-in for the app's settings, key and usage log, so the gateway's rules run on the JVM. */
class FakeAiEnvironment(
    var key: String? = "sk-or-v1-TEST-KEY-0000",
    var enabled: Boolean = true,
    var budgetUsd: Double = 2.0,
    override var strict: Boolean = true,
    var optIns: Set<AiFeature> = emptySet(),
    var privacy: AiPrivacy = AiPrivacy.NO_TRAINING,
    /** Spend already logged this month before the test starts. */
    var priorSpendUsd: Double = 0.0,
    var models: Map<String, ModelInfo> = emptyMap(),
    /** What [fallbackModels] offers, in order, minus what was already tried. */
    var fallbacks: List<String> = emptyList(),
) : AiEnvironment {
    val records = mutableListOf<AiUsageRecord>()
    var failRecording = false

    override fun openRouterKey(): String? = key
    override fun enabledNow(): Boolean = enabled

    override suspend fun policy() = AiPolicy(enabled, budgetUsd, privacy, optIns)
    override suspend fun modelFor(task: AiTask): String = "test/${task.name.lowercase()}"
    override suspend fun fallbackModels(task: AiTask, tried: Set<String>): List<String> = fallbacks.filter { it !in tried }
    override suspend fun priceOf(modelId: String): ModelInfo? = models[modelId]
    override suspend fun spentSince(startMillis: Long): Double =
        priorSpendUsd + records.filter { it.at >= startMillis }.sumOf { it.costUsd }

    override suspend fun record(record: AiUsageRecord, now: Long) {
        if (failRecording) error("disk full")
        records += record
    }
}
