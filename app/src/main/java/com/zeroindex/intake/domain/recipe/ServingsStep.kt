package com.zeroindex.intake.domain.recipe

/** The servings stepper on a recipe page: half a serving at a time, never below half a serving. */
object ServingsStep {
    const val STEP = 0.5
    const val MIN = 0.5

    /** One step up or down, kept on the half-serving grid and at or above [MIN]. */
    fun next(current: Double, up: Boolean): Double {
        val moved = if (up) current + STEP else current - STEP
        return maxOf(MIN, Math.round(moved / STEP) * STEP)
    }

    /** "1 serving", "1.5 servings", "2 servings". Matches the label a logged row carries. */
    fun label(servings: Double): String =
        if (servings == 1.0) "1 serving" else "${servings.toString().removeSuffix(".0")} servings"
}
