package com.zeroindex.intake.domain.training.generator

import com.zeroindex.intake.domain.model.ProgressionType

enum class TrainingGoal(val label: String, val blurb: String) {
    BUILD_MUSCLE("Build muscle", "More size, with steady strength gains."),
    GET_STRONGER("Get stronger", "Heavier lifts on a few main exercises."),
    LOSE_FAT("Lose fat, keep muscle", "Hold on to muscle while you eat less."),
    GENERAL_FITNESS("General fitness", "A balanced routine you can keep up."),
    ATHLETIC("Athletic and explosive", "Strength plus jumps and fast lifts."),
}

enum class Experience(val label: String, val blurb: String) {
    NEW("New", "Under 6 months of lifting"),
    SOME("Some", "6 to 24 months"),
    EXPERIENCED("Experienced", "2 years or more"),
}

enum class EquipmentPreset(val label: String, val kinds: Set<String>) {
    FULL_GYM("Full gym", Equipment.FULL_GYM),
    HOME_DUMBBELLS("Home dumbbells", Equipment.HOME_DUMBBELLS),
    BODYWEIGHT_ONLY("Bodyweight only", Equipment.BODYWEIGHT_ONLY),
    CUSTOM("Pick my own", emptySet()),
}

data class RepRange(val min: Int, val max: Int) {
    init {
        require(min in 1..100 && max >= min) { "Bad rep range $min-$max" }
    }
}

/** Everything the wizard asks. Every field has a default, so the wizard can be skipped. */
data class GeneratorInputs(
    val goal: TrainingGoal = TrainingGoal.BUILD_MUSCLE,
    val experience: Experience = Experience.SOME,
    val daysPerWeek: Int = 4,
    val sessionMinutes: Int = 60,
    val equipment: Set<String> = Equipment.FULL_GYM,
    /**
     * The specific items owned (ids from `EquipmentItems`). When set, an exercise is offered only if its requirements are met
     * by these items, and [equipment] is not used to filter. Null keeps the old kind-only rule.
     */
    val equipmentItems: Set<String>? = null,
    val focusMuscles: Set<String> = emptySet(),
    val avoid: Set<BodyArea> = emptySet(),
    /** Advanced: replaces the weekly hard sets for a muscle (0 leaves it out). */
    val weeklySetOverrides: Map<String, Int> = emptyMap(),
    /** Advanced: replaces the rep range on compound lifts, or on isolation lifts. */
    val compoundReps: RepRange? = null,
    val isolationReps: RepRange? = null,
    /** Advanced: weeks between deloads. */
    val deloadEveryWeeks: Int? = null,
    /** Exercises the person has hidden; the generator will not pick them. */
    val hiddenExerciseIds: Set<String> = emptySet(),
)

data class GeneratedExercise(
    val exerciseId: String,
    val name: String,
    val muscle: String,
    val sets: Int,
    val repsMin: Int,
    val repsMax: Int,
    /** Effort 1-10, or null for "no target". */
    val rpe: Double?,
    val restSec: Int,
    /** Null means no progression rule is attached. */
    val progression: ProgressionType?,
    /**
     * One line on why this exercise is in the plan, built from the answers that picked it, for example
     * "Lat pulldown: back is a focus, 2 of your 4 days train back, cable machine available."
     */
    val why: String,
    /** What the numbers mean: the role, reps, effort and how it progresses. */
    val prescription: String = "",
    val explosive: Boolean = false,
    /** First prescription when there is no history for the lift (kg); null means "pick a weight". */
    val startWeightKg: Double? = null,
    /** Why that starting weight, shown with it. */
    val startNote: String? = null,
)

data class GeneratedDay(
    val name: String,
    val exercises: List<GeneratedExercise>,
    val estimatedMinutes: Int,
    val why: String,
    /** ISO weekday 1 (Mon) to 7 (Sun) this day is scheduled on, or null for "next in the rotation". */
    val weekday: Int? = null,
)

data class GeneratedProgram(
    val name: String,
    val description: String,
    val days: List<GeneratedDay>,
    /** Hard sets per muscle per week actually placed in the days (power work excluded). */
    val weeklySets: Map<String, Int>,
    /** What the volume rules asked for before the session time budget trimmed it. */
    val targetSets: Map<String, Int>,
    val deloadEveryWeeks: Int,
    /** Program-level reasons, one line each, for the preview. */
    val whys: List<String>,
    val warnings: List<String>,
)
