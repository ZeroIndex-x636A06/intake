package com.zeroindex.intake.domain.training

import com.zeroindex.intake.domain.script.Reps

/**
 * How a rep target reads on screen. AMRAP is stored as 1 to [Reps.AMRAP_ENTITY_MAX] (see ProgramScript),
 * and the screens say "As many as you can" instead of "1-99". The script keyword stays `amrap`/`max`.
 */
object RepTarget {
    const val AMRAP_LABEL = "As many as you can"

    fun isAmrap(min: Int, max: Int): Boolean = min <= 1 && max >= Reps.AMRAP_ENTITY_MAX

    /** "8", "8–12" or "As many as you can". */
    fun label(min: Int, max: Int): String = when {
        isAmrap(min, max) -> AMRAP_LABEL
        max > min -> "$min–$max"
        else -> "$min"
    }

    /** The short form for tight spots such as the big prescription line: "8", "8–12" or "max". */
    fun short(min: Int, max: Int): String = if (isAmrap(min, max)) "max" else label(min, max)
}

/**
 * Whether a freshly logged set beats what the person already did. [priorSets] is the history in hand
 * (last session plus earlier sets today), so "new best" never claims an all-time record it cannot see.
 */
object NewBest {
    fun isNewBest(weightKg: Double, reps: Int, priorSets: List<SetResult>): Boolean {
        if (weightKg <= 0.0 || reps < 1) return false
        val prior = OneRepMax.best(priorSets) ?: return false
        return OneRepMax.epley(weightKg, reps) > prior + 0.05
    }
}
