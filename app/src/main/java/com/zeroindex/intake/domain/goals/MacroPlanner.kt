package com.zeroindex.intake.domain.goals

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class ProteinBasis(val label: String) {
    BODY_WEIGHT("body weight"),
    LEAN_MASS("lean mass"),
}

/** Protein, fat and carbs for one ordinary day, with a "why" for each. */
data class MacroPlan(
    val kcal: Int,
    val proteinG: Int,
    val fatG: Int,
    val carbsG: Int,
    val proteinPerKg: Double,
    val proteinBasis: ProteinBasis,
    val fatPerKg: Double,
    val proteinWhy: String,
    val fatWhy: String,
    val carbsWhy: String,
    val notes: List<String>,
) {
    /** The calories the grams add up to. Within a couple of kcal of [kcal] after rounding. */
    val gramsKcal: Int get() = proteinG * 4 + fatG * 9 + carbsG * 4
}

/**
 * Protein by g/kg, a fat floor, carbs take the rest (SPEC-v3 "Engines").
 *
 * The protein ranges follow the ISSN position stand (Jager 2017) and Helms 2014 for lean people
 * in a deficit: cut and contest prep 2.2-2.6, recomp 2.0-2.4, everything else 1.6-2.2 per kg.
 * This planner does not reuse `MacroSplit.forKcal`, which clamps protein to 1.6-2.2 and so could
 * not express a cut.
 */
object MacroPlanner {

    const val FAT_FLOOR_PER_KG = 0.6
    const val FAT_FLOOR_SHARE_OF_KCAL = 0.20
    const val PROTEIN_MIN_PER_KG = 0.8
    const val PROTEIN_MAX_PER_KG = 3.5

    fun proteinRange(phase: Phase): ClosedFloatingPointRange<Double> = when (phase) {
        Phase.CUT, Phase.CONTEST_PREP -> 2.2..2.6
        Phase.RECOMP -> 2.0..2.4
        Phase.LEAN_BULK, Phase.BULK, Phase.MAINTAIN, Phase.REVERSE_DIET -> 1.6..2.2
    }

    fun defaultProteinPerKg(phase: Phase): Double = when (phase) {
        Phase.CUT -> 2.4
        Phase.CONTEST_PREP -> 2.6
        Phase.RECOMP -> 2.2
        else -> 1.8
    }

    fun defaultFatPerKg(phase: Phase): Double = when (phase) {
        Phase.CUT, Phase.RECOMP -> 0.7
        Phase.CONTEST_PREP -> 0.6
        else -> 0.8
    }

    /**
     * @param weightKg body weight, which the fat floor always uses
     * @param leanMassKg known only when body fat is; protein then uses it as the basis
     * @param proteinPerKg Advanced override
     * @param fatPerKg Advanced override
     */
    fun plan(
        kcal: Int,
        weightKg: Double,
        leanMassKg: Double?,
        phase: Phase,
        proteinPerKg: Double? = null,
        fatPerKg: Double? = null,
    ): MacroPlan {
        require(kcal > 0) { "kcal must be positive" }
        require(weightKg > 0) { "weight must be positive" }
        val notes = mutableListOf<String>()

        val basis = if (leanMassKg != null && leanMassKg > 0) ProteinBasis.LEAN_MASS else ProteinBasis.BODY_WEIGHT
        val basisKg = if (basis == ProteinBasis.LEAN_MASS) leanMassKg!! else weightKg
        val pPerKg = (proteinPerKg ?: defaultProteinPerKg(phase)).coerceIn(PROTEIN_MIN_PER_KG, PROTEIN_MAX_PER_KG)
        var protein = (basisKg * pPerKg).roundToInt()

        val fPerKg = max(fatPerKg ?: defaultFatPerKg(phase), FAT_FLOOR_PER_KG)
        val fatFromWeight = weightKg * fPerKg
        val fatFromShare = FAT_FLOOR_SHARE_OF_KCAL * kcal / 9.0
        val fat = max(fatFromWeight, fatFromShare).roundToInt()
        val fatByShare = fatFromShare > fatFromWeight

        // Not enough calories for both: protein gives way, because the floor on fat is the hard one.
        var effectivePPerKg = pPerKg
        val proteinRoom = ((kcal - fat * 9) / 4.0).toInt()
        if (protein > proteinRoom) {
            protein = max(proteinRoom, 0)
            effectivePPerKg = if (basisKg > 0) protein / basisKg else 0.0
            notes += "Calories are so low that protein had to be trimmed to ${protein} g to fit the fat floor."
        }

        val carbs = max(0, ((kcal - protein * 4 - fat * 9) / 4.0).roundToInt())

        val range = proteinRange(phase)
        if (effectivePPerKg < range.start - 0.001 || effectivePPerKg > range.endInclusive + 0.001) {
            notes += "Protein is outside the usual ${range.start}-${range.endInclusive} g/kg for ${phase.phrase}."
        }

        val proteinWhy = "$protein g = ${fmt1(effectivePPerKg)} g per kg of ${basis.label} " +
            "(${fmt1(basisKg)} kg). The usual range for ${phase.phrase} is ${range.start}-${range.endInclusive}."
        val fatWhy = if (fatByShare) {
            "$fat g, set by the rule that fat is at least 20% of calories " +
                "(your ${fmt1(fPerKg)} g/kg would be ${fatFromWeight.roundToInt()} g)."
        } else {
            "$fat g = ${fmt1(fPerKg)} g per kg of body weight. Fat never drops below 0.6 g/kg or 20% of calories."
        }
        val carbsWhy = "$carbs g = what is left of ${kcal.grouped()} kcal after protein and fat. " +
            "Carbs fuel training, so they take the remainder."

        return MacroPlan(
            kcal = kcal,
            proteinG = protein,
            fatG = fat,
            carbsG = carbs,
            proteinPerKg = effectivePPerKg,
            proteinBasis = basis,
            fatPerKg = fPerKg,
            proteinWhy = proteinWhy,
            fatWhy = fatWhy,
            carbsWhy = carbsWhy,
            notes = notes,
        )
    }
}

enum class DayKind(val label: String) {
    TRAINING("Training day"),
    REST("Rest day"),
    REFEED("Refeed day"),
}

data class DayMacros(
    /** ISO weekday: 1 = Monday ... 7 = Sunday. */
    val weekday: Int,
    val kind: DayKind,
    val proteinG: Int,
    val fatG: Int,
    val carbsG: Int,
) {
    val kcal: Int get() = proteinG * 4 + fatG * 9 + carbsG * 4
}

/** A seven-day pattern. Always seven entries, Monday first. */
data class WeekPattern(val baseKcal: Int, val days: List<DayMacros>) {
    val weeklyKcal: Int get() = days.sumOf { it.kcal }
    val weeklyCarbsG: Int get() = days.sumOf { it.carbsG }
    fun forWeekday(isoDay: Int): DayMacros = days.first { it.weekday == isoDay }
}

data class WeekBuild(val pattern: WeekPattern, val warnings: List<PlanWarning>, val notes: List<String>)

/**
 * Advanced week shaping. Both tools move carbs between days and never change the week's total:
 *
 *  - Carb cycling: training days get [shiftPct]% more carbs than the average of the days it is
 *    shared with, rest days get the rest.
 *  - Refeeds: on N training days carbs rise until the day reaches maintenance calories; the other
 *    days give back exactly what the refeeds added, so the weekly deficit is unchanged.
 *
 * Protein and fat stay the same every day.
 */
object WeekPlanner {

    private const val MIN_REST_SHARE = 0.2

    fun build(
        base: MacroPlan,
        tdee: Int,
        floorKcal: Int,
        trainingWeekdays: Set<Int>,
        carbCycling: Boolean,
        shiftPct: Int,
        refeedDays: Int,
    ): WeekBuild {
        val warnings = mutableListOf<PlanWarning>()
        val notes = mutableListOf<String>()
        val training = trainingWeekdays.filter { it in 1..7 }.toSortedSet()
        val weekCarbs = base.carbsG * 7

        // --- Refeeds: the last N training days of the week, only when there is a deficit to refeed from.
        var refeeds = refeedDays.coerceIn(0, 3)
        if (refeeds > 0 && base.kcal >= tdee) {
            notes += "Refeeds only make sense in a deficit, so none were scheduled."
            refeeds = 0
        }
        refeeds = min(refeeds, training.size)
        val refeedCarbs = ((tdee - base.proteinG * 4 - base.fatG * 9) / 4.0).roundToInt()
        var refeedSet: Set<Int> = training.toList().takeLast(refeeds).toSet()
        var otherCarbsTotal = weekCarbs
        if (refeeds > 0) {
            // Each refeed raises carbs to [refeedCarbs]; shrink N until the leftover days stay above the floor.
            while (refeedSet.isNotEmpty()) {
                val n = refeedSet.size
                val leftover = (weekCarbs - n * refeedCarbs).toDouble() / (7 - n)
                val leftoverKcal = base.proteinG * 4 + base.fatG * 9 + leftover * 4
                if (refeedCarbs > base.carbsG && leftover >= 0 && leftoverKcal >= floorKcal) break
                refeedSet = refeedSet.drop(1).toSet()
            }
            if (refeedSet.size < refeeds) {
                warnings += PlanWarning(
                    WarningLevel.WARN,
                    if (refeedSet.isEmpty()) {
                        "Refeeds were skipped: paying for them would push the other days under your calorie floor."
                    } else {
                        "Only ${refeedSet.size} refeed day(s) fit: more would push the other days under your calorie floor."
                    },
                )
            }
            otherCarbsTotal = weekCarbs - refeedSet.size * refeedCarbs
        }

        // --- The non-refeed days share otherCarbsTotal; cycling tilts it toward training days.
        val shared = (1..7).filter { it !in refeedSet }
        val sharedTraining = shared.filter { it in training }
        val sharedRest = shared.filter { it !in training }
        val average = otherCarbsTotal.toDouble() / shared.size

        val carbsByDay = mutableMapOf<Int, Int>()
        refeedSet.forEach { carbsByDay[it] = refeedCarbs }
        if (carbCycling && sharedTraining.isNotEmpty() && sharedRest.isNotEmpty()) {
            val wantedTraining = average * (1 + shiftPct.coerceIn(0, 100) / 100.0)
            // Keep rest days from collapsing: at least 20% of the average.
            val maxTraining = (otherCarbsTotal - sharedRest.size * average * MIN_REST_SHARE) / sharedTraining.size
            val trainCarbs = min(wantedTraining, maxTraining).roundToInt()
            val rest = ((otherCarbsTotal - sharedTraining.size * trainCarbs).toDouble() / sharedRest.size).roundToInt()
            sharedTraining.forEach { carbsByDay[it] = trainCarbs }
            sharedRest.forEach { carbsByDay[it] = rest }
        } else {
            if (carbCycling) notes += "Carb cycling needs both training days and rest days, so every day is the same."
            shared.forEach { carbsByDay[it] = average.roundToInt() }
        }

        val days = (1..7).map { d ->
            val kind = when {
                d in refeedSet -> DayKind.REFEED
                d in training -> DayKind.TRAINING
                else -> DayKind.REST
            }
            DayMacros(d, kind, base.proteinG, base.fatG, carbsByDay.getValue(d))
        }
        return WeekBuild(WeekPattern(base.kcal, days), warnings, notes)
    }
}

/**
 * A scheduled maintenance week during a long diet. "1 week at maintenance every 8 to 12 weeks of
 * dieting": with [everyWeeks] = 10, weeks 1-10 are the diet, week 11 is the break, then the
 * count starts again.
 */
data class DietBreakPlan(val everyWeeks: Int, val maintenanceKcal: Int) {
    init {
        require(everyWeeks in MIN_WEEKS..MAX_WEEKS) { "Diet breaks every $everyWeeks weeks is outside $MIN_WEEKS-$MAX_WEEKS" }
    }

    /** [week] counts from 1 on the first day of the diet. */
    fun isBreakWeek(week: Int): Boolean = week > 0 && week % (everyWeeks + 1) == 0

    /** The first break week strictly after [fromWeek]. */
    fun nextBreakWeek(fromWeek: Int): Int = ((fromWeek / (everyWeeks + 1)) + 1) * (everyWeeks + 1)

    companion object {
        const val MIN_WEEKS = 8
        const val MAX_WEEKS = 12
        const val DEFAULT_WEEKS = 10

        /** Breaks help in long deficits only. */
        fun appliesTo(phase: Phase): Boolean = phase == Phase.CUT || phase == Phase.CONTEST_PREP
    }
}
