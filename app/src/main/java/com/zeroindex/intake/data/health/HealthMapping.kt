package com.zeroindex.intake.data.health

import com.zeroindex.intake.data.db.entity.HealthDailySummaryEntity
import com.zeroindex.intake.domain.health.DailyHealth

/** The screens' view of one stored day. Nothing is invented: a missing figure stays null. */
fun HealthDailySummaryEntity.toDailyHealth() = DailyHealth(
    dayStartMillis = dayStartMillis,
    steps = steps,
    sleepMinutes = sleepMinutes,
    activeKcalDay = activeKcalDay,
    restingHeartRate = restingHeartRate,
)
