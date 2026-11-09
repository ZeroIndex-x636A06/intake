package com.zeroindex.intake.data.backup

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.data.db.entity.CoachSuggestionEntity
import com.zeroindex.intake.domain.coach.CoachAction
import com.zeroindex.intake.domain.coach.SuggestionKind
import com.zeroindex.intake.domain.coach.SuggestionStatus
import kotlinx.serialization.json.Json
import org.junit.Test

/** Coach history travels in exports, backups and sync as an ordinary table. */
class CoachBackupTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test fun aCoachSuggestionRowRoundTripsThroughTheExportFormat() {
        val row = CoachSuggestionEntity(
            id = "s1",
            checkInDay = 20_000,
            suggestionKey = "cal:down",
            kind = SuggestionKind.WEIGHT_TREND,
            title = "Eat 150 fewer calories",
            why = "Your trend moved -0.1% a week",
            details = "More detail",
            actionJson = json.encodeToString(CoachAction.serializer(), CoachAction.SetCalories(2_200, 2_050)),
            priority = 10,
            status = SuggestionStatus.APPLIED,
            decidedAt = 123,
            aiTitle = "Trim 150 calories",
            aiWhy = null,
            createdAt = 1,
            updatedAt = 2,
        )
        val text = json.encodeToString(CoachSuggestionEntity.serializer(), row)
        assertThat(json.decodeFromString(CoachSuggestionEntity.serializer(), text)).isEqualTo(row)
    }

    @Test fun anOlderRowWithoutTheOptionalFieldsStillDecodes() {
        val text = """{"id":"s2","checkInDay":1,"suggestionKey":"gap","kind":"DATA_GAP","title":"t","why":"w",
            "actionJson":"{}","priority":1,"createdAt":1,"updatedAt":1}"""
        val row = json.decodeFromString(CoachSuggestionEntity.serializer(), text)
        assertThat(row.status).isEqualTo(SuggestionStatus.PENDING)
        assertThat(row.deletedAt).isNull()
    }
}
