package com.zeroindex.intake.data.backup

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.data.db.entity.AllergenAliasEntity
import com.zeroindex.intake.domain.model.FoodSource
import com.zeroindex.intake.data.db.entity.FoodEntity
import com.zeroindex.intake.domain.model.KeywordLevel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Test

/** A v2 backup (or a v2 peer in sync) must decode into v3 rows the same way MIGRATION_2_3 would. */
class LegacyRowsTest {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true; explicitNulls = false }

    private fun alias(raw: String): AllergenAliasEntity {
        val el = LegacyRows.upgrade("allergen_alias", json.parseToJsonElement(raw))
        return json.decodeFromJsonElement(AllergenAliasEntity.serializer(), el)
    }

    @Test
    fun v2KeywordsTakeTheirDefaultsAndUserTypedOnesBecomeCustom() {
        val seeded = alias("""{"id":"seed-dairy-whey","allergen":"dairy","aliasTerm":"whey","createdAt":1,"updatedAt":1}""")
        assertThat(seeded.isCustom).isFalse()
        assertThat(seeded.isEnabled).isTrue()
        assertThat(seeded.level).isEqualTo(KeywordLevel.CONTAINS)

        val typed = alias("""{"id":"9c1e","allergen":"dairy","aliasTerm":"oat creamer","createdAt":1,"updatedAt":1}""")
        assertThat(typed.isCustom).isTrue()
        assertThat(typed.isEnabled).isTrue()
        assertThat(typed.level).isEqualTo(KeywordLevel.CONTAINS)
    }

    @Test
    fun anExplicitIsCustomFromAV3FileIsNeverOverridden() {
        val row = alias("""{"id":"9c1e","allergen":"dairy","aliasTerm":"x","isCustom":false,"createdAt":1,"updatedAt":1}""")
        assertThat(row.isCustom).isFalse()
    }

    @Test
    fun otherTablesAndNonObjectsPassThroughUntouched() {
        val food = json.parseToJsonElement("""{"id":"f1"}""")
        assertThat(LegacyRows.upgrade("food", food)).isSameInstanceAs(food)
        val odd = json.parseToJsonElement("[1]")
        assertThat(LegacyRows.upgrade("allergen_alias", odd)).isSameInstanceAs(odd)
    }

    @Test
    fun aV2FoodRowDecodesWithNoContributionTimestamp() {
        val v2Food = FoodEntity.serializer().let { s ->
            val full = json.encodeToJsonElement(
                s,
                FoodEntity(
                    id = "f1", name = "Rice", servingSizeGrams = 100.0, servingLabel = "100 g", kcalPer100g = 130.0,
                    proteinPer100g = 2.7, carbsPer100g = 28.0, fatPer100g = 0.3, source = FoodSource.MANUAL, createdAt = 1, updatedAt = 1,
                ),
            ).jsonObject
            JsonObject(full - "offContributedAt")
        }
        val food = json.decodeFromJsonElement(FoodEntity.serializer(), v2Food)
        assertThat(food.offContributedAt).isNull()
        assertThat(food.name).isEqualTo("Rice")
    }

    @Test
    fun aV2WaterRowDecodesAsPlainWater() {
        val row = LegacyRows.upgrade(
            "hydration_log",
            json.parseToJsonElement("""{"id":"h1","loggedAt":10,"volumeMl":500.0,"presetId":null,"createdAt":1,"updatedAt":1}"""),
        )
        val log = json.decodeFromJsonElement(com.zeroindex.intake.data.db.entity.HydrationLogEntity.serializer(), row)
        assertThat(log.drinkType).isEqualTo(com.zeroindex.intake.domain.hydration.DrinkType.WATER)
        assertThat(log.hydrationFactor).isEqualTo(1.0)
        assertThat(log.caffeineMg).isEqualTo(0.0)
        assertThat(log.alcoholMl).isEqualTo(0.0)
        assertThat(log.effectiveMl).isEqualTo(500.0)
    }

    @Test
    fun aV2PresetDecodesAsWater() {
        val row = json.parseToJsonElement("""{"id":"p1","name":"Bottle","volumeMl":500.0,"displayOrder":1,"createdAt":1,"updatedAt":1}""")
        val preset = json.decodeFromJsonElement(com.zeroindex.intake.data.db.entity.HydrationPresetEntity.serializer(), row)
        assertThat(preset.drinkType).isEqualTo(com.zeroindex.intake.domain.hydration.DrinkType.WATER)
    }

    @Test
    fun aV3DrinkRowKeepsItsFactor() {
        val row = json.parseToJsonElement(
            """{"id":"h1","loggedAt":10,"volumeMl":44.0,"drinkType":"SPIRITS","hydrationFactor":-0.4,"alcoholMl":17.6,"createdAt":1,"updatedAt":1}""",
        )
        val log = json.decodeFromJsonElement(com.zeroindex.intake.data.db.entity.HydrationLogEntity.serializer(), row)
        assertThat(log.effectiveMl).isWithin(1e-9).of(-17.6)
    }

    @Test
    fun v2TrainingRowsDecodeWithTheNewColumnsAtTheirDefaults() {
        fun <T> decode(table: String, ser: kotlinx.serialization.KSerializer<T>, raw: String): T =
            json.decodeFromJsonElement(ser, LegacyRows.upgrade(table, json.parseToJsonElement(raw)))
        val ex = decode("exercise", com.zeroindex.intake.data.db.entity.ExerciseEntity.serializer(),
            """{"id":"e","name":"X","primaryMuscle":"chest","createdAt":1,"updatedAt":1}""")
        assertThat(ex.photoPath).isNull()
        val program = decode("program", com.zeroindex.intake.data.db.entity.ProgramEntity.serializer(),
            """{"id":"p","name":"P","createdAt":1,"updatedAt":1}""")
        assertThat(program.isStandalone).isFalse()
        val day = decode("program_day", com.zeroindex.intake.data.db.entity.ProgramDayEntity.serializer(),
            """{"id":"d","programId":"p","dayIndex":0,"name":"D","createdAt":1,"updatedAt":1}""")
        assertThat(day.weekday).isNull()
        val row = decode("program_exercise", com.zeroindex.intake.data.db.entity.ProgramExerciseEntity.serializer(),
            """{"id":"x","programDayId":"d","exerciseId":"e","displayOrder":0,"targetSets":3,"targetRepsMin":8,"targetRepsMax":12,"createdAt":1,"updatedAt":1}""")
        assertThat(row.notes).isNull()
    }

    @Test
    fun savedWorkoutsAndPhotoNamesRoundTripThroughTheBackupJson() {
        val p = com.zeroindex.intake.data.db.entity.ProgramEntity(id = "w", name = "Arm day", isStandalone = true, createdAt = 1, updatedAt = 1)
        val back = json.decodeFromString(com.zeroindex.intake.data.db.entity.ProgramEntity.serializer(), json.encodeToString(com.zeroindex.intake.data.db.entity.ProgramEntity.serializer(), p))
        assertThat(back).isEqualTo(p)
        val e = com.zeroindex.intake.data.db.entity.ExerciseEntity(id = "e", name = "X", primaryMuscle = "biceps", photoPath = "abc.jpg", createdAt = 1, updatedAt = 1)
        assertThat(json.decodeFromString(com.zeroindex.intake.data.db.entity.ExerciseEntity.serializer(), json.encodeToString(com.zeroindex.intake.data.db.entity.ExerciseEntity.serializer(), e))).isEqualTo(e)
    }
}
