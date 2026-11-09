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
}
