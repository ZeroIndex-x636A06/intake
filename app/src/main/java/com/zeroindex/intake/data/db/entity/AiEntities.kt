package com.zeroindex.intake.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Amendment §3: the single daily insight. [dayStartMillis] is the UTC instant
 * the local day began (SPEC §3.4: never a local date string). [rationale]
 * lists the data the model was given, so the "why" disclosure can show it.
 */
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
