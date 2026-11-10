package com.zeroindex.intake.domain.settings

/** The six muscle regions of the weekly volume view, in display order. */
enum class MuscleRegion(val label: String, val suggestedSets: Int) {
    CHEST("Chest", 12),
    BACK("Back", 14),
    SHOULDERS("Shoulders", 10),
    ARMS("Arms", 10),
    LEGS("Legs", 12),
    CORE("Core", 8),
}

/**
 * Settings > Training > Advanced: the weekly working-sets target per region. Stored as one comma-separated line of six
 * numbers in region order; an empty string means "use the suggested numbers". Pure, so it can be unit tested.
 */
object WeeklySets {
    const val MAX = 60

    /** The targets in [MuscleRegion] order: the stored numbers where valid, the suggestion for anything missing. */
    fun parse(csv: String): Map<MuscleRegion, Int> {
        val parts = csv.split(',').map { it.trim().toIntOrNull() }
        return MuscleRegion.entries.mapIndexed { i, region ->
            region to (parts.getOrNull(i)?.takeIf { it in 1..MAX } ?: region.suggestedSets)
        }.toMap()
    }

    /** Stores one region's number, keeping the others. A value outside 1..[MAX] is ignored. */
    fun with(csv: String, region: MuscleRegion, sets: Int): String {
        if (sets !in 1..MAX) return csv
        val current = parse(csv).toMutableMap()
        current[region] = sets
        return encode(current)
    }

    fun encode(values: Map<MuscleRegion, Int>): String =
        MuscleRegion.entries.joinToString(",") { (values[it] ?: it.suggestedSets).toString() }

    /** True when every region is at its suggested number (or nothing is stored). */
    fun isSuggested(csv: String): Boolean = csv.isBlank() || parse(csv).all { (r, v) -> v == r.suggestedSets }
}
