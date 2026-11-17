package com.zeroindex.intake.domain.plan

import com.zeroindex.intake.domain.allergen.AllergenScanResult

/**
 * Which shopping items carry an allergen marker for this person. The local engine reads the item name; an item that
 * matches one of the person's allergens is marked, never hidden. A clear result gets no marker at all: a bare item name
 * says nothing about the product on the shelf, so the list never shows a "fits" tick.
 */
object ShoppingAllergenMarks {

    /**
     * True when [scan] earns a marker: "contains" or "may contain" always; the softer "check the label" tier only when
     * [includeCheckLabel] (screens that do not already show a check-the-label chip of their own).
     */
    fun marks(scan: AllergenScanResult, includeCheckLabel: Boolean): Boolean =
        scan.hasAnything || (includeCheckLabel && scan.hasCheckLabel)

    /** The items worth a marker, by key, from a scan of each. */
    fun flagged(scans: Map<String, AllergenScanResult>, includeCheckLabel: Boolean): Map<String, AllergenScanResult> =
        scans.filterValues { marks(it, includeCheckLabel) }
}
