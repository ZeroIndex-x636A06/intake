package com.zeroindex.intake.domain.allergen

/**
 * Allergen verdicts for a meal template, which holds several foods. Local and deterministic, and it only ever
 * combines: nothing here removes or softens a match any food's own scan raised.
 */
object TemplateAllergens {

    /** One scan for the whole template: every food's matches together, so the worst tier of any food is the verdict. */
    fun merge(scans: List<AllergenScanResult>): AllergenScanResult =
        if (scans.isEmpty()) AllergenScanResult.CLEAN else AllergenScanResult(scans.flatMap { it.matches })

    /**
     * The words added to the "Logged ..." message when a template holds a food the engine did not block but flagged:
     * "Check the label: milk (Almond milk)". Null when nothing needs a second look. [items] are food name and scan.
     */
    fun logNote(items: List<Pair<String, AllergenScanResult>>): String? {
        val parts = items.mapNotNull { (name, scan) ->
            val term = when {
                scan.mayContain.isNotEmpty() -> "may contain " + scan.mayContain.terms()
                scan.checkLabel.isNotEmpty() -> "check the label for " + scan.checkLabel.terms()
                else -> null
            }
            term?.let { "$name: $it" }
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString("; ")?.replaceFirstChar { it.uppercase() }
    }

    private fun List<AllergenMatch>.terms(): String = map { it.allergen.trim().lowercase() }.distinct().joinToString(", ")
}
