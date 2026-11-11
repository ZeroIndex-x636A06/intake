package com.zeroindex.intake.domain.training

import com.zeroindex.intake.domain.training.generator.ExerciseCatalog

/**
 * "Know your numbers?" in the program generator. Someone who has trained before can say what they lift on the four
 * key barbell lifts (a weight for some reps, not a tested max). Those answers stand in for history, so the starting
 * weights of the new program, and of the lifts related to these four, come from what the person can do and not from a
 * guess off their body weight. Real logged sets always win over an answer here.
 */
object KnownLifts {
    val KEY_LIFTS = listOf("Bench press", "Back squat", "Deadlift", "Overhead press")
    const val MAX_REPS = 30

    /** One answer: a weight in kg for some reps. Null when either part is missing or out of range. */
    fun entry(weightKg: Double?, reps: Int?): PastSet? {
        val kg = weightKg?.takeIf { it > 0.0 && it < 1000.0 } ?: return null
        // A weight with no reps is read as a set of five, the usual "what do you lift" answer.
        val r = (reps ?: 5).takeIf { it in 1..MAX_REPS } ?: return null
        return PastSet(kg, r)
    }

    /** The answers keyed by exercise id, as the estimator's history is. Unknown names and empty answers are dropped. */
    fun asHistory(answers: Map<String, PastSet?>): Map<String, List<PastSet>> =
        answers.mapNotNull { (name, set) ->
            val id = ExerciseCatalog.byName(name)?.id ?: return@mapNotNull null
            set?.let { id to listOf(it) }
        }.toMap()
}
