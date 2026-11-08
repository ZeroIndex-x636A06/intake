package com.zeroindex.intake.domain.nfc

import com.zeroindex.intake.domain.model.MealSlot
import java.time.LocalTime

/**
 * Amendment §2: what an NFC tag on a meal-prep container holds - a deep link
 * naming one meal template, `intake://log-template/<uuid>`.
 *
 * The tag carries only an id. The foods and their amounts stay in the app's
 * database, so a lost container gives away nothing, and editing the template
 * changes what the same tag logs without rewriting it.
 */
object TemplateLink {
    const val SCHEME = "intake"
    const val HOST = "log-template"

    private val UUID_PATTERN = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

    fun uriFor(templateId: String): String = "$SCHEME://$HOST/$templateId"

    /** The template id, or null for anything that is not one of ours. */
    fun parse(uri: String?): String? {
        if (uri == null) return null
        val prefix = "$SCHEME://$HOST/"
        if (!uri.startsWith(prefix, ignoreCase = true)) return null
        val id = uri.substring(prefix.length).trimEnd('/').substringBefore('?')
        return id.takeIf { UUID_PATTERN.matches(it) }
    }

    /**
     * "Logs to the current slot": the meal for the time of day the container
     * is tapped. Same boundaries as Today's big Add button.
     */
    fun slotFor(time: LocalTime): MealSlot = when {
        time.hour < 10 -> MealSlot.BREAKFAST
        time.hour < 15 -> MealSlot.LUNCH
        time.hour < 21 -> MealSlot.DINNER
        else -> MealSlot.SNACK
    }
}
