package com.zeroindex.intake.domain.goals

import com.zeroindex.intake.domain.training.generator.BodyArea
import com.zeroindex.intake.domain.training.equipment.EquipmentItems
import com.zeroindex.intake.domain.training.generator.Equipment
import com.zeroindex.intake.domain.training.generator.EquipmentPreset
import com.zeroindex.intake.domain.training.generator.Experience
import com.zeroindex.intake.domain.training.generator.GeneratorInputs
import com.zeroindex.intake.domain.training.generator.Muscles
import com.zeroindex.intake.domain.training.generator.TrainingGoal
import kotlinx.serialization.Serializable

/**
 * The profile and goal vocabulary (SPEC-v3 "Engines"). Everything here is plain data with no
 * Android imports, so the goal builder and its tests run on the JVM.
 */

/** Sex as the energy formulas use it. It is a formula input, not a label for the person. */
@Serializable
enum class Sex(val label: String) {
    MALE("Male"),
    FEMALE("Female"),
}

/**
 * A simple build picker.
 *
 * This is NOT physiology. It does not change any formula, calorie, macro or training volume. It
 * only picks wording and the phase we suggest first, so the first screen feels like it was written
 * for the person using it. The classic somatotype names are kept as a small secondary label
 * because some people look for them; the science behind somatotypes is weak and the app does not
 * rely on it.
 */
@Serializable
enum class BuildType(
    val label: String,
    val blurb: String,
    val classicName: String,
    val suggestedPhase: Phase,
    /** One line of coaching copy shown under the picker. */
    val startingTip: String,
) {
    SLIM(
        "Slim",
        "Light frame. Weight is slow to go on.",
        "Ectomorph",
        Phase.LEAN_BULK,
        "Eating enough is usually the hard part. A small surplus and steady lifting works best.",
    ),
    AVERAGE(
        "Average",
        "Middle of the road. Neither very light nor very heavy.",
        "Mesomorph-ish",
        Phase.RECOMP,
        "A gentle plan and consistent training will move you either way. Start with one clear goal.",
    ),
    MUSCULAR(
        "Muscular",
        "Broad and strong. Muscle comes on easily.",
        "Mesomorph",
        Phase.MAINTAIN,
        "Your numbers matter more at this level. Weigh in often so the plan can be measured, not guessed.",
    ),
    HEAVIER(
        "Heavier",
        "More body fat. Weight comes on easily.",
        "Endomorph",
        Phase.CUT,
        "A steady, modest deficit with plenty of protein protects your muscle while the fat comes off.",
    ),
}

/** What the plan is trying to do with body weight. */
@Serializable
enum class Phase(
    val label: String,
    val blurb: String,
    val advanced: Boolean = false,
    /** True when the phase moves weight down, which the safety rails treat specially. */
    val losesWeight: Boolean = false,
    /** Short phrase used inside the "why" line: "for a steady cut". */
    val phrase: String,
) {
    CUT("Cut", "Lose fat and keep your muscle.", losesWeight = true, phrase = "cut"),
    LEAN_BULK("Lean bulk", "Build muscle with very little fat gain.", phrase = "lean bulk"),
    BULK("Bulk", "Gain size faster. Some fat comes with it.", phrase = "bulk"),
    MAINTAIN("Maintain", "Hold your weight and keep training.", phrase = "maintenance"),
    RECOMP("Recomp", "Slowly swap fat for muscle at about the same weight.", losesWeight = true, phrase = "recomp"),
    CONTEST_PREP(
        "Contest prep",
        "A precise, time-boxed cut for a show or a photo shoot.",
        advanced = true,
        losesWeight = true,
        phrase = "contest prep",
    ),
    REVERSE_DIET(
        "Reverse diet",
        "Climb back up to maintenance in small weekly steps after a long cut.",
        advanced = true,
        phrase = "reverse diet",
    ),
}

/** How fast. Applies to cuts, bulks and recomp; the other phases ignore it. */
@Serializable
enum class Pace(val label: String) {
    GENTLE("Gentle"),
    STEADY("Steady"),
    FAST("Fast"),
}

/** Everyday movement outside training. Steps from Health Connect override this once we have them. */
@Serializable
enum class ActivityLevel(val label: String, val blurb: String, val factor: Double) {
    SITTING("Mostly sitting", "Desk job, car, couch. A few short walks.", 1.2),
    STANDING("On my feet", "Teacher, retail, parent of small children.", 1.375),
    PHYSICAL("Physical job", "Trades, warehouse, farm, nursing floor.", 1.55),
}

@Serializable
enum class ProfileMode { SIMPLE, ADVANCED }

/**
 * Everything the profile screen collects. It lives in DataStore as one JSON value, so adding a
 * field later never needs a database migration, and an older saved value just loads the new
 * fields with their defaults.
 *
 * Current weight is deliberately NOT here: it is a weigh-in (body_metric), so the trend, the
 * charts and the baseline week all see the same number.
 */
@Serializable
data class UserProfile(
    val sex: Sex? = null,
    val birthYear: Int? = null,
    val heightCm: Double? = null,
    /** Optional. Switches the resting-burn formula to Katch-McArdle and protein to lean mass. */
    val bodyFatPercent: Double? = null,
    val build: BuildType = BuildType.AVERAGE,
    val phase: Phase = Phase.MAINTAIN,
    val pace: Pace = Pace.STEADY,
    val activity: ActivityLevel = ActivityLevel.SITTING,

    // --- Training availability (feeds GeneratorInputs) ---
    val trainingDaysPerWeek: Int = 4,
    val sessionMinutes: Int = 60,
    val equipment: Set<String> = Equipment.FULL_GYM,
    val equipmentPreset: EquipmentPreset = EquipmentPreset.FULL_GYM,
    /**
     * The specific items the person has (ids from [EquipmentItems]). Null on a profile saved before the checklist existed:
     * [effectiveEquipmentItems] then works them out from [equipment] and [equipmentPreset]. [equipment] (the old five kinds)
     * is kept in step with the items, because other screens still read it.
     */
    val equipmentItems: Set<String>? = null,
    /** The heaviest dumbbell they can use, in kg. Null when not given. */
    val dumbbellMaxKg: Double? = null,
    val experience: Experience = Experience.SOME,
    /** Null means "pick one that fits the phase". */
    val trainingGoal: TrainingGoal? = null,
    /** Optional. Muscles to give about 25% more weekly sets (the generator's focus). Library names, programmed muscles only. */
    val focusMuscles: Set<String> = emptySet(),
    /** Optional. Body areas to go easy on: the generator leaves out lifts that load them. */
    val avoidAreas: Set<BodyArea> = emptySet(),

    val mode: ProfileMode = ProfileMode.SIMPLE,

    // --- Advanced ---
    val proteinPerKg: Double? = null,
    val fatPerKg: Double? = null,
    val carbCycling: Boolean = false,
    /** How many percent more carbs than the daily average land on a training day. */
    val carbCycleShiftPct: Int = 25,
    val refeedDaysPerWeek: Int = 0,
    /** Null = no diet breaks. Otherwise a maintenance week after this many weeks of dieting. */
    val dietBreakEveryWeeks: Int? = null,
    /** Contest prep: weekly loss as a percent of body weight (0.5 to 1.0). */
    val contestWeeklyLossPct: Double = 0.75,
    /** Reverse diet: kcal added per week (50 to 100). */
    val reverseStepKcal: Int = 75,
    /** ISO weekdays 1 (Mon) to 7 (Sun). Empty means "spread them out for me". */
    val trainingWeekdays: Set<Int> = emptySet(),
    /** Explicit Advanced override of the calorie floor. */
    val allowBelowFloor: Boolean = false,
) {
    /** The basics the energy formulas cannot work without. */
    val hasBasics: Boolean get() = sex != null && birthYear != null && heightCm != null

    val isAdvanced: Boolean get() = mode == ProfileMode.ADVANCED

    /** The items to use: the person's list, else the move from the old kinds and preset. */
    val effectiveEquipmentItems: Set<String>
        get() = equipmentItems?.let { EquipmentItems.normalize(it) } ?: EquipmentItems.fromLegacy(equipment, equipmentPreset)

    /** A quick pick: fills the item list (and the old kinds) from the preset. CUSTOM keeps what is ticked. */
    fun withEquipmentPreset(preset: EquipmentPreset): UserProfile =
        if (preset == EquipmentPreset.CUSTOM) copy(equipmentPreset = preset, equipmentItems = effectiveEquipmentItems)
        else withEquipmentItems(EquipmentItems.presetItems(preset))

    /** Ticks exactly [items]; the preset chip follows (a list that matches a quick pick selects it). */
    fun withEquipmentItems(items: Set<String>): UserProfile {
        val clean = EquipmentItems.normalize(items)
        return copy(equipmentItems = clean, equipment = EquipmentItems.kindsOf(clean), equipmentPreset = EquipmentItems.presetFor(clean))
    }

    /** The training goal to use: the person's choice, else one that fits the phase. */
    val effectiveTrainingGoal: TrainingGoal get() = trainingGoal ?: GoalDefaults.trainingGoalFor(phase)

    /** Training weekdays to use: the person's choice, else evenly spread across the week. */
    val effectiveTrainingWeekdays: Set<Int>
        get() = trainingWeekdays.takeIf { it.isNotEmpty() } ?: GoalDefaults.spreadWeekdays(trainingDaysPerWeek)

    /**
     * Training days to plan for: when weekdays are chosen, exactly those (2 to 6), otherwise the
     * "days a week" answer. The weekday picker and the day count therefore never disagree.
     */
    val effectiveTrainingDays: Int
        get() = (if (trainingWeekdays.isNotEmpty()) trainingWeekdays.size else trainingDaysPerWeek).coerceIn(2, 6)

    /**
     * The inputs for the existing program generator, so one set of answers feeds both screens.
     * Everything the profile asks about training goes through: goal, experience, days, session
     * length, equipment, focus muscles, areas to go easy on, and hidden exercises.
     */
    fun toGeneratorInputs(hiddenExerciseIds: Set<String> = emptySet()): GeneratorInputs = GeneratorInputs(
        goal = effectiveTrainingGoal,
        experience = experience,
        daysPerWeek = effectiveTrainingDays,
        sessionMinutes = sessionMinutes,
        equipment = equipment,
        equipmentItems = effectiveEquipmentItems,
        focusMuscles = focusMuscles.filter { it in Muscles.PROGRAMMED }.toSet(),
        avoid = avoidAreas,
        hiddenExerciseIds = hiddenExerciseIds,
    )
}

object GoalDefaults {

    fun trainingGoalFor(phase: Phase): TrainingGoal = when (phase) {
        Phase.CUT, Phase.CONTEST_PREP -> TrainingGoal.LOSE_FAT
        Phase.LEAN_BULK, Phase.BULK, Phase.RECOMP -> TrainingGoal.BUILD_MUSCLE
        Phase.MAINTAIN, Phase.REVERSE_DIET -> TrainingGoal.GENERAL_FITNESS
    }

    /** Spreads [days] training days over Mon..Sun with rest days between them where possible. */
    fun spreadWeekdays(days: Int): Set<Int> = when (days.coerceIn(1, 7)) {
        1 -> setOf(3)
        2 -> setOf(1, 4)
        3 -> setOf(1, 3, 5)
        4 -> setOf(1, 2, 4, 5)
        5 -> setOf(1, 2, 4, 5, 6)
        6 -> setOf(1, 2, 3, 5, 6, 7)
        else -> (1..7).toSet()
    }
}

/**
 * Where the baseline week stands. Kept tiny and in DataStore: the data itself (food, weigh-ins,
 * steps) is read from the tables it already lives in.
 */
@Serializable
data class BaselineState(
    /** UTC epoch millis of the moment the person tapped Start. Null = never started. */
    val startedAtMillis: Long? = null,
    /** When the single "your plan is ready" notification went out. Null = not sent. */
    val notifiedAtMillis: Long? = null,
    /** True once a plan built from this baseline was applied, which ends the baseline card. */
    val completed: Boolean = false,
) {
    val active: Boolean get() = startedAtMillis != null && !completed
}

/** One applied plan, kept as a short history so a person can see what changed and why. */
@Serializable
data class AppliedPlan(
    val appliedAtMillis: Long,
    val phase: Phase,
    val kcal: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
    val waterMl: Int,
    val tdee: Int,
    val tdeeSource: TdeeSource,
    val summary: String,
    val programName: String? = null,
)
