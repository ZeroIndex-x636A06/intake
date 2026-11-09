package com.zeroindex.intake.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Amendment §3: the single daily insight. [dayStartMillis] is the UTC instant
 * the local day began (SPEC §3.4: never a local date string). [rationale]
 * lists the data the model was given, so the "why" disclosure can show it.
 */
@Serializable
@Entity(
    tableName = "ai_insight",
    indices = [Index("dayStartMillis"), Index("deletedAt")],
)
data class AiInsightEntity(
    @PrimaryKey override val id: String = newId(),
    val dayStartMillis: Long,
    val text: String,
    val rationale: String,
    val model: String,
    val dismissedAt: Long? = null,
    override val createdAt: Long,
    override val updatedAt: Long,
    override val deletedAt: Long? = null,
) : Audited

/**
 * One benchmark result: how a model did at one AI task against the fixture
 * set. Feeds the model picker's "Suggested" badge once real runs exist.
 */
@Serializable
@Entity(
    tableName = "model_benchmark",
    indices = [Index("task"), Index("modelId"), Index("deletedAt")],
)
data class ModelBenchmarkEntity(
    @PrimaryKey override val id: String = newId(),
    val task: String,
    val modelId: String,
    val runAt: Long,
    val fixtures: Int,
    /** 0..1, task-specific: macro error inside tolerance, allergen recall, etc. */
    val accuracy: Double,
    /** 0..1, share of responses that parsed against the schema first time. */
    val jsonValidity: Double,
    val medianLatencyMs: Long,
    val costUsdPerRequest: Double,
    override val createdAt: Long,
    override val updatedAt: Long,
    override val deletedAt: Long? = null,
) : Audited

/**
 * One model call, for the AI usage meter and the budget (SPEC-v3 "AI policy and cost"). It records which
 * feature called which model and what it cost, never the prompt, the reply or a key. It is per device and
 * is deliberately not in backups or sync: usage on one phone says nothing about another's bill.
 * "Reset counters" moves a marker in settings; no row is ever removed.
 */
@Entity(
    tableName = "ai_usage",
    indices = [Index("at"), Index("feature"), Index("deletedAt")],
)
data class AiUsageEntity(
    @PrimaryKey override val id: String = newId(),
    /** An [com.zeroindex.intake.domain.ai.AiFeature] name. */
    val feature: String,
    val model: String,
    /** An [com.zeroindex.intake.domain.ai.AiTrigger] name. */
    val triggerKind: String,
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val costUsd: Double,
    /** True when OpenRouter reported [costUsd]; false when it was worked out from catalog prices. */
    val costReported: Boolean,
    val success: Boolean,
    /** UTC epoch millis of the call. */
    val at: Long,
    override val createdAt: Long,
    override val updatedAt: Long,
    override val deletedAt: Long? = null,
) : Audited
