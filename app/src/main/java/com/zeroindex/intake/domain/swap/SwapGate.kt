package com.zeroindex.intake.domain.swap

import com.zeroindex.intake.domain.allergen.AllergenMatch
import com.zeroindex.intake.domain.allergen.AllergenScanResult
import com.zeroindex.intake.domain.allergen.MatchField
import com.zeroindex.intake.domain.model.AllergenMatchKind

/**
 * The allergen gate in front of every Swap choice. A choice is gated on the worse of two things: the live local scan and
 * the verdict the option already carries (a saved eat-out option's stored verdict can only raise the live one, never
 * lower it). A flagged result goes to the standard warning sheet, the same as every other log path.
 */
object SwapGate {

    /**
     * [live] raised to [verdict]: when the option's verdict is "contains" or "may contain" and the live scan does not
     * say so itself, a note-only match is added so the warning sheet still opens. A scan that already says as much, or
     * more, is returned as it is.
     */
    fun withVerdict(live: AllergenScanResult, verdict: SwapAllergen): AllergenScanResult {
        val kind = when (verdict) {
            SwapAllergen.CONTAINS -> AllergenMatchKind.CONTAINS
            SwapAllergen.MAY_CONTAIN -> AllergenMatchKind.MAY_CONTAIN
            else -> return live
        }
        val covered = if (kind == AllergenMatchKind.CONTAINS) live.blocks else live.hasAnything
        if (covered) return live
        val raised = AllergenMatch(
            allergen = "allergen",
            matchedTerm = "an allergen you avoid",
            field = MatchField.NAME,
            kind = kind,
            note = "This option was marked as a possible allergen match when it was listed.",
        )
        return AllergenScanResult(live.matches + raised)
    }

    /** What a tap on "Choose" does for a scan: log, or stop at the warning sheet. */
    sealed interface Decision {
        data object Proceed : Decision
        data class Warn(val scan: AllergenScanResult) : Decision
    }

    fun decide(scan: AllergenScanResult): Decision = if (scan.hasAnything) Decision.Warn(scan) else Decision.Proceed
}
