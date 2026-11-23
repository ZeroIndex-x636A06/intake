package com.zeroindex.intake.ui.foodtab

import androidx.annotation.StringRes
import com.zeroindex.intake.R

/** The four segments of the Food tab (owner decision 2). [Diary] is the old Nutrition home and the default. */
enum class FoodSegment(@StringRes val labelRes: Int) {
    Diary(R.string.food_segment_diary),
    Plan(R.string.food_segment_plan),
    Recipes(R.string.food_segment_recipes),
    Library(R.string.food_segment_library),
    ;

    companion object {
        val Default: FoodSegment = Diary

        /** Reads a saved index; anything out of range is the default. */
        fun fromIndex(index: Int): FoodSegment = entries.getOrElse(index) { Default }
    }
}
