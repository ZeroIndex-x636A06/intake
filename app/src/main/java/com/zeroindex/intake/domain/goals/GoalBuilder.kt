package com.zeroindex.intake.domain.goals

import com.zeroindex.intake.domain.hydration.HydrationTarget
import com.zeroindex.intake.domain.training.generator.GeneratedProgram
import kotlin.math.roundToInt

/** One extra line on the water target. Weather and drinks (Hydration v2) add these later. */
data class WaterAdjustment(val label: String, val ml: Int)

data class WaterPlan(
    /** The everyday target, rest day. */
    val baseMl: Int,
    /** The target on a training day (base plus the training bonus). */
    val trainingDayMl: Int,
    val adjustments: List<WaterAdjustment>,
    val why: String,
)

/**
 * The water target for a plan. It reuses [HydrationTarget] so the plan and the Today water card
 * can never disagree, and takes extra adjustments as data so weather, drinks and caffeine can be
 * added without touching this function.
 */
object WaterPlanner {

    fun plan(
        weightKg: Double,
        trainingDaysPerWeek: Int,
        manualBaseMl: Int? = null,
        extras: List<WaterAdjustment> = emptyList(),
    ): WaterPlan {
        val rest = HydrationTarget.compute(
            manualBaseMl = manualBaseMl?.toDouble(),
            bodyweightKg = weightKg,
            dynamic = false,
            trainedToday = false,
            activeKcalNet = null,
        )
        val training = HydrationTarget.compute(
            manualBaseMl = manualBaseMl?.toDouble(),
            bodyweightKg = weightKg,
            dynamic = true,
            trainedToday = true,
            activeKcalNet = null,
        )
        val extraMl = extras.sumOf { it.ml }
        val base = rest.baseMl.roundToInt() + extraMl
        val bonus = training.reasons.sumOf { it.ml }.roundToInt()
        val why = buildString {
            if (manualBaseMl != null) {
                append("${base.grouped()} ml is the number you chose")
            } else {
                append("${HydrationTarget.ML_PER_KG.roundToInt()} ml per kg x ${fmt1(weightKg)} kg = ${base.grouped()} ml")
            }
            if (trainingDaysPerWeek > 0 && bonus > 0) {
                append(", plus $bonus ml on each of your $trainingDaysPerWeek training days")
            }
            extras.forEach { append(", ${if (it.ml >= 0) "plus" else "minus"} ${kotlin.math.abs(it.ml)} ml ${it.label}") }
            append(".")
        }
        return WaterPlan(
            baseMl = base,
            trainingDayMl = base + bonus,
            adjustments = extras,
            why = why,
        )
    }
}

data class GoalInputs(
    val profile: UserProfile,
    val weightKg: Double,
    val currentYear: Int,
    /** Average daily steps from Health Connect, if connected. Overrides the activity choice. */
    val averageSteps: Double? = null,
    /** The baseline week's measurement, if there is one. */
    val baseline: MeasuredTdee? = null,
    /** The nightly adaptive estimate, if the app has one. */
    val adaptive: MeasuredEnergy.Adaptive? = null,
    /** What the person eats now; the reverse diet starts from it. Defaults to the baseline average. */
    val averageIntake: Double? = null,
    val waterExtras: List<WaterAdjustment> = emptyList(),
)

/** Edits the person made on the plan screen. Anything null is left as the engine worked it out. */
data class PlanOverrides(
    val kcal: Int? = null,
    val proteinPerKg: Double? = null,
    val fatPerKg: Double? = null,
    val waterMl: Int? = null,
)

/** A card on the "Your plan" screen: the number, a one-line why, and a longer explanation. */
data class PlanCard(
    val title: String,
    val headline: String,
    val why: String,
    val detail: String,
)

data class GoalPlan(
    val phase: Phase,
    val weightKg: Double,
    val bmr: Bmr,
    val activity: ActivityFactor,
    val estimate: Double,
    val tdee: Double,
    val tdeeSource: TdeeSource,
    val tdeeWhy: String,
    val calories: CalorieTarget,
    val macros: MacroPlan,
    val week: WeekBuild?,
    val dietBreak: DietBreakPlan?,
    val water: WaterPlan,
    val warnings: List<PlanWarning>,
    val calorieCard: PlanCard,
    val macroCard: PlanCard,
    val waterCard: PlanCard,
) {
    val kcal: Int get() = calories.kcal
}

/**
 * The goal builder (SPEC-v3 "Engines"). Pure and deterministic: the same inputs and overrides
 * always give the same plan, and every number comes back with its "why". No network and no AI.
 */
object GoalBuilder {

    fun build(inputs: GoalInputs, overrides: PlanOverrides = PlanOverrides()): GoalPlan {
        val profile = inputs.profile
        require(profile.hasBasics) { "Sex, birth year and height are needed to build a plan" }
        val sex = profile.sex!!
        val age = (inputs.currentYear - profile.birthYear!!).coerceAtLeast(0)
        val weight = inputs.weightKg
        val height = profile.heightCm!!
        val bodyFat = profile.bodyFatPercent?.takeIf { it in 3.0..60.0 }
        val leanMass = bodyFat?.let { weight * (1 - it / 100.0) }

        val bmr = EnergyModel.bmr(weight, height, age, sex, bodyFat)
        val activity = EnergyModel.activityFactor(profile.activity, profile.trainingDaysPerWeek, inputs.averageSteps)
        val estimate = EnergyModel.dayOneTdee(bmr.kcal, activity.value)
        val blend = MeasuredEnergy.blend(estimate, bmr.kcal, inputs.baseline, inputs.adaptive)
        val tdee = blend.tdee

        val burnLabel = when (blend.source) {
            TdeeSource.MEASURED -> "measured burn"
            TdeeSource.BLENDED -> "measured-and-estimated burn"
            TdeeSource.ESTIMATE -> "estimated burn"
        }
        val advanced = profile.isAdvanced
        val calories = PhasePlanner.calories(
            phase = profile.phase,
            pace = profile.pace,
            tdee = tdee,
            bmr = bmr.kcal,
            weightKg = weight,
            ageYears = age,
            contestWeeklyLossPct = profile.contestWeeklyLossPct,
            reverseStepKcal = profile.reverseStepKcal,
            averageIntake = inputs.averageIntake ?: inputs.baseline?.averageIntake,
            allowBelowFloor = advanced && profile.allowBelowFloor,
            manualKcal = overrides.kcal,
            burnLabel = burnLabel,
        )

        val macros = MacroPlanner.plan(
            kcal = calories.kcal,
            weightKg = weight,
            leanMassKg = leanMass,
            phase = profile.phase,
            proteinPerKg = overrides.proteinPerKg ?: profile.proteinPerKg.takeIf { advanced },
            fatPerKg = overrides.fatPerKg ?: profile.fatPerKg.takeIf { advanced },
        )

        val floorKcal = (bmr.kcal * PhasePlanner.FLOOR_OF_BMR).roundToInt()
        val week = if (advanced && (profile.carbCycling || profile.refeedDaysPerWeek > 0)) {
            WeekPlanner.build(
                base = macros,
                tdee = tdee.roundToInt(),
                floorKcal = if (profile.allowBelowFloor) 0 else floorKcal,
                trainingWeekdays = profile.effectiveTrainingWeekdays,
                carbCycling = profile.carbCycling,
                shiftPct = profile.carbCycleShiftPct,
                refeedDays = profile.refeedDaysPerWeek,
            )
        } else {
            null
        }

        val dietBreak = profile.dietBreakEveryWeeks
            ?.takeIf { advanced && DietBreakPlan.appliesTo(profile.phase) }
            ?.let { DietBreakPlan(it.coerceIn(DietBreakPlan.MIN_WEEKS, DietBreakPlan.MAX_WEEKS), tdee.roundToInt()) }

        val water = WaterPlanner.plan(
            weightKg = weight,
            trainingDaysPerWeek = profile.trainingDaysPerWeek,
            manualBaseMl = overrides.waterMl,
            extras = inputs.waterExtras,
        )

        val warnings = buildList {
            addAll(calories.warnings)
            macros.notes.forEach { add(PlanWarning(WarningLevel.INFO, it)) }
            week?.warnings?.let(::addAll)
            week?.notes?.forEach { add(PlanWarning(WarningLevel.INFO, it)) }
            if (blend.source == TdeeSource.ESTIMATE) {
                add(
                    PlanWarning(
                        WarningLevel.INFO,
                        "This plan starts from a formula. Log food and weigh in for a week and Intake will " +
                            "replace the guess with your own measured burn.",
                    ),
                )
            }
        }

        val paceWords = PhasePlanner.paceInWords(calories.kgPerWeek, weight)
        val calorieCard = PlanCard(
            title = "Calories",
            headline = "${calories.kcal.grouped()} kcal",
            why = calories.why,
            detail = listOf(
                "Daily calorie burn: ${tdee.roundToInt().grouped()} kcal (${blend.source.label}).",
                blend.why,
                "Calories at rest: ${bmr.why}",
                "Activity ${activity.why}",
                "Expected change: $paceWords.",
            ).joinToString("\n"),
        )
        val macroCard = PlanCard(
            title = "Macros",
            headline = "P ${macros.proteinG} g · C ${macros.carbsG} g · F ${macros.fatG} g",
            why = "Protein ${fmt1(macros.proteinPerKg)} g/kg of ${macros.proteinBasis.label}, fat at least " +
                "${fmt1(macros.fatPerKg)} g/kg, carbs fill the rest of ${macros.kcal.grouped()} kcal.",
            detail = listOf(macros.proteinWhy, macros.fatWhy, macros.carbsWhy).joinToString("\n"),
        )
        val waterCard = PlanCard(
            title = "Water",
            headline = "${water.baseMl.grouped()} ml a day",
            why = water.why,
            detail = "Training days: ${water.trainingDayMl.grouped()} ml. Heat, sweat and drinks can add to this " +
                "later; the number here is the plain starting point.",
        )

        return GoalPlan(
            phase = profile.phase,
            weightKg = weight,
            bmr = bmr,
            activity = activity,
            estimate = estimate,
            tdee = tdee,
            tdeeSource = blend.source,
            tdeeWhy = blend.why,
            calories = calories,
            macros = macros,
            week = week,
            dietBreak = dietBreak,
            water = water,
            warnings = warnings,
            calorieCard = calorieCard,
            macroCard = macroCard,
            waterCard = waterCard,
        )
    }
}

/** The training card: what the generator made, in a few plain lines. */
data class TrainingSummary(val headline: String, val why: String, val lines: List<String>)

object TrainingSummaries {

    fun of(program: GeneratedProgram, profile: UserProfile): TrainingSummary {
        val days = program.days.size
        val minutes = program.days.maxOfOrNull { it.estimatedMinutes } ?: 0
        val topMuscles = program.weeklySets.entries.sortedByDescending { it.value }.take(3)
            .joinToString(", ") { "${it.key} ${it.value}" }
        return TrainingSummary(
            headline = "$days days a week, up to $minutes min",
            why = "${program.name}: built for ${profile.effectiveTrainingGoal.label.lowercase()}, " +
                "${profile.experience.label.lowercase()} lifter, ${profile.trainingDaysPerWeek} days, " +
                "${profile.sessionMinutes} minutes.",
            lines = buildList {
                program.days.forEach { add("${it.name}: ${it.exercises.size} exercises, about ${it.estimatedMinutes} min") }
                if (topMuscles.isNotEmpty()) add("Most weekly sets: $topMuscles")
                add("Deload about every ${program.deloadEveryWeeks} weeks")
                addAll(program.warnings)
            },
        )
    }
}
