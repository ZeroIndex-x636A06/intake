package com.zeroindex.intake.data.ai.menu

import com.zeroindex.intake.data.db.entity.MenuItemEntity
import com.zeroindex.intake.data.db.entity.worseOf
import com.zeroindex.intake.domain.menu.MenuItemMatcher
import com.zeroindex.intake.domain.menu.NutritionPrompt
import com.zeroindex.intake.domain.menu.StatedAllergens

/** What merging a nutrition menu into an allergen chart produced. */
data class NutritionMergeResult(
    val items: List<MenuItemEntity>,
    /** Chart rows that got nutrition. */
    val matched: Int,
    /** Rows in the chart. */
    val chartTotal: Int,
    /** Nutrition items the chart has no row for: kept, with only their own allergen information. */
    val nutritionOnly: Int,
) {
    val summary: String get() = NutritionPrompt.summary(matched, chartTotal)
}

/**
 * Puts a nutrition menu and an allergen chart for one restaurant together.
 *
 * Nutrition comes from the nutrition file. Allergens are the union of both files and the harder verdict wins, so
 * merging can only raise a warning: a chart can never clear a nutrition item and the nutrition file never clears a
 * chart row. Items found in only one file are kept as they are.
 */
object MenuNutritionMerge {
    fun merge(chart: List<MenuItemEntity>, nutrition: List<MenuItemEntity>, sourceId: String, now: Long): NutritionMergeResult {
        val pairs = MenuItemMatcher.pair(chart.map { it.name }, nutrition.map { it.name })
        val out = ArrayList<MenuItemEntity>()
        val matchedChart = HashSet<Int>()
        var nutritionOnly = 0
        for (p in pairs) {
            val c = p.chart?.let(chart::get)
            val n = p.nutrition?.let(nutrition::get)
            when {
                c != null && n != null -> {
                    matchedChart += p.chart!!
                    out += combine(c, n, sourceId, now)
                }
                n != null -> { nutritionOnly++; out += n.copy(sourceId = sourceId) }
                c != null -> out += c
            }
        }
        return NutritionMergeResult(out, matchedChart.size, chart.size, nutritionOnly)
    }

    private fun combine(chart: MenuItemEntity, nutrition: MenuItemEntity, sourceId: String, now: Long): MenuItemEntity {
        val stated = MenuItemMatcher.union(StatedAllergens.parse(chart.ingredientsText), StatedAllergens.parse(nutrition.ingredientsText))
        val ingredients = listOfNotNull(StatedAllergens.textForScan(nutrition.ingredientsText), stated.toText().ifBlank { null })
            .joinToString("; ")
        val matches = (chart.allergenMatches.split(',') + nutrition.allergenMatches.split(','))
            .map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }.joinToString(", ")
        return nutrition.copy(
            sourceId = sourceId,
            description = listOf(chart.description, nutrition.description).filter { it.isNotBlank() }.distinct().joinToString(" · "),
            ingredientsText = ingredients,
            allergenStatus = worseOf(chart.allergenStatus, nutrition.allergenStatus) ?: nutrition.allergenStatus,
            allergenMatches = matches,
            updatedAt = now,
        )
    }
}
