package com.zeroindex.intake.domain.plan

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.domain.model.PlanLoopMode
import java.time.LocalDate
import org.junit.Test

class PlanScheduleTest {
    private val start = LocalDate.of(2026, 11, 2).toEpochDay() // a Monday

    private fun pos(daysAfter: Long, n: Int, mode: PlanLoopMode, count: Int = 0) =
        PlanSchedule.position(start, start + daysAfter, n, mode, count)

    @Test fun `no repeat runs the weeks then finishes on the last`() {
        assertThat(pos(0, 8, PlanLoopMode.NONE).week).isEqualTo(1)
        assertThat(pos(6, 8, PlanLoopMode.NONE).week).isEqualTo(1)
        assertThat(pos(7, 8, PlanLoopMode.NONE).week).isEqualTo(2)
        assertThat(pos(7 * 7 + 6, 8, PlanLoopMode.NONE).week).isEqualTo(8)
        assertThat(pos(7 * 7 + 6, 8, PlanLoopMode.NONE).state).isEqualTo(ScheduleState.RUNNING)
        val done = pos(7 * 8, 8, PlanLoopMode.NONE)
        assertThat(done.state).isEqualTo(ScheduleState.FINISHED)
        assertThat(done.week).isEqualTo(8)
        assertThat(done.label()).isEqualTo("Week 8 of 8 · finished")
    }

    @Test fun `repeat starts over forever`() {
        assertThat(pos(7 * 8, 8, PlanLoopMode.REPEAT).week).isEqualTo(1)
        assertThat(pos(7 * 8, 8, PlanLoopMode.REPEAT).cycle).isEqualTo(2)
        assertThat(pos(7 * 8 * 50 + 14, 8, PlanLoopMode.REPEAT).week).isEqualTo(3)
        assertThat(pos(7 * 8 * 50 + 14, 8, PlanLoopMode.REPEAT).state).isEqualTo(ScheduleState.RUNNING)
        assertThat(pos(14, 8, PlanLoopMode.REPEAT).label()).isEqualTo("Week 3 of 8 · repeats")
    }

    @Test fun `repeat n times then finishes`() {
        // 3 runs through a 4 week plan = 12 weeks.
        assertThat(pos(7 * 11, 4, PlanLoopMode.REPEAT_N, 3).week).isEqualTo(4)
        assertThat(pos(7 * 11, 4, PlanLoopMode.REPEAT_N, 3).cycle).isEqualTo(3)
        assertThat(pos(7 * 11, 4, PlanLoopMode.REPEAT_N, 3).state).isEqualTo(ScheduleState.RUNNING)
        val done = pos(7 * 12, 4, PlanLoopMode.REPEAT_N, 3)
        assertThat(done.state).isEqualTo(ScheduleState.FINISHED)
        assertThat(done.week).isEqualTo(4)
        assertThat(pos(7 * 5, 4, PlanLoopMode.REPEAT_N, 3).label()).isEqualTo("Week 2 of 4 · run 2 of 3")
    }

    @Test fun `a count below two is a single run`() {
        assertThat(pos(7 * 4, 4, PlanLoopMode.REPEAT_N, 1).state).isEqualTo(ScheduleState.FINISHED)
        assertThat(pos(0, 4, PlanLoopMode.REPEAT_N, 1).label()).isEqualTo("Week 1 of 4")
    }

    @Test fun `before the start shows week one and the wait`() {
        val p = PlanSchedule.position(start, start - 3, 8, PlanLoopMode.REPEAT, 0)
        assertThat(p.state).isEqualTo(ScheduleState.NOT_STARTED)
        assertThat(p.week).isEqualTo(1)
        assertThat(p.label()).isEqualTo("Week 1 of 8 · starts in 3 days")
        assertThat(PlanSchedule.position(start, start - 1, 8, PlanLoopMode.NONE, 0).label()).isEqualTo("Week 1 of 8 · starts in 1 day")
    }

    @Test fun `any length up to the maximum, including one week`() {
        assertThat(pos(7 * 103, 104, PlanLoopMode.NONE).week).isEqualTo(104)
        assertThat(pos(7 * 103, 104, PlanLoopMode.NONE).state).isEqualTo(ScheduleState.RUNNING)
        assertThat(pos(30, 1, PlanLoopMode.REPEAT).week).isEqualTo(1)
        assertThat(pos(30, 1, PlanLoopMode.NONE).state).isEqualTo(ScheduleState.FINISHED)
        // A zero-length plan is treated as one week rather than dividing by zero.
        assertThat(pos(30, 0, PlanLoopMode.REPEAT).week).isEqualTo(1)
    }

    @Test fun `jumping moves the start so today is that week`() {
        val today = start + 100
        for (target in listOf(1, 3, 12)) {
            val s = PlanSchedule.startForWeek(today, target)
            assertThat(PlanSchedule.position(s, today, 12, PlanLoopMode.NONE, 0).week).isEqualTo(target)
            assertThat(PlanSchedule.position(s, today + 7, 12, PlanLoopMode.REPEAT, 0).week).isEqualTo(target % 12 + 1)
        }
    }

    @Test fun `repeat text round trips`() {
        assertThat(PlanSchedule.parseRepeat("no")).isEqualTo(PlanLoopMode.NONE to 0)
        assertThat(PlanSchedule.parseRepeat("Yes")).isEqualTo(PlanLoopMode.REPEAT to 0)
        assertThat(PlanSchedule.parseRepeat("3 times")).isEqualTo(PlanLoopMode.REPEAT_N to 3)
        assertThat(PlanSchedule.parseRepeat("2x")).isEqualTo(PlanLoopMode.REPEAT_N to 2)
        assertThat(PlanSchedule.parseRepeat("1 time")).isEqualTo(PlanLoopMode.NONE to 0)
        assertThat(PlanSchedule.parseRepeat("0 times")).isNull()
        assertThat(PlanSchedule.parseRepeat("sometimes")).isNull()
        for ((m, c) in listOf(PlanLoopMode.NONE to 0, PlanLoopMode.REPEAT to 0, PlanLoopMode.REPEAT_N to 5)) {
            assertThat(PlanSchedule.parseRepeat(PlanSchedule.repeatText(m, c))).isEqualTo(m to c)
        }
    }
}
