package com.zeroindex.intake.domain.training

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** The bundled how-to for one catalog exercise (assets/exercises/exercises.json). */
data class ExerciseGuide(val name: String, val steps: List<String>)

/**
 * The index of bundled exercise photos and steps. Built by `tools/build_exercise_images.py` from the
 * Free Exercise DB (public domain). An exercise that is not in the index has only the muscle map.
 */
class ExerciseGuideIndex(private val items: Map<String, ExerciseGuide>) {
    fun has(exerciseId: String): Boolean = exerciseId in items
    fun guide(exerciseId: String): ExerciseGuide? = items[exerciseId]
    val ids: Set<String> get() = items.keys

    companion object {
        const val ASSET_DIR = "exercises"
        const val ASSET_INDEX = "$ASSET_DIR/exercises.json"
        const val CREDIT = "Exercise photos and steps: Free Exercise DB (github.com/yuhonas/free-exercise-db), public domain (Unlicense)."

        /** Asset path of the start (0) or end (1) photo of a catalog exercise. */
        fun photoAsset(exerciseId: String, index: Int): String = "$ASSET_DIR/${exerciseId}_${index.coerceIn(0, 1)}.webp"

        fun parse(text: String): ExerciseGuideIndex {
            val root = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return ExerciseGuideIndex(emptyMap())
            val items = (root["items"] as? JsonObject).orEmpty().mapNotNull { (id, value) ->
                val obj = value as? JsonObject ?: return@mapNotNull null
                val name = (obj["name"] as? JsonPrimitive)?.content ?: return@mapNotNull null
                val steps = runCatching { obj["steps"]!!.jsonArray.map { it.jsonPrimitive.content } }.getOrDefault(emptyList())
                id to ExerciseGuide(name, steps)
            }.toMap()
            return ExerciseGuideIndex(items)
        }
    }
}
