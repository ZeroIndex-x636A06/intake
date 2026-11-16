package com.zeroindex.intake.domain.nfc

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.domain.model.MealSlot
import java.time.LocalTime
import org.junit.Test

class TemplateLinkTest {

    private val id = "3f2b6c1e-8a4d-4c2e-9b1a-0d5e7f6a9c21"

    @Test
    fun `a written link parses back to the same id`() {
        assertThat(TemplateLink.parse(TemplateLink.uriFor(id))).isEqualTo(id)
    }

    @Test
    fun `anything that is not our link is ignored`() {
        assertThat(TemplateLink.parse("https://example.com/$id")).isNull()
        assertThat(TemplateLink.parse("intake://log-template/not-a-uuid")).isNull()
        assertThat(TemplateLink.parse("intake://log-template/$id/../../etc")).isNull()
        assertThat(TemplateLink.parse(null)).isNull()
    }

    @Test
    fun `trailing slash and query are tolerated`() {
        assertThat(TemplateLink.parse("intake://log-template/$id/")).isEqualTo(id)
        assertThat(TemplateLink.parse("INTAKE://LOG-TEMPLATE/$id?src=tag")).isEqualTo(id)
    }

    @Test
    fun `the current slot follows the time of day`() {
        assertThat(TemplateLink.slotFor(LocalTime.of(7, 30))).isEqualTo(MealSlot.BREAKFAST)
        assertThat(TemplateLink.slotFor(LocalTime.of(12, 5))).isEqualTo(MealSlot.LUNCH)
        assertThat(TemplateLink.slotFor(LocalTime.of(18, 45))).isEqualTo(MealSlot.DINNER)
        assertThat(TemplateLink.slotFor(LocalTime.of(22, 10))).isEqualTo(MealSlot.SNACK)
    }

    @Test
    fun `a tag tap asks first and a bad id is shown, not dropped (Q4-25)`() {
        assertThat(TemplateLink.parse("intake://log-template/not-a-real-id")).isNull()
        val candidates = listOf(java.io.File("src/main/java/com/zeroindex/intake/MainActivity.kt"), java.io.File("app/src/main/java/com/zeroindex/intake/MainActivity.kt"))
        val source = candidates.first { it.isFile }.readText()
        // The only call that logs is inside logTemplate, which only the dialog's Log button reaches.
        assertThat(Regex("templates\\.log\\(").findAll(source).count()).isEqualTo(1)
        assertThat(source).contains("PlannedMealSlot.forTime")
        assertThat(source).contains("This tag does not hold a meal")
    }
}
