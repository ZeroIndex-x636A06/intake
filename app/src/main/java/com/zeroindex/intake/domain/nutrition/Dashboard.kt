package com.zeroindex.intake.domain.nutrition

import kotlin.math.roundToInt

/**
 * One bar on the Nutrition dashboard. [value] is null for "not listed" (every logged food lacked it), which the screen
 * writes as "not listed" and never as 0. [target] is a personal target (calories, protein, carbs, fat) or, when
 * [reference] is true, a common reference limit (fibre, saturated fat, salt) the card labels as such.
 */
data class DashRow(
    val id: String,
    val label: String,
    val value: Double?,
    val unit: String,
    val target: Double?,
    val reference: Boolean,
    /** "2 foods have no fibre value" when part of the total is unknown, else null. */
    val note: String?,
) {
    val listed: Boolean get() = value != null

    /** 0..1 for the bar; an unknown or target-less row is 0. */
    val fraction: Float
        get() {
            val v = value ?: return 0f
            val t = target ?: return 0f
            return if (t <= 0.0) 0f else (v / t).coerceIn(0.0, 1.0).toFloat()
        }
}

/** The calorie ring: eaten against the target, and what is left. */
data class CalorieRing(val eaten: Int, val target: Int?, val left: Int?, val over: Boolean, val fraction: Float)

data class DashboardModel(
    val calories: CalorieRing,
    val macros: List<DashRow>,
    val others: List<DashRow>,
    /** Whole-card notes: one line per nutrient whose total is only a floor. */
    val notes: List<String>,
    val entries: Int,
)

/** Builds the dashboard's rows from the day's aggregate; pure, so "unknown is not zero" is unit tested. */
object NutritionDashboard {

    fun build(day: DayNutrition, target: MacroTarget?, saltInsteadOfSodium: Boolean): DashboardModel {
        val eaten = day.kcal.roundToInt()
        val left = target?.let { it.kcal - eaten }
        val ring = CalorieRing(
            eaten = eaten,
            target = target?.kcal,
            left = left,
            over = target != null && eaten > target.kcal,
            fraction = if (target == null || target.kcal <= 0) 0f else (day.kcal / target.kcal).coerceIn(0.0, 1.0).toFloat(),
        )
        val macros = listOf(
            DashRow("protein", "Protein", day.protein, "g", target?.proteinG?.toDouble(), false, null),
            DashRow("carbs", "Carbs", day.carbs, "g", target?.carbsG?.toDouble(), false, null),
            DashRow("fat", "Fat", day.fat, "g", target?.fatG?.toDouble(), false, null),
        )
        val sodiumRow = if (saltInsteadOfSodium) {
            DashRow("salt", "Salt", day.saltGrams, "g", ReferenceLimits.SALT_G, true, day.sodium.note?.replace("sodium", "salt"))
        } else {
            DashRow("sodium", "Sodium", day.sodium.value, "mg", ReferenceLimits.SODIUM_MG, true, day.sodium.note)
        }
        val others = listOf(
            DashRow("fiber", "Fibre", day.fiber.value, "g", ReferenceLimits.FIBER_G, true, day.fiber.note),
            DashRow("satfat", "Sat fat", day.saturatedFat.value, "g", ReferenceLimits.SATURATED_FAT_G, true, day.saturatedFat.note),
            DashRow("sugar", "Sugar", day.sugar.value, "g", null, false, day.sugar.note),
            sodiumRow,
        )
        return DashboardModel(ring, macros, others, others.mapNotNull { it.note }, day.entries)
    }

    /** "19 g", "1,640 mg" or "not listed". */
    fun valueText(row: DashRow): String {
        val v = row.value ?: return "not listed"
        val n = if (row.unit == "g" && v < 10.0 && v != Math.floor(v)) {
            "%.1f".format(java.util.Locale.US, v)
        } else {
            "%,d".format(java.util.Locale.US, v.roundToInt())
        }
        return "$n ${row.unit}"
    }
}
