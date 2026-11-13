package com.zeroindex.intake.domain.backup

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

/**
 * The preferences that travel with an export and an encrypted backup (docs/SYNC-EXPORT.md "Settings").
 *
 * Pure Kotlin, no Android: the DataStore side hands in plain `name -> value` pairs and gets plain
 * pairs back, so the whole rule, including "no secret ever leaves the phone", is unit-tested on the JVM.
 *
 * What is IN: units, theme, rest timer, plates, targets settings, the profile and plan history,
 * baseline marker, reduce motion, week pattern, hydration settings, coach settings, AI privacy choices and model
 * choices, food-database switches.
 *
 * What is OUT, always: API keys (they live in EncryptedSharedPreferences and are not in DataStore at
 * all), sync server, device id and cursors, backup destination and credentials, pinned host keys,
 * "seen" flags and anything else tied to this one device. A name that looks like a secret is refused
 * even if an allowed prefix would take it, so a future key cannot leak by accident.
 */
object PortableSettings {

    /** Written as `settings.version`. Bump only if the meaning of the section changes. */
    const val VERSION = 1

    /** The exact preference names that are portable. */
    private val exact: Set<String> = setOf(
        "weight_unit", "length_unit", "theme_mode", "dynamic_color", "reduce_motion",
        "default_rest_sec", "rest_vibrate", "bar_weight_kg", "available_plates_kg",
        "goal_weight_kg", "goal_rate_kg_per_week", "protein_g_per_kg", "fat_g_per_kg",
        "auto_targets", "add_active_kcal", "current_plan_week",
        "volume_unit",
        "ai_privacy", "ai_allow_retaining", "daily_insight_enabled",
        // AI master switch, monthly budget and the weekly menu refresh opt-in. NOT ai_usage_reset_at: that
        // marker belongs to this device's own usage log.
        "ai_master_enabled", "ai_monthly_budget_usd", "ai_menu_auto_refresh",
        "profile_json", "baseline_json", "plan_history_json", "week_pattern_json",
        // Round 3: You and Settings (all plain preferences; never a secret or a device id).
        "profile_display_name", "portion_unit", "today_card_next", "today_card_water", "today_card_weight",
        "today_card_meals", "today_card_activity", "show_sodium", "eat_out_radius_mi", "eat_out_minutes", "eat_out_travel", "jump_tests", "weekly_sets_csv",
        "videos_only_workouts", "videos_personalise", "videos_hide_shorts", "photo_reminder_weeks", "export_reminder",
        "export_include_photos",
        // Round 3, item D: daily reminders (the switches and times). NOT the last-sent markers: those are device-local.
        "workout_reminder_on", "workout_reminder_minute", "meal_reminder_on", "breakfast_reminder_minute",
        "lunch_reminder_minute", "dinner_reminder_minute", "water_reminder_on", "water_reminder_start_hour",
        "water_reminder_end_hour",
        "off_search_enabled", "off_contribute_enabled", "off_other_languages", "off_language",
        // Hydration v2 (its own store): the opt-in, the chosen place name, drink edits and the measured sweat rate.
        // NOT the coordinates, the cached forecast or the retry time: a restore asks for the location again.
        "weather_enabled", "weather_location_mode", "weather_place", "drink_overrides",
        "sweat_pre_kg", "sweat_post_kg", "sweat_fluid_ml", "sweat_minutes", "sweat_use_measured",
    )

    /** Families of names: every hydration setting, every coach setting, one model choice per AI task. */
    private val prefixes: List<String> = listOf("hydration_", "coach_", "ai_model_", "ai_uses_", "water_")

    /** Never portable, whatever else matches. Checked against the lower-cased name. */
    private val forbiddenFragments: List<String> = listOf(
        "key", "token", "secret", "password", "passphrase", "credential", "pin_", "pinned", "fingerprint",
        "host", "device", "sync_", "backup_destination", "seen", "tour", "dismissed_tip", "last_sync",
        "last_export", "demo_data", "health_last",
        "weather_lat", "weather_lon", "weather_cache", "weather_last",
    )

    fun isPortable(name: String): Boolean {
        val n = name.lowercase()
        if (forbiddenFragments.any { it in n }) return false
        return name in exact || prefixes.any { name.startsWith(it) }
    }

    /** One value, typed so a Double stays a Double on the way back into DataStore. */
    sealed interface Value {
        data class Str(val v: String) : Value
        data class Bool(val v: Boolean) : Value
        data class IntV(val v: Int) : Value
        data class LongV(val v: Long) : Value
        data class DoubleV(val v: Double) : Value
        data class StrSet(val v: Set<String>) : Value
    }

    /** What an import found: values to restore and names it left alone. */
    data class Decoded(
        val values: Map<String, Value>,
        val ignored: List<String>,
        val version: Int,
    )

    /**
     * Builds the `settings` section from raw preference pairs. Anything not portable is dropped, so
     * callers can pass the whole store.
     */
    fun encode(raw: Map<String, Any?>): JsonObject {
        val out = LinkedHashMap<String, JsonElement>()
        raw.entries.sortedBy { it.key }.forEach { (name, value) ->
            if (!isPortable(name)) return@forEach
            val typed = when (value) {
                is String -> "string" to JsonPrimitive(value)
                is Boolean -> "bool" to JsonPrimitive(value)
                is Int -> "int" to JsonPrimitive(value)
                is Long -> "long" to JsonPrimitive(value)
                is Double -> "double" to JsonPrimitive(value)
                is Float -> "double" to JsonPrimitive(value.toDouble())
                is Set<*> -> "stringset" to JsonArray(value.filterIsInstance<String>().sorted().map(::JsonPrimitive))
                else -> return@forEach
            }
            out[name] = JsonObject(mapOf("type" to JsonPrimitive(typed.first), "value" to typed.second))
        }
        return JsonObject(mapOf("version" to JsonPrimitive(VERSION), "values" to JsonObject(out)))
    }

    /**
     * Reads a `settings` section. Unknown, forbidden or malformed entries are ignored and listed in
     * [Decoded.ignored]; nothing here ever throws on bad input.
     */
    fun decode(section: JsonElement?): Decoded {
        val obj = (section as? JsonObject) ?: return Decoded(emptyMap(), emptyList(), 0)
        val version = (obj["version"] as? JsonPrimitive)?.intOrNull ?: 0
        val values = (obj["values"] as? JsonObject) ?: return Decoded(emptyMap(), emptyList(), version)
        val good = LinkedHashMap<String, Value>()
        val ignored = mutableListOf<String>()
        values.forEach { (name, element) ->
            val value = if (isPortable(name)) decodeOne(element) else null
            if (value == null) ignored += name else good[name] = value
        }
        return Decoded(good, ignored, version)
    }

    private fun decodeOne(element: JsonElement): Value? {
        val o = runCatching { element.jsonObject }.getOrNull() ?: return null
        val type = (o["type"] as? JsonPrimitive)?.contentOrNull ?: return null
        val v = o["value"] ?: return null
        if (v is JsonNull) return null
        return when (type) {
            "string" -> (v as? JsonPrimitive)?.takeIf { it.isString }?.content?.let(Value::Str)
            "bool" -> (v as? JsonPrimitive)?.booleanOrNull?.let(Value::Bool)
            "int" -> (v as? JsonPrimitive)?.intOrNull?.let(Value::IntV)
            "long" -> (v as? JsonPrimitive)?.longOrNull?.let(Value::LongV)
            "double" -> (v as? JsonPrimitive)?.doubleOrNull?.let(Value::DoubleV)
            "stringset" -> (v as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
                ?.toSet()?.let(Value::StrSet)
            else -> null
        }
    }

    /**
     * The import preview line: "Settings: units, targets, profile ...". Plain words for the groups
     * that are present, in a fixed order.
     */
    fun describe(names: Collection<String>): String {
        if (names.isEmpty()) return "Settings: none in this backup"
        val groups = listOf(
            "units" to setOf("weight_unit", "length_unit", "volume_unit", "portion_unit"),
            "look and feel" to setOf(
                "theme_mode", "dynamic_color", "reduce_motion", "today_card_next", "today_card_water",
                "today_card_weight", "today_card_meals", "today_card_activity", "profile_display_name",
            ),
            "reminders and videos" to setOf(
                "photo_reminder_weeks", "export_reminder", "export_include_photos", "videos_only_workouts",
                "videos_hide_shorts", "workout_reminder_on", "meal_reminder_on", "water_reminder_on",
                "videos_personalise", "show_sodium", "eat_out_radius_mi", "eat_out_minutes", "eat_out_travel", "jump_tests", "weekly_sets_csv",
            ),
            "workout timer and plates" to setOf("default_rest_sec", "rest_vibrate", "bar_weight_kg", "available_plates_kg"),
            "targets" to setOf(
                "goal_weight_kg", "goal_rate_kg_per_week", "protein_g_per_kg", "fat_g_per_kg", "auto_targets",
                "add_active_kcal", "current_plan_week", "week_pattern_json",
            ),
            "profile" to setOf("profile_json"),
            "baseline and plan history" to setOf("baseline_json", "plan_history_json"),
            "water" to emptySet(),
            "coach" to emptySet(),
            "AI choices" to setOf(
                "ai_privacy", "ai_allow_retaining", "daily_insight_enabled",
                "ai_master_enabled", "ai_monthly_budget_usd", "ai_menu_auto_refresh",
            ),
            "food database" to setOf("off_search_enabled", "off_contribute_enabled", "off_other_languages", "off_language"),
        )
        val present = groups.filter { (label, keys) ->
            when (label) {
                "water" -> names.any { it.startsWith("hydration_") || it.startsWith("water_") }
                "coach" -> names.any { it.startsWith("coach_") }
                "AI choices" -> names.any { it in keys || it.startsWith("ai_model_") || it.startsWith("ai_uses_") }
                else -> names.any { it in keys }
            }
        }.map { it.first }
        return "Settings: " + (present.joinToString(", ").ifEmpty { "other preferences" })
    }
}
