package com.zeroindex.intake.domain.backup

/**
 * Plain-language text for every way an import can fail. The raw exception or serializer text is never shown to
 * the person; at most a field name is pulled out of it. Pure Kotlin so the mapping is unit-tested.
 */
object ImportMessages {
    const val DAMAGED = "This backup is damaged or incomplete. The file may have been cut off while it was copied or " +
        "downloaded. Nothing was imported."
    const val NOT_BACKUP = "That file is not an Intake backup. Nothing was imported."
    const val NO_SCHEMA = "This backup is missing information the app needs, so it cannot be imported. Nothing was imported."
    const val NO_TABLES = "This backup has no data in it. Nothing was imported."
    const val UNREADABLE = "Something went wrong while reading that backup. Nothing was imported."
    const val WRONG_PASSPHRASE = "That passphrase did not open it. Try again."

    fun newerVersion(): String =
        "This backup was made by a newer version of Intake. Update the app first, then import it again. Nothing was imported."

    /** The text could not be parsed as a JSON object. A cut-off backup still starts like one. */
    fun notJson(text: String): String {
        val t = text.trimStart()
        return if (t.startsWith("{") && t.contains("\"format\"")) DAMAGED else NOT_BACKUP
    }

    /** A row of [table] did not decode. [raw] is the serializer's message, used only to find a field name. */
    fun badTable(table: String, raw: String?): String {
        val label = table.replace('_', ' ')
        val field = fieldName(raw)
        return if (field != null) {
            "A value in the $label data (the \"$field\" field) is damaged, so the backup cannot be imported. Nothing was imported."
        } else {
            "Some $label data in this backup is damaged, so it cannot be imported. Nothing was imported."
        }
    }

    private val requiredField = Regex("""Field '([A-Za-z_][\w]*)' is required""")
    private val pathField = Regex("""at path: \$((?:\.[A-Za-z_]\w*|\[\d+])*)""")
    private val segment = Regex("""\.([A-Za-z_]\w*)""")

    internal fun fieldName(raw: String?): String? {
        if (raw == null) return null
        requiredField.find(raw)?.let { return it.groupValues[1] }
        val path = pathField.find(raw)?.groupValues?.get(1) ?: return null
        return segment.findAll(path).lastOrNull()?.groupValues?.get(1)
    }
}
