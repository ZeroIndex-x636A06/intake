package com.zeroindex.intake.ui.screens.more

/**
 * The script docs split by subject: food entry points show only the meal-plan pages, training
 * entry points the program pages, and More > Help the lot. A page decides its topic; a page that
 * belongs to neither (the overview, errors, the AI prompt) is [ALL].
 */
enum class ScriptTopic(val title: String, val pages: List<String>) {
    FOOD("Meal plan scripts", listOf("meal-plan", "examples/meal-plan-sample")),
    TRAIN("Program scripts", listOf("program", "progress-rules", "examples/strength-block")),
    ALL("Script reference", emptyList());

    val firstPage: String get() = pages.firstOrNull() ?: "README"

    companion object {
        fun of(page: String): ScriptTopic = entries.firstOrNull { page in it.pages } ?: ALL
    }
}
