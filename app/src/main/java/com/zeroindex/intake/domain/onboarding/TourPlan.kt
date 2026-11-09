package com.zeroindex.intake.domain.onboarding

/** Ids shared by the screens that mark a target and the steps that point at it. */
object TourIds {
    const val RING = "ring"
    const val ADD_FOOD = "add-food"
    const val WATER = "water"
    const val WORKOUT = "workout"
    const val PILL = "pill"
    const val COACH = "coach"
}

data class TourStep(val targetId: String, val text: String)

/** The Today tour, in order. Steps whose target is not on screen are dropped by [TourPlan.filter]. */
object TourSteps {
    val today: List<TourStep> = listOf(
        TourStep(TourIds.RING, "Calories and protein left today. The ring fills as you log."),
        TourStep(TourIds.ADD_FOOD, "Tap here to log a meal. Search, scan a barcode, or pick something you ate before."),
        TourStep(TourIds.WATER, "Tap a drink size to log water. Tap the card for more."),
        TourStep(TourIds.WORKOUT, "Your baseline week or today's workout shows up here."),
        TourStep(TourIds.PILL, "Food, Train, Progress, More. Tap one, or slide your finger along the bar to switch."),
        TourStep(TourIds.COACH, "One thing to do today. A small suggestion that changes with your week."),
    )
}

object TourPlan {
    /** Keeps the steps whose target exists, in their original order. Nothing is added or reordered. */
    fun filter(steps: List<TourStep>, availableTargets: Set<String>): List<TourStep> =
        steps.filter { it.targetId in availableTargets }

    /** The next index, or null when the tour has run out of steps. */
    fun advance(index: Int, size: Int): Int? = (index + 1).takeIf { it < size }

    /** The previous index, never below the first step. */
    fun previous(index: Int): Int = (index - 1).coerceAtLeast(0)

    /** "2 of 5" for the bubble. */
    fun label(index: Int, size: Int): String = "${index + 1} of $size"
}
