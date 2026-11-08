package com.zeroindex.intake.work

import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Test

class BackupWorkerTest {
    private val zone = ZoneId.of("America/New_York")

    @Test
    fun `first run waits until the coming 2 AM`() {
        val evening = ZonedDateTime.of(2026, 11, 8, 22, 30, 0, 0, zone)
        assertThat(BackupWorker.delayUntilTarget(evening)).isEqualTo(Duration.ofMinutes(210))
        val early = ZonedDateTime.of(2026, 11, 8, 1, 0, 0, 0, zone)
        assertThat(BackupWorker.delayUntilTarget(early)).isEqualTo(Duration.ofHours(1))
    }

    @Test
    fun `exactly at 2 AM schedules the next night, not now`() {
        val two = ZonedDateTime.of(2026, 11, 9, 2, 0, 0, 0, zone)
        assertThat(BackupWorker.delayUntilTarget(two)).isEqualTo(Duration.ofHours(24))
    }
}
