package com.zeroindex.intake.domain.allergen

import com.zeroindex.intake.domain.swap.SwapOption

/** What the "Choose anyway" warning dialog says. Built from the option's own local-engine evidence. */
data class OverridePrompt(
    val optionKey: String,
    val title: String,
    val allergens: List<String>,
    val evidence: List<String>,
) {
    val headline: String get() = "Contains ${AllergenOverride.nameList(allergens)}"
    val consequence: String get() = AllergenOverride.CONSEQUENCE
}

/**
 * The person's explicit override of a "contains" verdict (docs/ALLERGENS.md, "Choosing anyway"). It is a user action:
 * it logs the item with the warning kept on the entry, and it never changes any status to clear. A model or a file
 * can never trigger it. Only a blocked option has a prompt, so an option that is not blocked cannot be "overridden".
 */
object AllergenOverride {

    const val CONSEQUENCE = "Intake will log this, but the allergen warning stays on the entry."

    /** The dialog for [option], or null when it is not blocked (there is nothing to override). */
    fun prompt(option: SwapOption): OverridePrompt? {
        if (!option.blocked) return null
        return OverridePrompt(
            optionKey = option.key,
            title = option.title,
            allergens = option.matchedAllergens.ifEmpty { emptyList() },
            evidence = option.evidence.ifEmpty { listOfNotNull(option.allergenReason) },
        )
    }

    /** "dairy", "dairy and egg", "dairy, egg and soy"; "an allergen you avoid" when none is named. */
    fun nameList(allergens: List<String>): String = when (allergens.size) {
        0 -> "an allergen you avoid"
        1 -> allergens[0]
        else -> allergens.dropLast(1).joinToString(", ") + " and " + allergens.last()
    }

    /** The words kept on the log entry: "Logged despite: dairy". */
    fun loggedDespite(allergens: List<String>): String =
        "Logged despite: " + allergens.joinToString(", ").ifBlank { "allergen warning" }

    /**
     * The note for an entry logged after the person tapped "Log it anyway" on a warning sheet: every allergen the scan
     * raised (contains and may contain), so the entry keeps what it was logged despite.
     */
    fun noteFor(scan: AllergenScanResult): String =
        loggedDespite((scan.contains + scan.mayContain).map { it.allergen.trim().lowercase() }.distinct())

    /** True when an entry's text already says it was logged despite a warning (the person chose it before). */
    fun alreadyNoted(text: String): Boolean = text.contains("Logged despite", ignoreCase = true)

    /** The entry's name and serving label with the override note added. Applying it twice adds nothing new. */
    fun annotate(name: String, servingLabel: String?, note: String): Pair<String, String> {
        val newName = if (name.contains(note, ignoreCase = true)) name else "$name ($note)"
        val newLabel = when {
            servingLabel.isNullOrBlank() -> note
            servingLabel.contains(note, ignoreCase = true) -> servingLabel
            else -> "$servingLabel · $note"
        }
        return newName to newLabel
    }

    private val NOTE_IN_PARENS = Regex("\\s*\\(\\s*Logged despite:[^)]*\\)", RegexOption.IGNORE_CASE)
    private val NOTE_IN_LABEL = Regex("\\s*(?:\u00b7\\s*)?Logged despite:[^\u00b7()]*", RegexOption.IGNORE_CASE)

    /**
     * [text] without any override note this app added ("Baked mac and cheese (Logged despite: dairy)" becomes "Baked
     * mac and cheese"). The note is a record of what a person chose, never part of a food's name, so it is never copied
     * into a template or a name and never read by the allergen matcher (its word "dairy" is not an ingredient).
     */
    fun stripNote(text: String): String =
        text.replace(NOTE_IN_PARENS, "").replace(NOTE_IN_LABEL, "").trim()

    /** [stripNote] for text that may be absent; blank becomes null. */
    fun stripNoteOrNull(text: String?): String? = text?.let(::stripNote)?.takeIf { it.isNotEmpty() }
}
