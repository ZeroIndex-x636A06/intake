package com.zeroindex.intake.data.plan

import com.zeroindex.intake.data.db.entity.AllergenStatus
import com.zeroindex.intake.data.db.entity.rank
import com.zeroindex.intake.data.db.entity.EatOutOptionEntity
import com.zeroindex.intake.data.db.entity.EatOutOrigin
import com.zeroindex.intake.data.db.entity.FoodEntity
import com.zeroindex.intake.data.db.entity.MealPlanEntity
import com.zeroindex.intake.data.db.entity.PlanWeekEntity
import com.zeroindex.intake.data.db.entity.PlanWeekRecipeEntity
import com.zeroindex.intake.data.db.entity.RecipeEntity
import com.zeroindex.intake.data.db.entity.RecipeIngredientEntity
import com.zeroindex.intake.data.db.entity.RecipeOrigin
import com.zeroindex.intake.data.db.entity.ShoppingListItemEntity
import com.zeroindex.intake.data.db.entity.seedId
import com.zeroindex.intake.domain.allergen.AllergenScanResult
import com.zeroindex.intake.domain.model.AllergenMatchKind
import com.zeroindex.intake.domain.model.FoodSource
import com.zeroindex.intake.domain.model.PlanRecipeRole
import com.zeroindex.intake.domain.model.StoreCategory
import com.zeroindex.intake.domain.plan.CookSteps
import com.zeroindex.intake.domain.plan.IngredientNutrition
import com.zeroindex.intake.domain.plan.PlanFile
import com.zeroindex.intake.domain.plan.PlanFileIngredient
import com.zeroindex.intake.domain.plan.PlanMeta
import com.zeroindex.intake.domain.plan.PlanWeekExtras
import com.zeroindex.intake.domain.plan.RecipeMacros
import com.zeroindex.intake.domain.plan.ShoppingListBuilder
import com.zeroindex.intake.domain.plan.compute

/** One allergen hit the import preview shows before the user confirms. */
data class ImportFinding(
    /** "Week 3 · Smash burger bowls · Duke's mayo" */
    val subject: String,
    val status: AllergenStatus,
    /** Matched terms and the engine's own note. */
    val detail: String,
    /** Where the item sits: "Week 3 · Smash burger bowls". */
    val where: String = "",
    /** The item's full text, never shortened. */
    val item: String = subject,
    /** Plain reasons for the tier this row sits in: "Says butter", "Mentions butter as something to avoid". */
    val reasons: List<String> = emptyList(),
    /** The words that caused the verdict, for bolding in the item and the reasons. */
    val terms: List<String> = emptyList(),
)

/** The preview's wording, in one place so both import screens and the tests agree. */
object ImportSummary {
    /** "2 need a decision · 5 worth a look · 3 mentioned as avoided"; zero parts are left out. */
    fun line(blocked: Int, mayContain: Int, checkLabel: Int, mentioned: Int): String {
        val parts = buildList {
            if (blocked > 0) add("$blocked need a decision")
            if (mayContain + checkLabel > 0) add("${mayContain + checkLabel} worth a look")
            if (mentioned > 0) add("$mentioned mentioned as avoided")
        }
        return if (parts.isEmpty()) "Nothing flagged" else parts.joinToString(" · ")
    }
}

data class PlanImportBatch(
    val foods: List<FoodEntity>,
    val recipes: List<RecipeEntity>,
    val ingredients: List<RecipeIngredientEntity>,
    val weeks: List<PlanWeekEntity>,
    val links: List<PlanWeekRecipeEntity>,
    val shoppingItems: List<ShoppingListItemEntity>,
    val eatOutOptions: List<EatOutOptionEntity>,
    val findings: List<ImportFinding>,
    /** Ingredients kept as text because the file gave no grams or nutrition. */
    val ingredientsWithoutNutrition: Int,
    val eatOutWithoutEstimate: Int,
    /** The plan these rows belong to (new, or the existing plan with the same name, updated). Set by the repository. */
    val plan: MealPlanEntity? = null,
    /** True when [plan] already existed and is being updated. */
    val isUpdate: Boolean = false,
    /** Weeks of an updated plan that the file no longer has: removed softly. */
    val removedWeekIds: List<String> = emptyList(),
    /** Recipe links of an updated plan's weeks that the file no longer has: removed softly. */
    val staleLinkIds: List<String> = emptyList(),
) {
    val blocked: List<ImportFinding> get() = findings.filter { it.status == AllergenStatus.CONTAINS }
    val mayContain: List<ImportFinding> get() = findings.filter { it.status == AllergenStatus.MAY_CONTAIN }
    /** The soft tier: worth reading the label, never blocks anything. */
    val checkLabel: List<ImportFinding> get() = findings.filter { it.status == AllergenStatus.CHECK_LABEL }
    /** Informational only: free text that names an allergen to say it is left out. Not a problem. */
    val mentionedAvoided: List<ImportFinding> get() = findings.filter { it.status == AllergenStatus.MENTIONED_AVOIDED }

    val summaryLine: String get() = ImportSummary.line(blocked.size, mayContain.size, checkLabel.size, mentionedAvoided.size)
}

/** The local allergen engine, as the importer needs it: text in, verdict out. */
fun interface AllergenCheck {
    fun scan(name: String, ingredientsText: String?): AllergenScanResult

    /**
     * [panelText] is label-like text, such as a file's allergen tags: it can add a warning but is never
     * read for negation. The default folds it into [ingredientsText] for simple checks.
     */
    fun scan(name: String, ingredientsText: String?, panelText: String?): AllergenScanResult =
        scan(name, listOfNotNull(ingredientsText, panelText).joinToString("\n").ifBlank { null })
}

/**
 * Turns a validated [PlanFile] into rows. Pure: no database, no clock. Every id
 * is deterministic ([seedId]) so importing the same file twice upserts the same
 * rows instead of adding a second copy.
 *
 * Allergen rule: every ingredient name, recipe and dish name and eat-out order
 * goes through [check], which is the local engine. The file's allergenTags are
 * handed to the engine as extra text to scan, so they can only add a warning;
 * nothing in the file can lower a verdict.
 */
object PlanImportBuilder {

    /** The plan that schema 2 databases were migrated into; its rows keep their pre-plan id formulas. */
    const val LEGACY_PLAN_ID = "plan-default"

    private fun scope(planId: String) = if (planId == LEGACY_PLAN_ID || planId.isEmpty()) "" else "$planId|"

    fun weekId(planId: String, weekNumber: Int) = seedId("plan-week", "${scope(planId)}$weekNumber")
    fun recipeId(planId: String, weekNumber: Int, name: String) = seedId("plan-recipe", "${scope(planId)}$weekNumber|$name")

    /**
     * [existingWeekIds] maps week number to the id of a week row the plan already has (live or
     * removed), so a re-import updates that row instead of adding a twin.
     */
    fun build(
        file: PlanFile,
        now: Long,
        check: AllergenCheck,
        planId: String,
        existingWeekIds: Map<Int, String> = emptyMap(),
    ): PlanImportBatch {
        fun weekId(n: Int) = existingWeekIds[n] ?: weekId(planId, n)
        val sc = scope(planId)
        val foods = LinkedHashMap<String, FoodEntity>()
        val recipes = ArrayList<RecipeEntity>()
        val ingredients = ArrayList<RecipeIngredientEntity>()
        val weeks = ArrayList<PlanWeekEntity>()
        val links = ArrayList<PlanWeekRecipeEntity>()
        val shopping = LinkedHashMap<String, ShoppingListItemEntity>()
        val findings = ArrayList<ImportFinding>()
        var withoutNutrition = 0

        val seenFindings = HashSet<String>()
        fun record(where: String, item: String, scan: AllergenScanResult): AllergenStatus {
            val status = statusOf(scan)
            if (status != AllergenStatus.CLEAR) {
                val subject = if (where.isBlank()) item else "$where · $item"
                // One row per distinct item and verdict: a dish named in the week and again as its recipe is one row.
                if (seenFindings.add("$status|$subject")) {
                    val top = scan.matches.filter { kindOf(it.kind) == status }
                    findings += ImportFinding(
                        subject = subject,
                        status = status,
                        detail = scan.matches.joinToString("; ") { "${it.matchedTerm} (${it.allergen}): ${it.note}" },
                        where = where,
                        item = item,
                        reasons = top.map { it.reason }.distinct(),
                        terms = top.map { it.matchedTerm }.distinct(),
                    )
                }
            }
            return status
        }

        file.weeks.sortedBy { it.weekNumber }.forEach { w ->
            val wid = weekId(w.weekNumber)
            val at = "Week ${w.weekNumber}"
            record(at, w.mainDishName, check.scan(w.mainDishName, null))
            weeks += PlanWeekEntity(
                id = wid,
                planId = planId,
                weekNumber = w.weekNumber,
                mainDishName = w.mainDishName.trim(),
                proteinType = w.proteinType.trim(),
                notes = PlanWeekExtras(
                    note = w.notes.trim(),
                    serving = w.serving.trim(),
                    servingTitle = w.servingTitle.trim(),
                    variations = w.variations.map { it.trim() }.filter { it.isNotEmpty() },
                ).encode(),
                createdAt = now,
                updatedAt = now,
            )
            // Week-level variation ideas are one-line text: scan them too.
            w.variations.forEach { v -> record("$at · idea", v, check.scan(v, null)) }

            w.recipes.forEach { r ->
                val rid = recipeId(planId, w.weekNumber, r.name)
                val role = if (r.role.uppercase() == "VARIATION") PlanRecipeRole.VARIATION else PlanRecipeRole.MAIN
                val known = ArrayList<IngredientNutrition>()
                val textOnly = ArrayList<String>()
                var worst = AllergenStatus.CLEAR
                fun raise(s: AllergenStatus) { if (rank(s) > rank(worst)) worst = s }

                raise(record(at, r.name, check.scan(r.name, null)))

                r.ingredients.forEachIndexed { index, i ->
                    raise(record("$at · ${r.name}", i.name, check.scan(i.name, null, i.allergenTags.takeIf { it.isNotBlank() })))
                    if (i.hasNutrition) {
                        val food = foodFor(i, now)
                        foods.putIfAbsent(food.id, food)
                        ingredients += RecipeIngredientEntity(
                            id = seedId("plan-ingredient", "$sc${w.weekNumber}|${r.name}|$index|${i.name}"),
                            recipeId = rid,
                            foodId = food.id,
                            quantityGrams = i.grams!!,
                            displayOrder = index,
                            createdAt = now,
                            updatedAt = now,
                        )
                        known += nutrition(i)
                    } else {
                        withoutNutrition++
                        textOnly += listOfNotNull(
                            i.grams?.takeIf { it > 0 }?.let { "${trimNumber(it)} g" },
                            i.name.trim(),
                        ).joinToString(" ")
                    }
                    // Shopping: the recipe's own ingredients. Grams are known only when given.
                }
                val generated = ShoppingListBuilder.generate(
                    r.ingredients.map { com.zeroindex.intake.domain.plan.ShoppingIngredient(it.name, it.grams) },
                )
                generated.forEach { g ->
                    val id = seedId("shopping", "$wid:${g.key}")
                    shopping.putIfAbsent(
                        id,
                        ShoppingListItemEntity(
                            id = id, planWeekId = wid, name = g.name, quantity = g.quantity,
                            category = g.category, isPantryStaple = g.isPantryStaple,
                            createdAt = now, updatedAt = now,
                        ),
                    )
                }

                val result = RecipeMacros.compute(known, textOnly, r.servings)
                recipes += RecipeEntity(
                    id = rid,
                    name = r.name.trim(),
                    servings = r.servings.coerceAtLeast(1),
                    instructions = CookSteps.join(r.steps),
                    totalTimeMinutes = r.totalTimeMinutes,
                    sourceWeekNumber = w.weekNumber,
                    origin = RecipeOrigin.PLAN,
                    ingredientsText = textOnly.joinToString("\n"),
                    // Stored only when every ingredient had data: a partial total must not look complete.
                    kcalPerServing = result.perServing.kcal.takeIf { result.isComplete },
                    proteinPerServing = result.perServing.protein.takeIf { result.isComplete },
                    carbsPerServing = result.perServing.carbs.takeIf { result.isComplete },
                    fatPerServing = result.perServing.fat.takeIf { result.isComplete },
                    allergenStatus = worst,
                    createdAt = now,
                    updatedAt = now,
                )
                links += PlanWeekRecipeEntity(
                    id = seedId("plan-link", "$sc${w.weekNumber}|${r.name}"),
                    planWeekId = wid,
                    recipeId = rid,
                    role = role,
                    createdAt = now,
                    updatedAt = now,
                )
            }
        }

        val weekNumbers = file.weeks.map { it.weekNumber }.toSet()

        // Explicit shopping items; null week means every week in the file.
        file.shoppingItems.forEach { s ->
            val targets = s.weekNumber?.let { listOf(it) } ?: weekNumbers.sorted()
            targets.filter { it in weekNumbers }.forEach { n ->
                val wid = weekId(n)
                val id = seedId("shopping", "$wid:${ShoppingListBuilder.normalise(s.name)}")
                shopping[id] = ShoppingListItemEntity(
                    id = id, planWeekId = wid, name = s.name.trim(), quantity = s.quantity.trim(),
                    category = runCatching { StoreCategory.valueOf(s.category.uppercase()) }.getOrDefault(StoreCategory.OTHER),
                    isPantryStaple = s.isPantryStaple, createdAt = now, updatedAt = now,
                )
            }
        }
        // The plan-wide pantry list becomes pantry-staple items on every week.
        file.pantry.map { it.trim() }.filter { it.isNotEmpty() }.forEach { name ->
            weekNumbers.sorted().forEach { n ->
                val wid = weekId(n)
                val id = seedId("shopping", "$wid:${ShoppingListBuilder.normalise(name)}")
                shopping[id] = ShoppingListItemEntity(
                    id = id, planWeekId = wid, name = name, category = StoreCategory.PANTRY,
                    isPantryStaple = true, createdAt = now, updatedAt = now,
                )
            }
        }

        var noEstimate = 0
        val eatOut = file.eatOutOptions.map { o ->
            val status = record("Eat out · ${o.venueName}", o.whatToOrder, check.scan(o.whatToOrder, null))
            val hasEstimate = (o.estimatedKcal ?: 0) > 0
            if (!hasEstimate) noEstimate++
            EatOutOptionEntity(
                id = seedId("plan-eatout", "${o.weekNumber ?: "*"}|${o.venueName}|${o.whatToOrder}"),
                planWeekId = o.weekNumber?.takeIf { it in weekNumbers }?.let { weekId(it) },
                venueName = o.venueName.trim(),
                whatToOrder = o.whatToOrder.trim(),
                cautionNote = o.cautionNote.trim(),
                // 0 kcal means "no estimate"; the UI says so instead of showing 0.
                estimatedKcal = if (hasEstimate) o.estimatedKcal!! else 0,
                estimatedProtein = if (hasEstimate) o.estimatedProtein ?: 0 else 0,
                estimatedCarbs = if (hasEstimate) o.estimatedCarbs ?: 0 else 0,
                estimatedFat = if (hasEstimate) o.estimatedFat ?: 0 else 0,
                origin = EatOutOrigin.PLAN,
                allergenStatus = status,
                createdAt = now,
                updatedAt = now,
            )
        }

        return PlanImportBatch(
            foods = foods.values.toList(),
            recipes = recipes,
            ingredients = ingredients,
            weeks = weeks,
            links = links,
            shoppingItems = shopping.values.toList(),
            eatOutOptions = eatOut,
            findings = findings,
            ingredientsWithoutNutrition = withoutNutrition,
            eatOutWithoutEstimate = noEstimate,
        )
    }

    fun statusOf(scan: AllergenScanResult): AllergenStatus = when {
        scan.matches.any { it.kind == AllergenMatchKind.CONTAINS } -> AllergenStatus.CONTAINS
        scan.matches.any { it.kind == AllergenMatchKind.MAY_CONTAIN } -> AllergenStatus.MAY_CONTAIN
        scan.matches.any { it.kind == AllergenMatchKind.CHECK_LABEL } -> AllergenStatus.CHECK_LABEL
        scan.matches.any { it.kind == AllergenMatchKind.MENTIONED_AVOIDED } -> AllergenStatus.MENTIONED_AVOIDED
        else -> AllergenStatus.CLEAR
    }

    private fun kindOf(kind: AllergenMatchKind): AllergenStatus = when (kind) {
        AllergenMatchKind.CONTAINS -> AllergenStatus.CONTAINS
        AllergenMatchKind.MAY_CONTAIN -> AllergenStatus.MAY_CONTAIN
        AllergenMatchKind.CHECK_LABEL -> AllergenStatus.CHECK_LABEL
        AllergenMatchKind.MENTIONED_AVOIDED -> AllergenStatus.MENTIONED_AVOIDED
    }

    /** CONTAINS > MAY_CONTAIN > CHECK_LABEL > CLEAR; UNKNOWN is below CLEAR so a real verdict always wins. */
    fun rank(s: AllergenStatus?): Int = s.rank

    private fun nutrition(i: PlanFileIngredient) = IngredientNutrition(
        name = i.name, grams = i.grams!!, kcalPer100g = i.kcalPer100g!!, proteinPer100g = i.proteinPer100g!!,
        carbsPer100g = i.carbsPer100g!!, fatPer100g = i.fatPer100g!!,
    )

    private fun foodFor(i: PlanFileIngredient, now: Long) = FoodEntity(
        id = seedId(
            "plan-food",
            "${i.name}|${i.kcalPer100g}|${i.proteinPer100g}|${i.carbsPer100g}|${i.fatPer100g}",
        ),
        name = i.name.trim(),
        servingSizeGrams = 100.0,
        servingLabel = "100 g",
        kcalPer100g = i.kcalPer100g!!,
        proteinPer100g = i.proteinPer100g!!,
        carbsPer100g = i.carbsPer100g!!,
        fatPer100g = i.fatPer100g!!,
        source = FoodSource.RECIPE,
        // Only the file's own words; the verdict is recomputed by the engine whenever it is needed.
        allergenTags = i.allergenTags.trim(),
        createdAt = now,
        updatedAt = now,
    )

    private fun trimNumber(v: Double) = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
}
