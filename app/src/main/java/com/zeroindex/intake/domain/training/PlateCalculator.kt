package com.zeroindex.intake.domain.training

import kotlin.math.abs

/** What to hang on each side, biggest first. */
data class PlateLoad(
    val perSide: List<Double>,
    val barWeightKg: Double,
    /** How far the loadable total is from what was asked for. */
    val shortfallKg: Double,
) {
    val totalKg: Double get() = barWeightKg + perSide.sum() * 2
    val isExact: Boolean get() = abs(shortfallKg) < 0.001
}

/**
 * SPEC §10: which plates per side for the prescribed weight, configurable for
 * the plates actually available and the bar in use.
 */
object PlateCalculator {

    /**
     * Greedy from the heaviest plate down, which is how anyone loads a bar.
     * Returns an empty load when the target is at or below the bar.
     */
    fun calculate(
        targetKg: Double,
        barWeightKg: Double,
        availablePlatesKg: List<Double>,
    ): PlateLoad {
        val plates = availablePlatesKg.filter { it > 0 }.sortedDescending()
        if (targetKg <= barWeightKg || plates.isEmpty()) {
            return PlateLoad(
                perSide = emptyList(),
                barWeightKg = barWeightKg,
                shortfallKg = targetKg - barWeightKg,
            )
        }

        var remainingPerSide = (targetKg - barWeightKg) / 2.0
        val chosen = mutableListOf<Double>()
        plates.forEach { plate ->
            while (remainingPerSide >= plate - 0.001) {
                chosen += plate
                remainingPerSide -= plate
            }
        }

        return PlateLoad(
            perSide = chosen,
            barWeightKg = barWeightKg,
            shortfallKg = remainingPerSide * 2,
        )
    }

    /** "2 x 20, 1 x 10, 1 x 2.5" - how you would say it out loud. */
    fun describe(load: PlateLoad): String {
        if (load.perSide.isEmpty()) {
            return if (load.shortfallKg > 0.001) "Bar only (plus ${fmt(load.shortfallKg)} kg short)" else "Bar only"
        }
        val grouped = load.perSide.groupingBy { it }.eachCount()
        val parts = grouped.entries.joinToString(", ") { (plate, count) -> "$count x ${fmt(plate)}" }
        return if (load.isExact) parts else "$parts (${fmt(load.shortfallKg)} kg short)"
    }

    private fun fmt(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
}

/** SPEC §10: a warmup set generator. */
object WarmupGenerator {

    /**
     * Ramps to the working weight in [steps], rounded to loadable increments.
     * Bodyweight or very light work gets no warmup ramp - there is nothing to
     * ramp from.
     */
    fun generate(
        workingWeightKg: Double,
        barWeightKg: Double,
        incrementKg: Double = 2.5,
        steps: Int = 3,
    ): List<WarmupSet> {
        if (workingWeightKg <= barWeightKg || steps < 1) return emptyList()
        val fractions = when (steps) {
            1 -> listOf(0.6)
            2 -> listOf(0.5, 0.75)
            else -> listOf(0.4, 0.6, 0.8)
        }
        val reps = listOf(8, 5, 3)
        return fractions.mapIndexed { index, fraction ->
            val raw = workingWeightKg * fraction
            val rounded = (raw / incrementKg).toInt() * incrementKg
            WarmupSet(
                weightKg = maxOf(rounded, barWeightKg),
                reps = reps.getOrElse(index) { 3 },
            )
        }.distinctBy { it.weightKg }
    }
}

data class WarmupSet(val weightKg: Double, val reps: Int)
