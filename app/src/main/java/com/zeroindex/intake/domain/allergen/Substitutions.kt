package com.zeroindex.intake.domain.allergen

import com.zeroindex.intake.domain.recipe.IngredientAmount
import com.zeroindex.intake.domain.recipe.IngredientAmounts
import kotlin.math.roundToInt

/**
 * Recipe Hunter's "Could be adapted" rules (docs/ALLERGENS.md, "Adapting a recipe"). Deterministic and local: no
 * model decides whether a recipe can be adapted, and nothing here ever clears an allergen warning.
 *
 * The engine answers one question for a recipe that the allergen scan blocked: is the allergen a small,
 * replaceable part of the dish, or is it the dish? It reads the parsed ingredient lines and works from concepts:
 *
 * - **Swap classes.** Each allergen has classes of ingredient (cheese, butter, milk, cream, egg as binder, pasta,
 *   soy sauce, nuts as a topping, ...) with named substitutes in order of preference. A substitute that carries
 *   another allergen the person avoids is skipped (oat milk for someone avoiding gluten, tamari for soy).
 * - **Role and quantity.** A class is substitutable only while it stays a minor part of the dish: its share of
 *   the recipe's approximate weight must stay under the class limit. Between the "borderline" and the "limit"
 *   share the swap is offered with a warning that the result will not be the same dish.
 * - **Count cap.** More than [MAX_ALLERGEN_LINES] distinct ingredient lines carrying avoided allergens means the
 *   recipe is built on them.
 * - **Defining dishes.** A title that names a dish whose identity is the allergen (cheesecake, custard, alfredo,
 *   tres leches, meringue, souffle, brioche, ...) is refused whatever the amounts. A title that merely names the
 *   ingredient is refused once the ingredient is a real share of the recipe.
 * - **Baking chemistry.** In a baked good, three or more eggs or a flour share beyond a threshold is the
 *   structure of the product, not a binder.
 *
 * Anything the engine cannot place is refused: a flagged line with no class, an allergen the scan found that no
 * ingredient line explains, an unknown amount. Refusing is always the safe side. Allergen status of the original
 * recipe is never changed; the caller re-scans the adapted lines and only a copy whose ingredients scan clean of
 * blocking matches can be saved.
 */
object Substitutions {
    /** At most this many distinct ingredient lines may carry an avoided allergen. */
    const val MAX_ALLERGEN_LINES = 3

    /** When all of one allergen's lines together are more than this share of the recipe, the allergen is the base. */
    const val MAX_ALLERGEN_TOTAL_SHARE = 0.40

    /** Eggs at or above this count in a baked good are its structure. */
    const val BAKED_EGG_COUNT = 3

    /** Eggs above this share of a baked good's weight are its structure. */
    const val BAKED_EGG_SHARE = 0.20

    /**
     * @param lineScans one scan per ingredient line (same order as [ingredients]), each of that line alone.
     * @param overall the scan of the whole recipe (title and all lines, plus anything a model claimed). It decides
     *   whether the recipe is blocked at all; every blocking allergen in it must be explained by a line.
     */
    fun assess(
        title: String,
        ingredients: List<String>,
        avoided: Set<String>,
        lineScans: List<AllergenScanResult>,
        overall: AllergenScanResult,
    ): Adaptation {
        val blocking = overall.contains.map { it.allergen }.toSet()
        if (blocking.isEmpty()) return Adaptation.AlreadyFine
        if (lineScans.size != ingredients.size) {
            return Adaptation.NotAdaptable(listOf("The ingredient lines could not be checked one by one"))
        }

        val reasons = LinkedHashSet<String>()
        val parsed = ingredients.map { IngredientAmounts.parse(it) }
        val matches = ingredients.map { line -> matchClasses(line) }
        val grams = ingredients.indices.map { i -> approxGrams(parsed[i], matches[i].firstOrNull()?.cls) }
        val total = grams.sum().coerceAtLeast(1.0)

        // Which lines carry which blocking allergen, from each line's own scan.
        val flagged: List<Set<String>> = lineScans.map { s -> s.contains.map { it.allergen }.toSet() intersect blocking }
        val flaggedLines = flagged.indices.filter { flagged[it].isNotEmpty() }
        val explained = flagged.flatten().toSet()
        for (a in blocking - explained) {
            reasons += "The ${name(a)} warning does not come from a single ingredient line, so there is nothing to swap"
        }

        // 1. Defining dishes, by concept, for each allergen that really is in the recipe.
        for (a in blocking) {
            DEFINING[a]?.firstOrNull { it.first.containsMatchIn(title) }?.let { (_, what) ->
                reasons += "$what: ${name(a)} is what the dish is, so no swap would leave the same recipe"
            }
        }

        // 2. Too many allergen lines.
        if (flaggedLines.size > MAX_ALLERGEN_LINES) {
            reasons += "${flaggedLines.size} ingredient lines carry ${joinNames(blocking)}, more than $MAX_ALLERGEN_LINES swaps would be a different recipe"
        }

        // 3. Per-allergen total share.
        for (a in blocking) {
            val g = flaggedLines.filter { a in flagged[it] }.sumOf { grams[it] }
            val share = g / total
            if (share > MAX_ALLERGEN_TOTAL_SHARE) {
                reasons += "${name(a).replaceFirstChar(Char::uppercase)} ingredients make up about ${pct(share)} of the recipe by weight, so they are its base"
            }
        }

        // 4. Per-line swaps.
        val baked = isBakedGood(title, ingredients)
        val swaps = ArrayList<Swap>()
        val classShare = HashMap<String, Double>()
        for (i in flaggedLines) for (m in matches[i]) classShare.merge(m.cls.id, grams[i] / total, Double::plus)
        val eggCount = ingredients.indices.sumOf { i ->
            matches[i].firstOrNull { it.cls.id == "egg" }?.let { (parsed[i].value ?: 1.0) * (if (parsed[i].unit == null) 1.0 else 0.0) } ?: 0.0
        }

        for (i in flaggedLines) {
            val line = ingredients[i]
            val lineAllergens = flagged[i]
            val usable = matches[i].filter { m -> m.cls.allergens.any { it in lineAllergens } }
            val covered = usable.flatMap { it.cls.allergens }.toSet()
            val uncovered = lineAllergens - covered
            val optional = parsed[i].optional || parsed[i].toTaste
            if (usable.isEmpty() || uncovered.isNotEmpty()) {
                if (optional && grams[i] / total <= 0.05) {
                    swaps += Swap(i, lineAllergens.first(), line, label(line), "leave it out", SwapConfidence.HIGH, "It is optional.", null)
                } else {
                    reasons += "No safe like-for-like swap for \"${label(line)}\" (${joinNames(uncovered.ifEmpty { lineAllergens })})"
                }
                continue
            }
            for (m in usable) {
                val c = m.cls
                val sub = c.subs.firstOrNull { s -> s.carries.none { it in avoided } }
                if (sub == null) {
                    reasons += "The usual swap for ${c.label} contains something else you avoid"
                    continue
                }
                val share = classShare[c.id] ?: 0.0
                val limit = if (baked) c.bakedMaxShare ?: c.maxShare else c.maxShare
                var confidence = sub.confidence
                var note = sub.note
                if (c.id == "egg" && baked && (eggCount >= BAKED_EGG_COUNT || share >= BAKED_EGG_SHARE)) {
                    reasons += "Eggs are the structure of this baked recipe (${eggCount.roundToInt()} eggs, about ${pct(share)} of its weight); a flax egg cannot hold it up"
                    continue
                }
                if (share > limit) {
                    reasons += "${c.label.replaceFirstChar(Char::uppercase)} is about ${pct(share)} of the recipe by weight, so it is a main ingredient and not an add-on"
                    continue
                }
                if (share > c.borderShare) {
                    confidence = SwapConfidence.LOW
                    note = listOfNotNull(note, "A large share of this recipe (about ${pct(share)}): it will not taste or feel the same").joinToString(". ")
                }
                if (c.titleWords?.containsMatchIn(title) == true && share >= c.titleShare) {
                    reasons += "${c.label.replaceFirstChar(Char::uppercase)} is named in the title and is about ${pct(share)} of the recipe, so it is the point of the dish"
                    continue
                }
                if (c.id == "egg" && eggCount <= 1.0 && confidence == SwapConfidence.MEDIUM) confidence = SwapConfidence.HIGH
                val range = m.range
                val from = line.substring(range).trim()
                swaps += Swap(i, c.allergens.first { it in lineAllergens }, line, from.lowercase(), sub.display(from), confidence, note, null, range, c, sub)
            }
        }
        if (reasons.isNotEmpty()) return Adaptation.NotAdaptable(reasons.toList())

        // Build the adapted lines: replacements applied right to left so earlier spans stay valid.
        val adapted = ingredients.toMutableList()
        val removed = HashSet<Int>()
        for (i in swaps.map { it.lineIndex }.distinct()) {
            val lineSwaps = swaps.filter { it.lineIndex == i }
            if (lineSwaps.any { it.range == null }) {
                removed += i
                continue
            }
            var text = ingredients[i]
            for (s in lineSwaps.sortedByDescending { it.range!!.first }) {
                text = text.replaceRange(s.range!!, s.to)
            }
            adapted[i] = text
        }
        val finished = swaps.map { s -> s.copy(adaptedLine = if (s.range == null) null else adapted[s.lineIndex]) }
        val adaptedLines = adapted.filterIndexed { i, _ -> i !in removed }
        var adaptedTitle = title
        for (s in finished.mapNotNull { it.cls }.distinct()) {
            adaptedTitle = s.find.replace(adaptedTitle) { mr -> s.subs.first { sub -> sub.carries.none { it in avoided } }.display(mr.value) }
        }
        val confidence = finished.minOf { it.confidence }
        val caveats = finished.mapNotNull { it.note }.distinct()
        return Adaptation.Adaptable(finished, adaptedTitle, adaptedLines, confidence, caveats)
    }

    // ---- concepts -------------------------------------------------------------------------------------

    private data class ClassMatch(val cls: SwapClass, val range: IntRange)

    /** Every class found in a line, longest and most specific first, with overlapping spans dropped. */
    private fun matchClasses(line: String): List<ClassMatch> {
        val out = ArrayList<ClassMatch>()
        for (c in CLASSES) {
            val m = c.find.find(line) ?: continue
            val r = m.range
            if (out.any { it.range.first <= r.last && r.first <= it.range.last }) continue
            out += ClassMatch(c, r)
        }
        return out
    }

    private fun isBakedGood(title: String, lines: List<String>): Boolean {
        if (BAKED_TITLE.containsMatchIn(title)) return true
        val text = lines.joinToString("\n").lowercase()
        val flour = Regex("""\bflour\b""").containsMatchIn(text)
        val leaven = Regex("""baking (?:powder|soda)|bicarbonate|\byeast\b""").containsMatchIn(text)
        return flour && leaven
    }

    private fun approxGrams(a: IngredientAmount, cls: SwapClass?): Double {
        if (a.toTaste && a.value == null) return 1.0
        a.statedGrams?.let { return it }
        val v = a.value ?: return if (a.unit == null) 5.0 else 1.0
        val cup = cls?.cupGrams ?: 200.0
        val each = cls?.eachGrams ?: 80.0
        val size = when (a.size) { "small" -> 0.75; "large" -> 1.25; else -> 1.0 }
        val per = when (a.unit) {
            "cup" -> cup
            "tbsp" -> cup / 16.0
            "tsp" -> cup / 48.0
            "floz" -> 30.0
            "ml" -> 1.0
            "l" -> 1000.0
            "oz" -> 28.35
            "lb" -> 453.6
            "g" -> 1.0
            "kg" -> 1000.0
            "pinch", "dash" -> 0.4
            "clove" -> 4.0
            "slice" -> cls?.sliceGrams ?: 25.0
            "can" -> 400.0
            "jar" -> 300.0
            "package", "packet" -> 250.0
            "stick" -> 113.0
            "bunch" -> 100.0
            "sprig" -> 2.0
            "head" -> 500.0
            "piece" -> 80.0
            "stalk" -> 40.0
            "leaf" -> 1.0
            "fillet" -> 150.0
            "cube" -> 10.0
            else -> each * size
        }
        return v * per
    }

    private fun label(line: String): String =
        IngredientAmounts.parse(line).name.ifBlank { line }.take(40)

    private fun name(allergen: String) = AllergenCatalog.displayName(allergen).lowercase()
    private fun joinNames(set: Set<String>) = set.map(::name).sorted().joinToString(" and ")
    private fun pct(share: Double) = "${(share * 100).roundToInt()}%"

    private fun rx(p: String) = Regex(p, RegexOption.IGNORE_CASE)

    /** One substitute. [carries] are the allergens it brings with it; it is skipped when the person avoids one. */
    class Sub(
        val name: String,
        val carries: Set<String> = emptySet(),
        val confidence: SwapConfidence = SwapConfidence.HIGH,
        val note: String? = null,
        private val replacement: ((String) -> String)? = null,
    ) {
        fun display(matched: String): String = replacement?.invoke(matched) ?: name
    }

    class SwapClass(
        val id: String,
        val label: String,
        val allergens: Set<String>,
        val find: Regex,
        val subs: List<Sub>,
        /** Above this share of recipe weight the ingredient is a main ingredient: refused. */
        val maxShare: Double,
        /** Above this share the swap is offered as "borderline". */
        val borderShare: Double,
        val bakedMaxShare: Double? = null,
        val titleWords: Regex? = null,
        val titleShare: Double = 0.12,
        val cupGrams: Double? = null,
        val eachGrams: Double? = null,
        val sliceGrams: Double? = null,
    )

    private val DAIRY = AllergenCatalog.DAIRY
    private val EGG = AllergenCatalog.EGG
    private val WHEAT = AllergenCatalog.WHEAT
    private val GLUTEN = AllergenCatalog.GLUTEN
    private val SOY = AllergenCatalog.SOY
    private val NUTS = AllergenCatalog.TREE_NUTS
    private val PEANUTS = AllergenCatalog.PEANUTS
    private val SESAME = AllergenCatalog.SESAME

    private val CHEESE_TYPES =
        "cheddar|mozzarella|parmesan|parmigiano(?:[- ]reggiano)?|pecorino(?: romano)?|romano|swiss|gruyere|gruyère|provolone|feta|monterey jack|pepper jack|jack|colby|gouda|asiago|cotija|queso fresco|goat|blue|brie|halloumi|havarti|fontina|manchego|gorgonzola|american|mexican blend|italian blend"

    /** Plant milks in order of preference; each is skipped if the person avoids what it carries. */
    private fun plantMilks(prefix: String = "") = listOf(
        Sub("${prefix}oat milk", setOf(GLUTEN), SwapConfidence.HIGH),
        Sub("${prefix}soy milk", setOf(SOY), SwapConfidence.HIGH),
        Sub("${prefix}unsweetened coconut milk beverage", emptySet(), SwapConfidence.HIGH),
        Sub("${prefix}almond milk", setOf(NUTS), SwapConfidence.HIGH),
    )

    private val CLASSES: List<SwapClass> = listOf(
        // ---- soy sauce: soy, wheat and gluten all live in it ----
        SwapClass(
            "soysauce", "soy sauce", setOf(SOY, WHEAT, GLUTEN), rx("""\b(?:soy sauce|shoyu|tamari|soya sauce)\b"""),
            listOf(
                Sub("tamari", setOf(SOY), SwapConfidence.HIGH, "Check the tamari label says wheat-free"),
                Sub("coconut aminos", emptySet(), SwapConfidence.HIGH, "Coconut aminos is sweeter and less salty; add a pinch of salt"),
            ),
            maxShare = 0.12, borderShare = 0.08, cupGrams = 255.0,
        ),
        SwapClass(
            "eggnoodle", "egg noodles", setOf(EGG, WHEAT, GLUTEN), rx("""\begg noodles?\b"""),
            listOf(Sub("rice noodles", emptySet(), SwapConfidence.HIGH)), maxShare = 0.75, borderShare = 0.65,
        ),
        // ---- dairy ----
        SwapClass(
            "creamcheese", "cream cheese", setOf(DAIRY), rx("""\bcream cheese\b"""),
            listOf(Sub("vegan cream cheese", emptySet(), SwapConfidence.HIGH)),
            maxShare = 0.20, borderShare = 0.10, titleWords = rx("""cream cheese"""), cupGrams = 230.0,
        ),
        SwapClass(
            "freshcheese", "ricotta or cottage cheese", setOf(DAIRY), rx("""\b(?:ricotta|cottage cheese|mascarpone|paneer|quark)\b"""),
            listOf(
                Sub("vegan ricotta", emptySet(), SwapConfidence.MEDIUM, "Store-bought vegan ricotta, or blended tofu with lemon and salt"),
                Sub("tofu ricotta", setOf(SOY), SwapConfidence.MEDIUM),
                Sub("cashew ricotta", setOf(NUTS), SwapConfidence.MEDIUM),
            ),
            maxShare = 0.25, borderShare = 0.12, titleWords = rx("""ricotta|cottage|mascarpone"""), cupGrams = 245.0,
        ),
        SwapClass(
            "cheese", "cheese", setOf(DAIRY), rx("""(?<!cream )\b(?:(?:$CHEESE_TYPES)(?:\s+cheese)?|cheese)\b"""),
            listOf(Sub("vegan cheese", emptySet(), SwapConfidence.HIGH, "Vegan cheese melts less well; add it near the end", replacement = { m ->
                val t = m.trim().lowercase().removeSuffix(" cheese").trim()
                if (t == "cheese" || t.isEmpty()) "vegan cheese" else "vegan $t"
            })),
            maxShare = 0.30, borderShare = 0.15, titleWords = rx("""cheese|cheesy|cheddar|mozzarella|parmesan"""),
            titleShare = 0.15, cupGrams = 110.0, sliceGrams = 20.0,
        ),
        SwapClass(
            "buttermilk", "buttermilk", setOf(DAIRY), rx("""\bbuttermilk\b"""),
            plantMilks().map { s -> Sub("${s.name} with 1 tbsp lemon juice per cup", s.carries, SwapConfidence.MEDIUM) },
            maxShare = 0.40, borderShare = 0.25, cupGrams = 245.0,
        ),
        SwapClass(
            "sourcream", "sour cream", setOf(DAIRY), rx("""\b(?:sour cream|crème fraîche|creme fraiche)\b"""),
            listOf(Sub("plant-based sour cream", emptySet(), SwapConfidence.HIGH)),
            maxShare = 0.30, borderShare = 0.15, cupGrams = 240.0,
        ),
        SwapClass(
            "condensed", "condensed or evaporated milk", setOf(DAIRY), rx("""\b(?:sweetened condensed|condensed|evaporated) milk\b"""),
            listOf(Sub("coconut condensed milk", emptySet(), SwapConfidence.MEDIUM, "Use full-fat canned coconut milk, simmered down with sugar if it is meant to be sweetened")),
            maxShare = 0.20, borderShare = 0.10, titleWords = rx("""leches|condensed"""), titleShare = 0.05, cupGrams = 250.0,
        ),
        SwapClass(
            "cream", "cream", setOf(DAIRY), rx("""(?<!sour )(?<!ice )(?<!coconut )(?<!cream of )\b(?:(?:heavy|whipping|whipped|double|single|light|thickened|heavy whipping) cream|half[- ]and[- ]half|cream)\b(?!\s+(?:cheese|of tartar|of (?:mushroom|chicken|celery)|soda))"""),
            listOf(
                Sub("coconut cream", emptySet(), SwapConfidence.MEDIUM, "Coconut cream adds a mild coconut taste"),
                Sub("oat cream", setOf(GLUTEN), SwapConfidence.MEDIUM),
                Sub("cashew cream", setOf(NUTS), SwapConfidence.MEDIUM),
            ),
            maxShare = 0.40, borderShare = 0.20, titleWords = rx("""\bcream"""), titleShare = 0.30, cupGrams = 240.0,
        ),
        SwapClass(
            "yogurt", "yogurt", setOf(DAIRY), rx("""\b(?:(?:greek|plain|natural|full[- ]fat|whole[- ]milk|low[- ]fat|nonfat|fat[- ]free) )*yogh?urt\b"""),
            listOf(
                Sub("plain coconut yogurt", emptySet(), SwapConfidence.HIGH),
                Sub("plain soy yogurt", setOf(SOY), SwapConfidence.HIGH),
                Sub("plain oat yogurt", setOf(GLUTEN), SwapConfidence.HIGH),
            ),
            maxShare = 0.40, borderShare = 0.25, titleWords = rx("""yogh?urt"""), titleShare = 0.25, cupGrams = 245.0,
        ),
        SwapClass(
            "milkchoc", "milk chocolate", setOf(DAIRY), rx("""\bmilk chocolate\b"""),
            listOf(Sub("dairy-free dark chocolate", emptySet(), SwapConfidence.MEDIUM, "Check the chocolate label for milk")),
            maxShare = 0.30, borderShare = 0.15, cupGrams = 170.0,
        ),
        SwapClass(
            "butter", "butter", setOf(DAIRY),
            rx("""(?<!peanut )(?<!almond )(?<!cashew )(?<!hazelnut )(?<!cocoa )(?<!shea )(?<!nut )(?<!apple )(?<!sunflower seed )(?<!seed )(?<!coconut )\b(?:(?:unsalted|salted|melted|softened|cold|browned|clarified) )*(?:butter|ghee)\b(?!\s*(?:beans?|nut|milk|cup|scotch|fly|lettuce))"""),
            listOf(
                Sub("vegan butter", emptySet(), SwapConfidence.HIGH),
                Sub("olive oil", emptySet(), SwapConfidence.MEDIUM, "Use about three quarters as much oil as butter"),
            ),
            maxShare = 0.35, borderShare = 0.25, cupGrams = 227.0,
        ),
        SwapClass(
            "milk", "milk", setOf(DAIRY),
            rx("""(?<!butter)(?<!coconut )(?<!almond )(?<!oat )(?<!soy )(?<!rice )(?<!cashew )(?<!plant )(?<!nut )(?<!hemp )\b(?:(?:whole|skim|skimmed|low[- ]fat|nonfat|full[- ]fat|2%|1%|semi-skimmed|fat[- ]free|reduced[- ]fat|dairy|cow'?s) )*milk\b(?!\s+(?:chocolate|powder|chips|thistle))"""),
            plantMilks(), maxShare = 0.50, borderShare = 0.35, cupGrams = 245.0,
        ),
        // ---- egg ----
        SwapClass(
            "mayo", "mayonnaise", setOf(EGG), rx("""\b(?:mayonnaise|mayo|aioli)\b"""),
            listOf(Sub("vegan mayonnaise", emptySet(), SwapConfidence.HIGH)), maxShare = 0.15, borderShare = 0.08, cupGrams = 220.0,
        ),
        SwapClass(
            "eggwhite", "egg whites", setOf(EGG), rx("""\begg whites?\b"""),
            listOf(Sub("aquafaba (3 tbsp per white)", emptySet(), SwapConfidence.MEDIUM, "Aquafaba is the liquid from a can of chickpeas")),
            maxShare = 0.20, borderShare = 0.10, titleWords = rx("""\begg"""), titleShare = 0.10, eachGrams = 33.0,
        ),
        SwapClass(
            "egg", "eggs", setOf(EGG), rx("""\beggs?\b(?!\s*(?:yolks?|plant|noodles?|roll))"""),
            listOf(
                Sub("flax egg", emptySet(), SwapConfidence.MEDIUM, "A flax egg is 1 tbsp ground flax stirred into 3 tbsp water and rested 5 minutes, per egg", replacement = { m ->
                    if (m.trim().lowercase().endsWith("s")) "flax eggs" else "flax egg"
                }),
                Sub("commercial egg replacer", emptySet(), SwapConfidence.MEDIUM),
            ),
            maxShare = 0.25, borderShare = 0.12, titleWords = rx("""\begg"""), titleShare = 0.10, eachGrams = 50.0,
        ),
        // ---- wheat and gluten ----
        SwapClass(
            "crumbs", "breadcrumbs", setOf(WHEAT, GLUTEN), rx("""\b(?:panko(?: bread ?crumbs?| crumbs?)?|(?:bread|cracker) ?crumbs?)\b"""),
            listOf(Sub("gluten-free breadcrumbs", emptySet(), SwapConfidence.HIGH)), maxShare = 0.15, borderShare = 0.10, cupGrams = 110.0,
        ),
        SwapClass(
            "pasta", "pasta", setOf(WHEAT, GLUTEN),
            rx("""\b(?:spaghetti|penne|fusilli|macaroni|fettuccine|linguine|rigatoni|farfalle|orzo|lasagn[ae](?: sheets| noodles)?|noodles?|pasta|ziti|rotini|tagliatelle|vermicelli|angel hair|shells|elbows)\b"""),
            listOf(Sub("gluten-free pasta", emptySet(), SwapConfidence.HIGH, "Gluten-free pasta overcooks quickly; cook it a minute less", replacement = { m ->
                val t = m.trim().lowercase()
                if (t == "pasta") "gluten-free pasta" else "gluten-free $t"
            })),
            maxShare = 0.75, borderShare = 0.65, cupGrams = 100.0,
        ),
        SwapClass(
            "grain", "wheat grain", setOf(WHEAT, GLUTEN), rx("""\b(?:bulgur|farro|barley|freekeh|couscous|wheat berries|spelt|semolina)\b"""),
            listOf(Sub("quinoa", emptySet(), SwapConfidence.MEDIUM, "Quinoa cooks differently from the grain it replaces; follow its pack")),
            maxShare = 0.60, borderShare = 0.50, cupGrams = 170.0,
        ),
        SwapClass(
            "bread", "bread", setOf(WHEAT, GLUTEN),
            rx("""\b(?:(?:hamburger|hot dog|burger|sandwich|brioche|sub|hoagie|english muffin|pita|flour|corn) )?(?:buns?|rolls?|bread|bagels?|tortillas?|wraps?|pitas?|naan|toast|baguette|croutons?)\b"""),
            listOf(Sub("gluten-free bread", emptySet(), SwapConfidence.MEDIUM, "Gluten-free bread is more fragile; toast or warm it first", replacement = { m ->
                val t = m.trim().lowercase()
                if (t.startsWith("gluten-free")) t else "gluten-free $t"
            })),
            maxShare = 0.35, borderShare = 0.25, titleWords = rx("""sandwich|toast|\bbread\b"""), titleShare = 0.20, sliceGrams = 30.0, eachGrams = 60.0,
        ),
        SwapClass(
            "flour", "wheat flour", setOf(WHEAT, GLUTEN),
            rx("""(?<!almond )(?<!rice )(?<!coconut )(?<!corn )(?<!oat )(?<!chickpea )\b(?:(?:all[- ]purpose|plain|self[- ]raising|self[- ]rising|whole[- ]wheat|wholemeal|bread|cake|pastry|white|wheat|unbleached) )*flour\b"""),
            listOf(Sub("cornstarch (about half the amount)", emptySet(), SwapConfidence.HIGH, "Cornstarch thickens about twice as strongly as flour, so use half as much")),
            maxShare = 0.12, borderShare = 0.07, bakedMaxShare = 0.08, cupGrams = 125.0,
        ),
        // ---- tree nuts, peanuts, sesame ----
        SwapClass(
            "nutmilk", "nut milk", setOf(NUTS), rx("""\b(?:almond|cashew|hazelnut|macadamia|pistachio|walnut|pecan|nut) milk\b"""),
            plantMilks().filter { !it.name.contains("almond") }, maxShare = 0.50, borderShare = 0.35, cupGrams = 245.0,
        ),
        SwapClass(
            "nutbutter", "nut butter", setOf(NUTS), rx("""\b(?:almond|cashew|hazelnut|macadamia|pistachio|walnut|pecan|mixed nut|nut) butter\b"""),
            listOf(Sub("sunflower seed butter", emptySet(), SwapConfidence.MEDIUM)),
            maxShare = 0.12, borderShare = 0.06, titleWords = rx("""\b(?:almond|cashew|hazelnut|nut)"""), titleShare = 0.06, cupGrams = 250.0,
        ),
        SwapClass(
            "nuts", "nuts", setOf(NUTS),
            rx("""\b(?:(?:chopped|sliced|slivered|toasted|crushed|roasted|whole|raw|blanched) )*(?:almonds?|walnuts?|pecans?|cashews?|pistachios?|hazelnuts?|macadamias?|macadamia nuts?|pine nuts?|pignoli|brazil nuts?|mixed nuts?|nuts?)\b(?!\s*(?:flour|meal|milk|butter|extract|paste|oil|free))"""),
            listOf(Sub("toasted sunflower seeds", emptySet(), SwapConfidence.HIGH, "Seeds give the same crunch")),
            maxShare = 0.12, borderShare = 0.06, titleWords = rx("""almond|walnut|pecan|cashew|pistachio|hazelnut|macadamia|\bnuts?\b"""), titleShare = 0.06, cupGrams = 140.0,
        ),
        SwapClass(
            "peanutbutter", "peanut butter", setOf(PEANUTS), rx("""\bpeanut (?:butter|sauce)\b"""),
            listOf(Sub("sunflower seed butter", emptySet(), SwapConfidence.MEDIUM, "Seed butter can turn a sauce slightly green when cooked with baking soda; it tastes fine")),
            maxShare = 0.12, borderShare = 0.06, titleWords = rx("""peanut"""), titleShare = 0.06, cupGrams = 260.0,
        ),
        SwapClass(
            "peanutoil", "peanut oil", setOf(PEANUTS), rx("""\b(?:refined |roasted )?peanut oil\b"""),
            listOf(Sub("neutral vegetable oil", emptySet(), SwapConfidence.HIGH)),
            maxShare = 0.20, borderShare = 0.10, cupGrams = 216.0,
        ),
        SwapClass(
            "peanuts", "peanuts", setOf(PEANUTS), rx("""\b(?:(?:chopped|crushed|roasted|salted|dry[- ]roasted|raw) )*peanuts?\b(?!\s*(?:butter|oil|sauce|flour|free))"""),
            listOf(Sub("toasted sunflower seeds", emptySet(), SwapConfidence.HIGH)),
            maxShare = 0.12, borderShare = 0.06, titleWords = rx("""peanut"""), titleShare = 0.06, cupGrams = 145.0,
        ),
        SwapClass(
            "sesameoil", "sesame oil", setOf(SESAME), rx("""\b(?:toasted )?sesame oil\b"""),
            listOf(Sub("neutral oil", emptySet(), SwapConfidence.MEDIUM, "The toasty sesame flavour will be missing")),
            maxShare = 0.08, borderShare = 0.04, cupGrams = 216.0,
        ),
        SwapClass(
            "tahini", "tahini", setOf(SESAME), rx("""\btahini\b"""),
            listOf(Sub("sunflower seed butter", emptySet(), SwapConfidence.MEDIUM)),
            maxShare = 0.12, borderShare = 0.06, titleWords = rx("""tahini"""), titleShare = 0.05, cupGrams = 240.0,
        ),
        SwapClass(
            "sesameseeds", "sesame seeds", setOf(SESAME), rx("""\b(?:(?:toasted|black|white) )*sesame seeds?\b"""),
            listOf(Sub("toasted sunflower seeds", emptySet(), SwapConfidence.HIGH)),
            maxShare = 0.06, borderShare = 0.03, titleWords = rx("""sesame"""), titleShare = 0.04, cupGrams = 145.0,
        ),
        // ---- soy beyond soy sauce ----
        SwapClass(
            "soymilk", "soy milk", setOf(SOY), rx("""\b(?:soy(?:a|bean)? ?milk)\b"""),
            plantMilks().filter { !it.name.contains("soy") }, maxShare = 0.50, borderShare = 0.35, cupGrams = 245.0,
        ),
        SwapClass(
            "miso", "miso", setOf(SOY), rx("""\bmiso(?: paste)?\b"""),
            listOf(Sub("chickpea miso", emptySet(), SwapConfidence.MEDIUM, "Chickpea miso is milder; add salt to taste")),
            maxShare = 0.08, borderShare = 0.04, cupGrams = 275.0,
        ),
    )

    private val WHEAT_DEFINING: Pair<Regex, String> = rx(
        """\b(?:bread|loaf|bagels?|croissants?|focaccia|naan|brioche|pastry|pastries|gnocchi|dumplings?|pizza|pancakes?|waffles?|muffins?|cookies?|brownies?|scones?|donuts?|doughnuts?|crackers?|shortbread|strudel|baklava|pretzels?|crepes?|tarts?|cupcakes?|pot pie|pie crust)\b|(?<!crab )(?<!fish )(?<!rice )(?<!salmon )(?<!potato )(?<!tuna )(?<!cauliflower )(?<!chicken )\bcakes?\b""",
    ) to "This is a baked or dough-based dish"

    /** Dishes whose identity is the allergen, by concept. A title matching one is refused whatever the amounts. */
    private val DEFINING: Map<String, List<Pair<Regex, String>>> = mapOf(
        DAIRY to listOf(
            rx("""cheese ?cake|tres leches|\balfredo\b|\bcustard\b|cr[eè]me br[uû]l[eé]e|panna cotta|\bflan\b|ice[- ]cream|\btiramisu\b|\bfondue\b|\bpaneer\b|\bqueso\b|\bcarbonara\b|\bquiche\b|\bsouffl[eé]\b|\bb[eé]chamel\b|\bmilk ?shake\b|\blassi\b|\bgelato\b|rice pudding|\bpudding\b|\braita\b|\bmac(?:aroni)?(?: ?(?:and|&|n'?) ?)cheese\b|\bmac ?[&n] ?cheese\b|cheese (?:sauce|soup|dip|ball|board|lasagn)|\bgrilled cheese\b|\bcheesy\b|four cheese|three cheese|\bcr[eè]me anglaise\b|\bbutterscotch\b|\bfudge\b|\bkheer\b|\bburrata\b|\bcaprese\b|\bcacio e pepe\b""") to "This is a dairy dish",
        ),
        EGG to listOf(
            rx("""\bmeringue\b|\bpavlova\b|\bsouffl[eé]\b|\bquiche\b|\bcustard\b|\bfrittata\b|\bomelet+e?\b|\bscrambled\b|egg salad|deviled eggs?|\bscotch eggs?\b|\bhollandaise\b|\bbrioche\b|\bmacarons?\b|angel food|\bcarbonara\b|egg drop|eggs? benedict|cr[eè]me br[uû]l[eé]e|\bflan\b|\bfrench toast\b|\bchiffon\b|\bsponge cake\b|\bzabaglione\b|\bshakshuka\b|\bpoached egg|\bfried egg|\bbaked eggs?\b|\bpound cake\b""") to "This is an egg dish",
        ),
        WHEAT to listOf(WHEAT_DEFINING),
        GLUTEN to listOf(WHEAT_DEFINING),
        SOY to listOf(
            rx("""\btofu\b|\btempeh\b|\bedamame\b|\bnatto\b|miso soup|\bteriyaki\b|\bsoy (?:chunks|protein)\b""") to "This is a soy dish",
        ),
        AllergenCatalog.TREE_NUTS to listOf(
            rx("""\bmarzipan\b|\bpraline\b|\bfrangipane\b|\bbaklava\b|nut (?:roast|loaf)|\bnougat\b|\bgianduja\b|\bnut(?:ty)? cake\b|\bnutella\b|\bnut brittle\b|\bpesto\b""") to "This is a nut dish",
        ),
        AllergenCatalog.PEANUTS to listOf(
            rx("""\bsatay\b|peanut (?:brittle|butter cup|butter cookies?|butter pie|noodles?)|\bpeanut sauce\b""") to "This is a peanut dish",
        ),
        AllergenCatalog.SESAME to listOf(
            rx("""\bhummus\b|\bhalva\b|\bhalvah\b|\btahini\b|baba gh?anou[sj]h?|\bfalafel\b|\bsesame (?:chicken|beef|balls?|bars?|noodles?)\b""") to "This is a sesame dish",
        ),
    )

    private val BAKED_TITLE = rx(
        """\b(?:cakes?|cupcakes?|muffins?|cookies?|brownies?|bread|loaf|buns?|rolls?|scones?|biscuits?|pancakes?|waffles?|crepes?|pizza|dough|crust|tarts?|pies?|pastry|pastries|donuts?|doughnuts?|bagels?|focaccia|croissants?|shortbread|crackers?|flatbread|naan|pretzels?|bars?|blondies|cobbler|crumble|strudel)\b""",
    )
}

/** Where the engine's three outcomes are told apart. */
sealed interface Adaptation {
    /** The scan found nothing that blocks: no adaptation needed. */
    data object AlreadyFine : Adaptation

    /**
     * Swaps that would make the recipe free of the blocking allergens. A suggestion only: the original recipe keeps
     * its warning, and [adaptedIngredients] must be re-scanned locally before anything is saved.
     */
    data class Adaptable(
        val swaps: List<Swap>,
        val adaptedTitle: String,
        val adaptedIngredients: List<String>,
        val confidence: SwapConfidence,
        val caveats: List<String>,
    ) : Adaptation

    /** [reasons] are plain sentences, the strongest first. */
    data class NotAdaptable(val reasons: List<String>) : Adaptation {
        val reason: String get() = reasons.first()
    }
}

enum class SwapConfidence(val label: String) {
    HIGH("Easy swap"),
    MEDIUM("Works, with a small change"),
    LOW("Borderline: it will not be the same dish"),
}

data class Swap(
    val lineIndex: Int,
    val allergen: String,
    val original: String,
    val from: String,
    val to: String,
    val confidence: SwapConfidence,
    val note: String?,
    /** The ingredient line after every swap on it; null when the line is dropped. */
    val adaptedLine: String?,
    internal val range: IntRange? = null,
    internal val cls: Substitutions.SwapClass? = null,
    internal val sub: Substitutions.Sub? = null,
) {
    val summary: String get() = if (to == "leave it out") "Leave out $from" else "Swap $from → $to"
}
