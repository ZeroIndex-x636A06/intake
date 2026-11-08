package com.zeroindex.intake.data.plan

import com.zeroindex.intake.data.db.entity.AllergenStatus
import com.zeroindex.intake.data.db.entity.EatOutOptionEntity
import com.zeroindex.intake.data.db.entity.EatOutOrigin
import com.zeroindex.intake.data.db.entity.FoodEntity
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
)

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
) {
    val blocked: List<ImportFinding> get() = findings.filter { it.status == AllergenStatus.CONTAINS }
    val mayContain: List<ImportFinding> get() = findings.filter { it.status == AllergenStatus.MAY_CONTAIN }
}

/** The local allergen engine, as the importer needs it: text in, verdict out. */
fun interface AllergenCheck {
    fun scan(name: String, ingredientsText: String?): AllergenScanResult
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

    fun weekId(weekNumber: Int) = seedId("plan-week", weekNumber.toString())
    fun recipeId(weekNumber: Int, name: String) = seedId("plan-recipe", "$weekNumber|$name")

    fun build(file: PlanFile, now: Long, check: AllergenCheck): PlanImportBatch {
        val foods = LinkedHashMap<String, FoodEntity>()
        val recipes = ArrayList<RecipeEntity>()
        val ingredients = ArrayList<RecipeIngredientEntity>()
        val weeks = ArrayList<PlanWeekEntity>()
        val links = ArrayList<PlanWeekRecipeEntity>()
        val shopping = LinkedHashMap<String, ShoppingListItemEntity>()
        val findings = ArrayList<ImportFinding>()
        var withoutNutrition = 0

        fun record(subject: String, scan: AllergenScanResult): AllergenStatus {
            val status = statusOf(scan)
            if (status == AllergenStatus.CONTAINS || status == AllergenStatus.MAY_CONTAIN) {
                val detail = scan.matches.joinToString("; ") { "${it.matchedTerm} (${it.allergen}): ${it.note}" }
                findings += ImportFinding(subject, status, detail)
            }
            return status
        }

        val meta = PlanMeta.of(file)
        if (!meta.isEmpty) {
            weeks += PlanWeekEntity(
                id = weekId(PlanMeta.WEEK_NUMBER),
                weekNumber = PlanMeta.WEEK_NUMBER,
                mainDishName = "Every week",
                proteinType = "",
                notes = meta.encode(),
                createdAt = now,
                updatedAt = now,
            )
        }

        file.weeks.sortedBy { it.weekNumber }.forEach { w ->
            val wid = weekId(w.weekNumber)
            val at = "Week ${w.weekNumber}"
            record("$at · ${w.mainDishName}", check.scan(w.mainDishName, null))
            weeks += PlanWeekEntity(
                id = wid,
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
            w.variations.forEach { v -> record("$at · idea \"${v.take(40)}\"", check.scan(v, null)) }

            w.recipes.forEach { r ->
                val rid = recipeId(w.weekNumber, r.name)
                val role = if (r.role.uppercase() == "VARIATION") PlanRecipeRole.VARIATION else PlanRecipeRole.MAIN
                val known = ArrayList<IngredientNutrition>()
                val textOnly = ArrayList<String>()
                var worst = AllergenStatus.CLEAR
                fun raise(s: AllergenStatus) { if (rank(s) > rank(worst)) worst = s }

                raise(record("$at · ${r.name}", check.scan(r.name, null)))

                r.ingredients.forEachIndexed { index, i ->
                    raise(record("$at · ${r.name} · ${i.name}", check.scan(i.name, i.allergenTags.takeIf { it.isNotBlank() })))
                    if (i.hasNutrition) {
                        val food = foodFor(i, now)
                        foods.putIfAbsent(food.id, food)
                        ingredients += RecipeIngredientEntity(
                            id = seedId("plan-ingredient", "${w.weekNumber}|${r.name}|$index|${i.name}"),
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
                    id = seedId("plan-link", "${w.weekNumber}|${r.name}"),
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
            val status = record("Eat out · ${o.venueName} · ${o.whatToOrder.take(40)}", check.scan(o.whatToOrder, null))
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
        else -> AllergenStatus.CLEAR
    }

    /** CONTAINS > MAY_CONTAIN > CLEAR; UNKNOWN is below CLEAR so a real verdict always wins. */
    fun rank(s: AllergenStatus?): Int = when (s) {
        AllergenStatus.CONTAINS -> 3
        AllergenStatus.MAY_CONTAIN -> 2
        AllergenStatus.CLEAR -> 1
        else -> 0
    }

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
