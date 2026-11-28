package com.zeroindex.intake.domain.training

import java.util.Locale

/** Numbers and counts on the exercise history, written the same way as the rest of the app (thousands separators, no stray decimals). */
object HistoryFormat {

    /**
     * A weight or volume in the person's unit: "160 lb", "62.5 lb", "10,310 lb". Under 1,000 it keeps one decimal when
     * it has one; from 1,000 up it is rounded to a whole number, since a decimal on a volume means nothing.
     */
    fun weight(value: Double, unitLabel: String): String = "${number(value)} $unitLabel"

    fun number(value: Double): String {
        if (value >= 1000.0) return String.format(Locale.US, "%,d", Math.round(value))
        val tenth = Math.round(value * 10) / 10.0
        return if (tenth % 1.0 == 0.0) tenth.toLong().toString() else tenth.toString()
    }

    /** "1 record", "3 records". */
    fun records(count: Int): String = if (count == 1) "1 record" else "$count records"

    /** "Last 3 sessions", "Last session"; with the record count when there is one: "Last 3 sessions · 1 record so far". */
    fun sessionsHeading(sessionCount: Int, recordCount: Int): String {
        val sessions = if (sessionCount == 1) "Last session" else "Last $sessionCount sessions"
        return if (recordCount > 0) "$sessions · ${records(recordCount)} so far" else sessions
    }
}
