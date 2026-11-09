package com.zeroindex.intake.domain.training

import com.zeroindex.intake.domain.goals.Sex
import com.zeroindex.intake.domain.training.generator.CatalogExercise
import com.zeroindex.intake.domain.training.generator.Equipment
import com.zeroindex.intake.domain.training.generator.ExerciseCatalog
import com.zeroindex.intake.domain.training.generator.Experience
import com.zeroindex.intake.domain.training.generator.GeneratedProgram
import kotlin.math.floor
import kotlin.math.roundToInt

/** One working set from a past session. */
data class PastSet(val weightKg: Double, val reps: Int)

/** Where a starting weight came from, so the screen can say so. */
enum class StartSource { SAME_LIFT, RELATED_LIFT, BODYWEIGHT_GUESS }

data class StartingWeightEstimate(
    val weightKg: Double,
    val source: StartSource,
    /** One plain sentence on where the number comes from. */
    val basis: String,
) {
    /** The note stored with the exercise and shown on the workout screen. */
    val note: String get() = "$LABEL. $basis"

    companion object {
        const val LABEL = "Start light; adjusts after your first session"
    }
}

/** What the estimator knows about the person and their gym. */
data class StartingWeightContext(
    val experience: Experience,
    val bodyweightKg: Double?,
    val sex: Sex?,
    val barWeightKg: Double = 20.0,
    /** Plates on one side, kg (the Settings list). */
    val platesKg: List<Double> = listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25),
    /** Smallest jump on dumbbells, machines and cables. */
    val smallStepKg: Double = 2.5,
)

/**
 * Starting weights for a new program (SPEC-v3 "Engines"). Pure Kotlin, deterministic, no AI. The same
 * rules every time:
 *
 * 1. **The same lift has history.** Take the best estimated max from the last session
 *    ([OneRepMax.epley]), work out the weight for the new rep range, and start at 87.5% of it (the
 *    middle of 85 to 90%). Early sessions are for finding the groove, not for testing.
 * 2. **A related lift has history** (a dumbbell press when the person has benched). The same sum,
 *    scaled by how heavy the two lifts usually are against each other (see [FAMILIES]).
 * 3. **No history at all, but one of the key lifts:** a guess from body weight and experience,
 *    deliberately low, for 8 to 10 reps.
 * 4. **Anything else** gets no number; the app says "pick a weight" and remembers what you do.
 *
 * The result is rounded **down** to something loadable: with the plates you have for a barbell
 * (never below the empty bar), in 2.5 kg steps otherwise.
 */
object StartingWeightEstimator {

    /** The middle of "about 85 to 90%". */
    const val START_FRACTION = 0.875

    /**
     * Lifts that are roughly comparable. Each member's number is how heavy its working weight usually
     * is against the family's reference lift at the same reps (a dumbbell press is counted per hand).
     * These are rules of thumb, not measurements: they are only used to start someone light.
     */
    private class Family(val reference: String, val members: Map<String, Double>, val bodyweightFraction: Map<Experience, Double>)

    private val FAMILIES: List<Family> = listOf(
        Family(
            "Bench press",
            mapOf(
                "Bench press" to 1.0, "Incline bench press" to 0.8, "Close-grip bench press" to 0.85,
                "Dumbbell bench press" to 0.38, "Incline dumbbell press" to 0.32, "Machine chest press" to 0.8,
            ),
            mapOf(Experience.NEW to 0.40, Experience.SOME to 0.65, Experience.EXPERIENCED to 0.85),
        ),
        Family(
            "Overhead press",
            mapOf("Overhead press" to 1.0, "Dumbbell shoulder press" to 0.38, "Machine shoulder press" to 0.9),
            mapOf(Experience.NEW to 0.25, Experience.SOME to 0.40, Experience.EXPERIENCED to 0.55),
        ),
        Family(
            "Back squat",
            mapOf(
                "Back squat" to 1.0, "Front squat" to 0.8, "Hack squat" to 0.9, "Leg press" to 1.8,
                "Goblet squat" to 0.3, "Walking lunge" to 0.2, "Step-up" to 0.2, "Bulgarian split squat" to 0.2,
            ),
            mapOf(Experience.NEW to 0.50, Experience.SOME to 0.80, Experience.EXPERIENCED to 1.10),
        ),
        Family(
            "Deadlift",
            mapOf("Deadlift" to 1.0, "Romanian deadlift" to 0.75, "Dumbbell Romanian deadlift" to 0.3, "Good morning" to 0.4),
            mapOf(Experience.NEW to 0.60, Experience.SOME to 1.00, Experience.EXPERIENCED to 1.35),
        ),
        Family(
            "Barbell row",
            mapOf(
                "Barbell row" to 1.0, "Seated cable row" to 0.9, "One-arm dumbbell row" to 0.38,
                "Chest-supported row" to 0.9,
            ),
            mapOf(Experience.NEW to 0.35, Experience.SOME to 0.55, Experience.EXPERIENCED to 0.75),
        ),
        Family(
            "Lat pulldown",
            mapOf("Lat pulldown" to 1.0),
            mapOf(Experience.NEW to 0.40, Experience.SOME to 0.60, Experience.EXPERIENCED to 0.80),
        ),
        Family(
            "Hip thrust",
            mapOf("Hip thrust" to 1.0, "Dumbbell glute bridge" to 0.5),
            mapOf(Experience.NEW to 0.50, Experience.SOME to 0.85, Experience.EXPERIENCED to 1.20),
        ),
    )

    /** Women lift less in absolute terms at the same experience; the guess is scaled, not the maths. */
    private const val FEMALE_UPPER = 0.62
    private const val FEMALE_LOWER = 0.80

    private fun familyOf(name: String): Pair<Family, Double>? =
        FAMILIES.firstNotNullOfOrNull { f -> f.members[name]?.let { f to it } }

    /** The weight (kg) a person who can do [weightKg] for [reps] could do for [targetReps]. */
    fun equivalentWeight(weightKg: Double, reps: Int, targetReps: Int): Double {
        val e1rm = OneRepMax.epley(weightKg, reps.coerceAtLeast(1))
        return e1rm / (1.0 + targetReps.coerceAtLeast(1) / 30.0)
    }

    /**
     * @param lastSets the working sets of the most recent session that trained an exercise id (empty if none)
     */
    fun estimate(
        exercise: CatalogExercise,
        repsMin: Int,
        repsMax: Int,
        context: StartingWeightContext,
        lastSets: (exerciseId: String) -> List<PastSet>,
    ): StartingWeightEstimate? {
        if (exercise.isBodyweight || exercise.explosive || exercise.name == "Plank") return null
        val targetReps = ((repsMin + repsMax) / 2).coerceAtLeast(1)
        val family = familyOf(exercise.name)

        // 1. The same lift.
        bestSet(lastSets(exercise.id))?.let { (w, reps) ->
            val start = equivalentWeight(w, reps, targetReps) * START_FRACTION
            val rounded = roundDown(start, exercise, context) ?: return null
            return StartingWeightEstimate(
                rounded, StartSource.SAME_LIFT,
                "Based on your last ${exercise.name}: ${fmt(w)} kg for $reps.",
            )
        }

        // 2. A related lift.
        if (family != null) {
            val (fam, factor) = family
            val related = fam.members.entries
                .filter { it.key != exercise.name }
                .mapNotNull { (name, otherFactor) ->
                    val other = ExerciseCatalog.byName(name) ?: return@mapNotNull null
                    val set = bestSet(lastSets(other.id)) ?: return@mapNotNull null
                    Triple(other, set, otherFactor)
                }
                .maxByOrNull { (_, set, _) -> OneRepMax.epley(set.first, set.second) }
            if (related != null) {
                val (other, set, otherFactor) = related
                val same = equivalentWeight(set.first, set.second, targetReps)
                val start = same * (factor / otherFactor) * START_FRACTION
                val rounded = roundDown(start, exercise, context) ?: return null
                return StartingWeightEstimate(
                    rounded, StartSource.RELATED_LIFT,
                    "Based on your ${other.name} (${fmt(set.first)} kg for ${set.second}), which is a similar lift.",
                )
            }

            // 3. A key lift with no history: a guess from body weight.
            val bw = context.bodyweightKg?.takeIf { it > 0 }
            if (bw != null) {
                val fraction = fam.bodyweightFraction.getValue(context.experience)
                val sexScale = if (context.sex == Sex.FEMALE) {
                    if (exercise.primaryMuscle in LOWER_BODY) FEMALE_LOWER else FEMALE_UPPER
                } else 1.0
                // The table is for about 10 reps; move it to this rep range.
                val atTen = bw * fraction * sexScale * factor
                val start = equivalentWeight(atTen, 10, targetReps)
                val rounded = roundDown(start, exercise, context) ?: return null
                return StartingWeightEstimate(
                    rounded, StartSource.BODYWEIGHT_GUESS,
                    "A careful guess from your body weight and ${context.experience.label.lowercase()} experience.",
                )
            }
        }
        return null
    }

    /** Every catalog exercise id whose history could feed an estimate for [exerciseId]: itself and its family. */
    fun relevantIds(exerciseId: String): Set<String> {
        val ex = ExerciseCatalog.byId(exerciseId) ?: return setOf(exerciseId)
        val family = familyOf(ex.name)?.first ?: return setOf(exerciseId)
        return family.members.keys.mapNotNull { ExerciseCatalog.byName(it)?.id }.toSet() + exerciseId
    }

    /** Fills in a starting weight on every exercise of [program] that has a rule for one. */
    fun apply(
        program: GeneratedProgram,
        context: StartingWeightContext,
        lastSets: (exerciseId: String) -> List<PastSet>,
    ): GeneratedProgram = program.copy(
        days = program.days.map { day ->
            day.copy(
                exercises = day.exercises.map { e ->
                    if (e.startWeightKg != null) return@map e
                    val catalog = ExerciseCatalog.byId(e.exerciseId) ?: return@map e
                    val est = estimate(catalog, e.repsMin, e.repsMax, context, lastSets) ?: return@map e
                    e.copy(startWeightKg = est.weightKg, startNote = est.note)
                },
            )
        },
    )

    private val LOWER_BODY = setOf("quads", "hamstrings", "glutes", "calves")

    private fun bestSet(sets: List<PastSet>): Pair<Double, Int>? =
        sets.filter { it.weightKg > 0 && it.reps >= 1 }
            .maxByOrNull { OneRepMax.epley(it.weightKg, it.reps) }
            ?.let { it.weightKg to it.reps }

    /**
     * Rounds down to a load that exists. Barbell lifts use the plates and bar given (the empty bar is the
     * floor, so a very light estimate becomes "start with the bar"); everything else moves in
     * [StartingWeightContext.smallStepKg] steps. Null if the estimate is below the smallest step.
     */
    fun roundDown(kg: Double, exercise: CatalogExercise, context: StartingWeightContext): Double? {
        if (kg <= 0.0) return null
        if (exercise.equipment == Equipment.BARBELL) {
            val bar = context.barWeightKg
            if (kg <= bar) return bar
            val load = PlateCalculator.calculate(kg, bar, context.platesKg)
            return load.totalKg
        }
        val step = context.smallStepKg.coerceAtLeast(0.5)
        val steps = floor(kg / step).toInt()
        return if (steps < 1) null else steps * step
    }

    private fun fmt(v: Double): String = if (v == v.roundToInt().toDouble()) v.roundToInt().toString() else String.format(java.util.Locale.ROOT, "%.1f", v)
}
