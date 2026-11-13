package com.zeroindex.intake.domain.training

import com.zeroindex.intake.domain.training.muscles.BodyRegion
import com.zeroindex.intake.domain.training.muscles.MuscleMap
import com.zeroindex.intake.domain.training.muscles.MuscleRegion
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** A logged working set, with just what the volume maths needs. */
data class VolumeSet(
    val exerciseId: String,
    val exerciseName: String,
    val primaryMuscle: String,
    val secondaryMuscles: List<String>,
    val completedAt: Long,
    val isWarmup: Boolean = false,
)

enum class VolumeStatus(val label: String) {
    UNDER("Under"),
    IN_RANGE("In range"),
    OVER("Over"),
}

/** How much one exercise added to a region this week, after weighting. */
data class VolumeContributor(val exerciseId: String, val exerciseName: String, val effectiveSets: Double, val loggedSets: Int)

data class RegionVolume(
    val region: BodyRegion,
    /** Weighted sets: a hard set counts 1.0 for its main muscle and [MuscleWeights.SECONDARY] for each helper region. */
    val sets: Double,
    val target: Int,
    /** The "in range" band: from [low] up to and including [high]. */
    val low: Int,
    val high: Int,
    val status: VolumeStatus,
    val contributors: List<VolumeContributor>,
) {
    /** "9.5 of 12". */
    fun summary(): String = "${WeeklyVolume.formatSets(sets)} of $target sets"
}

/**
 * The weights of the volume model. Without the weighted `exercise_muscle` table (left for later), a set counts fully for the
 * exercise's primary muscle and by half for each secondary muscle, which is the convention the Liftosaur study notes call
 * "main muscle full credit, helpers half". The numbers are original and deliberately simple.
 */
object MuscleWeights {
    const val PRIMARY = 1.0
    const val SECONDARY = 0.5

    /** Region credit for one set. A region named twice (two helpers in one region) counts once, at its highest weight. */
    fun regionCredit(primaryMuscle: String, secondaryMuscles: List<String>): Map<BodyRegion, Double> {
        val out = linkedMapOf<BodyRegion, Double>()
        regionOf(primaryMuscle)?.let { out[it] = PRIMARY }
        secondaryMuscles.forEach { m ->
            val r = regionOf(m) ?: return@forEach
            if (r !in out) out[r] = SECONDARY
        }
        return out
    }

    /**
     * Credit for one set per drawn muscle region (the body diagram's 20 regions). The main muscle's regions (narrowed by the
     * exercise name: a lateral raise is the side delt) get [PRIMARY], helper regions [SECONDARY]; a region is never both.
     */
    fun muscleCredit(exerciseName: String, primaryMuscle: String, secondaryMuscles: List<String>): Map<MuscleRegion, Double> {
        val h = MuscleMap.highlightFor(exerciseName, primaryMuscle, secondaryMuscles)
        val out = linkedMapOf<MuscleRegion, Double>()
        h.primary.forEach { out[it] = PRIMARY }
        h.secondary.forEach { if (it !in out) out[it] = SECONDARY }
        return out
    }

    /**
     * How much of its body region's weekly target one diagram region is asked to carry. The big movers carry all of it;
     * the small or hard-to-isolate muscles carry half, so a normal week can fill them.
     */
    fun targetShare(region: MuscleRegion): Double = when (region) {
        MuscleRegion.UPPER_TRAPS, MuscleRegion.LOWER_BACK, MuscleRegion.FOREARMS, MuscleRegion.OBLIQUES,
        MuscleRegion.CALVES, MuscleRegion.HIP_ADDUCTORS, MuscleRegion.HIP_ABDUCTORS,
        -> 0.5
        else -> 1.0
    }

    /** The coarse region a library muscle name belongs to (traps and lower back count with the back). */
    fun regionOf(muscle: String): BodyRegion? = MuscleMap.regionsOf(muscle).firstOrNull()?.body
}

/** One diagram region's week: weighted sets against its share of the body region's target. */
data class MuscleVolume(
    val region: MuscleRegion,
    val sets: Double,
    val target: Int,
    val status: VolumeStatus,
    val contributors: List<VolumeContributor>,
) {
    fun summary(): String = "${WeeklyVolume.formatSets(sets)} of $target sets"
}

/**
 * Weekly working sets per body region against the person's targets (Settings > Training > Advanced), on calendar weeks.
 * Pure Kotlin: the Train screen, the Muscle volume page and the workout summary all use it.
 */
object WeeklyVolume {
    /** In range is 75% to 150% of the target, rounded to whole sets. Below is under, above is over. */
    private const val LOW_FACTOR = 0.75
    private const val HIGH_FACTOR = 1.5

    /** Monday 00:00 of the week holding [epochMillis], in [zone]. */
    fun weekStart(epochMillis: Long, zone: ZoneId): Long {
        val date = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay(zone).toInstant().toEpochMilli()
    }

    /** The calendar week [weeksAgo] weeks before the one holding [now]: start inclusive, end exclusive. */
    fun weekRange(now: Long, zone: ZoneId, weeksAgo: Int = 0): LongRange {
        val start = Instant.ofEpochMilli(weekStart(now, zone)).atZone(zone).toLocalDate().minusWeeks(weeksAgo.toLong())
        val from = start.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = start.plusWeeks(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return from until to
    }

    fun band(target: Int): Pair<Int, Int> {
        val t = target.coerceAtLeast(1)
        return Math.round(t * LOW_FACTOR).toInt().coerceAtLeast(1) to Math.round(t * HIGH_FACTOR).toInt()
    }

    fun status(sets: Double, target: Int): VolumeStatus {
        val (low, high) = band(target)
        return when {
            sets < low -> VolumeStatus.UNDER
            sets > high -> VolumeStatus.OVER
            else -> VolumeStatus.IN_RANGE
        }
    }

    /** All six regions, in display order, for the sets inside [range]. Warm-ups never count. */
    fun compute(sets: List<VolumeSet>, targets: Map<BodyRegion, Int>, range: LongRange): List<RegionVolume> {
        val inWeek = sets.filter { !it.isWarmup && it.completedAt in range }
        val byRegion = BodyRegion.entries.associateWith { mutableMapOf<String, Triple<String, Double, Int>>() }
        for (s in inWeek) {
            for ((region, weight) in MuscleWeights.regionCredit(s.primaryMuscle, s.secondaryMuscles)) {
                val m = byRegion.getValue(region)
                val old = m[s.exerciseId]
                m[s.exerciseId] = Triple(s.exerciseName, (old?.second ?: 0.0) + weight, (old?.third ?: 0) + 1)
            }
        }
        return BodyRegion.entries.map { region ->
            val contributors = byRegion.getValue(region).map { (id, t) -> VolumeContributor(id, t.first, t.second, t.third) }
                .sortedWith(compareByDescending<VolumeContributor> { it.effectiveSets }.thenBy { it.exerciseName })
            val total = contributors.sumOf { it.effectiveSets }
            val target = (targets[region] ?: 10).coerceAtLeast(1)
            val (low, high) = band(target)
            RegionVolume(region, total, target, low, high, status(total, target), contributors)
        }
    }

    /** All twenty diagram regions, in map order, for the sets inside [range]. Warm-ups never count. */
    fun computeMuscles(sets: List<VolumeSet>, targets: Map<BodyRegion, Int>, range: LongRange): List<MuscleVolume> {
        val inWeek = sets.filter { !it.isWarmup && it.completedAt in range }
        val byRegion = MuscleRegion.entries.associateWith { mutableMapOf<String, Triple<String, Double, Int>>() }
        for (s in inWeek) {
            for ((region, weight) in MuscleWeights.muscleCredit(s.exerciseName, s.primaryMuscle, s.secondaryMuscles)) {
                val m = byRegion.getValue(region)
                val old = m[s.exerciseId]
                m[s.exerciseId] = Triple(s.exerciseName, (old?.second ?: 0.0) + weight, (old?.third ?: 0) + 1)
            }
        }
        return MuscleRegion.entries.map { region ->
            val contributors = byRegion.getValue(region).map { (id, t) -> VolumeContributor(id, t.first, t.second, t.third) }
                .sortedWith(compareByDescending<VolumeContributor> { it.effectiveSets }.thenBy { it.exerciseName })
            val total = contributors.sumOf { it.effectiveSets }
            val coarse = (targets[region.body] ?: 10).coerceAtLeast(1)
            val target = Math.round(coarse * MuscleWeights.targetShare(region)).toInt().coerceAtLeast(1)
            MuscleVolume(region, total, target, status(total, target), contributors)
        }
    }

    /** "Light on back this week", or an encouraging line when nothing is under. Null when no set is logged at all. */
    fun hint(volumes: List<RegionVolume>): String? {
        if (volumes.all { it.sets == 0.0 }) return null
        val under = volumes.filter { it.status == VolumeStatus.UNDER }
        if (under.isEmpty()) return if (volumes.any { it.status == VolumeStatus.OVER }) "Plenty of volume this week" else "Every region is in range"
        val worst = under.minBy { it.sets / it.target }
        return "Light on ${worst.region.label.lowercase()} this week"
    }

    /** Plain words for the bar: "a bit low", "about right", "a lot". */
    fun words(status: VolumeStatus): String = when (status) {
        VolumeStatus.UNDER -> "a bit low"
        VolumeStatus.IN_RANGE -> "about right"
        VolumeStatus.OVER -> "a lot"
    }

    fun formatSets(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else (Math.round(value * 10) / 10.0).toString()
}
