package com.zeroindex.intake.domain.script

import com.zeroindex.intake.domain.plan.CookSteps
import com.zeroindex.intake.domain.plan.PlanFile
import com.zeroindex.intake.domain.plan.PlanFileEatOut
import com.zeroindex.intake.domain.plan.PlanFileIngredient
import com.zeroindex.intake.domain.plan.PlanFileParser
import com.zeroindex.intake.domain.plan.PlanFileRecipe
import com.zeroindex.intake.domain.plan.PlanFileShoppingItem
import com.zeroindex.intake.domain.plan.PlanFileWeek
import com.zeroindex.intake.domain.plan.PlanSchedule

/**
 * A parsed meal plan script. The content is the importer's own [PlanFile]
 * (so the integration step only calls the existing importer); [name] and
 * [avoid] are script-level and have no PlanFile field. [avoid] is information
 * only: the allergen engine never reads it and it can never clear a warning.
 */
data class MealPlanDoc(
    val name: String,
    val avoid: List<String> = emptyList(),
    val file: PlanFile,
) {
    fun toPlanFile(): PlanFile = file

    /**
     * Weeks that have no "Shopping:" items. Their list is generated from the
     * recipe's ingredients by the importer (ShoppingListBuilder).
     */
    val weeksWithGeneratedShopping: List<Int>
        get() = file.weeks.map { it.weekNumber }
            .filter { n -> file.shoppingItems.none { it.weekNumber == n } }
}

object MealPlanScript {
    private enum class Scope { HEADER, EVERY, ABOUT, WEEK, RECIPE, EATOUT, VENUE }

    private enum class Sec {
        ABOUT_TEXT, BREAKFASTS, SNACKS, PANTRY, WATCH, SHOPPING, INGREDIENTS, STEPS, SERVE,
        VARIATIONS, NOTES, EATOUT_TEXT, ORDERS,
    }

    private val FREE_TEXT = setOf(Sec.ABOUT_TEXT, Sec.SERVE, Sec.NOTES, Sec.EATOUT_TEXT)
    private val CONTINUABLE = setOf(Sec.STEPS, Sec.VARIATIONS, Sec.BREAKFASTS, Sec.SNACKS, Sec.WATCH)

    // normalised key -> canonical key
    private val HEADER_LABELS = mapOf(
        "avoid" to "avoid", "about" to "about", "starts" to "starts", "start" to "starts",
        "startson" to "starts", "repeat" to "repeat", "repeats" to "repeat", "loop" to "repeat",
    )
    private val EVERY_LABELS = mapOf(
        "breakfasts" to "breakfasts", "standingbreakfasts" to "breakfasts", "breakfast" to "breakfasts",
        "snacks" to "snacks", "standingsnacks" to "snacks", "snack" to "snacks",
        "pantry" to "pantry",
        "watchfor" to "watch", "hiddendairychecklist" to "watch", "hiddendairy" to "watch",
        "shopping" to "shopping",
    )
    private val WEEK_LABELS = mapOf(
        "protein" to "protein", "servings" to "servings", "serves" to "servings", "yield" to "servings",
        "time" to "time", "totaltime" to "time", "serve" to "serve", "servetitle" to "servetitle",
        "servingtitle" to "servetitle", "ingredients" to "ingredients", "steps" to "steps",
        "variations" to "variations", "shopping" to "shopping", "notes" to "notes", "note" to "notes",
    )
    private val RECIPE_LABELS = mapOf(
        "servings" to "servings", "serves" to "servings", "yield" to "servings", "time" to "time",
        "totaltime" to "time", "ingredients" to "ingredients", "steps" to "steps",
    )
    private val INGREDIENT_SUBKEYS = mapOf(
        "weight" to "weight", "per100g" to "per100g", "per100" to "per100g", "nutrition" to "per100g",
        "allergens" to "allergens", "allergen" to "allergens",
    )
    private val ORDER_SUBKEYS = mapOf("caution" to "caution", "estimate" to "estimate", "weeks" to "weeks", "week" to "weeks")

    private val NAMES = mapOf(
        "avoid" to "avoid", "starts" to "starts", "repeat" to "repeat", "about" to "About", "breakfasts" to "Breakfasts", "snacks" to "Snacks",
        "pantry" to "Pantry", "watch" to "Watch for", "shopping" to "Shopping", "protein" to "Protein",
        "servings" to "Servings", "time" to "Time", "serve" to "Serve", "servetitle" to "Serve title",
        "ingredients" to "Ingredients", "steps" to "Steps", "variations" to "Variations", "notes" to "Notes",
    )

    private val CATEGORY_WORDS = mapOf(
        "PRODUCE" to listOf("produce", "fruit", "fruits", "vegetable", "vegetables", "veg", "veggies", "greens", "fresh"),
        "MEAT" to listOf("meat", "meats", "protein", "proteins", "seafood", "fish", "poultry", "deli", "butcher"),
        "PANTRY" to listOf("pantry", "dry", "grains", "grain", "canned", "spices", "spice", "condiments", "baking", "staples", "shelf"),
        "FROZEN" to listOf("frozen", "freezer"),
        "OTHER" to listOf("other", "misc", "miscellaneous", "bakery", "bread", "snacks", "drinks", "household"),
    )

    fun parse(text: String): ScriptResult<MealPlanDoc> {
        val sink = DiagnosticSink()
        val doc = try {
            val lines = ScriptText.prepare(text, sink)
            if (lines == null) null else Parser(lines, sink).run()
        } catch (e: Exception) {
            sink.report(DiagnosticCode.NOT_AN_INTAKE_SCRIPT, 1, 1, "This file could not be read as a meal plan.")
            null
        }
        val diagnostics = sink.sorted()
        return ScriptResult(if (diagnostics.any { it.severity == Severity.ERROR }) null else doc, diagnostics)
    }

    // ---- builders --------------------------------------------------------

    private class IngB(val line: Int, val col: Int) {
        var quantityText = ""
        var name = ""
        var grams: Double? = null
        var kcal: Double? = null
        var protein: Double? = null
        var carbs: Double? = null
        var fat: Double? = null
        var tags = ""
        val seen = HashSet<String>()
        var nutritionLine = 0

        fun build() = PlanFileIngredient(name, quantityText, grams, kcal, protein, carbs, fat, tags)
    }

    private class RecipeB(val name: String, val role: String, val line: Int) {
        var servings: Int? = null
        var minutes: Int? = null
        val ingredients = ArrayList<PlanFileIngredient>()
        val steps = ArrayList<String>()
        val has: Boolean
            get() = servings != null || minutes != null || ingredients.isNotEmpty() || steps.isNotEmpty()

        fun build() = PlanFileRecipe(name, role, servings ?: 1, minutes ?: 0, steps.toList(), ingredients.toList())
    }

    private class WeekB(val number: Int, val dish: String, val line: Int) {
        var protein = ""
        var serveTitle = ""
        val serve = ArrayList<String>()
        val notes = ArrayList<String>()
        val variations = ArrayList<String>()
        val main = RecipeB(dish, "MAIN", line)
        val extras = ArrayList<RecipeB>()
        var sawShopping = false
        val seenKeys = HashSet<String>()
    }

    private class OrderB(val line: Int, val text: String) {
        var caution = ""
        var estimate = arrayOfNulls<Int>(4)
        var weeks: List<Int>? = null
        val seen = HashSet<String>()
    }

    private class VenueB(val name: String, val line: Int) {
        val note = ArrayList<String>()
        val orders = ArrayList<OrderB>()
    }

    private class Parser(val lines: List<SrcLine>, val sink: DiagnosticSink) {
        var name = ""
        var startsOn = ""
        var repeat = ""
        val avoid = ArrayList<String>()
        val about = ArrayList<String>()
        val breakfasts = ArrayList<String>()
        val snacks = ArrayList<String>()
        val pantry = ArrayList<String>()
        val watch = ArrayList<String>()
        val shopping = ArrayList<PlanFileShoppingItem>()
        val weeks = ArrayList<PlanFileWeek>()
        val weekNumbers = HashSet<Int>()
        val eatOutNotes = ArrayList<String>()
        val options = ArrayList<PlanFileEatOut>()
        val aboutHeading = ArrayList<String>()

        var scope = Scope.HEADER
        var sec: Sec? = null
        var week: WeekB? = null
        var recipe: RecipeB? = null
        var ing: IngB? = null
        var venue: VenueB? = null
        var order: OrderB? = null
        var lastLine = 1
        val seenHeader = HashSet<String>()
        val seenEvery = HashSet<String>()
        var listCount = 0

        fun run(): MealPlanDoc? {
            var started = false
            for (line in lines) {
                lastLine = line.no
                if (!started) {
                    if (line.isBlank) continue
                    started = true
                    if (!title(line)) return null
                    continue
                }
                if (line.isBlank) {
                    if (sec in FREE_TEXT) addFree("")
                    continue
                }
                val h = ScriptText.heading(line)
                if (h != null) heading(line, h) else body(line)
            }
            if (!started) {
                sink.report(DiagnosticCode.NOT_AN_INTAKE_SCRIPT, 1, 1, "The file is empty. A meal plan starts with \"# Meal plan: <name>\".")
                return null
            }
            closeAll()
            if (weeks.isEmpty()) {
                sink.report(DiagnosticCode.NO_WEEKS, lastLine, 1, "The plan has no weeks. Add \"## Week 1: <main dish>\".")
            }
            val intro = ScriptText.freeText(about + (if (about.isNotEmpty() && aboutHeading.isNotEmpty()) listOf("") else emptyList()) + aboutHeading)
            val file = PlanFile(
                format = PlanFileParser.FORMAT,
                version = PlanFileParser.SUPPORTED_VERSION,
                name = name,
                startsOn = startsOn,
                repeat = repeat,
                intro = intro,
                standingBreakfasts = breakfasts.toList(),
                standingSnacks = snacks.toList(),
                pantry = pantry.toList(),
                hiddenDairyChecklist = watch.toList(),
                eatOutNotes = ScriptText.freeText(eatOutNotes),
                weeks = weeks.toList(),
                shoppingItems = shopping.toList(),
                eatOutOptions = options.toList(),
            )
            return MealPlanDoc(name, avoid.toList(), file)
        }

        fun title(line: SrcLine): Boolean {
            val h = ScriptText.heading(line)
            val m = h?.let { Regex("""(?i)^meal\s*plan\s*:\s*(.*)$""").matchEntire(it.text) }
            if (h == null || h.level != 1 || m == null) {
                sink.report(
                    DiagnosticCode.NOT_AN_INTAKE_SCRIPT, line.no, line.indent + 1,
                    "A meal plan file must start with \"# Meal plan: <name>\", found \"${line.trimmed.take(40)}\".",
                )
                return false
            }
            name = m.groupValues[1].trim()
            if (name.isEmpty()) {
                sink.report(DiagnosticCode.MISSING_PLAN_NAME, line.no, line.text.length + 1, "The plan needs a name: \"# Meal plan: Winter plan\".")
            }
            return true
        }

        // ---- headings ----

        fun heading(line: SrcLine, h: Heading) {
            when (h.level) {
                1 -> sink.report(DiagnosticCode.UNEXPECTED_HEADING, line.no, h.textCol, "A second \"# \" heading. Use \"## \" for sections and weeks.")
                2 -> level2(line, h)
                3 -> level3(line, h)
                else -> sink.report(
                    DiagnosticCode.UNEXPECTED_HEADING, line.no, line.indent + 1,
                    "\"${"#".repeat(h.level)}\" headings are not used in meal plans.",
                )
            }
        }

        fun level2(line: SrcLine, h: Heading) {
            closeAll()
            val k = ScriptText.key(h.text)
            when {
                k == "everyweek" -> { scope = Scope.EVERY; sec = null }
                k == "about" -> { scope = Scope.ABOUT; sec = Sec.ABOUT_TEXT }
                k == "eatout" || k == "eatingout" -> { scope = Scope.EATOUT; sec = Sec.EATOUT_TEXT }
                Regex("""(?i)^week\b.*""").matches(h.text) -> weekHeading(line, h)
                else -> {
                    scope = Scope.HEADER; sec = null
                    skipScope = true
                    sink.report(
                        DiagnosticCode.UNKNOWN_SECTION, line.no, h.textCol,
                        "Unknown section \"${h.text}\". Sections are: Every week, Week N: <dish>, Eat out, About.",
                    )
                }
            }
            if (scope != Scope.HEADER) skipScope = false
        }

        var skipScope = false

        fun weekHeading(line: SrcLine, h: Heading) {
            scope = Scope.WEEK
            sec = null
            val m = Regex("""(?i)^week\s*(\d+)\s*(?:[:–—.-]\s*)?(.*)$""").matchEntire(h.text)
            if (m == null) {
                sink.report(DiagnosticCode.BAD_WEEK_HEADING, line.no, h.textCol, "Expected \"## Week 1: <main dish>\", found \"${h.text}\".")
                skipScope = true
                scope = Scope.HEADER
                return
            }
            val n = m.groupValues[1].toIntOrNull() ?: -1
            val dish = m.groupValues[2].trim()
            var ok = true
            if (n !in 1..PlanFileParser.MAX_WEEKS) {
                sink.report(DiagnosticCode.BAD_WEEK_NUMBER, line.no, h.textCol, "Week numbers run from 1 to ${PlanFileParser.MAX_WEEKS}, found ${m.groupValues[1]}.")
                ok = false
            } else if (!weekNumbers.add(n)) {
                sink.report(DiagnosticCode.DUPLICATE_WEEK, line.no, h.textCol, "Week $n appears more than once.")
                ok = false
            }
            if (dish.isEmpty()) {
                sink.report(DiagnosticCode.BAD_WEEK_HEADING, line.no, line.text.length + 1, "Week $n needs a main dish: \"## Week $n: Taco bowls\".")
                ok = false
            }
            val w = WeekB(n, dish, line.no)
            week = w
            recipe = w.main
            if (!ok) {
                // keep parsing the body so its own problems are reported too
            }
        }

        fun level3(line: SrcLine, h: Heading) {
            when (scope) {
                Scope.WEEK, Scope.RECIPE -> {
                    val m = Regex("""(?i)^recipe\s*:\s*(.*)$""").matchEntire(h.text)
                    if (m == null || m.groupValues[1].isBlank()) {
                        sink.report(
                            DiagnosticCode.UNEXPECTED_HEADING, line.no, h.textCol,
                            "Inside a week, \"### \" is only used for an extra recipe: \"### Recipe: <name>\".",
                        )
                        return
                    }
                    flushItems()
                    val w = week ?: return
                    val rname = m.groupValues[1].trim()
                    if (rname.equals(w.dish, true) || w.extras.any { it.name.equals(rname, true) }) {
                        sink.report(DiagnosticCode.DUPLICATE_RECIPE, line.no, h.textCol, "Week ${w.number} already has a recipe named \"$rname\".")
                    }
                    val r = RecipeB(rname, "VARIATION", line.no)
                    w.extras += r
                    recipe = r
                    scope = Scope.RECIPE
                    sec = null
                }
                Scope.EATOUT, Scope.VENUE -> {
                    closeVenue()
                    if (h.text.isEmpty()) {
                        sink.report(DiagnosticCode.UNEXPECTED_HEADING, line.no, h.textCol, "This venue has no name: \"### Subway\".")
                    }
                    venue = VenueB(h.text.ifEmpty { "Venue" }, line.no)
                    scope = Scope.VENUE
                    sec = Sec.ORDERS
                }
                else -> if (!skipScope) sink.report(
                    DiagnosticCode.UNEXPECTED_HEADING, line.no, h.textCol,
                    "\"### \" headings are used for venues under \"## Eat out\" and \"### Recipe:\" inside a week.",
                )
            }
        }

        // ---- body lines ----

        fun body(line: SrcLine) {
            if (skipScope) return
            val labels = when (scope) {
                Scope.HEADER -> HEADER_LABELS
                Scope.EVERY -> EVERY_LABELS
                Scope.WEEK -> WEEK_LABELS
                Scope.RECIPE -> RECIPE_LABELS
                else -> emptyMap()
            }

            // Sub-lines under the current ingredient or order.
            val subKeys = when {
                sec == Sec.INGREDIENTS && ing != null -> INGREDIENT_SUBKEYS
                sec == Sec.ORDERS && scope == Scope.VENUE -> ORDER_SUBKEYS
                else -> null
            }
            if (subKeys != null) {
                val sl = ScriptText.label(line, allowBullet = line.indent > 0)
                val canon = sl?.let { subKeys[it.key] }
                if (sl != null && canon != null) {
                    subLine(line, sl, canon)
                    return
                }
            }

            val lab = ScriptText.label(line, allowBullet = false)
            val canon = lab?.let { labels[it.key] }
            if (lab != null && canon != null) {
                startLabel(line, lab, canon)
                return
            }

            when (sec) {
                null -> noSection(line, lab, labels)
                in FREE_TEXT -> addFree(line.trimmed)
                Sec.ORDERS -> orderLine(line)
                else -> listLine(line)
            }
        }

        fun noSection(line: SrcLine, lab: Label?, labels: Map<String, String>) {
            val valid = labels.values.distinct().map { NAMES[it] ?: it }
            if (lab != null) {
                val sug = ScriptText.suggest(lab.raw, labels.keys)?.let { NAMES[labels[it]] }
                sink.report(
                    DiagnosticCode.UNKNOWN_KEY, line.no, lab.keyCol,
                    "Unknown label \"${lab.raw}\"." + (if (sug != null) " Did you mean \"$sug\"?" else "") +
                        " Labels here: ${valid.joinToString(", ")}.",
                )
            } else {
                val first = line.trimmed.split(Regex("""\s+""")).firstOrNull().orEmpty()
                val known = labels.keys.firstOrNull { it == ScriptText.key(first) }
                if (known != null) {
                    sink.report(
                        DiagnosticCode.MISSING_COLON, line.no, line.indent + 1,
                        "\"$first\" needs a colon after it, like \"${NAMES[labels[known]] ?: first}: ...\".",
                    )
                } else {
                    sink.report(
                        DiagnosticCode.UNEXPECTED_TEXT, line.no, line.indent + 1,
                        "Expected a label such as ${valid.take(3).joinToString(", ") { "\"$it:\"" }} here, found \"${line.trimmed.take(50)}\".",
                    )
                }
            }
        }

        fun startLabel(line: SrcLine, lab: Label, canon: String) {
            flushItems()
            val v = lab.value
            when (canon) {
                "avoid", "starts", "repeat", "protein", "servings", "time", "servetitle" -> {
                    sec = null
                    if (v.isEmpty()) {
                        sink.report(DiagnosticCode.EMPTY_VALUE, line.no, lab.valueCol, "\"${lab.raw}:\" needs a value after the colon.")
                        return
                    }
                    val seen = if (scope == Scope.HEADER) seenHeader else week?.seenKeys ?: HashSet()
                    val dupKey = if (canon == "servings" || canon == "time") "$canon@${recipe?.name}" else canon
                    if (!seen.add(dupKey) && canon != "avoid") {
                        sink.report(DiagnosticCode.DUPLICATE_KEY, line.no, lab.keyCol, "\"${lab.raw}\" is already set; give it once.")
                        return
                    }
                    when (canon) {
                        "avoid" -> v.split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { avoid += it }
                        "starts" -> {
                            val d = runCatching { java.time.LocalDate.parse(v.trim()) }.getOrNull()
                            if (d == null) {
                                sink.report(
                                    DiagnosticCode.BAD_START_DATE, line.no, lab.valueCol,
                                    "Expected a date like \"2026-11-09\" (year-month-day), found \"$v\".",
                                )
                            } else {
                                startsOn = d.toString()
                            }
                        }
                        "repeat" -> {
                            if (PlanSchedule.parseRepeat(v) == null) {
                                sink.report(
                                    DiagnosticCode.BAD_REPEAT, line.no, lab.valueCol,
                                    "Expected \"no\", \"yes\" or a count like \"3 times\", found \"$v\".",
                                )
                            } else {
                                repeat = v.trim()
                            }
                        }
                        "protein" -> week?.protein = v
                        "servetitle" -> week?.serveTitle = v
                        "servings" -> servings(v, line, lab)
                        "time" -> time(v, line, lab)
                    }
                    return
                }
            }
            val s = when (canon) {
                "about" -> Sec.ABOUT_TEXT
                "breakfasts" -> Sec.BREAKFASTS
                "snacks" -> Sec.SNACKS
                "pantry" -> Sec.PANTRY
                "watch" -> Sec.WATCH
                "shopping" -> Sec.SHOPPING
                "ingredients" -> Sec.INGREDIENTS
                "steps" -> Sec.STEPS
                "serve" -> Sec.SERVE
                "variations" -> Sec.VARIATIONS
                "notes" -> Sec.NOTES
                else -> null
            }
            sec = s
            listCount = 0
            if (canon == "shopping" && scope == Scope.WEEK) week?.sawShopping = true
            if (v.isNotEmpty()) {
                if (s in FREE_TEXT) addFree(v) else item(line, v, lab.valueCol, inline = true)
            }
        }

        fun addFree(text: String) {
            when (sec) {
                Sec.ABOUT_TEXT -> if (scope == Scope.ABOUT) aboutHeading += text else about += text
                Sec.EATOUT_TEXT -> eatOutNotes += text
                Sec.SERVE -> week?.serve?.plusAssign(text)
                Sec.NOTES -> week?.notes?.plusAssign(text)
                else -> {}
            }
        }

        fun listLine(line: SrcLine) {
            val b = ScriptText.bullet(line)
            if (b == null) {
                if (line.indent > 0 && sec in CONTINUABLE && continueLast(line.trimmed)) return
                val what = when (sec) {
                    Sec.STEPS -> "Steps start with \"1.\" or \"-\""
                    Sec.INGREDIENTS -> "Ingredients start with \"-\""
                    else -> "List items start with \"-\""
                }
                sink.report(DiagnosticCode.UNEXPECTED_TEXT, line.no, line.indent + 1, "$what, found \"${line.trimmed.take(50)}\".")
                return
            }
            item(line, b.content, b.contentCol, inline = false)
        }

        fun continueLast(text: String): Boolean {
            fun MutableList<String>.append(): Boolean {
                if (isEmpty()) return false
                this[lastIndex] = this[lastIndex] + " " + text
                return true
            }
            return when (sec) {
                Sec.STEPS -> recipe?.steps?.append() ?: false
                Sec.VARIATIONS -> week?.variations?.append() ?: false
                Sec.BREAKFASTS -> breakfasts.append()
                Sec.SNACKS -> snacks.append()
                Sec.WATCH -> watch.append()
                else -> false
            }
        }

        fun item(line: SrcLine, text: String, col: Int, inline: Boolean) {
            when (sec) {
                Sec.BREAKFASTS -> breakfasts += text
                Sec.SNACKS -> snacks += text
                Sec.WATCH -> watch += text
                Sec.PANTRY -> text.split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { pantry += it }
                Sec.VARIATIONS -> week?.variations?.plusAssign(text)
                Sec.STEPS -> recipe?.steps?.plusAssign(text)
                Sec.INGREDIENTS -> ingredient(line, text, col)
                Sec.SHOPPING -> shoppingLine(line, text, col)
                else -> {}
            }
        }

        // ---- week fields ----

        fun servings(v: String, line: SrcLine, lab: Label) {
            val m = Regex("""(?i)^(?:makes\s+)?(\d{1,3})(?:\s*(?:-|–|to)\s*(\d{1,3}))?(?:\s*servings?)?$""").matchEntire(v)
            val n = m?.let { (it.groupValues[2].ifEmpty { it.groupValues[1] }).toInt() }
            if (n == null || n < 1) {
                sink.report(DiagnosticCode.BAD_SERVINGS, line.no, lab.valueCol, "Expected a number of servings like \"5\", found \"$v\".")
                return
            }
            recipe?.servings = n
        }

        fun time(v: String, line: SrcLine, lab: Label) {
            val seconds = CookSteps.durations(v).firstOrNull()?.seconds
                ?: v.trim().takeIf { Regex("""\d{1,4}""").matches(it) }?.toInt()?.times(60)
            if (seconds == null || seconds <= 0) {
                sink.report(DiagnosticCode.BAD_TIME, line.no, lab.valueCol, "Expected a time like \"45 min\" or \"1 hour 30 minutes\", found \"$v\".")
                return
            }
            recipe?.minutes = (seconds + 59) / 60
        }

        // ---- ingredients ----

        fun ingredient(line: SrcLine, text: String, col: Int) {
            flushIng()
            val b = IngB(line.no, col)
            var rest = text
            val q = ScriptText.quantity(text)
            if (q != null) {
                b.quantityText = q.text
                rest = q.name
                q.unit?.let { u -> ScriptText.gramsPerUnit(u)?.let { b.grams = ScriptText.round1(q.value * it) } }
                if (rest.isBlank()) {
                    sink.report(DiagnosticCode.INGREDIENT_NO_NAME, line.no, col, "\"$text\" has an amount but no ingredient name, like \"${text} ground beef\".")
                    rest = text
                    b.quantityText = ""
                    b.grams = null
                }
            }
            Regex("""^(.*?)\s*\((\d+(?:\.\d+)?\s*(?:g|kg|oz|lbs?))\)$""", RegexOption.IGNORE_CASE).matchEntire(rest)?.let { m ->
                ScriptText.gramsOf(m.groupValues[2])?.let { g ->
                    if (m.groupValues[1].isNotBlank()) {
                        b.grams = g
                        rest = m.groupValues[1]
                    }
                }
            }
            b.name = rest.trim()
            ing = b
        }

        fun flushIng() {
            val b = ing ?: return
            ing = null
            recipe?.ingredients?.plusAssign(b.build())
            val hasAny = b.kcal != null || b.protein != null || b.carbs != null || b.fat != null
            if (hasAny && (b.kcal == null || b.protein == null || b.carbs == null || b.fat == null)) {
                sink.report(
                    DiagnosticCode.INCOMPLETE_NUTRITION, b.nutritionLine, 1,
                    "\"${b.name}\" gives only some of kcal, protein, carbs and fat per 100 g. All four are needed for macro math, so it will be treated as having none.",
                )
            } else if (hasAny && b.grams == null) {
                sink.report(
                    DiagnosticCode.INCOMPLETE_NUTRITION, b.nutritionLine, 1,
                    "\"${b.name}\" has nutrition but no weight. Add \"weight: 100 g\" so it counts toward macros.",
                )
            }
        }

        fun subLine(line: SrcLine, lab: Label, canon: String) {
            if (lab.value.isEmpty()) {
                sink.report(DiagnosticCode.EMPTY_VALUE, line.no, lab.valueCol, "\"${lab.raw}:\" needs a value after the colon.")
                return
            }
            when (canon) {
                "weight", "per100g", "allergens" -> {
                    val b = ing ?: return
                    if (!b.seen.add(canon)) {
                        sink.report(DiagnosticCode.DUPLICATE_KEY, line.no, lab.keyCol, "\"${lab.raw}\" is already set for \"${b.name}\".")
                        return
                    }
                    when (canon) {
                        "weight" -> {
                            val g = ScriptText.gramsOf(lab.value)
                            if (g == null || g <= 0.0 || g > 20_000) {
                                sink.report(DiagnosticCode.BAD_INGREDIENT_WEIGHT, line.no, lab.valueCol, "Expected a weight like \"370 g\", \"1.5 kg\", \"13 oz\" or \"2 lb\", found \"${lab.value}\".")
                            } else {
                                b.grams = g
                            }
                        }
                        "allergens" -> b.tags = lab.value
                        else -> {
                            val n = nutrition(lab.value, line.no, lab.valueCol, DiagnosticCode.BAD_NUTRITION) ?: return
                            b.kcal = n[0]; b.protein = n[1]; b.carbs = n[2]; b.fat = n[3]
                            b.nutritionLine = line.no
                            val macro = (n[1] ?: 0.0) + (n[2] ?: 0.0) + (n[3] ?: 0.0)
                            if ((n[0] ?: 0.0) > 900.0 || macro > 100.5) {
                                sink.report(
                                    DiagnosticCode.NUTRITION_IMPLAUSIBLE, line.no, lab.valueCol,
                                    "These per 100 g values are not possible (over 900 kcal, or protein + carbs + fat over 100 g).",
                                )
                            }
                        }
                    }
                }
                "caution" -> {
                    val o = order
                    if (o == null) {
                        venue?.note?.plusAssign(lab.value)
                    } else if (!o.seen.add("caution")) {
                        sink.report(DiagnosticCode.DUPLICATE_KEY, line.no, lab.keyCol, "\"caution\" is already set for this order.")
                    } else {
                        o.caution = lab.value
                    }
                }
                "estimate" -> {
                    val o = order
                    if (o == null) {
                        sink.report(DiagnosticCode.UNEXPECTED_TEXT, line.no, lab.keyCol, "\"estimate:\" belongs under an order line (\"- ...\").")
                        return
                    }
                    if (!o.seen.add("estimate")) {
                        sink.report(DiagnosticCode.DUPLICATE_KEY, line.no, lab.keyCol, "\"estimate\" is already set for this order.")
                        return
                    }
                    val n = nutrition(lab.value, line.no, lab.valueCol, DiagnosticCode.BAD_ESTIMATE) ?: return
                    for (i in 0..3) o.estimate[i] = n[i]?.let { Math.round(it).toInt() }
                }
                "weeks" -> {
                    val o = order
                    if (o == null) {
                        sink.report(DiagnosticCode.UNEXPECTED_TEXT, line.no, lab.keyCol, "\"weeks:\" belongs under an order line (\"- ...\").")
                        return
                    }
                    if (!o.seen.add("weeks")) {
                        sink.report(DiagnosticCode.DUPLICATE_KEY, line.no, lab.keyCol, "\"weeks\" is already set for this order.")
                        return
                    }
                    o.weeks = weeksList(lab.value, line.no, lab.valueCol)
                }
            }
        }

        /** kcal, protein, carbs, fat in any order, any subset. Null (after reporting) when a part is not understood. */
        fun nutrition(v: String, line: Int, col: Int, code: DiagnosticCode): Array<Double?>? {
            val out = arrayOfNulls<Double>(4)
            for (raw in v.split(',', ';')) {
                var part = raw.trim()
                if (part.isEmpty()) continue
                part = part.replace(Regex("""(?i)\s*(?:/|per)\s*100\s*g(?:rams)?\s*$"""), "")
                val nums = Regex("""\d+(?:\.\d+)?""").findAll(part).toList()
                val words = part.replace(Regex("""\d+(?:\.\d+)?"""), " ").lowercase()
                    .split(Regex("""[^a-z]+""")).filter { it.isNotEmpty() && it !in setOf("g", "gram", "grams", "of") }
                val idx = if (words.size == 1) when (words[0]) {
                    "kcal", "cal", "cals", "calorie", "calories", "energy" -> 0
                    "protein", "proteins", "p" -> 1
                    "carb", "carbs", "carbohydrate", "carbohydrates", "c" -> 2
                    "fat", "fats", "f" -> 3
                    else -> -1
                } else -1
                if (nums.size != 1 || idx < 0) {
                    sink.report(code, line, col, "Expected parts like \"176 kcal\" or \"20 g protein\" (kcal, protein, carbs, fat), found \"$part\".")
                    return null
                }
                if (out[idx] != null) {
                    sink.report(code, line, col, "\"${words[0]}\" is given twice.")
                    return null
                }
                out[idx] = nums[0].value.toDouble()
            }
            if (out.all { it == null }) {
                sink.report(code, line, col, "Expected at least one value like \"176 kcal\".")
                return null
            }
            return out
        }

        fun weeksList(v: String, line: Int, col: Int): List<Int>? {
            if (Regex("""(?i)all|every week|every|any""").matches(v.trim())) return null
            val out = LinkedHashSet<Int>()
            for (part in v.split(',', ';')) {
                val p = part.trim()
                val m = Regex("""(?i)^(?:week\s*)?(\d{1,3})(?:\s*(?:-|–|—|to)\s*(\d{1,3}))?$""").matchEntire(p)
                val a = m?.groupValues?.get(1)?.toInt()
                val b = m?.groupValues?.get(2)?.takeIf { it.isNotEmpty() }?.toInt() ?: a
                if (m == null || a == null || b == null || a !in 1..PlanFileParser.MAX_WEEKS || b !in 1..PlanFileParser.MAX_WEEKS || a > b) {
                    sink.report(DiagnosticCode.BAD_WEEKS_LIST, line, col, "Expected weeks like \"1, 3-5\" (each from 1 to ${PlanFileParser.MAX_WEEKS}), found \"$p\".")
                    return null
                }
                for (w in a..b) out += w
            }
            if (out.isEmpty()) {
                sink.report(DiagnosticCode.BAD_WEEKS_LIST, line, col, "Expected weeks like \"1, 3-5\" or \"all\".")
                return null
            }
            return out.toList()
        }

        // ---- shopping ----

        fun shoppingLine(line: SrcLine, text: String, col: Int) {
            val m = Regex("""^([A-Za-z][A-Za-z &/-]{0,30}?)\s*:\s*(.*)$""").matchEntire(text)
            var category = "OTHER"
            var staple = false
            var items = text
            if (m == null) {
                sink.report(
                    DiagnosticCode.SHOPPING_NO_CATEGORY, line.no, col,
                    "Shopping lines look like \"- Produce: 4 avocados, onion\". \"${text.take(30)}\" has no category, so it goes under Other.",
                )
            } else {
                items = m.groupValues[2]
                val label = m.groupValues[1].trim()
                val k = ScriptText.key(label)
                if (k == "staples" || k == "pantrystaples" || k == "staple") {
                    category = "PANTRY"; staple = true
                } else {
                    val words = label.lowercase().split(Regex("""[^a-z]+""")).filter { it.isNotEmpty() }
                    val hit = CATEGORY_WORDS.entries.firstOrNull { (_, ws) -> words.firstOrNull() in ws }
                    if (hit != null) {
                        category = hit.key
                    } else {
                        sink.report(
                            DiagnosticCode.SHOPPING_UNKNOWN_CATEGORY, line.no, col,
                            "Unknown shopping category \"$label\"; using Other. Categories: Produce, Meat, Pantry, Frozen, Other.",
                        )
                    }
                }
            }
            val weekNumber = if (scope == Scope.WEEK) week?.number else null
            splitTop(items).forEach { raw ->
                val q = ScriptText.quantity(raw)
                val (qty, nm) = if (q != null && q.name.isNotBlank()) q.text to q.name else "" to raw
                shopping += PlanFileShoppingItem(nm, qty, category, staple, weekNumber)
            }
        }

        fun splitTop(s: String): List<String> {
            val out = ArrayList<String>()
            var depth = 0
            val cur = StringBuilder()
            for (c in s) {
                when {
                    c == '(' -> { depth++; cur.append(c) }
                    c == ')' -> { depth = maxOf(0, depth - 1); cur.append(c) }
                    c == ',' && depth == 0 -> { out += cur.toString(); cur.clear() }
                    else -> cur.append(c)
                }
            }
            out += cur.toString()
            return out.map { it.trim() }.filter { it.isNotEmpty() }
        }

        // ---- eat out ----

        fun orderLine(line: SrcLine) {
            val v = venue
            val b = ScriptText.bullet(line)
            if (b != null) {
                flushOrder()
                if (v == null) return
                order = OrderB(line.no, b.content)
                return
            }
            // Venue-level paragraph: the venue note, applied as caution to its orders.
            v?.note?.plusAssign(line.trimmed)
        }

        fun flushOrder() {
            val o = order ?: return
            order = null
            val v = venue ?: return
            v.orders += o
        }

        fun closeVenue() {
            flushOrder()
            val v = venue ?: return
            venue = null
            val note = v.note.joinToString(" ").trim()
            if (v.orders.isEmpty()) {
                if (note.isNotEmpty()) {
                    sink.report(
                        DiagnosticCode.VENUE_NO_ORDERS, v.line, 1,
                        "\"${v.name}\" has text but no orders, so the text would be lost. Add an order line like \"- Grilled chicken sandwich, no ranch\" and keep the text as its caution.",
                    )
                } else {
                    sink.report(DiagnosticCode.VENUE_NO_ORDERS, v.line, 1, "\"${v.name}\" has no orders.")
                }
                return
            }
            v.orders.forEach { o ->
                val caution = listOf(note, o.caution.trim()).filter { it.isNotEmpty() }.joinToString(" ")
                val weeksList = o.weeks
                val targets: List<Int?> = weeksList ?: listOf(null)
                targets.forEach { wk ->
                    options += PlanFileEatOut(v.name, o.text, caution, o.estimate[0], o.estimate[1], o.estimate[2], o.estimate[3], wk)
                }
            }
        }

        // ---- closing ----

        fun flushItems() {
            flushIng()
            flushOrder()
        }

        fun closeAll() {
            flushItems()
            if (venue != null) closeVenue()
            val w = week ?: return
            week = null
            recipe = null
            val recipes = ArrayList<PlanFileRecipe>()
            if (w.main.has) recipes += w.main.build()
            w.extras.forEach { recipes += it.build() }
            (listOf(w.main) + w.extras).filter { it.has }.forEach { r ->
                val built = r.build().ingredients
                val counted = built.count { it.hasNutrition }
                if (counted in 1 until built.size) {
                    sink.report(
                        DiagnosticCode.INCOMPLETE_MACROS, r.line, 1,
                        "\"${r.name}\": ${built.size - counted} of ${built.size} ingredients have no weight and nutrition, so the recipe's macros will be reported as incomplete.",
                    )
                }
            }
            weeks += PlanFileWeek(
                weekNumber = w.number,
                mainDishName = w.dish,
                proteinType = w.protein,
                notes = ScriptText.freeText(w.notes),
                serving = ScriptText.freeText(w.serve),
                servingTitle = w.serveTitle,
                variations = w.variations.toList(),
                recipes = recipes,
            )
        }
    }

    // ---- printer ---------------------------------------------------------

    fun print(doc: MealPlanDoc): String = printFile(doc.file, doc.name, doc.avoid)

    /** Canonical script text for [file]. `parse(print(doc))` yields an equal document. */
    fun printFile(file: PlanFile, name: String = file.name.ifBlank { "Meal plan" }, avoid: List<String> = emptyList()): String = buildString {
        append("# Meal plan: $name\n")
        if (avoid.isNotEmpty()) append("avoid: ${avoid.joinToString(", ")}\n")
        if (file.startsOn.isNotBlank()) append("starts: ${file.startsOn.trim()}\n")
        if (file.repeat.isNotBlank()) append("repeat: ${file.repeat.trim()}\n")
        if (file.intro.isNotBlank()) append("About:\n${file.intro.trim()}\n")

        val everyShopping = file.shoppingItems.filter { it.weekNumber == null }
        if (file.standingBreakfasts.isNotEmpty() || file.standingSnacks.isNotEmpty() || file.pantry.isNotEmpty() ||
            file.hiddenDairyChecklist.isNotEmpty() || everyShopping.isNotEmpty()
        ) {
            append("\n## Every week\n")
            list("Breakfasts", file.standingBreakfasts)
            list("Snacks", file.standingSnacks)
            list("Pantry", file.pantry)
            list("Watch for", file.hiddenDairyChecklist)
            shoppingBlock(everyShopping)
        }

        file.weeks.forEach { w ->
            append("\n## Week ${w.weekNumber}: ${w.mainDishName}\n")
            if (w.proteinType.isNotBlank()) append("Protein: ${w.proteinType}\n")
            val main = w.recipes.firstOrNull { it.role.uppercase() == "MAIN" }
            if (main != null) recipeBody(main, withExtras = true)
            if (w.servingTitle.isNotBlank()) append("Serve title: ${w.servingTitle}\n")
            if (w.serving.isNotBlank()) append("Serve:\n${w.serving.trim()}\n")
            list("Variations", w.variations)
            shoppingBlock(file.shoppingItems.filter { it.weekNumber == w.weekNumber })
            if (w.notes.isNotBlank()) append("Notes:\n${w.notes.trim()}\n")
            w.recipes.filter { it.role.uppercase() != "MAIN" }.forEach { r ->
                append("\n### Recipe: ${r.name}\n")
                recipeBody(r, withExtras = false)
            }
        }

        if (file.eatOutNotes.isNotBlank() || file.eatOutOptions.isNotEmpty()) {
            append("\n## Eat out\n")
            if (file.eatOutNotes.isNotBlank()) append("${file.eatOutNotes.trim()}\n")
            eatOut(file.eatOutOptions)
        }
    }

    private fun StringBuilder.list(label: String, items: List<String>) {
        if (items.isEmpty()) return
        append("$label:\n")
        items.forEach { append("- ${it.replace('\n', ' ')}\n") }
    }

    private fun StringBuilder.recipeBody(r: PlanFileRecipe, @Suppress("UNUSED_PARAMETER") withExtras: Boolean) {
        if (r.servings != 1) append("Servings: ${r.servings}\n")
        if (r.totalTimeMinutes > 0) append("Time: ${r.totalTimeMinutes} min\n")
        if (r.ingredients.isNotEmpty()) {
            append("Ingredients:\n")
            r.ingredients.forEach { i ->
                append("- " + (if (i.quantityText.isNotBlank()) "${i.quantityText} " else "") + i.name + "\n")
                val derived = ScriptText.quantity(i.quantityText)?.let { q ->
                    q.unit?.let { u -> ScriptText.gramsPerUnit(u)?.let { ScriptText.round1(q.value * it) } }
                }
                if (i.grams != null && derived != i.grams) append("  weight: ${ScriptText.fmt(i.grams)} g\n")
                val parts = listOfNotNull(
                    i.kcalPer100g?.let { "${ScriptText.fmt(it)} kcal" },
                    i.proteinPer100g?.let { "${ScriptText.fmt(it)} g protein" },
                    i.carbsPer100g?.let { "${ScriptText.fmt(it)} g carbs" },
                    i.fatPer100g?.let { "${ScriptText.fmt(it)} g fat" },
                )
                if (parts.isNotEmpty()) append("  per 100g: ${parts.joinToString(", ")}\n")
                if (i.allergenTags.isNotBlank()) append("  allergens: ${i.allergenTags}\n")
            }
        }
        if (r.steps.isNotEmpty()) {
            append("Steps:\n")
            r.steps.forEachIndexed { n, s -> append("${n + 1}. ${s.replace('\n', ' ')}\n") }
        }
    }

    private fun StringBuilder.shoppingBlock(items: List<PlanFileShoppingItem>) {
        if (items.isEmpty()) return
        append("Shopping:\n")
        val groups = LinkedHashMap<String, MutableList<PlanFileShoppingItem>>()
        items.forEach { i ->
            val label = if (i.isPantryStaple) "Staples" else i.category.lowercase().replaceFirstChar { it.uppercase() }
            groups.getOrPut(label) { ArrayList() } += i
        }
        groups.forEach { (label, list) ->
            append("- $label: " + list.joinToString(", ") { (if (it.quantity.isNotBlank()) "${it.quantity} " else "") + it.name } + "\n")
        }
    }

    private fun StringBuilder.eatOut(options: List<PlanFileEatOut>) {
        var prevVenue: String? = null
        var i = 0
        while (i < options.size) {
            val o = options[i]
            if (o.venueName != prevVenue) {
                append("\n### ${o.venueName}\n")
                prevVenue = o.venueName
            }
            // Merge following options that differ only by week into one "weeks:" line.
            val weeks = ArrayList<Int>()
            var j = i
            if (o.weekNumber != null) {
                while (j < options.size && options[j].weekNumber != null &&
                    options[j].copy(weekNumber = null) == o.copy(weekNumber = null)
                ) {
                    weeks += options[j].weekNumber!!
                    j++
                }
            } else {
                j = i + 1
            }
            append("- ${o.whatToOrder.replace('\n', ' ')}\n")
            if (o.cautionNote.isNotBlank()) append("  caution: ${o.cautionNote.replace('\n', ' ')}\n")
            val est = listOfNotNull(
                o.estimatedKcal?.let { "$it kcal" }, o.estimatedProtein?.let { "$it g protein" },
                o.estimatedCarbs?.let { "$it g carbs" }, o.estimatedFat?.let { "$it g fat" },
            )
            if (est.isNotEmpty()) append("  estimate: ${est.joinToString(", ")}\n")
            if (weeks.isNotEmpty()) append("  weeks: ${compress(weeks)}\n")
            i = if (o.weekNumber != null) j else i + 1
        }
    }

    private fun compress(weeks: List<Int>): String {
        val out = ArrayList<String>()
        var i = 0
        while (i < weeks.size) {
            var j = i
            while (j + 1 < weeks.size && weeks[j + 1] == weeks[j] + 1) j++
            out += if (j > i) "${weeks[i]}-${weeks[j]}" else "${weeks[i]}"
            i = j + 1
        }
        return out.joinToString(", ")
    }
}
