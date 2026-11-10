package com.zeroindex.intake.domain.settings

/**
 * The monthly "export now" reminder (Settings > Reminders). It exists because without sync the phone holds the only
 * copy of the data. It is off while sync is paired (the server keeps a copy), and it is a switch.
 */
object ExportReminderRule {
    const val INTERVAL_DAYS = 30
    private const val DAY_MS = 86_400_000L

    /**
     * @param enabled the Settings switch
     * @param syncPaired true when a sync server is paired: no reminder then
     * @param lastExportAt millis of the last export, or null if there never was one
     * @param firstUseAt millis the app was first used; the clock starts here when nothing was ever exported
     */
    fun isDue(enabled: Boolean, syncPaired: Boolean, lastExportAt: Long?, firstUseAt: Long?, now: Long): Boolean {
        if (!enabled || syncPaired) return false
        val since = lastExportAt ?: firstUseAt ?: return false
        return now - since >= INTERVAL_DAYS * DAY_MS
    }

    /** "It has been 5 weeks since your last export." or "You have not exported yet." */
    fun message(lastExportAt: Long?, now: Long): String {
        val last = lastExportAt ?: return "You have not exported your data yet."
        val days = ((now - last) / DAY_MS).toInt()
        return if (days < 14) "It has been $days days since your last export." else "It has been ${days / 7} weeks since your last export."
    }
}
