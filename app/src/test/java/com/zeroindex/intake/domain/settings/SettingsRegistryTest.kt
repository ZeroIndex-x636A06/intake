package com.zeroindex.intake.domain.settings

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.zeroindex.intake.domain.backup.PortableSettings
import java.io.File
import org.junit.Test

/** Every row of docs/SETTINGS-INVENTORY.md has a registry entry, a group and a control in the Settings UI. */
class SettingsRegistryTest {
    private val root = listOf(File("."), File("..")).first { File(it, "docs/SETTINGS-INVENTORY.md").isFile }
    private val inventory = File(root, "docs/SETTINGS-INVENTORY.md").readLines()
    private val uiDir = listOf(File("src/main/java"), File("app/src/main/java")).first { it.isDirectory }.resolve("com/zeroindex/intake/ui/screens")

    private data class Row(val number: Int, val key: String, val group: String)

    private val rows: List<Row> = inventory.mapNotNull { line ->
        val cells = line.trim().trim('|').split('|').map { it.trim() }
        if (cells.size < 7 || cells[0].toIntOrNull() == null) return@mapNotNull null
        val key = Regex("`([^`]+)`").find(cells[2])?.groupValues?.get(1) ?: return@mapNotNull null
        Row(cells[0].toInt(), key, cells[3])
    }

    private val titleOf = mapOf(
        "Appearance and units" to SettingsGroup.APPEARANCE, "Nutrition" to SettingsGroup.NUTRITION,
        "Water and drinks" to SettingsGroup.WATER, "Training" to SettingsGroup.TRAINING,
        "Health Connect" to SettingsGroup.HEALTH, "Food database" to SettingsGroup.FOOD_DB, "Videos" to SettingsGroup.VIDEOS,
        "Reminders" to SettingsGroup.REMINDERS, "Sync and export" to SettingsGroup.SYNC, "AI (optional)" to SettingsGroup.AI,
        "API keys" to SettingsGroup.KEYS, "About and help" to SettingsGroup.ABOUT,
    )

    @Test
    fun `the inventory has 114 controls in 12 groups`() {
        assertThat(rows).hasSize(114)
        assertThat(rows.map { it.group }.toSet()).isEqualTo(titleOf.keys)
        assertThat(SettingsGroup.entries).hasSize(12)
        assertThat(SettingsRegistry.all).hasSize(114)
        assertThat(SettingsRegistry.all.count { it.isStored }).isEqualTo(97)
        assertThat(SettingsRegistry.all.count { it.kind == ControlKind.ACTION }).isEqualTo(16)
    }

    @Test
    fun `every inventory key is in the registry under the same group`() {
        rows.forEach { r ->
            val spec = SettingsRegistry.byId(r.key)
            assertWithMessage("inventory key ${r.key} is not in the registry").that(spec).isNotNull()
            assertWithMessage("${r.key} is in the wrong group").that(spec!!.group).isEqualTo(titleOf.getValue(r.group))
            assertThat(spec.number).isEqualTo(r.number)
        }
        assertThat(SettingsRegistry.all.map { it.id }).containsNoDuplicates()
    }

    @Test
    fun `every registry id has a control in the Settings UI`() {
        val source = uiDir.resolve("settings/SettingsControls.kt").readText()
        val ai = uiDir.resolve("ai/AiSettingControls.kt").readText()
        val health = uiDir.resolve("health/HealthConnectControls.kt").readText()
        val families = listOf("aiModel." to ai, "aiUses." to ai, "hc." to health)
        val missing = SettingsRegistry.all.filter { spec ->
            val family = families.firstOrNull { spec.id.startsWith(it.first) }
            if (family != null) !family.second.contains("\"${family.first}")
            else !source.contains("\"${spec.id}\"") && !ai.contains("\"${spec.id}\"")
        }.map { it.id }
        assertWithMessage("Registry ids with no control: $missing").that(missing).isEmpty()
    }

    @Test
    fun `search finds a control by its words and an empty query finds nothing`() {
        assertThat(SettingsRegistry.search("sodium").map { it.id }).contains("showSodium")
        assertThat(SettingsRegistry.search("default rest").map { it.id }).contains("defaultRestSec")
        assertThat(SettingsRegistry.search("")).isEmpty()
    }

    @Test
    fun `new keys are classified portable or device-local`() {
        listOf(
            "profile_display_name", "portion_unit", "today_card_next", "today_card_water", "today_card_weight", "today_card_meals",
            "show_sodium", "eat_out_radius_mi", "jump_tests", "weekly_sets_csv", "videos_only_workouts", "videos_personalise",
            "photo_reminder_weeks", "export_reminder", "export_include_photos",
        ).forEach { assertWithMessage(it).that(PortableSettings.isPortable(it)).isTrue() }
        assertThat(PortableSettings.isPortable("first_use_at_ms")).isFalse()
        assertThat(PortableSettings.isPortable("youtube_api_key")).isFalse()
    }
}
