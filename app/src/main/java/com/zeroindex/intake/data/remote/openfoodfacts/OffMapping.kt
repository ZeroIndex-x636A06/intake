package com.zeroindex.intake.data.remote.openfoodfacts

import com.zeroindex.intake.data.db.entity.FoodEntity
import com.zeroindex.intake.domain.model.FoodSource

/**
 * Open Food Facts to [FoodEntity], for both the barcode lookup and the search. A
 * product without a name or without energy cannot be logged, so it maps to null.
 * Values stay per 100 g, with sodium converted from grams to milligrams.
 */
object OffMapping {

    fun fromProduct(product: OffProduct, barcode: String): FoodEntity? = toFood(
        barcode = barcode,
        name = product.productName,
        brand = product.brands?.split(',')?.firstOrNull()?.trim(),
        servingSize = product.servingSize,
        servingQuantity = product.servingQuantity,
        nutriments = product.nutriments,
        ingredientsText = product.ingredientsText,
        allergenTags = product.allergensTags,
        traceTags = product.tracesTags,
    )

    fun fromHit(hit: OffSearchHit): FoodEntity? {
        val code = hit.code?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return toFood(
            barcode = code,
            name = hit.productName,
            brand = hit.firstBrand,
            servingSize = hit.servingSize,
            servingQuantity = hit.servingQuantity,
            nutriments = hit.nutriments,
            ingredientsText = null,
            allergenTags = hit.allergensTags.orEmpty(),
            traceTags = emptyList(),
        )
    }

    private fun toFood(
        barcode: String,
        name: String?,
        brand: String?,
        servingSize: String?,
        servingQuantity: Double?,
        nutriments: OffNutriments?,
        ingredientsText: String?,
        allergenTags: List<String>,
        traceTags: List<String>,
    ): FoodEntity? {
        val n = nutriments ?: return null
        val kcal = n.energyKcal100g?.takeIf { it > 0.0 } ?: return null
        val cleanName = name?.trim()?.takeIf { it.isNotEmpty() } ?: return null

        val servingGrams = servingQuantity?.takeIf { it > 0 } ?: 100.0
        // Open Food Facts reports sodium in grams; salt (about 39% sodium) is the fallback.
        val sodiumMg = (n.sodium100g ?: n.salt100g?.let { it * 0.393 })?.times(1000.0) ?: 0.0

        return FoodEntity(
            name = cleanName,
            brand = brand?.takeIf { it.isNotEmpty() },
            barcode = barcode,
            servingSizeGrams = servingGrams,
            servingLabel = servingSize?.trim()?.takeIf { it.isNotEmpty() } ?: "${servingGrams.toInt()} g",
            kcalPer100g = kcal,
            proteinPer100g = n.proteins100g ?: 0.0,
            carbsPer100g = n.carbohydrates100g ?: 0.0,
            fatPer100g = n.fat100g ?: 0.0,
            fiberPer100g = n.fiber100g ?: 0.0,
            sugarPer100g = n.sugars100g ?: 0.0,
            sodiumPer100g = sodiumMg,
            saturatedFatPer100g = n.saturatedFat100g ?: 0.0,
            source = FoodSource.OPEN_FOOD_FACTS,
            // Both the structured tags and the panel text are kept: the allergen
            // engine rescans this whenever the food is logged again (SPEC §7.3).
            allergenTags = FoodIngredients.join(allergenTags + traceTags, ingredientsText),
            createdAt = 0,
            updatedAt = 0,
        )
    }
}

/**
 * `FoodEntity.allergenTags` holds "tag,tag | ingredient text" (SPEC §4). These helpers
 * keep the two halves apart so the edit screen can change the ingredient text
 * without touching the structured tags, and the contribution can send the text
 * alone.
 */
object FoodIngredients {
    private const val SEPARATOR = " | "

    fun join(tags: List<String>, ingredients: String?): String = buildString {
        val tagText = tags.joinToString(",")
        if (tagText.isNotEmpty()) append(tagText)
        ingredients?.trim()?.takeIf { it.isNotEmpty() }?.let {
            if (isNotEmpty()) append(SEPARATOR)
            append(it)
        }
    }

    /** The structured tags half: items that look like "en:milk". */
    fun tags(stored: String): String {
        val at = stored.indexOf(SEPARATOR)
        val head = if (at >= 0) stored.substring(0, at) else stored
        return if (looksLikeTags(head)) head else ""
    }

    /** The ingredient text half, or "" when only tags are stored. */
    fun text(stored: String): String {
        val at = stored.indexOf(SEPARATOR)
        if (at >= 0) return stored.substring(at + SEPARATOR.length).trim()
        return if (looksLikeTags(stored)) "" else stored.trim()
    }

    private fun looksLikeTags(part: String): Boolean {
        val pieces = part.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        return pieces.isNotEmpty() && pieces.all { Regex("^[a-z]{2}:[a-z0-9-]+$").matches(it) }
    }
}
