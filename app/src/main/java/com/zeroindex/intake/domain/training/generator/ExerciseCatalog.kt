package com.zeroindex.intake.domain.training.generator

import com.zeroindex.intake.data.db.entity.seedId

/** Body areas a person can say they want to protect; an exercise lists the ones it loads. */
enum class BodyArea(val label: String) {
    SHOULDER("Shoulder"),
    ELBOW("Elbow"),
    WRIST("Wrist"),
    LOWER_BACK("Lower back"),
    KNEE("Knee"),
    HIP("Hip"),
}

/**
 * One entry of the built-in exercise library. The library rows, the generator and the program
 * templates all read this list, so an exercise id used anywhere is guaranteed to be seeded.
 */
data class CatalogExercise(
    val name: String,
    val primaryMuscle: String,
    val equipment: String,
    val compound: Boolean,
    val secondaryMuscles: List<String> = emptyList(),
    val isBodyweight: Boolean = false,
    /** Areas this lift loads hard enough that "avoid" should skip it. */
    val loads: Set<BodyArea> = emptySet(),
    /** Fast, low-rep power work. The generator treats it separately from hard sets. */
    val explosive: Boolean = false,
    /** The least experienced lifter it is offered to. */
    val minExperience: Experience = Experience.NEW,
    val defaultRestSec: Int = if (compound) 150 else 75,
) {
    val id: String get() = idOf(name)

    companion object {
        fun idOf(name: String): String = seedId("exercise", name)
    }
}

object Equipment {
    const val BARBELL = "barbell"
    const val DUMBBELL = "dumbbell"
    const val MACHINE = "machine"
    const val CABLE = "cable"
    const val BODYWEIGHT = "bodyweight"

    val ALL: List<String> = listOf(BARBELL, DUMBBELL, MACHINE, CABLE, BODYWEIGHT)
    val FULL_GYM: Set<String> = ALL.toSet()
    val HOME_DUMBBELLS: Set<String> = setOf(DUMBBELL, BODYWEIGHT)
    val BODYWEIGHT_ONLY: Set<String> = setOf(BODYWEIGHT)

    fun label(value: String): String = value.replaceFirstChar { it.uppercase() }
}

object Muscles {
    /** The muscles the generator programs, in the order a session works them. */
    val PROGRAMMED: List<String> = listOf(
        "chest", "back", "shoulders", "quads", "hamstrings", "glutes", "biceps", "triceps", "calves", "core",
    )

    /** Every muscle the library uses, for filters and the custom-exercise form. */
    val ALL: List<String> = PROGRAMMED + listOf("traps", "forearms")

    fun label(value: String): String = value.replaceFirstChar { it.uppercase() }
}

object ExerciseCatalog {

    private fun ex(
        name: String,
        muscle: String,
        equipment: String,
        compound: Boolean,
        secondary: String = "",
        bodyweight: Boolean = equipment == Equipment.BODYWEIGHT,
        loads: Set<BodyArea> = emptySet(),
        explosive: Boolean = false,
        min: Experience = Experience.NEW,
        rest: Int? = null,
    ) = CatalogExercise(
        name = name,
        primaryMuscle = muscle,
        equipment = equipment,
        compound = compound,
        secondaryMuscles = secondary.split(',').map { it.trim() }.filter { it.isNotEmpty() },
        isBodyweight = bodyweight,
        loads = loads,
        explosive = explosive,
        minExperience = min,
        defaultRestSec = rest ?: if (compound) 150 else 75,
    )

    private val S = BodyArea.SHOULDER
    private val E = BodyArea.ELBOW
    private val W = BodyArea.WRIST
    private val L = BodyArea.LOWER_BACK
    private val K = BodyArea.KNEE
    private val H = BodyArea.HIP

    /** Order matters: within a muscle, earlier entries are preferred. */
    val ALL: List<CatalogExercise> = listOf(
        // Chest
        ex("Bench press", "chest", Equipment.BARBELL, true, "triceps,shoulders", loads = setOf(S, W)),
        ex("Incline dumbbell press", "chest", Equipment.DUMBBELL, true, "shoulders,triceps", loads = setOf(S)),
        ex("Dumbbell bench press", "chest", Equipment.DUMBBELL, true, "triceps,shoulders", loads = setOf(S)),
        ex("Machine chest press", "chest", Equipment.MACHINE, true, "triceps,shoulders", loads = setOf(S)),
        ex("Incline bench press", "chest", Equipment.BARBELL, true, "shoulders,triceps", loads = setOf(S, W)),
        ex("Push-up", "chest", Equipment.BODYWEIGHT, true, "triceps,shoulders", loads = setOf(S, W)),
        ex("Feet-elevated push-up", "chest", Equipment.BODYWEIGHT, true, "shoulders,triceps", loads = setOf(S, W)),
        ex("Chest dip", "chest", Equipment.BODYWEIGHT, true, "triceps,shoulders", loads = setOf(S, E), min = Experience.SOME),
        ex("Cable fly", "chest", Equipment.CABLE, false, loads = setOf(S)),
        ex("Pec deck", "chest", Equipment.MACHINE, false, loads = setOf(S)),
        ex("Dumbbell fly", "chest", Equipment.DUMBBELL, false, loads = setOf(S)),
        // Back
        ex("Lat pulldown", "back", Equipment.CABLE, true, "biceps"),
        ex("Barbell row", "back", Equipment.BARBELL, true, "biceps,traps", loads = setOf(L)),
        ex("Pull-up", "back", Equipment.BODYWEIGHT, true, "biceps", loads = setOf(S, E)),
        ex("Seated cable row", "back", Equipment.CABLE, true, "biceps"),
        ex("One-arm dumbbell row", "back", Equipment.DUMBBELL, true, "biceps"),
        ex("Chest-supported row", "back", Equipment.MACHINE, true, "biceps"),
        ex("Chin-up", "back", Equipment.BODYWEIGHT, true, "biceps", loads = setOf(E)),
        ex("Inverted row", "back", Equipment.BODYWEIGHT, true, "biceps"),
        ex("Deadlift", "back", Equipment.BARBELL, true, "hamstrings,glutes", loads = setOf(L, H), rest = 180),
        ex("Straight-arm pulldown", "back", Equipment.CABLE, false),
        ex("Dumbbell pullover", "back", Equipment.DUMBBELL, false, "chest", loads = setOf(S)),
        // Shoulders
        ex("Overhead press", "shoulders", Equipment.BARBELL, true, "triceps", loads = setOf(S, L)),
        ex("Dumbbell shoulder press", "shoulders", Equipment.DUMBBELL, true, "triceps", loads = setOf(S)),
        ex("Machine shoulder press", "shoulders", Equipment.MACHINE, true, "triceps", loads = setOf(S)),
        ex("Pike push-up", "shoulders", Equipment.BODYWEIGHT, true, "triceps", loads = setOf(S, W)),
        ex("Lateral raise", "shoulders", Equipment.DUMBBELL, false),
        ex("Cable lateral raise", "shoulders", Equipment.CABLE, false),
        ex("Machine lateral raise", "shoulders", Equipment.MACHINE, false),
        ex("Face pull", "shoulders", Equipment.CABLE, false, "traps"),
        ex("Rear delt fly", "shoulders", Equipment.DUMBBELL, false, "back"),
        ex("Reverse pec deck", "shoulders", Equipment.MACHINE, false, "back"),
        // Quads
        ex("Back squat", "quads", Equipment.BARBELL, true, "glutes,hamstrings", loads = setOf(K, L, H), rest = 180),
        ex("Leg press", "quads", Equipment.MACHINE, true, "glutes", loads = setOf(K)),
        ex("Goblet squat", "quads", Equipment.DUMBBELL, true, "glutes", loads = setOf(K)),
        ex("Hack squat", "quads", Equipment.MACHINE, true, "glutes", loads = setOf(K)),
        ex("Front squat", "quads", Equipment.BARBELL, true, "glutes", loads = setOf(K, W), min = Experience.SOME, rest = 180),
        ex("Bulgarian split squat", "quads", Equipment.DUMBBELL, true, "glutes", loads = setOf(K, H)),
        ex("Walking lunge", "quads", Equipment.DUMBBELL, true, "glutes,hamstrings", loads = setOf(K, H)),
        ex("Step-up", "quads", Equipment.DUMBBELL, true, "glutes", loads = setOf(K)),
        ex("Bodyweight squat", "quads", Equipment.BODYWEIGHT, true, "glutes", loads = setOf(K)),
        ex("Reverse lunge", "quads", Equipment.BODYWEIGHT, true, "glutes", loads = setOf(K)),
        ex("Bodyweight split squat", "quads", Equipment.BODYWEIGHT, true, "glutes", loads = setOf(K)),
        ex("Leg extension", "quads", Equipment.MACHINE, false, loads = setOf(K)),
        // Hamstrings
        ex("Romanian deadlift", "hamstrings", Equipment.BARBELL, true, "glutes,back", loads = setOf(L, H)),
        ex("Dumbbell Romanian deadlift", "hamstrings", Equipment.DUMBBELL, true, "glutes", loads = setOf(L, H)),
        ex("Single-leg Romanian deadlift", "hamstrings", Equipment.BODYWEIGHT, true, "glutes", loads = setOf(H)),
        ex("Good morning", "hamstrings", Equipment.BARBELL, true, "glutes,back", loads = setOf(L, H), min = Experience.SOME),
        ex("Leg curl", "hamstrings", Equipment.MACHINE, false, loads = setOf(K)),
        ex("Seated leg curl", "hamstrings", Equipment.MACHINE, false),
        ex("Cable pull-through", "hamstrings", Equipment.CABLE, true, "glutes", loads = setOf(H)),
        ex("Nordic curl", "hamstrings", Equipment.BODYWEIGHT, false, loads = setOf(K), min = Experience.SOME),
        // Glutes
        ex("Hip thrust", "glutes", Equipment.BARBELL, true, "hamstrings", loads = setOf(H)),
        ex("Dumbbell glute bridge", "glutes", Equipment.DUMBBELL, true, "hamstrings"),
        ex("Glute bridge", "glutes", Equipment.BODYWEIGHT, true, "hamstrings"),
        ex("Single-leg glute bridge", "glutes", Equipment.BODYWEIGHT, true, "hamstrings"),
        ex("Cable kickback", "glutes", Equipment.CABLE, false),
        ex("Hip abduction machine", "glutes", Equipment.MACHINE, false),
        // Biceps
        ex("Barbell curl", "biceps", Equipment.BARBELL, false, "forearms", loads = setOf(E, W)),
        ex("Hammer curl", "biceps", Equipment.DUMBBELL, false, "forearms"),
        ex("Dumbbell curl", "biceps", Equipment.DUMBBELL, false, "forearms"),
        ex("Incline dumbbell curl", "biceps", Equipment.DUMBBELL, false),
        ex("Cable curl", "biceps", Equipment.CABLE, false),
        ex("Preacher curl", "biceps", Equipment.MACHINE, false, loads = setOf(E)),
        ex("Close-grip chin-up", "biceps", Equipment.BODYWEIGHT, true, "back", loads = setOf(E)),
        // Triceps
        ex("Triceps pushdown", "triceps", Equipment.CABLE, false),
        ex("Overhead triceps extension", "triceps", Equipment.DUMBBELL, false, loads = setOf(E, S)),
        ex("Cable overhead triceps extension", "triceps", Equipment.CABLE, false, loads = setOf(E)),
        ex("Skull crusher", "triceps", Equipment.BARBELL, false, loads = setOf(E, W)),
        ex("Close-grip bench press", "triceps", Equipment.BARBELL, true, "chest,shoulders", loads = setOf(S, W)),
        ex("Dumbbell kickback", "triceps", Equipment.DUMBBELL, false),
        ex("Diamond push-up", "triceps", Equipment.BODYWEIGHT, true, "chest", loads = setOf(S, W, E)),
        ex("Bench dip", "triceps", Equipment.BODYWEIGHT, true, "chest", loads = setOf(S, E)),
        // Calves
        ex("Calf raise", "calves", Equipment.MACHINE, false),
        ex("Seated calf raise", "calves", Equipment.MACHINE, false),
        ex("Dumbbell calf raise", "calves", Equipment.DUMBBELL, false),
        ex("Single-leg calf raise", "calves", Equipment.BODYWEIGHT, false),
        // Core
        ex("Plank", "core", Equipment.BODYWEIGHT, false, loads = setOf(W)),
        ex("Hanging leg raise", "core", Equipment.BODYWEIGHT, false, loads = setOf(S)),
        ex("Cable crunch", "core", Equipment.CABLE, false),
        ex("Dead bug", "core", Equipment.BODYWEIGHT, false),
        ex("Crunch", "core", Equipment.BODYWEIGHT, false),
        ex("Pallof press", "core", Equipment.CABLE, false),
        ex("Ab wheel rollout", "core", Equipment.BODYWEIGHT, false, loads = setOf(S, L), min = Experience.SOME),
        // Other
        ex("Barbell shrug", "traps", Equipment.BARBELL, false),
        ex("Dumbbell shrug", "traps", Equipment.DUMBBELL, false),
        ex("Wrist curl", "forearms", Equipment.DUMBBELL, false, loads = setOf(W)),
        ex("Farmer's carry", "forearms", Equipment.DUMBBELL, true, "traps,core"),
        // Explosive work (athletic goal). Counted as power work, not as weekly hard sets.
        ex("Jump squat", "quads", Equipment.BODYWEIGHT, true, "glutes", loads = setOf(K), explosive = true, rest = 90),
        ex("Box jump", "quads", Equipment.BODYWEIGHT, true, "glutes", loads = setOf(K), explosive = true, rest = 90),
        ex("Broad jump", "quads", Equipment.BODYWEIGHT, true, "glutes,hamstrings", loads = setOf(K, H), explosive = true, rest = 90),
        ex("Dumbbell swing", "hamstrings", Equipment.DUMBBELL, true, "glutes", loads = setOf(L, H), explosive = true, rest = 90),
        ex("Power clean", "back", Equipment.BARBELL, true, "quads,traps", loads = setOf(L, W, S), explosive = true, min = Experience.EXPERIENCED, rest = 120),
        ex("Clap push-up", "chest", Equipment.BODYWEIGHT, true, "triceps", loads = setOf(S, W), explosive = true, min = Experience.SOME, rest = 90),
    ).also { list ->
        val names = list.map { it.name.lowercase() }
        require(names.size == names.toSet().size) { "Duplicate exercise name in the catalog" }
    }

    private val byId: Map<String, CatalogExercise> by lazy { ALL.associateBy { it.id } }

    fun byId(id: String): CatalogExercise? = byId[id]
    fun byName(name: String): CatalogExercise? = ALL.firstOrNull { it.name.equals(name, ignoreCase = true) }
    fun requireByName(name: String): CatalogExercise =
        byName(name) ?: error("No catalog exercise named \"$name\"")
}
