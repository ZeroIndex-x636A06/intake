package com.zeroindex.intake.domain.photos

import java.time.Instant
import java.time.ZoneId

/** The three angles of a progress photo. Each is optional on any date. */
enum class PhotoAngle(val label: String) { FRONT("Front"), SIDE("Side"), BACK("Back") }

/** The metadata of one photo. The image bytes live in the app's private files at [fileName]; they never sync. */
data class PhotoMeta(
    val id: String,
    /** UTC epoch millis the photo was taken (or chosen for). */
    val takenAt: Long,
    val angle: PhotoAngle,
    val fileName: String,
)

/** All the photos of one local calendar day, at most one per angle (the newest wins). */
data class PhotoDay(val epochDay: Long, val photos: Map<PhotoAngle, PhotoMeta>) {
    val angles: Set<PhotoAngle> get() = photos.keys

    /** The photo to use as the day's cover: front, else side, else back. */
    val cover: PhotoMeta? get() = PhotoAngle.entries.firstNotNullOfOrNull { photos[it] }
}

/** Timeline grouping: photos by local day, newest day first. */
object PhotoTimeline {
    fun group(photos: List<PhotoMeta>, zone: ZoneId): List<PhotoDay> =
        photos.groupBy { Instant.ofEpochMilli(it.takenAt).atZone(zone).toLocalDate().toEpochDay() }
            .map { (day, list) ->
                // Newest photo per angle wins when the same angle was taken twice in a day.
                val byAngle = list.groupBy { it.angle }.mapValues { (_, v) -> v.maxBy { it.takenAt } }
                PhotoDay(day, byAngle.toSortedMap(compareBy { it.ordinal }))
            }
            .sortedByDescending { it.epochDay }

    /** Month headings for the grid: "2026-11" style keys with the days under each, newest first. */
    fun byMonth(days: List<PhotoDay>): List<Pair<String, List<PhotoDay>>> =
        days.groupBy { java.time.LocalDate.ofEpochDay(it.epochDay).let { d -> "%04d-%02d".format(d.year, d.monthValue) } }
            .toList().sortedByDescending { it.first }
}

/** One side of a comparison. */
data class CompareSide(val day: PhotoDay, val photo: PhotoMeta)

/** The model behind Compare: two dates, one angle, older on the left, newer on the right. */
data class CompareModel(val angle: PhotoAngle, val older: CompareSide, val newer: CompareSide) {
    val daysBetween: Long get() = newer.day.epochDay - older.day.epochDay
}

object PhotoCompare {
    /** Angles that both days have, in front, side, back order. */
    fun commonAngles(a: PhotoDay, b: PhotoDay): List<PhotoAngle> = PhotoAngle.entries.filter { it in a.angles && it in b.angles }

    /**
     * Builds the comparison for [first] and [second] (in either order). [preferred] is used when both days have it,
     * else the first common angle. Null when the two days share no angle, or are the same day.
     */
    fun build(first: PhotoDay, second: PhotoDay, preferred: PhotoAngle? = null): CompareModel? {
        if (first.epochDay == second.epochDay) return null
        val (older, newer) = if (first.epochDay < second.epochDay) first to second else second to first
        val common = commonAngles(older, newer)
        val angle = preferred?.takeIf { it in common } ?: common.firstOrNull() ?: return null
        return CompareModel(
            angle,
            CompareSide(older, older.photos.getValue(angle)),
            CompareSide(newer, newer.photos.getValue(angle)),
        )
    }

    /** The divider position as a fraction of the width, kept inside the picture. */
    fun clampDivider(fraction: Float): Float = fraction.coerceIn(0f, 1f)

    /** Where a drag of [deltaPx] moves the divider on a picture [widthPx] wide. */
    fun dragDivider(current: Float, deltaPx: Float, widthPx: Float): Float =
        if (widthPx <= 0f) clampDivider(current) else clampDivider(current + deltaPx / widthPx)

    /** "14 weeks apart", "3 days apart". */
    fun spanLabel(model: CompareModel): String {
        val d = model.daysBetween
        return when {
            d < 14 -> "$d day${if (d == 1L) "" else "s"} apart"
            d < 120 -> "${d / 7} weeks apart"
            else -> "${d / 30} months apart"
        }
    }
}

/** The optional "time for a progress photo" nudge (Settings > Reminders). */
object PhotoReminderRule {
    private const val DAY_MS = 86_400_000L

    /** [weeks] is 0 (off), 2, 3 or 4. Due when the newest photo is at least that old, or there is none yet. */
    fun isDue(weeks: Int, newestPhotoAt: Long?, now: Long): Boolean {
        if (weeks !in 2..4) return false
        val last = newestPhotoAt ?: return true
        return now - last >= weeks * 7 * DAY_MS
    }
}
