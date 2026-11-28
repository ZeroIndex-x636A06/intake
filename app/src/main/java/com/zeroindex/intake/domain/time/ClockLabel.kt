package com.zeroindex.intake.domain.time

import java.util.Locale

/** Writes a time of day the way the phone's clock does: "18:30" on a 24 hour phone, "6:30 PM" on a 12 hour one. */
object ClockLabel {
    fun fromMinute(minuteOfDay: Int, is24Hour: Boolean): String {
        val m = ((minuteOfDay % 1440) + 1440) % 1440
        val h = m / 60
        val mm = m % 60
        if (is24Hour) return String.format(Locale.US, "%d:%02d", h, mm)
        val h12 = if (h % 12 == 0) 12 else h % 12
        return String.format(Locale.US, "%d:%02d %s", h12, mm, if (h < 12) "AM" else "PM")
    }

    /** The local time of day of an instant, written the same way. */
    fun fromMillis(millis: Long, zone: java.time.ZoneId, is24Hour: Boolean): String {
        val t = java.time.Instant.ofEpochMilli(millis).atZone(zone)
        return fromMinute(t.hour * 60 + t.minute, is24Hour)
    }

    fun fromHour(hour: Int, is24Hour: Boolean): String = fromMinute(hour * 60, is24Hour)
}
