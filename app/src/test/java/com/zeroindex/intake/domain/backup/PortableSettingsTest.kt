package com.zeroindex.intake.domain.backup

import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Test

class PortableSettingsTest {

    private val json = Json { prettyPrint = false }

    private val device = mapOf<String, Any?>(
        "weight_unit" to "KILOGRAMS",
        "length_unit" to "CENTIMETERS",
        "dynamic_color" to true,
        "default_rest_sec" to 90,
        "bar_weight_kg" to 20.0,
        "available_plates_kg" to "25,20,10,5,2.5,1.25",
        "goal_weight_kg" to 78.5,
        "auto_targets" to false,
        "profile_json" to """{"sex":"MALE","birthYear":1990,"heightCm":180.0}""",
        "baseline_json" to """{"startedAtMillis":1700000000000,"completed":true}""",
        "plan_history_json" to "[]",
        "week_pattern_json" to "{}",
        "hydration_base_ml" to 2800.0,
        "hydration_training_bonus_ml" to 500.0,
        "coach_weekday" to 3,
        "coach_notify" to true,
        "coach_ai_reword" to false,
        "coach_state_json" to """{"lastCheckInDay":20000}""",
        "ai_privacy" to "NO_TRAINING",
        "ai_model_DAILY_INSIGHT" to "vendor/model",
        "off_search_enabled" to true,
        "off_other_languages" to true,
        "weather_enabled" to true,
        "weather_location_mode" to "CITY",
        "weather_place" to "Leeds",
        "drink_overrides" to "COFFEE:0.9",
        "sweat_pre_kg" to 80.0,
        "sweat_use_measured" to true,
        // Device-local or private: none of these may travel.
        "sync_server_url" to "https://sync.example.org",
        "sync_device_id" to "device-123",
        "sync_device_name" to "Pixel",
        "sync_pulled_seq" to 42L,
        "backup_destination_json" to """{"host":"nas.local","user":"me"}""",
        "backup_schedule_token" to "x",
        "last_export_at" to 1L,
        "demo_data_loaded" to true,
        "health_last_write_at" to 5L,
        "weather_lat" to 53.8,
        "weather_lon" to -1.5,
        "weather_cache_temp_max_c" to 21.0,
        "weather_last_attempt_at" to 7L,
        "openrouter_api_key" to "sk-or-secret-value",
        "usda_api_key" to "usda-secret-value",
        "off_username" to "someone",
        "off_password" to "hunter2",
        "sftp_pinned_host_key" to "SHA256:abc",
        "tour_seen" to true,
        "tip_seen_today" to true,
    )

    private val secrets = listOf(
        "sk-or-secret-value", "usda-secret-value", "hunter2", "someone", "SHA256:abc", "device-123",
        "sync.example.org", "nas.local", "openrouter_api_key", "usda_api_key", "off_password", "off_username",
        "sync_device_id", "sync_server_url", "pinned", "weather_lat", "53.8",
    )

    @Test fun roundTripRestoresEveryPortableValueWithItsType() {
        val section = PortableSettings.encode(device)
        val back = PortableSettings.decode(json.parseToJsonElement(json.encodeToString(JsonObject.serializer(), section)))
        assertThat(back.version).isEqualTo(PortableSettings.VERSION)
        assertThat(back.ignored).isEmpty()
        val v = back.values
        assertThat(v["off_other_languages"]).isEqualTo(PortableSettings.Value.Bool(true))
        assertThat(v["weight_unit"]).isEqualTo(PortableSettings.Value.Str("KILOGRAMS"))
        assertThat(v["dynamic_color"]).isEqualTo(PortableSettings.Value.Bool(true))
        assertThat(v["default_rest_sec"]).isEqualTo(PortableSettings.Value.IntV(90))
        assertThat(v["bar_weight_kg"]).isEqualTo(PortableSettings.Value.DoubleV(20.0))
        assertThat(v["hydration_base_ml"]).isEqualTo(PortableSettings.Value.DoubleV(2800.0))
        assertThat(v["profile_json"]).isEqualTo(PortableSettings.Value.Str(device.getValue("profile_json") as String))
        assertThat(v["coach_weekday"]).isEqualTo(PortableSettings.Value.IntV(3))
        assertThat(v["weather_place"]).isEqualTo(PortableSettings.Value.Str("Leeds"))
        assertThat(v["sweat_pre_kg"]).isEqualTo(PortableSettings.Value.DoubleV(80.0))
        assertThat(v["ai_model_DAILY_INSIGHT"]).isEqualTo(PortableSettings.Value.Str("vendor/model"))
        // Everything that came back is something that went in, unchanged in value.
        v.forEach { (name, value) ->
            val original = device.getValue(name)
            val expected = when (original) {
                is String -> PortableSettings.Value.Str(original)
                is Boolean -> PortableSettings.Value.Bool(original)
                is Int -> PortableSettings.Value.IntV(original)
                is Long -> PortableSettings.Value.LongV(original)
                is Double -> PortableSettings.Value.DoubleV(original)
                else -> error("unexpected $name")
            }
            assertThat(value).isEqualTo(expected)
        }
    }

    @Test fun noSecretOrDeviceValueEverAppearsInTheExport() {
        val text = json.encodeToString(JsonObject.serializer(), PortableSettings.encode(device))
        secrets.forEach { assertThat(text).doesNotContain(it) }
        val names = PortableSettings.decode(PortableSettings.encode(device)).values.keys
        listOf(
            "sync_server_url", "sync_device_id", "sync_pulled_seq", "backup_destination_json", "last_export_at",
            "demo_data_loaded", "health_last_write_at", "weather_lat", "weather_lon", "weather_cache_temp_max_c",
            "weather_last_attempt_at", "openrouter_api_key", "usda_api_key", "off_username", "off_password",
            "sftp_pinned_host_key", "tour_seen", "tip_seen_today", "backup_schedule_token",
        ).forEach { assertThat(names).doesNotContain(it) }
    }

    @Test fun aSecretLookingNameIsRefusedEvenUnderAnAllowedPrefix() {
        assertThat(PortableSettings.isPortable("hydration_api_key")).isFalse()
        assertThat(PortableSettings.isPortable("coach_token")).isFalse()
        assertThat(PortableSettings.isPortable("coach_tip_seen")).isFalse()
        assertThat(PortableSettings.isPortable("coach_weekday")).isTrue()
    }

    @Test fun importIgnoresUnknownAndForbiddenKeysAndBadValues() {
        val hostile = JsonObject(
            mapOf(
                "version" to JsonPrimitive(1),
                "values" to JsonObject(
                    mapOf(
                        "weight_unit" to entry("string", JsonPrimitive("POUNDS")),
                        "openrouter_api_key" to entry("string", JsonPrimitive("sk-evil")),
                        "sync_device_id" to entry("string", JsonPrimitive("evil")),
                        "some_future_setting" to entry("string", JsonPrimitive("x")),
                        "default_rest_sec" to entry("int", JsonPrimitive("not a number")),
                        "bar_weight_kg" to entry("blob", JsonPrimitive(1)),
                        "dynamic_color" to JsonPrimitive(true),
                    ),
                ),
            ),
        )
        val back = PortableSettings.decode(hostile)
        assertThat(back.values.keys).containsExactly("weight_unit")
        assertThat(back.ignored).containsAtLeast("openrouter_api_key", "sync_device_id", "some_future_setting", "default_rest_sec", "bar_weight_kg", "dynamic_color")
    }

    @Test fun anOldBackupWithoutASettingsSectionStillImports() {
        val old = json.parseToJsonElement("""{"format":"intake-export","formatVersion":1,"schemaVersion":2,"tables":{}}""").jsonObject
        val back = PortableSettings.decode(old["settings"])
        assertThat(back.values).isEmpty()
        assertThat(back.ignored).isEmpty()
        assertThat(PortableSettings.decode(JsonPrimitive("garbage")).values).isEmpty()
        assertThat(PortableSettings.decode(JsonObject(mapOf("version" to JsonPrimitive(9)))).values).isEmpty()
    }

    @Test fun theImportPreviewNamesTheGroups() {
        val line = PortableSettings.describe(setOf("weight_unit", "goal_weight_kg", "profile_json", "hydration_base_ml", "coach_weekday"))
        assertThat(line).isEqualTo("Settings: units, targets, profile, water, coach")
        assertThat(PortableSettings.describe(emptySet())).contains("none")
    }

    @Test fun everyPreferenceNameInTheStoresIsDeliberatelyIncludedOrExcluded() {
        val deviceLocal = setOf(
            "backup_destination_json", "sync_server_url", "sync_device_id", "sync_device_name", "sync_pulled_seq",
            "sync_pushed_up_to", "sync_schema_version", "sync_last_at", "sync_last_error", "last_export_at",
            "demo_data_loaded", "health_last_write_at", "health_last_sync_at",
            // Motion polish: the day the protein burst last played is per device, never exported.
            "last_celebration_day",
            // AI usage meter: where this device's counters were last reset.
            "ai_usage_reset_at",
            // Hydration v2: precise location, forecast cache and retry time.
            "weather_lat", "weather_lon", "weather_cache_date", "weather_cache_lat", "weather_cache_lon",
            "weather_cache_temp_max_c", "weather_cache_humidity_pct", "weather_cache_fetched_at", "weather_last_attempt_at",
        )
        val sources = listOf("SettingsRepository.kt", "FoodDataPrefs.kt", "HydrationPrefs.kt")
        val names = sources.flatMap { file ->
            val text = settingsSource(file).readText()
            Regex("""PreferencesKey\("([a-z0-9_]+)"\)""").findAll(text).map { it.groupValues[1] }.toList()
        }
        assertThat(names).isNotEmpty()
        names.forEach { name ->
            val portable = PortableSettings.isPortable(name)
            assertThat(portable || name in deviceLocal).isTrue()
            if (name in deviceLocal) assertThat(portable).isFalse()
        }
    }

    private fun settingsSource(file: String): File =
        listOf(
            File("src/main/java/com/zeroindex/intake/data/settings/$file"),
            File("app/src/main/java/com/zeroindex/intake/data/settings/$file"),
        ).first { it.isFile }

    private fun entry(type: String, value: JsonPrimitive) = JsonObject(mapOf("type" to JsonPrimitive(type), "value" to value))

    @Test fun trainingAnswersTravelInsideTheProfileValue() {
        val profile = """{"focusMuscles":["back"],"avoidAreas":["KNEE"],"trainingWeekdays":[1,3,5]}"""
        val section = PortableSettings.encode(mapOf("profile_json" to profile))
        val back = PortableSettings.decode(section)
        assertThat(back.values["profile_json"]).isEqualTo(PortableSettings.Value.Str(profile))
        assertThat(back.ignored).isEmpty()
    }
}
