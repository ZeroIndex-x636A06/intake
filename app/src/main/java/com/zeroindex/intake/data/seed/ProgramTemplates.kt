package com.zeroindex.intake.data.seed

import com.zeroindex.intake.domain.model.ProgressionType
import com.zeroindex.intake.domain.training.generator.ExerciseCatalog
import com.zeroindex.intake.domain.training.generator.GeneratedDay
import com.zeroindex.intake.domain.training.generator.GeneratedExercise
import com.zeroindex.intake.domain.training.generator.GeneratedProgram

/**
 * Starting points a person can copy. A template is never a program: it lives in code, and picking
 * one writes a new, ordinary, editable program (see ProgramBlueprintBuilder). Nothing here is
 * ever the active program, and editing the copy never touches the template.
 */
data class ProgramTemplate(
    val id: String,
    val title: String,
    val summary: String,
    val level: String,
    val blueprint: GeneratedProgram,
) {
    val dayCount: Int get() = blueprint.days.size
}

object ProgramTemplates {

    private class Row(val name: String, val sets: Int, val repsMin: Int, val repsMax: Int)

    private fun r(name: String, sets: Int, repsMin: Int, repsMax: Int = repsMin) = Row(name, sets, repsMin, repsMax)

    private fun day(name: String, vararg rows: Row): GeneratedDay = GeneratedDay(
        name = name,
        exercises = rows.map { row ->
            val ex = ExerciseCatalog.requireByName(row.name)
            val timed = ex.name == "Plank"
            GeneratedExercise(
                exerciseId = ex.id,
                name = ex.name,
                muscle = ex.primaryMuscle,
                sets = row.sets,
                repsMin = if (timed) 30 else row.repsMin,
                repsMax = if (timed) 60 else row.repsMax,
                rpe = null,
                restSec = if (ex.compound) 150 else 75,
                progression = when {
                    timed -> null
                    ex.compound && !ex.isBodyweight -> ProgressionType.LINEAR
                    else -> ProgressionType.DOUBLE_PROGRESSION
                },
                why = "",
            )
        },
        estimatedMinutes = 0,
        why = "",
    )

    private fun program(name: String, description: String, vararg days: GeneratedDay) = GeneratedProgram(
        name = name,
        description = description,
        days = days.toList(),
        weeklySets = emptyMap(),
        targetSets = emptyMap(),
        deloadEveryWeeks = 6,
        whys = emptyList(),
        warnings = emptyList(),
    )

    val ALL: List<ProgramTemplate> = listOf(
        ProgramTemplate(
            id = "upper-lower-4",
            title = "Upper / lower, four days",
            summary = "Heavy compounds, then accessories. Each muscle twice a week.",
            level = "Some experience",
            blueprint = program(
                "Upper / lower, four days",
                "Compound lifts on linear progression, accessories on double progression. A starting point, not a prescription.",
                day(
                    "Lower A",
                    r("Back squat", 3, 5), r("Romanian deadlift", 3, 8, 10), r("Leg curl", 3, 10, 12),
                    r("Calf raise", 4, 10, 15), r("Hanging leg raise", 3, 8, 12),
                ),
                day(
                    "Upper push",
                    r("Bench press", 3, 5), r("Overhead press", 3, 8, 10), r("Incline dumbbell press", 3, 10, 12),
                    r("Lateral raise", 3, 12, 15), r("Triceps pushdown", 3, 10, 12),
                ),
                day(
                    "Lower B",
                    r("Deadlift", 3, 5), r("Front squat", 3, 6, 8), r("Leg press", 3, 10, 12),
                    r("Calf raise", 4, 10, 15), r("Plank", 3, 1),
                ),
                day(
                    "Upper pull",
                    r("Barbell row", 3, 6, 8), r("Pull-up", 3, 6, 10), r("Lat pulldown", 3, 10, 12),
                    r("Face pull", 3, 12, 15), r("Barbell curl", 3, 10, 12),
                ),
            ),
        ),
        ProgramTemplate(
            id = "full-body-3",
            title = "Full body, three days",
            summary = "Three simple sessions a week. A good first program.",
            level = "New",
            blueprint = program(
                "Full body, three days",
                "Three full-body sessions. Add weight when you hit every rep.",
                day("Day A", r("Back squat", 3, 5), r("Bench press", 3, 5), r("Barbell row", 3, 8), r("Triceps pushdown", 2, 10, 12), r("Plank", 3, 1)),
                day("Day B", r("Romanian deadlift", 3, 8), r("Overhead press", 3, 6, 8), r("Lat pulldown", 3, 8, 10), r("Leg press", 2, 10, 12), r("Barbell curl", 2, 10, 12)),
                day("Day C", r("Leg press", 3, 8, 10), r("Incline dumbbell press", 3, 8, 10), r("Seated cable row", 3, 8, 10), r("Leg curl", 3, 10, 12), r("Lateral raise", 3, 12, 15)),
            ),
        ),
        ProgramTemplate(
            id = "ppl-3",
            title = "Push / pull / legs",
            summary = "One push, one pull, one legs day. Run it twice for six days.",
            level = "Some experience",
            blueprint = program(
                "Push / pull / legs",
                "Each day trains one group of muscles. Double progression on the accessories.",
                day("Push", r("Bench press", 4, 6, 8), r("Overhead press", 3, 6, 10), r("Incline dumbbell press", 3, 8, 12), r("Lateral raise", 4, 12, 15), r("Triceps pushdown", 3, 10, 12)),
                day("Pull", r("Barbell row", 4, 6, 8), r("Pull-up", 3, 6, 10), r("Seated cable row", 3, 8, 12), r("Face pull", 3, 12, 15), r("Barbell curl", 3, 8, 12), r("Hammer curl", 2, 10, 12)),
                day("Legs", r("Back squat", 4, 5, 8), r("Romanian deadlift", 3, 8, 10), r("Leg press", 3, 10, 12), r("Leg curl", 3, 10, 12), r("Calf raise", 4, 10, 15)),
            ),
        ),
        ProgramTemplate(
            id = "home-dumbbells-3",
            title = "Home dumbbells, three days",
            summary = "Needs only a pair of dumbbells and a bench or chair.",
            level = "New",
            blueprint = program(
                "Home dumbbells, three days",
                "Three full-body sessions with dumbbells. Work up the rep range, then use heavier dumbbells.",
                day("Day A", r("Goblet squat", 3, 8, 12), r("Dumbbell bench press", 3, 8, 12), r("One-arm dumbbell row", 3, 8, 12), r("Lateral raise", 2, 12, 15), r("Hammer curl", 2, 10, 12)),
                day("Day B", r("Dumbbell Romanian deadlift", 3, 8, 12), r("Dumbbell shoulder press", 3, 8, 12), r("One-arm dumbbell row", 3, 8, 12), r("Walking lunge", 2, 10, 12), r("Overhead triceps extension", 2, 10, 12)),
                day("Day C", r("Bulgarian split squat", 3, 8, 12), r("Incline dumbbell press", 3, 8, 12), r("Dumbbell pullover", 3, 10, 12), r("Dumbbell glute bridge", 3, 10, 15), r("Dumbbell curl", 2, 10, 12)),
            ),
        ),
        ProgramTemplate(
            id = "bodyweight-3",
            title = "Bodyweight, three days",
            summary = "No equipment beyond a pull-up bar. Add reps, then make it harder.",
            level = "New",
            blueprint = program(
                "Bodyweight, three days",
                "Three full-body sessions with no weights. Add reps each week, then move to the harder variation.",
                day("Day A", r("Push-up", 3, 8, 15), r("Bodyweight squat", 3, 12, 20), r("Inverted row", 3, 8, 15), r("Glute bridge", 3, 12, 20), r("Plank", 3, 1)),
                day("Day B", r("Pike push-up", 3, 6, 12), r("Reverse lunge", 3, 10, 15), r("Chin-up", 3, 3, 8), r("Single-leg glute bridge", 3, 10, 15), r("Dead bug", 3, 8, 12)),
                day("Day C", r("Feet-elevated push-up", 3, 8, 15), r("Bodyweight split squat", 3, 8, 12), r("Pull-up", 3, 3, 8), r("Single-leg calf raise", 3, 12, 20), r("Crunch", 3, 12, 20)),
            ),
        ),
    )

    fun byId(id: String): ProgramTemplate? = ALL.firstOrNull { it.id == id }
}
