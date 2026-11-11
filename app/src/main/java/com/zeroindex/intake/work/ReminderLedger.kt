package com.zeroindex.intake.work

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remembers which reminders have already gone out, so each slot fires once. Kept in SharedPreferences, not in the
 * settings DataStore: it is this device's own memory, so it is never exported, backed up or synced. Every read is
 * wrapped, so a broken store only means a reminder may repeat, never a crash.
 */
@Singleton
class ReminderLedger @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs by lazy { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    /** True if the daily [slot] already fired on [day]. */
    fun sent(day: LocalDate, slot: String): Boolean = runCatching {
        prefs.getStringSet(KEY_DAILY, emptySet()).orEmpty().contains(entry(day, slot))
    }.getOrDefault(false)

    /** Records a daily slot as sent. Entries from earlier days are dropped, so the set stays small. */
    fun markSent(day: LocalDate, slot: String) {
        runCatching {
            val today = day.toString()
            val kept = prefs.getStringSet(KEY_DAILY, emptySet()).orEmpty().filter { it.startsWith("$today|") }.toMutableSet()
            kept += entry(day, slot)
            prefs.edit().putStringSet(KEY_DAILY, kept).apply()
        }
    }

    /**
     * The progress photo reminder fires once per photo cycle: [newestPhotoAt] is the newest photo's time (0 when there
     * is none). Taking a new photo starts a new cycle, so the next reminder can fire.
     */
    fun photoCycleSent(newestPhotoAt: Long): Boolean = runCatching {
        prefs.getLong(KEY_PHOTO_CYCLE, Long.MIN_VALUE) == newestPhotoAt
    }.getOrDefault(false)

    fun markPhotoCycleSent(newestPhotoAt: Long) {
        runCatching { prefs.edit().putLong(KEY_PHOTO_CYCLE, newestPhotoAt).apply() }
    }

    private fun entry(day: LocalDate, slot: String) = "$day|$slot"

    private companion object {
        const val PREFS = "intake_reminders"
        const val KEY_DAILY = "daily_sent"
        const val KEY_PHOTO_CYCLE = "photo_cycle_newest_at"
    }
}
