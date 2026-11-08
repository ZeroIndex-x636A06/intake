package com.zeroindex.intake.domain.allergen

import com.zeroindex.intake.domain.model.AllergenMatchKind
import com.zeroindex.intake.domain.model.KeywordLevel
import java.util.concurrent.ConcurrentHashMap

/**
 * One keyword for an allergen, e.g. dairy -> "sodium caseinate". [level] is the strongest verdict a hit
 * on it can give. Keywords the user switched off are filtered out before they reach the engine.
 */
data class AllergenAlias(
    val allergen: String,
    val aliasTerm: String,
    val level: KeywordLevel = KeywordLevel.CONTAINS,
)

/** Where a match was found, so the warning can say exactly that (SPEC §7.2). */
enum class MatchField { NAME, BRAND, INGREDIENTS, STRUCTURED_TAG }

data class AllergenMatch(
    val allergen: String,
    val matchedTerm: String,
    val field: MatchField,
    val kind: AllergenMatchKind,
    /** Short human explanation, shown verbatim in the warning sheet. */
    val note: String,
    /** One plain line for lists and the import preview: "Says butter", "Mentions butter as something to avoid". */
    val reason: String = note,
)

data class AllergenScanResult(
    val matches: List<AllergenMatch>,
) {
    val contains: List<AllergenMatch> = matches.filter { it.kind == AllergenMatchKind.CONTAINS }
    val mayContain: List<AllergenMatch> = matches.filter { it.kind == AllergenMatchKind.MAY_CONTAIN }

    /** The softest warning tier: "Check the label". Never blocks, never opens a sheet. */
    val checkLabel: List<AllergenMatch> = matches.filter { it.kind == AllergenMatchKind.CHECK_LABEL }

    /**
     * Free text that names the allergen only to say it is left out ("no cheese"). Informational: not a
     * block, not a problem, shown only in the import preview and in details.
     */
    val mentionedAvoided: List<AllergenMatch> = matches.filter { it.kind == AllergenMatchKind.MENTIONED_AVOIDED }

    /** True when the save must be blocked behind the red sheet (SPEC §7.2). */
    val blocks: Boolean = contains.isNotEmpty()

    /** True when something stronger than "check the label" was found (the sheets key off this). */
    val hasAnything: Boolean = contains.isNotEmpty() || mayContain.isNotEmpty()
    val hasCheckLabel: Boolean = checkLabel.isNotEmpty()
    val hasMentionedAvoided: Boolean = mentionedAvoided.isNotEmpty()

    companion object {
        val CLEAN = AllergenScanResult(emptyList())
    }
}

/**
 * SPEC §7.1: runs the name, brand and ingredient text against the keyword table, case-insensitively,
 * with whole-word matching so "buttermilk" matches and "butternut" does not.
 *
 * Pure Kotlin so the matching rules are unit-testable on their own - the buttermilk-versus-butternut
 * case is the one SPEC §14 calls out by name. The rules in full are in docs/ALLERGENS.md; in short:
 *
 * - **Safety rule.** Negation ("no cheese") and "-free" wording apply only to text a person wrote:
 *   names, notes, recipe ideas, eat-out wording. A product's ingredient panel and Open Food Facts tags
 *   are never negation-downgraded: "Contains: milk" stays a hard warning. See [scan]'s `freeText`.
 * - A keyword's own level is the most a hit can say. Wording can only soften it, and only in free text.
 * - Plant substitutes ("oat milk", "vegan butter") and "dairy-free <substitute>" are check-the-label
 *   with a reason, never "may contain" and never silent.
 * - "lactose-free" never negates, and "non-dairy" is check-the-label.
 */
object AllergenMatcher {

    private const val NON_DAIRY_REASON =
        "\"Non-dairy\" foods can still contain casein or other milk proteins - check the label."
    private const val CHEESE_REASON = "Some dairy-free cheeses still contain casein"
    private const val DAIRY_FREE_OTHER_REASON =
        "Some dairy-free butters and spreads still contain milk ingredients - check the label."
    private const val PLANT_MILK_REASON = "Plant milks are usually dairy-free, but check the label."
    private const val PLANT_OTHER_REASON =
        "Plant-based versions are usually dairy-free, but some contain casein - check the label."

    /** Words that follow "X-free" and name a substitute even when they are not keywords themselves. */
    private val SUBSTITUTE_NOUNS = setOf(
        "shred", "shreds", "shredded", "slice", "slices", "alternative", "alternatives", "substitute",
        "substitutes", "spread", "version", "versions", "replacement",
    )

    private val CHEESE_WORDS = setOf(
        "cheese", "mozzarella", "cheddar", "parmesan", "parmigiano", "romano", "pecorino", "provolone", "swiss",
        "gruyere", "gouda", "edam", "emmental", "brie", "camembert", "feta", "halloumi", "gorgonzola", "roquefort",
        "stilton", "jack", "colby", "havarti", "fontina", "asiago", "manchego", "queso", "cotija", "ricotta",
        "mascarpone", "paneer", "shreds", "shredded", "queso", "cheesy", "cheeseburger", "cheesesteak",
    )

    /** "lactose-free" claims are about lactose, not about milk: they never negate anything. */
    private val NEVER_NEGATING = setOf("lactose")

    private val regexCache = ConcurrentHashMap<String, Regex>()

    /** One alias as a regex: whole words, hyphen or space between words, optional plural, optional "non". */
    private fun termRegex(term: String): Regex = regexCache.getOrPut(term) {
        val words = Negation.normalize(term).trim().split(Regex("[\\s\\-]+")).filter { it.isNotEmpty() }
        val body = words.joinToString("[\\s\\-]+") { Regex.escape(it) }
        val plural = if (words.lastOrNull()?.lastOrNull()?.isLetter() == true) "(?:s|es)?" else ""
        Regex("(?<![\\p{L}\\p{N}])(non[\\-\\s]?)?($body)$plural(?![\\p{L}\\p{N}])")
    }

    private val FREE_AFTER = Regex("^(?:-|\\s+)free(?![\\p{L}\\p{N}])(?!\\s+(?:of|from)\\b)")

    fun scan(
        aliases: List<AllergenAlias>,
        avoidedAllergens: Set<String>,
        name: String?,
        brand: String? = null,
        ingredients: String? = null,
        /** Structured "contains" tags, e.g. from Open Food Facts (SPEC §7.3). */
        structuredContains: List<String> = emptyList(),
        structuredTraces: List<String> = emptyList(),
        /** Foods that commonly hide an allergen; the softest tier (docs/ALLERGENS.md). */
        checkLabelTerms: List<CheckLabelTerm> = HiddenCarriers.ALL,
        /**
         * Whether the ingredients text is free-text lines rather than a label panel. Off by default: a
         * real ingredient panel already says what is in the food, and "natural flavor" would nag on nearly
         * every label. On for plan recipe lines and Recipe Hunter lists, which have no label.
         */
        checkLabelInIngredients: Boolean = false,
        /**
         * The text was written by a person (a plan, a note, a recipe idea, eat-out wording), so a clause
         * like "no cheese" may become "mentioned as avoided". Leave false for anything that describes a
         * product: names and panels from a barcode, Open Food Facts, a model.
         */
        freeText: Boolean = false,
        /**
         * A label-like text that is never read for negation or "-free" wording: a file's allergen tags,
         * or an ingredient panel passed alongside free-text lines.
         */
        panelText: String? = null,
    ): AllergenScanResult {
        if (avoidedAllergens.isEmpty()) return AllergenScanResult.CLEAN

        val avoided = avoidedAllergens.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
        val relevant = aliases.filter { it.allergen.trim().lowercase() in avoided }
        val found = LinkedHashMap<String, AllergenMatch>()

        fun add(key: String, match: AllergenMatch) {
            val existing = found[key]
            if (existing == null || match.kind.rank > existing.kind.rank) found[key] = match
        }

        // SPEC §7.3: structured tags are the primary signal where they exist. Never downgraded.
        structuredContains.forEach { tag ->
            matchAllergenForTag(tag, avoided, relevant)?.let { allergen ->
                val term = tag.substringAfterLast(':')
                add(
                    "${allergen.lowercase()}|tag:$tag",
                    AllergenMatch(
                        allergen = allergen,
                        matchedTerm = term,
                        field = MatchField.STRUCTURED_TAG,
                        kind = AllergenMatchKind.CONTAINS,
                        note = "The product's own allergen list declares $term.",
                        reason = "The product's allergen list names $term",
                    ),
                )
            }
        }
        structuredTraces.forEach { tag ->
            matchAllergenForTag(tag, avoided, relevant)?.let { allergen ->
                val term = tag.substringAfterLast(':')
                add(
                    "${allergen.lowercase()}|trace:$tag",
                    AllergenMatch(
                        allergen = allergen,
                        matchedTerm = term,
                        field = MatchField.STRUCTURED_TAG,
                        kind = AllergenMatchKind.MAY_CONTAIN,
                        note = "The product lists $term under traces or \"may contain\".",
                        reason = "The product lists $term as a possible trace",
                    ),
                )
            }
        }

        // Second pass over free text, field by field.
        val freeIngredients = checkLabelInIngredients
        val fields = listOf(
            FieldText(MatchField.NAME, name, claims = true, negation = freeText),
            FieldText(MatchField.BRAND, brand, claims = true, negation = freeText),
            FieldText(MatchField.INGREDIENTS, ingredients, claims = freeIngredients, negation = freeText && freeIngredients),
            FieldText(MatchField.INGREDIENTS, panelText, claims = false, negation = false),
        )
        val byAllergen = relevant.groupBy { it.allergen.trim().lowercase() }
        for (ft in fields) {
            if (ft.text.isNullOrBlank()) continue
            for ((allergen, list) in byAllergen) {
                scanField(ft, allergen, list).forEach { hit ->
                    add("$allergen|${hit.term}", hit.toMatch(ft.field))
                }
            }
        }

        // Softest tier, last, and only where nothing stronger exists for that allergen: this can add a
        // nudge but can never replace or hide a stronger hit.
        val strongerFor = found.values
            .filter { it.kind == AllergenMatchKind.CONTAINS || it.kind == AllergenMatchKind.MAY_CONTAIN }
            .map { it.allergen.lowercase() }.toSet()
        val softFields = buildList {
            add(FieldText(MatchField.NAME, name, claims = true, negation = freeText))
            if (checkLabelInIngredients) add(FieldText(MatchField.INGREDIENTS, ingredients, claims = true, negation = freeText))
        }
        val seenPhrases = HashSet<String>()
        checkLabelTerms.forEach { term ->
            val allergen = term.allergen.trim().lowercase()
            if (allergen !in avoided || allergen in strongerFor) return@forEach
            var field: MatchField? = null
            var hit: String? = null
            for (ft in softFields) {
                val text = ft.text
                if (text.isNullOrBlank()) continue
                val norm = Negation.normalize(text)
                val m = term.findIn(norm) ?: continue
                if (ft.negation && isHandled(norm, m.range.first)) continue
                field = ft.field
                hit = m.value.lowercase().replace(Regex("\\s+"), " ")
                break
            }
            if (field == null || hit == null) return@forEach
            if (!seenPhrases.add(allergen + "|" + term.phrase)) return@forEach
            add(
                "$allergen|check:${term.phrase}",
                AllergenMatch(
                    allergen = allergen,
                    matchedTerm = hit,
                    field = field,
                    kind = AllergenMatchKind.CHECK_LABEL,
                    note = term.reason,
                    reason = term.reason,
                ),
            )
        }

        // Never-clear: an allergen with a CONTAINS or MAY_CONTAIN hit keeps no "check the label" nudge.
        val strongNow = found.values
            .filter { it.kind == AllergenMatchKind.CONTAINS || it.kind == AllergenMatchKind.MAY_CONTAIN }
            .map { it.allergen.lowercase() }.toSet()
        val result = found.values.filterNot {
            it.kind == AllergenMatchKind.CHECK_LABEL && it.allergen.lowercase() in strongNow
        }
        return AllergenScanResult(result)
    }

    /** True when the carrier at [position] sits in a negated clause or a "check the X for Y" span. */
    private fun isHandled(norm: String, position: Int): Boolean {
        val clause = Negation.clauses(norm).firstOrNull { position in it } ?: return false
        val reading = Negation.read(norm, clause)
        return reading.isNegated(position) || reading.isBeingChecked(position)
    }

    private class FieldText(val field: MatchField, val text: String?, val claims: Boolean, val negation: Boolean)

    private class Hit(
        val term: String,
        val kind: AllergenMatchKind,
        val note: String,
        val reason: String,
        val start: Int,
        val end: Int,
        /** Softened by wording around it (a substitute, a plant milk): it also covers shorter hits inside. */
        val softened: Boolean,
        val allergen: String,
    ) {
        fun toMatch(field: MatchField) = AllergenMatch(allergen, term, field, kind, note, reason)
    }

    private class Occurrence(
        val alias: AllergenAlias,
        val claimOnly: Boolean,
        val start: Int,
        val end: Int,
        val nonPrefix: Boolean,
        /** End of a directly following "-free" / " free", or -1. */
        val freeEnd: Int,
    )

    private fun scanField(ft: FieldText, allergen: String, aliases: List<AllergenAlias>): List<Hit> {
        val text = Negation.normalize(ft.text.orEmpty())
        if (text.isBlank()) return emptyList()
        val display = AllergenCatalog.displayName(allergen).lowercase()

        // Occurrences of every keyword, plus the allergen's own name so "nightshade-free" is read as a claim.
        val keyAlias = AllergenAlias(allergen, allergen, KeywordLevel.CONTAINS)
        val withKey = if (aliases.any { it.aliasTerm.trim().lowercase() == allergen }) aliases else aliases + keyAlias
        val occurrences = ArrayList<Occurrence>()
        for (alias in withKey) {
            val term = alias.aliasTerm.trim()
            if (term.isEmpty()) continue
            for (m in termRegex(term).findAll(text)) {
                val core = m.groups[2]!!
                val nonGroup = m.groups[1]
                val start = nonGroup?.range?.first ?: core.range.first
                val end = m.range.last + 1
                val free = FREE_AFTER.find(text.substring(end))?.let { end + it.value.length } ?: -1
                occurrences += Occurrence(
                    alias, alias === keyAlias,
                    start, end, nonGroup != null, free,
                )
            }
        }
        if (occurrences.isEmpty()) return emptyList()

        val clauses = Negation.clauses(text)
        val readings: Map<IntRange, Negation.Reading> =
            if (ft.negation) clauses.associateWith { Negation.read(text, it) } else emptyMap()
        fun clauseOf(position: Int) = clauses.firstOrNull { position in it }

        // Exclusions: "peanut butter" is not butter, "almond flour" is not wheat flour.
        val exclusions = AllergenCatalog.NOT_THE_ALLERGEN[allergen].orEmpty().flatMap { phrase ->
            termRegex(phrase).findAll(text).map { (it.groups[2]!!.range.first) until (it.range.last + 1) }.toList()
        }

        // "-free" claims for this allergen, per clause (lactose-free is not one).
        val claims = if (ft.claims) {
            occurrences.filter { it.freeEnd >= 0 && it.alias.aliasTerm.trim().lowercase() !in NEVER_NEGATING }
        } else {
            emptyList()
        }

        val hits = ArrayList<Hit>()
        for (o in occurrences) {
            val term = o.alias.aliasTerm.trim().lowercase()
            // Self-hit: the allergen's own name inside its free-claim is not a hit. Always, in every field.
            if (o.freeEnd >= 0 && term !in NEVER_NEGATING) continue
            if (o.claimOnly) continue
            if (exclusions.any { o.start >= it.first && o.end <= it.last + 1 }) continue

            val clause = clauseOf(o.start) ?: continue
            val level = o.alias.level.kind
            val shown = if (o.freeEnd >= 0) "$term-free" else term

            // 1. Negation, only in text a person wrote.
            if (ft.negation && readings[clause]?.isNegated(o.start) == true) {
                hits += Hit(
                    term, AllergenMatchKind.MENTIONED_AVOIDED,
                    note = "Mentions \"$term\" as something to avoid.",
                    reason = "Mentions $term as something to avoid",
                    start = o.start, end = o.end, softened = true, allergen = allergen,
                )
                continue
            }

            // 2. Plant substitutes: always check-the-label, in every field.
            if (allergen == AllergenCatalog.DAIRY && plantQualifierBefore(text, o.start, clause.first)) {
                val reason = if (term == "milk") PLANT_MILK_REASON else PLANT_OTHER_REASON
                hits += Hit(term, AllergenMatchKind.CHECK_LABEL, reason, reason, o.start, o.end, true, allergen)
                continue
            }

            if (ft.claims) {
                // 3. "non-dairy".
                if (o.nonPrefix) {
                    hits += Hit(term, AllergenMatchKind.CHECK_LABEL, NON_DAIRY_REASON, NON_DAIRY_REASON, o.start, o.end, true, allergen)
                    continue
                }
                // 4. A "-free" claim just before: a substitute.
                val claim = claims.firstOrNull { c ->
                    c.freeEnd <= o.start && clause.first <= c.start &&
                        Negation.tokens(text, c.freeEnd, o.start).size <= 3 &&
                        clauseOf(c.start) == clause
                }
                if (claim != null) {
                    val reason = substituteReason(allergen, term, claim.alias.aliasTerm)
                    hits += Hit(term, AllergenMatchKind.CHECK_LABEL, reason, reason, o.start, o.end, true, allergen)
                    continue
                }
                // Same clause says "-free <substitute>": a hit that only MAY applies softens to a check.
                if (level == AllergenMatchKind.MAY_CONTAIN && claims.any { clauseOf(it.start) == clause }) {
                    val reason = substituteReason(allergen, term, claims.first { clauseOf(it.start) == clause }.alias.aliasTerm)
                    hits += Hit(term, AllergenMatchKind.CHECK_LABEL, reason, reason, o.start, o.end, true, allergen)
                    continue
                }
            }

            // 5. The keyword says what it says.
            val note = when {
                o.freeEnd >= 0 -> "\"$shown\" - lactose-free dairy still contains milk."
                level == AllergenMatchKind.CONTAINS -> "\"$term\" found in the ${ft.field.describe()}."
                level == AllergenMatchKind.MAY_CONTAIN ->
                    "\"$term\" found in the ${ft.field.describe()} - often made with $display, but not always."
                else -> "\"$term\" found in the ${ft.field.describe()} - check the label for $display."
            }
            val reason = when {
                o.freeEnd >= 0 -> "Says $shown, which is still dairy"
                level == AllergenMatchKind.CONTAINS -> "Says $term"
                level == AllergenMatchKind.MAY_CONTAIN -> "Says $term, which often contains $display"
                else -> "Says $term - check the label for $display"
            }
            hits += Hit(term, level, note, reason, o.start, o.end, false, allergen)
        }

        // "X-free <substitute noun>" with no keyword after it: "dairy-free shreds".
        for (c in claims) {
            val clause = clauseOf(c.start) ?: continue
            val after = Negation.tokens(text, c.freeEnd, clause.last + 1).take(3)
            val covered = hits.any { it.start >= c.freeEnd && it.start <= (after.lastOrNull()?.second ?: c.freeEnd) }
            val noun = after.firstOrNull { it.first in SUBSTITUTE_NOUNS } ?: continue
            if (covered) continue
            if (ft.negation && readings[clause]?.isNegated(c.start) == true) continue
            val claimWord = c.alias.aliasTerm.trim().lowercase()
            val reason = substituteReason(allergen, noun.first, claimWord)
            hits += Hit(
                "$claimWord-free ${noun.first}", AllergenMatchKind.CHECK_LABEL, reason, reason,
                c.start, noun.second + noun.first.length, true, allergen,
            )
        }

        return dropCovered(hits)
    }

    /** A shorter hit lying inside a longer one is dropped, unless that would weaken the verdict. */
    private fun dropCovered(hits: List<Hit>): List<Hit> = hits.filter { b ->
        hits.none { a ->
            a !== b && a.start <= b.start && a.end >= b.end && (a.end - a.start) > (b.end - b.start) &&
                (a.kind.rank >= b.kind.rank || a.softened)
        }
    }

    private fun substituteReason(allergen: String, hitTerm: String, claimTerm: String): String {
        if (allergen == AllergenCatalog.DAIRY) {
            val words = hitTerm.split(' ', '-')
            return if (words.any { it in CHEESE_WORDS }) CHEESE_REASON else DAIRY_FREE_OTHER_REASON
        }
        return "Some \"${claimTerm.trim()}-free\" versions still contain it - check the label."
    }

    /** "oat milk", "vegan butter", "plant-based cheese": the word right before the keyword. */
    private fun plantQualifierBefore(text: String, start: Int, clauseStart: Int): Boolean {
        val before = Negation.tokens(text, clauseStart, start)
        val last = before.lastOrNull() ?: return false
        if (text.substring(last.second + last.first.length, start).any { it.isLetterOrDigit() }) return false
        if (last.first in AllergenCatalog.PLANT_QUALIFIERS) return true
        if (last.first == "based" && before.size >= 2 && before[before.size - 2].first == "plant") return true
        return false
    }

    private fun MatchField.describe(): String = when (this) {
        MatchField.NAME -> "name"
        MatchField.BRAND -> "brand"
        MatchField.INGREDIENTS -> "ingredients"
        MatchField.STRUCTURED_TAG -> "product allergen tags"
    }

    /**
     * Open Food Facts tags look like "en:milk", so the useful part is the last segment. A tag names an
     * ingredient, not an allergen category, so it is resolved through the keyword table: "milk" is how
     * the panel says "dairy".
     */
    private fun matchAllergenForTag(
        tag: String,
        avoided: Set<String>,
        relevant: List<AllergenAlias>,
    ): String? {
        val bare = tag.substringAfterLast(':').trim().lowercase().replace('-', ' ')
        if (bare.isEmpty()) return null
        avoided.firstOrNull { it == bare }?.let { return it }
        relevant.firstOrNull { it.aliasTerm.trim().lowercase() == bare }?.let { return it.allergen }
        return relevant.firstOrNull { termRegex(it.aliasTerm).containsMatchIn(bare) }?.allergen
    }
}
