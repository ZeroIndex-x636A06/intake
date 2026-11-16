package com.zeroindex.intake.domain.allergen

/**
 * What a scan needs, narrowed once so the matcher does not redo it per food: only the keywords of the allergens the
 * person avoids (the matcher filters to them anyway, so the result is identical). Pure, so it is tested against the
 * unnarrowed inputs.
 */
object ScanIndexBuilder {
    fun relevantAliases(aliases: List<AllergenAlias>, avoided: Set<String>): List<AllergenAlias> =
        if (avoided.isEmpty()) emptyList() else aliases.filter { it.allergen.trim().lowercase() in avoided }
}
