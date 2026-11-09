package com.zeroindex.intake.domain.goals

/**
 * The baseline week (SPEC-v3 point 11): seven days of normal life, logged, so the plan can be
 * built from the person's own numbers instead of a formula.
 *
 * The rule is pure so it can be tested to the edge:
 *  - at least [MIN_DAYS] days have passed, counting the start day as day 1;
 *  - at least [MIN_LOGGED_DAYS] of those days are logged. A day counts as logged only with at
 *    least one food entry AND roughly [MIN_LOGGED_KCAL] kcal, so a day where someone logged a
 *    coffee and gave up does not pretend to be a day of eating;
 *  - at least [MIN_WEIGH_IN_DAYS] days carry a weigh-in. Two weigh-ins on one day count once,
 *    because the weight trend needs different days, not more readings of the same morning.
 *
 * Steps, sleep, resting heart rate and workouts are collected and shown but never gate the plan:
 * they only exist for people with a watch, and the app has to work without one.
 */
object BaselineReadiness {

    const val MIN_DAYS = 7
    const val MIN_LOGGED_DAYS = 5
    const val MIN_WEIGH_IN_DAYS = 4

    /** Recommended, not required. */
    const val RECOMMENDED_WEIGH_IN_DAYS = 5

    /** Roughly a day of eating: below this the day is treated as partly logged. */
    const val MIN_LOGGED_KCAL = 800.0

    /** Past this day the baseline is "extended" and the screen says why it is still waiting. */
    const val EXTEND_AFTER_DAY = 10

    /** One day of food in the baseline window. [dayIndex] is days since the epoch in the user's zone. */
    data class FoodDay(val dayIndex: Long, val entryCount: Int, val kcal: Double) {
        val counts: Boolean get() = entryCount >= 1 && kcal >= MIN_LOGGED_KCAL
    }

    enum class Status {
        /** Still inside the first week. */
        COLLECTING,

        /** The rule is met: time to build the plan. */
        READY,

        /** Past day 10 and still short. The wait is extended with a plain explanation. */
        EXTENDED,
    }

    /** One thing still missing, with the reason it matters. */
    data class Missing(val what: String, val why: String)

    data class Progress(
        /** 1-based: the start day is day 1. */
        val dayNumber: Int,
        val loggedDays: Int,
        val weighInDays: Int,
        val status: Status,
        val missing: List<Missing>,
    ) {
        val ready: Boolean get() = status == Status.READY

        /** "Baseline: day 3 of 7", or "day 9" once it runs past the week. */
        val headline: String
            get() = when {
                ready -> "Baseline: ready"
                dayNumber <= MIN_DAYS -> "Baseline: day $dayNumber of $MIN_DAYS"
                else -> "Baseline: day $dayNumber"
            }
    }

    /**
     * @param startDay the local day index the baseline started on
     * @param today the current local day index
     * @param foodDays logged food, one entry per day that has any
     * @param weighInDayIndices the local day index of every weigh-in (repeats are fine)
     */
    fun evaluate(
        startDay: Long,
        today: Long,
        foodDays: List<FoodDay>,
        weighInDayIndices: List<Long>,
    ): Progress {
        val dayNumber = (today - startDay + 1).coerceAtLeast(1L).toInt()
        val window = startDay..today
        val logged = foodDays.filter { it.dayIndex in window && it.counts }.map { it.dayIndex }.distinct().size
        val weighIns = weighInDayIndices.filter { it in window }.distinct().size

        val missing = buildList {
            val daysLeft = MIN_DAYS - dayNumber
            if (daysLeft > 0) {
                add(
                    Missing(
                        what = if (daysLeft == 1) "1 more day" else "$daysLeft more days",
                        why = "A full week averages out the weekday and weekend swings.",
                    ),
                )
            }
            val foodLeft = MIN_LOGGED_DAYS - logged
            if (foodLeft > 0) {
                add(
                    Missing(
                        what = if (foodLeft == 1) "1 more day of food" else "$foodLeft more days of food",
                        why = "Your real average intake is half of working out what you burn. " +
                            "A day counts once you have logged about $MIN_LOGGED_KCAL_TEXT kcal.",
                    ),
                )
            }
            val weighLeft = MIN_WEIGH_IN_DAYS - weighIns
            if (weighLeft > 0) {
                add(
                    Missing(
                        what = if (weighLeft == 1) "1 more weigh-in" else "$weighLeft more weigh-ins",
                        why = "The other half is how your weight moves. Weigh in on different mornings.",
                    ),
                )
            }
        }

        val met = dayNumber >= MIN_DAYS && logged >= MIN_LOGGED_DAYS && weighIns >= MIN_WEIGH_IN_DAYS
        val status = when {
            met -> Status.READY
            dayNumber > EXTEND_AFTER_DAY -> Status.EXTENDED
            else -> Status.COLLECTING
        }
        return Progress(dayNumber, logged, weighIns, status, missing)
    }

    /** Why the baseline is still open past day 10, in plain words. */
    fun extensionExplanation(progress: Progress): String? {
        if (progress.status != Status.EXTENDED) return null
        val short = progress.missing.joinToString(" and ") { it.what }
        return "We are still waiting on $short. Rather than guess, Intake keeps the baseline open " +
            "so your plan is built from real numbers. You can also build a plan now from a formula " +
            "and refine it later."
    }

    private const val MIN_LOGGED_KCAL_TEXT = "800"
}
