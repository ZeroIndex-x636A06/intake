package com.zeroindex.intake.domain.ai

import kotlin.math.roundToInt

/** Everything the daily insight may know, already reduced to numbers. */
data class InsightInputs(
    val days: Int,
    val kcalTarget: Int?,
    val proteinTarget: Int?,
    /** One entry per day, oldest first; null means nothing logged. */
    val dailyKcal: List<Double?>,
    val dailyProtein: List<Double?>,
    val weightTrendKgPerWeek: Double?,
    val tdeeKcal: Double?,
    val tdeeConfidence: String?,
    val bodyFatPercent: Double?,
    val leanMassKg: Double?,
    val sessionsLastWeek: Int,
    val sessionsWeekBefore: Int,
    /** Average working-set RPE per week, oldest first. */
    val weeklyAvgRpe: List<Double?>,
    val sleepMinutesAvg: Double?,
    val restingHrFirstWeek: Double?,
    val restingHrLastWeek: Double?,
    val stepsAvg: Double?,
    val waterMlAvg: Double?,
    val waterTargetMl: Double?,
)

/**
 * Amendment §3: what goes into the daily insight, built here so it is
 * reviewable and tested rather than assembled ad hoc in a prompt.
 *
 * Privacy by construction: only numbers and trends. No food names, no notes,
 * no dates, no location - the model learns that protein ran 40 g short three
 * days running, not what was eaten. [lines] is shown to the user verbatim
 * in the card's "why" disclosure, so they see exactly what was sent.
 */
object InsightDigest {

    fun lines(i: InsightInputs): List<String> = buildList {
        val logged = i.dailyKcal.count { it != null }
        add("Days logged: $logged of ${i.days}")
        if (i.kcalTarget != null) {
            avg(i.dailyKcal)?.let { add("Calories: averaging ${it.roundToInt()} against a ${i.kcalTarget} target") }
        }
        if (i.proteinTarget != null) {
            avg(i.dailyProtein)?.let { add("Protein: averaging ${it.roundToInt()} g against a ${i.proteinTarget} g target") }
            val shortRun = i.dailyProtein.takeLastWhile { it != null && it < i.proteinTarget * 0.9 }.size
            if (shortRun >= 2) add("Protein under 90% of target for the last $shortRun logged days")
        }
        i.weightTrendKgPerWeek?.let { add("Weight trend: ${signed(it, 2)} kg per week") }
        i.tdeeKcal?.let { add("Measured maintenance: ${it.roundToInt()} kcal (${i.tdeeConfidence ?: "unknown"} confidence)") }
        i.bodyFatPercent?.let { add("Body fat: ${"%.1f".format(it)}%") }
        i.leanMassKg?.let { add("Lean mass: ${"%.1f".format(it)} kg") }
        add("Training sessions: ${i.sessionsLastWeek} last 7 days, ${i.sessionsWeekBefore} the 7 before")
        val rpe = i.weeklyAvgRpe.filterNotNull()
        if (rpe.size >= 2) add("Average set RPE by week: ${rpe.joinToString(" → ") { "%.1f".format(it) }}")
        i.sleepMinutesAvg?.let { add("Sleep: averaging ${"%.1f".format(it / 60)} h a night") }
        if (i.restingHrFirstWeek != null && i.restingHrLastWeek != null) {
            add("Resting heart rate: ${i.restingHrFirstWeek.roundToInt()} → ${i.restingHrLastWeek.roundToInt()} bpm")
        }
        i.stepsAvg?.let { add("Steps: averaging ${"%,d".format(it.roundToInt())} a day") }
        if (i.waterMlAvg != null && i.waterTargetMl != null) {
            add("Water: averaging ${i.waterMlAvg.roundToInt()} ml against ${i.waterTargetMl.roundToInt()} ml")
        }
    }

    /** Too little data and the insight would be noise; better to say nothing. */
    fun hasEnough(i: InsightInputs): Boolean = i.dailyKcal.count { it != null } >= 3

    private fun avg(values: List<Double?>): Double? = values.filterNotNull().takeIf { it.isNotEmpty() }?.average()

    private fun signed(v: Double, decimals: Int): String = (if (v > 0) "+" else "") + "%.${decimals}f".format(v)
}
