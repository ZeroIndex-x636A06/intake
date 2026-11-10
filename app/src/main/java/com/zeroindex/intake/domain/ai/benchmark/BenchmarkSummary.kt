package com.zeroindex.intake.domain.ai.benchmark

import com.zeroindex.intake.domain.ai.AiFailureKind

/** A model a run did not store a result for. */
data class SkippedEntry(
    val task: String,
    val model: String,
    val reason: String,
    val kind: AiFailureKind?,
) {
    /** The short phrase it is grouped under. */
    val group: String get() = kind?.label ?: reason
}

/**
 * The compact results line (B7): "2 saved · 4 skipped (3 rate limited, 1 timed out)", with the per-model
 * reasons kept behind a tap instead of listed one by one under the card.
 */
object BenchmarkSummary {

    fun headline(saved: Int, skipped: List<SkippedEntry>): String {
        val savedPart = "$saved saved"
        if (skipped.isEmpty()) return savedPart
        val groups = skipped.groupingBy { it.group.lowercase() }.eachCount().entries
            .sortedByDescending { it.value }
            .joinToString(", ") { "${it.value} ${it.key}" }
        return "$savedPart · ${skipped.size} skipped ($groups)"
    }

    /** For the expanded view: each reason once, with the models it applied to. */
    fun groups(skipped: List<SkippedEntry>): List<Pair<String, List<String>>> =
        skipped.groupBy { it.group }
            .map { (reason, rows) -> reason to rows.map { "${it.model.substringAfter('/')} (${it.task})" } }
            .sortedByDescending { it.second.size }
}
