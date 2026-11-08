package com.zeroindex.intake.domain.training

import com.zeroindex.intake.domain.model.ProgressionType

/** Plain-language names for the progression rules; the builder shows these, not the enum. */
object ProgressionLabels {

    fun title(type: ProgressionType): String = when (type) {
        ProgressionType.LINEAR -> "Add weight when you hit all reps"
        ProgressionType.DOUBLE_PROGRESSION -> "Work up the rep range, then add weight"
        ProgressionType.RPE_BASED -> "Go by effort"
        ProgressionType.PERCENTAGE_1RM -> "% of max"
        ProgressionType.CUSTOM -> "Custom formula"
    }

    fun explanation(type: ProgressionType): String = when (type) {
        ProgressionType.LINEAR ->
            "If every set reaches the lowest rep target, the weight goes up next time. Best for beginners."
        ProgressionType.DOUBLE_PROGRESSION ->
            "Add reps each session at the same weight. When every set reaches the top of the range, the weight goes up."
        ProgressionType.RPE_BASED ->
            "The weight follows how hard the last session felt, so it adjusts to good and bad days."
        ProgressionType.PERCENTAGE_1RM ->
            "The weight is a percentage of your estimated max, which updates as you get stronger."
        ProgressionType.CUSTOM ->
            "Write your own rule. It can use weight, reps, effort and the program week."
    }

    /** The choices shown first; the rest sit behind "Advanced". */
    val SIMPLE: List<ProgressionType> = listOf(
        ProgressionType.LINEAR,
        ProgressionType.DOUBLE_PROGRESSION,
        ProgressionType.RPE_BASED,
    )

    val ADVANCED: List<ProgressionType> = listOf(ProgressionType.PERCENTAGE_1RM, ProgressionType.CUSTOM)

    /** The weight jump stored in a rule's params, if it names one. */
    fun incrementOf(paramsJson: String): Double? = number(paramsJson, "incrementKg")

    fun percentOf(paramsJson: String): Double? = number(paramsJson, "percentageOf1rm")

    /** The custom formula stored in a rule's params, unescaped, or null. */
    fun expressionOf(paramsJson: String): String? =
        Regex("\"expression\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(paramsJson)?.groupValues?.get(1)
            ?.replace("\\\"", "\"")?.replace("\\\\", "\\")

    private fun number(json: String, key: String): Double? =
        Regex("\"$key\"\\s*:\\s*(-?[0-9]+(?:\\.[0-9]+)?)").find(json)?.groupValues?.get(1)?.toDoubleOrNull()

    fun summary(type: ProgressionType, paramsJson: String): String {
        val inc = incrementOf(paramsJson)
        return when (type) {
            ProgressionType.LINEAR, ProgressionType.DOUBLE_PROGRESSION ->
                title(type) + (inc?.let { " (+${ProgressionEvaluator.fmt(it)} kg)" } ?: "")
            ProgressionType.PERCENTAGE_1RM ->
                percentOf(paramsJson)?.let { "${ProgressionEvaluator.fmt(it)}% of max" } ?: title(type)
            else -> title(type)
        }
    }
}
