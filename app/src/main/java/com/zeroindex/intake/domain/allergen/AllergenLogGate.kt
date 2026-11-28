package com.zeroindex.intake.domain.allergen

/**
 * The one rule every path that creates a food log entry follows (docs/ALLERGENS.md, "Where it shows"):
 *
 * 1. Scan what is about to be logged with the local engine, as it is now.
 * 2. A CONTAINS or MAY_CONTAIN verdict is never logged silently. A person sees the warning sheet and chooses
 *    "Log it anyway"; the entry then keeps the note from [overrideNote].
 * 3. A surface with no sheet (the watch) refuses instead, and sends the person to the phone ([forWatch]).
 *
 * `LogPathAuditTest` lists every source file that writes a food log and fails when a new one is not accounted for,
 * so a new log path cannot skip this gate unnoticed. Nothing here ever clears or softens a verdict.
 */
object AllergenLogGate {

    /** What a log attempt does next. */
    sealed interface Decision {
        /** Nothing to review (clear, check-the-label, or mentioned as avoided): log it. */
        data object Proceed : Decision

        /** The person must see the warning sheet for [scan] first. */
        data class Review(val scan: AllergenScanResult) : Decision
    }

    fun decide(scan: AllergenScanResult): Decision = if (scan.hasAnything) Decision.Review(scan) else Decision.Proceed

    /** The "Logged despite: ..." note for an entry logged after the person's own "Log it anyway". */
    fun overrideNote(scan: AllergenScanResult): String = AllergenOverride.noteFor(scan)

    /** What a surface that cannot show the warning sheet (the watch) does with a scan. */
    sealed interface WatchDecision {
        data object Allow : WatchDecision

        /** A CONTAINS match: refused, and the watch is told which words matched. */
        data class Blocked(val terms: List<String>) : WatchDecision

        /** A MAY_CONTAIN match: refused, because the phone's answer is a sheet a person must read and confirm. */
        data class CheckOnPhone(val terms: List<String>) : WatchDecision
    }

    fun forWatch(scan: AllergenScanResult): WatchDecision = when {
        scan.blocks -> WatchDecision.Blocked(scan.contains.map { it.matchedTerm }.distinct())
        scan.hasAnything -> WatchDecision.CheckOnPhone(scan.mayContain.map { it.matchedTerm }.distinct())
        else -> WatchDecision.Allow
    }
}
