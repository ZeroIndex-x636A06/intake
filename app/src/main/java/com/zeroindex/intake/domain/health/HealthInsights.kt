package com.zeroindex.intake.domain.health

import java.time.Instant
import java.time.ZoneId
import kotlin.math.max

/**
 * The pure parts of the Health Connect tie-in: no Android, no database, so each rule has a unit test.
 * Everything here treats "no data" as absent (null or an empty list), never as zero.
 */

/** A weigh-in already in the database: its id (deleted or not) and when it was taken. */
data class KnownWeighIn(val id: String, val measuredAt: Long, val deleted: Boolean)

/** One weigh-in Health Connect offers: Health Connect's own record id and its time. */
data class OfferedWeighIn(val id: String, val measuredAt: Long)

object WeighInDedupe {
    /** Two readings this close are taken to be the same weigh-in. */
    const val MATCH_WINDOW_MS = 10 * 60 * 1000L

    /**
     * Which offered weigh-ins to import. Left out:
     * - one whose row id exists already, even soft-deleted (the person removed it; it must not come back);
     * - one within [MATCH_WINDOW_MS] of a live row, whether the person logged it in the app or it was imported before;
     * - a second offer within the window of one accepted in this same pass (two apps reporting the same scale).
     */
    fun toImport(offered: List<OfferedWeighIn>, known: List<KnownWeighIn>, rowId: (String) -> String): List<OfferedWeighIn> {
        val knownIds = known.mapTo(HashSet()) { it.id }
        val liveTimes = known.filter { !it.deleted }.map { it.measuredAt }.toMutableList()
        val out = mutableListOf<OfferedWeighIn>()
        offered.sortedBy { it.measuredAt }.forEach { o ->
            if (rowId(o.id) in knownIds) return@forEach
            if (liveTimes.any { kotlin.math.abs(it - o.measuredAt) <= MATCH_WINDOW_MS }) return@forEach
            liveTimes += o.measuredAt
            out += o
        }
        return out
    }
}

/** A sleep session, as start and end millis. */
data class SleepSpan(val startMillis: Long, val endMillis: Long)

object SleepMath {
    /** A session this short that starts in daytime is a nap, not "last night". */
    const val NAP_MAX_MINUTES = 180

    /** Total minutes covered by the spans, counting overlapping spans (a phone and a watch) once. */
    fun unionMinutes(spans: List<SleepSpan>): Long {
        var total = 0L
        var curStart = Long.MIN_VALUE
        var curEnd = Long.MIN_VALUE
        spans.filter { it.endMillis > it.startMillis }.sortedBy { it.startMillis }.forEach { s ->
            if (curEnd == Long.MIN_VALUE || s.startMillis > curEnd) {
                if (curEnd != Long.MIN_VALUE) total += curEnd - curStart
                curStart = s.startMillis
                curEnd = s.endMillis
            } else {
                curEnd = max(curEnd, s.endMillis)
            }
        }
        if (curEnd != Long.MIN_VALUE) total += curEnd - curStart
        return total / 60_000
    }

    /**
     * Last night's sleep for the day [dayStart, dayEnd): sessions that END in the day, overlaps counted once, short
     * daytime naps left out unless they are all there is. Null when no session ended that day.
     */
    fun lastNight(spans: List<SleepSpan>, dayStart: Long, dayEnd: Long, zone: ZoneId): Long? {
        val ending = spans.filter { it.endMillis >= dayStart && it.endMillis < dayEnd }
        if (ending.isEmpty()) return null
        val nights = ending.filterNot { isNap(it, zone) }
        return unionMinutes(nights.ifEmpty { ending }).takeIf { it > 0 }
    }

    private fun isNap(s: SleepSpan, zone: ZoneId): Boolean {
        val minutes = (s.endMillis - s.startMillis) / 60_000
        val hour = Instant.ofEpochMilli(s.startMillis).atZone(zone).hour
        return minutes <= NAP_MAX_MINUTES && hour in 9..19
    }
}

object SessionMath {
    /** Overlapping or touching workout windows become one, so a watch and a phone logging the same workout count once. */
    fun merge(windows: List<SessionWindow>): List<SessionWindow> {
        val out = mutableListOf<SessionWindow>()
        windows.filter { it.endMillis > it.startMillis }.sortedBy { it.startMillis }.forEach { w ->
            val last = out.lastOrNull()
            if (last != null && w.startMillis <= last.endMillis) {
                out[out.lastIndex] = SessionWindow(last.startMillis, max(last.endMillis, w.endMillis))
            } else {
                out += w
            }
        }
        return out
    }
}

/** One day of Health Connect numbers, as the screens use them. Every field is null when there is no data. */
data class DailyHealth(
    val dayStartMillis: Long,
    val steps: Long?,
    val sleepMinutes: Long?,
    /** All-day active calories (Health Connect's total), the figure a watch shows. */
    val activeKcalDay: Double?,
    val restingHeartRate: Double?,
)

/** The compact Today card. Null when nothing at all is known, so the card is absent rather than full of zeros. */
data class ActivityToday(val steps: Long?, val sleepMinutes: Long?, val activeKcal: Int?) {
    companion object {
        fun from(today: DailyHealth?): ActivityToday? {
            if (today == null) return null
            val card = ActivityToday(
                steps = today.steps?.takeIf { it > 0 },
                sleepMinutes = today.sleepMinutes?.takeIf { it > 0 },
                activeKcal = today.activeKcalDay?.takeIf { it > 0 }?.let { Math.round(it).toInt() },
            )
            return card.takeIf { it.steps != null || it.sleepMinutes != null || it.activeKcal != null }
        }
    }
}

data class DayPoint(val dayStartMillis: Long, val value: Double)

object HealthSeries {
    /** Days that have a value above zero, oldest first; days with no data are left out, not drawn as zero. */
    fun points(days: List<DailyHealth>, pick: (DailyHealth) -> Double?): List<DayPoint> =
        days.sortedBy { it.dayStartMillis }.mapNotNull { d -> pick(d)?.takeIf { it > 0 }?.let { DayPoint(d.dayStartMillis, it) } }

    fun sleepHours(days: List<DailyHealth>) = points(days) { d -> d.sleepMinutes?.let { it / 60.0 } }
    fun steps(days: List<DailyHealth>) = points(days) { d -> d.steps?.toDouble() }
    fun restingHeartRate(days: List<DailyHealth>) = points(days) { d -> d.restingHeartRate }
}
