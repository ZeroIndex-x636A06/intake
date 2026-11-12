package com.zeroindex.intake.data.swap

import com.zeroindex.intake.data.ai.planb.PlanBFinder
import com.zeroindex.intake.data.ai.planb.PlanBOption
import com.zeroindex.intake.data.db.entity.AllergenStatus
import com.zeroindex.intake.data.repository.AllergenRepository
import com.zeroindex.intake.data.repository.MealLogResult
import com.zeroindex.intake.data.repository.MealPlanRepository
import com.zeroindex.intake.data.repository.MealTemplateRepository
import com.zeroindex.intake.data.repository.RecipeContent
import com.zeroindex.intake.data.repository.TemplateLogResult
import com.zeroindex.intake.data.db.entity.EatOutOptionEntity
import com.zeroindex.intake.data.db.entity.FoodLogEntity
import com.zeroindex.intake.domain.allergen.AllergenScanResult
import com.zeroindex.intake.domain.model.MealSlot
import com.zeroindex.intake.domain.nutrition.Macros
import com.zeroindex.intake.domain.swap.MealNutrition
import com.zeroindex.intake.domain.swap.NearbyRanking
import com.zeroindex.intake.domain.swap.SwapAllergen
import com.zeroindex.intake.domain.swap.SwapKind
import com.zeroindex.intake.domain.swap.SwapMath
import com.zeroindex.intake.domain.swap.SwapOption
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/** The meal the Swap sheet starts from: today's planned or prepped meal. */
data class PlannedMeal(
    val title: String,
    val subtitle: String?,
    val macros: Macros,
    val allergen: SwapAllergen,
    val allergenReason: String?,
    /** The recipe behind it, so it is not offered as its own replacement. */
    val recipeId: String?,
)

/** Where an option came from, so choosing it can log it the right way. */
sealed interface SwapSource {
    data class Recipe(val content: RecipeContent) : SwapSource
    data class Template(val templateId: String) : SwapSource
    data class EatOut(val option: EatOutOptionEntity) : SwapSource
    data class Found(val option: PlanBOption) : SwapSource
}

/** An option and where it came from. The domain only ever sees [option]. */
data class SwapEntry(val option: SwapOption, val source: SwapSource)

sealed interface SwapLogResult {
    /** [undo] soft-deletes everything this choice logged. */
    data class Logged(val text: String, val undo: suspend () -> Unit) : SwapLogResult
    data class Blocked(val reason: String) : SwapLogResult
}

/**
 * Everything the Swap sheet reads and does (R3-2). The local parts (today's planned meal, recipes, saved meals, saved
 * eat-out options) never touch the network. Every option goes through the local allergen engine; a "contains" verdict
 * blocks choosing it, here as well as in the sheet, so a stale screen cannot log it. The nearby search is the Plan B
 * finder ([PlanBFinder]) and runs only when the person taps Search.
 */
@Singleton
class SwapRepository @Inject constructor(
    private val plans: MealPlanRepository,
    private val templates: MealTemplateRepository,
    private val allergens: AllergenRepository,
    private val finder: PlanBFinder,
) {
    /** Today's planned meal: the main recipe of this week of the active plan. Null with no plan. */
    suspend fun planned(): PlannedMeal? {
        val week = plans.observeCurrentWeekNumber().first()
        val main = plans.observeWeekContent(week).first()?.main ?: return null
        val r = main.recipe
        return PlannedMeal(
            title = r.name,
            subtitle = null,
            macros = main.macros.perServing,
            allergen = main.allergenStatus.toSwap(),
            allergenReason = main.allergenTerms.takeIf { it.isNotEmpty() }?.let { reasonFor(main.allergenStatus.toSwap(), it) },
            recipeId = r.id,
        )
    }

    /** Recipes, saved meals and saved eat-out options: all on this phone, nothing sent anywhere. */
    suspend fun offlineEntries(planned: PlannedMeal?): List<SwapEntry> {
        val out = mutableListOf<SwapEntry>()

        plans.recipeContents().filter { it.recipe.id != planned?.recipeId && !it.macros.isEmpty }.forEach { c ->
            val verdict = c.allergenStatus.toSwap()
            out += SwapEntry(
                SwapOption(
                    key = "recipe:${c.recipe.id}", title = c.recipe.name, subtitle = "Recipe · 1 serving", kind = SwapKind.PLAN_RECIPE,
                    macros = c.macros.perServing, estimate = !c.macros.isComplete, allergen = verdict,
                    allergenReason = reasonFor(verdict, c.allergenTerms),
                ),
                SwapSource.Recipe(c),
            )
        }

        val scanFood = allergens.foodScanner()
        templates.observeSummaries().first().filter { it.items.isNotEmpty() }.forEach { s ->
            val scans = s.items.map { (food, _) -> scanFood(food) }
            val verdict = SwapMath.verdict(
                contains = scans.any { it.blocks }, mayContain = scans.any { it.mayContain.isNotEmpty() }, checkLabel = scans.any { it.hasCheckLabel },
            )
            val terms = scans.flatMap { it.contains + it.mayContain + it.checkLabel }.map { it.matchedTerm }.distinct()
            val macros = s.items.fold(Macros()) { acc, (f, g) ->
                acc + Macros.fromPer100g(g, f.kcalPer100g, f.proteinPer100g, f.carbsPer100g, f.fatPer100g)
            }
            out += SwapEntry(
                SwapOption(
                    key = "template:${s.template.id}", title = s.template.name, subtitle = "Saved meal · ${s.items.size} ${if (s.items.size == 1) "food" else "foods"}",
                    kind = SwapKind.SAVED_MEAL, macros = macros, estimate = false, allergen = verdict, allergenReason = reasonFor(verdict, terms),
                ),
                SwapSource.Template(s.template.id),
            )
        }

        out += savedEatOut(plans.observeEatOutOptions().first())
        // Imported restaurant menus: cleared, scanned again now, and usable with no signal.
        out += finder.menuOptions().map(::menuEntry)
        return out
    }

    /**
     * The saved eat-out options as swap entries (also the Eating out screen's list). The stored verdict can only be
     * raised by today's scan of what to order, never lowered.
     */
    suspend fun savedEatOut(options: List<EatOutOptionEntity>): List<SwapEntry> = options.map { o ->
        val live = allergens.scan(name = o.whatToOrder)
        val verdict = SwapMath.raise(live.verdict(), o.allergenStatus.toSwap())
        val terms = (live.contains + live.mayContain + live.checkLabel).map { it.matchedTerm }.distinct()
        SwapEntry(
            SwapOption(
                key = "eatout:${o.id}", title = o.whatToOrder, subtitle = "Saved eat-out · ${o.venueName} · works offline",
                kind = SwapKind.SAVED_EAT_OUT,
                macros = Macros(o.estimatedKcal.toDouble(), o.estimatedProtein.toDouble(), o.estimatedCarbs.toDouble(), o.estimatedFat.toDouble()),
                estimate = true, allergen = verdict, allergenReason = reasonFor(verdict, terms),
            ),
            SwapSource.EatOut(o),
        )
    }

    /** One nearby result as a swap entry. The finder already merged the model's allergen claims upward. */
    fun foundEntry(o: PlanBOption): SwapEntry {
        val verdict = o.scan.verdict()
        val terms = (o.scan.contains + o.scan.mayContain + o.scan.checkLabel).map { it.matchedTerm }.distinct()
        return SwapEntry(
            SwapOption(
                key = o.key, title = o.order, subtitle = listOfNotNull(o.restaurant, o.distanceMiles?.let { "%.1f mi".format(java.util.Locale.US, it) }).joinToString(" · "),
                kind = SwapKind.NEARBY, macros = o.macros, estimate = true, allergen = verdict, allergenReason = reasonFor(verdict, terms),
            ),
            SwapSource.Found(o),
        )
    }

    /**
     * An imported menu item as a swap entry, in the "Saved eat-out options" section. Its verdict is from today's scan.
     * An item with no ingredient information is "check the label", never clear.
     */
    fun menuEntry(o: PlanBOption): SwapEntry {
        val scanVerdict = o.scan.verdict()
        val unknown = o.nameOnly && scanVerdict == SwapAllergen.CLEAR
        val verdict = if (unknown) SwapAllergen.CHECK_LABEL else scanVerdict
        val terms = (o.scan.contains + o.scan.mayContain + o.scan.checkLabel).map { it.matchedTerm }.distinct()
        val reason = if (unknown) "Ingredients not listed: check with the restaurant" else reasonFor(verdict, terms)
        return SwapEntry(
            SwapOption(
                key = o.key, title = o.order, subtitle = "Imported menu · ${o.restaurant} · works offline",
                kind = SwapKind.SAVED_EAT_OUT, macros = o.macros, estimate = true, allergen = verdict, allergenReason = reason,
            ),
            SwapSource.Found(o),
        )
    }

    suspend fun saveForOffline(o: PlanBOption) = finder.saveForOffline(o)

    /**
     * Eating out > healthier options, the part that needs no network: saved eat-out options and the imported restaurant
     * menus, each scanned by the local allergen engine and scored against what is left today. A "contains" option stays
     * in the list, last, with its reason.
     */
    suspend fun healthierOffline(limit: Int = HEALTHIER_LIMIT): List<SwapEntry> {
        val saved = savedEatOut(plans.observeEatOutOptions().first())
        val menus = finder.menuOptions(limit = MENU_SCAN_LIMIT).map(::menuEntry)
        return healthierRank(saved + menus).take(limit)
    }

    /** Nearby (AI) results for the healthier mode: the same local scan as Swap, then the healthier ranking. */
    suspend fun healthierFound(options: List<PlanBOption>): List<SwapEntry> =
        healthierRank(options.map(::foundEntry))

    /**
     * Scores each entry for how healthy it is against what is left today and orders them: choosable first, healthiest
     * first. Values a source did not give stay unknown; they are never counted as zero.
     */
    suspend fun healthierRank(entries: List<SwapEntry>): List<SwapEntry> {
        val left = finder.remaining()
        val scored = entries.map { e ->
            val n = when (val src = e.source) {
                is SwapSource.Found -> src.option.nutrition
                else -> MealNutrition(e.option.macros.kcal, e.option.macros.protein)
            }
            val h = NearbyRanking.healthier(n, left.kcalRemaining, left.proteinRemaining)
            e.copy(option = e.option.copy(score = h.score, reason = h.reason))
        }
        val order = NearbyRanking.orderHealthier(scored.map { it.option }).map { it.key }
        val byKey = scored.associateBy { it.option.key }
        return order.mapNotNull(byKey::get)
    }

    /** Logs the choice in place of the planned meal. A "contains" verdict is refused here too. */
    suspend fun choose(entry: SwapEntry, slot: MealSlot): SwapLogResult {
        if (entry.option.blocked) return SwapLogResult.Blocked(entry.option.allergenReason ?: "Contains an allergen you avoid")
        val title = entry.option.title
        return when (val src = entry.source) {
            is SwapSource.Recipe -> when (val r = plans.logMeal(src.content, slot)) {
                is MealLogResult.Blocked -> SwapLogResult.Blocked("Contains ${r.terms.joinToString()}")
                is MealLogResult.Logged -> SwapLogResult.Logged("Logged $title to ${slot.name.lowercase()}") { plans.undoLog(r.log) }
            }
            is SwapSource.Template -> when (val r = templates.log(src.templateId, slot)) {
                is TemplateLogResult.Blocked -> SwapLogResult.Blocked("Contains ${r.foods.joinToString()}")
                TemplateLogResult.NotFound -> SwapLogResult.Blocked("That saved meal is empty or gone")
                is TemplateLogResult.Logged -> SwapLogResult.Logged("Logged $title to ${slot.name.lowercase()}") { templates.undo(r.logs) }
            }
            is SwapSource.EatOut -> {
                val live = allergens.scan(name = src.option.whatToOrder)
                if (live.blocks) {
                    SwapLogResult.Blocked(reasonFor(SwapAllergen.CONTAINS, live.contains.map { it.matchedTerm }.distinct()) ?: "Contains an allergen you avoid")
                } else {
                    val log: FoodLogEntity = plans.logEatOut(src.option, slot)
                    SwapLogResult.Logged("Logged $title to ${slot.name.lowercase()}") { plans.undoLog(log) }
                }
            }
            is SwapSource.Found -> {
                // An imported menu item is scanned again now: the screen may be older than the person's allergen list.
                val live = finder.rescanMenuItem(src.option)
                if (live != null && live.blocks) {
                    SwapLogResult.Blocked(reasonFor(SwapAllergen.CONTAINS, live.contains.map { it.matchedTerm }.distinct()) ?: "Contains an allergen you avoid")
                } else if (src.option.rejected) {
                    SwapLogResult.Blocked(entry.option.allergenReason ?: "Contains an allergen you avoid")
                } else {
                    val log = finder.log(src.option, slot)
                    SwapLogResult.Logged("Logged $title to ${slot.name.lowercase()}") { plans.undoLog(log) }
                }
            }
        }
    }

    private fun AllergenStatus?.toSwap(): SwapAllergen = when (this) {
        AllergenStatus.CONTAINS -> SwapAllergen.CONTAINS
        AllergenStatus.MAY_CONTAIN -> SwapAllergen.MAY_CONTAIN
        AllergenStatus.CHECK_LABEL, AllergenStatus.UNKNOWN -> SwapAllergen.CHECK_LABEL
        else -> SwapAllergen.CLEAR
    }

    private fun AllergenScanResult.verdict(): SwapAllergen = SwapMath.verdict(blocks, mayContain.isNotEmpty(), hasCheckLabel)

    companion object {
        const val HEALTHIER_LIMIT = 40
        const val MENU_SCAN_LIMIT = 300

        /** "Contains milk and peanuts", "May contain milk", "Check the label: teriyaki", or null when clear. */
        fun reasonFor(verdict: SwapAllergen, terms: List<String>): String? {
            val t = when (terms.size) {
                0 -> null
                1 -> terms[0]
                else -> terms.dropLast(1).joinToString(", ") + " and " + terms.last()
            }
            return when (verdict) {
                SwapAllergen.CLEAR -> null
                SwapAllergen.CONTAINS -> if (t != null) "Contains $t" else "Contains an allergen you avoid"
                SwapAllergen.MAY_CONTAIN -> if (t != null) "May contain $t" else "May contain an allergen you avoid"
                SwapAllergen.CHECK_LABEL -> if (t != null) "Check the label: $t" else "Check the label"
            }
        }
    }
}
