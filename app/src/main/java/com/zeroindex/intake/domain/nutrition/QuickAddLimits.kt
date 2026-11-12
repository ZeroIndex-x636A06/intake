package com.zeroindex.intake.domain.nutrition

/** One typed-in number of a quick add, with the most one entry may hold. */
enum class QuickAddField(val label: String, val unit: String, val max: Double) {
    KCAL("Calories", "kcal", 5000.0),
    PROTEIN("Protein", "g", 500.0),
    CARBS("Carbs", "g", 500.0),
    FAT("Fat", "g", 500.0),
    FIBRE("Fibre", "g", 300.0),
    SUGAR("Sugar", "g", 300.0),
    SODIUM("Sodium", "mg", 20000.0),
}

/**
 * Per-entry sanity limits for a quick add (SPEC §5.4), so a slipped digit such as 348416 g of protein cannot reach the
 * log. The limits are per entry, not per day. Blank text means "not typed" and counts as zero. Pure Kotlin: the phone's
 * form, its save path and the watch's calorie request all use this.
 */
object QuickAddLimits {
    /** Null when [text] is acceptable; otherwise the message to show under the field. Blank is acceptable. */
    fun problem(field: QuickAddField, text: String): String? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        val value = trimmed.toDoubleOrNull() ?: return NOT_A_NUMBER
        return problem(field, value)
    }

    /** Null when [value] is acceptable; otherwise the message to show. */
    fun problem(field: QuickAddField, value: Double): String? = when {
        !value.isFinite() -> NOT_A_NUMBER
        value < 0.0 -> "Cannot be negative"
        value > field.max -> "Over ${field.max.toLong()} ${field.unit} for one entry"
        else -> null
    }

    /** The first problem among the quick add's numbers, labelled with its field, or null when all are acceptable. */
    fun firstProblem(macros: Macros): String? = listOf(
        QuickAddField.KCAL to macros.kcal,
        QuickAddField.PROTEIN to macros.protein,
        QuickAddField.CARBS to macros.carbs,
        QuickAddField.FAT to macros.fat,
        QuickAddField.FIBRE to macros.fiber,
        QuickAddField.SUGAR to macros.sugar,
        QuickAddField.SODIUM to macros.sodium,
    ).firstNotNullOfOrNull { (field, value) -> problem(field, value)?.let { "${field.label}: $it" } }

    private const val NOT_A_NUMBER = "Enter a number"
}
