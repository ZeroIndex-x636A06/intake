package com.zeroindex.intake.domain.photos

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Test

class ProgressPhotosTest {
    private val zone = ZoneId.of("UTC")
    private fun at(date: String, hour: Int = 12) = LocalDate.parse(date).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
    private fun photo(id: String, date: String, angle: PhotoAngle, hour: Int = 12) = PhotoMeta(id, at(date, hour), angle, "$id.jpg")

    @Test fun `the timeline groups by day, newest first, one photo per angle`() {
        val days = PhotoTimeline.group(
            listOf(
                photo("a", "2026-08-18", PhotoAngle.FRONT), photo("b", "2026-08-18", PhotoAngle.SIDE),
                photo("c", "2026-11-03", PhotoAngle.FRONT, 9), photo("d", "2026-11-03", PhotoAngle.FRONT, 18),
            ),
            zone,
        )
        assertThat(days.map { LocalDate.ofEpochDay(it.epochDay).toString() }).containsExactly("2026-11-03", "2026-08-18").inOrder()
        assertThat(days[0].photos).hasSize(1)
        assertThat(days[0].photos.getValue(PhotoAngle.FRONT).id).isEqualTo("d")
        assertThat(days[1].angles).containsExactly(PhotoAngle.FRONT, PhotoAngle.SIDE).inOrder()
        assertThat(days[1].cover?.id).isEqualTo("a")
    }

    @Test fun `months group newest first`() {
        val days = PhotoTimeline.group(listOf(photo("a", "2026-08-18", PhotoAngle.FRONT), photo("b", "2026-11-03", PhotoAngle.FRONT), photo("c", "2026-11-20", PhotoAngle.BACK)), zone)
        val months = PhotoTimeline.byMonth(days)
        assertThat(months.map { it.first }).containsExactly("2026-11", "2026-08").inOrder()
        assertThat(months[0].second).hasSize(2)
    }

    @Test fun `compare puts the older date left whatever the pick order`() {
        val days = PhotoTimeline.group(listOf(photo("a", "2026-08-18", PhotoAngle.FRONT), photo("b", "2026-11-03", PhotoAngle.FRONT)), zone)
        val m = PhotoCompare.build(days[0], days[1])!!
        assertThat(m.older.photo.id).isEqualTo("a")
        assertThat(m.newer.photo.id).isEqualTo("b")
        assertThat(m.daysBetween).isEqualTo(77)
        assertThat(PhotoCompare.spanLabel(m)).isEqualTo("11 weeks apart")
    }

    @Test fun `compare needs a shared angle and two different days`() {
        val days = PhotoTimeline.group(listOf(photo("a", "2026-08-18", PhotoAngle.FRONT), photo("b", "2026-11-03", PhotoAngle.BACK)), zone)
        assertThat(PhotoCompare.build(days[0], days[1])).isNull()
        assertThat(PhotoCompare.build(days[0], days[0])).isNull()
    }

    @Test fun `the preferred angle is used when both have it`() {
        val days = PhotoTimeline.group(
            listOf(
                photo("a", "2026-08-18", PhotoAngle.FRONT), photo("b", "2026-08-18", PhotoAngle.SIDE),
                photo("c", "2026-11-03", PhotoAngle.FRONT), photo("d", "2026-11-03", PhotoAngle.SIDE),
            ),
            zone,
        )
        assertThat(PhotoCompare.build(days[0], days[1])!!.angle).isEqualTo(PhotoAngle.FRONT)
        assertThat(PhotoCompare.build(days[0], days[1], PhotoAngle.SIDE)!!.angle).isEqualTo(PhotoAngle.SIDE)
        assertThat(PhotoCompare.commonAngles(days[0], days[1])).containsExactly(PhotoAngle.FRONT, PhotoAngle.SIDE).inOrder()
    }

    @Test fun `the divider stays inside the picture`() {
        assertThat(PhotoCompare.clampDivider(-0.2f)).isEqualTo(0f)
        assertThat(PhotoCompare.clampDivider(1.4f)).isEqualTo(1f)
        assertThat(PhotoCompare.dragDivider(0.5f, 100f, 400f)).isEqualTo(0.75f)
        assertThat(PhotoCompare.dragDivider(0.9f, 400f, 400f)).isEqualTo(1f)
        assertThat(PhotoCompare.dragDivider(0.3f, 50f, 0f)).isEqualTo(0.3f)
    }

    @Test fun `the photo reminder is due by weeks since the newest photo`() {
        val day = 86_400_000L
        val now = 100 * day
        assertThat(PhotoReminderRule.isDue(0, null, now)).isFalse()
        assertThat(PhotoReminderRule.isDue(2, null, now)).isTrue()
        assertThat(PhotoReminderRule.isDue(2, now - 14 * day, now)).isTrue()
        assertThat(PhotoReminderRule.isDue(2, now - 13 * day, now)).isFalse()
        assertThat(PhotoReminderRule.isDue(4, now - 27 * day, now)).isFalse()
        assertThat(PhotoReminderRule.isDue(5, null, now)).isFalse()
    }
}
