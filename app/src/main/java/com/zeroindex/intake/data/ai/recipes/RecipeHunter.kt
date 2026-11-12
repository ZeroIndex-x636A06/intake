package com.zeroindex.intake.data.ai.recipes

import com.zeroindex.intake.data.ai.AiError
import com.zeroindex.intake.data.ai.AiGateway
import com.zeroindex.intake.data.ai.AiPart
import com.zeroindex.intake.data.ai.ServerTool
import com.zeroindex.intake.data.db.dao.RecipeDao
import com.zeroindex.intake.data.db.entity.AllergenStatus
import com.zeroindex.intake.data.db.entity.RecipeEntity
import com.zeroindex.intake.data.db.entity.RecipeOrigin
import com.zeroindex.intake.data.recipe.FetchResult
import com.zeroindex.intake.data.recipe.RecipeFetcher
import com.zeroindex.intake.data.repository.AllergenRepository
import com.zeroindex.intake.domain.ai.AiFeature
import com.zeroindex.intake.domain.ai.AiTrigger
import com.zeroindex.intake.domain.ai.Fit
import com.zeroindex.intake.domain.ai.HuntDedupe
import com.zeroindex.intake.domain.ai.HuntPolicy
import com.zeroindex.intake.domain.ai.HuntRules
import com.zeroindex.intake.domain.ai.HuntStop
import com.zeroindex.intake.domain.ai.RecipeCandidate
import com.zeroindex.intake.domain.ai.RecipeFilter
import com.zeroindex.intake.domain.ai.RecipeQuery
import com.zeroindex.intake.domain.ai.RecipeVerdict
import com.zeroindex.intake.domain.allergen.AllergenScanResult
import com.zeroindex.intake.domain.allergen.ModelAllergenClaim
import com.zeroindex.intake.domain.allergen.ModelAllergenPolicy
import com.zeroindex.intake.domain.recipe.PageParse
import com.zeroindex.intake.domain.recipe.RecipePageParser
import com.zeroindex.intake.domain.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** A recipe as read from its own page by this app. A null number is "the page does not state it". */
data class HuntRecipe(
    val title: String,
    val sourceUrl: String,
    val sourceName: String,
    val servings: Int,
    val totalMinutes: Int?,
    val ingredients: List<String>,
    val steps: List<String>,
    val kcalPerServing: Double?,
    val proteinPerServing: Double?,
    val carbsPerServing: Double?,
    val fatPerServing: Double?,
) {
    val nutritionStated: Boolean get() = kcalPerServing != null || proteinPerServing != null
}

/** One result with the local verdict and the macro fit attached. */
data class HuntedRecipe(
    val recipe: HuntRecipe,
    val verdict: RecipeVerdict,
    val scan: AllergenScanResult,
    val fit: Fit,
    /** Why it is a near miss or removed, when the numbers are the reason. */
    val fitReason: String? = null,
) {
    private val checked get() = verdict is RecipeVerdict.Passed || verdict is RecipeVerdict.NeedsReview

    /** Inside every limit the page lets us check, or nothing to check. */
    val isKept get() = checked && (fit == Fit.MATCH || fit == Fit.UNCHECKED)

    /** Over a limit by 10% or less. */
    val isClose get() = checked && fit == Fit.CLOSE
    val isRemoved get() = verdict is RecipeVerdict.Rejected
    val isUnverified get() = verdict is RecipeVerdict.Unverified
    val isSavable get() = checked
}

data class HuntProgress(
    val message: String,
    val round: Int,
    val costUsd: Double = 0.0,
    val kept: Int = 0,
)

data class HuntResult(
    val results: List<HuntedRecipe>,
    /** Rounds and cost are totals over the search and every "Find more" after it. */
    val rounds: Int,
    val costUsd: Double,
    val model: String,
    val stop: HuntStop,
    val policy: HuntPolicy,
    /** What the last run (the search or the latest "Find more") spent against [HuntPolicy.capUsd]. */
    val lastRunCostUsd: Double,
    val candidatesSeen: Int,
) {
    val kept: List<HuntedRecipe> get() = results.filter { it.isKept }
    val close: List<HuntedRecipe> get() = results.filter { it.isClose }
    val removed: List<HuntedRecipe> get() = results.filter { it.isRemoved }
    val unverified: List<HuntedRecipe> get() = results.filter { it.isUnverified }
}

/**
 * Amendment §1 Recipe Hunter: a loop of search, read, verify.
 *
 * 1. The model searches the web (OpenRouter's server side search, nothing else) and returns recipe page
 *    addresses, about [HuntPolicy.perRound] a round. It does not read or copy the recipes: that made each
 *    round slow and costly and left room for only a handful of recipes in its reply.
 * 2. This app downloads each page itself, a few at a time, and reads the page's own recipe data
 *    ([RecipePageParser]). Allergens are scanned here over the full ingredient list; protein, calories and
 *    time are compared with the page's published numbers ([RecipeFilter]). A page with no published
 *    nutrition is kept and labelled, a near miss goes to "Close matches", and a page that cannot be read is
 *    listed as unchecked, never passed.
 * 3. Rounds continue until [HuntPolicy.targetKept] are kept or a round or cost cap is reached. Every address
 *    and title already seen is sent back as an exclusion and also filtered here.
 *
 * [hunt] is called only from a tap (Search, or Find more). Dairy-free is not a prompt instruction the model
 * may or may not follow: it is step 2, in code, on every result.
 */
@Singleton
class RecipeHunter @Inject constructor(
    private val gateway: AiGateway,
    private val allergens: AllergenRepository,
    private val recipeDao: RecipeDao,
    private val fetcher: RecipeFetcher,
    private val clock: Clock,
) {
    companion object {
        /** Pages read at once. Kind to the sites and to the phone. */
        const val FETCH_CONCURRENCY = 4

        /** Web searches the model may run in one round; the main cost of a round. */
        const val SEARCHES_PER_ROUND = 2
        const val RESULTS_PER_SEARCH = 10

        /** Used to predict the next round's price when the response carries no cost. */
        const val ASSUMED_ROUND_USD = 0.025

        /** Most exclusions shown to the model, newest last, so the prompt stays small on a long hunt. */
        const val MAX_EXCLUSIONS = 60
    }

    /**
     * Runs one search, or, with [previous], a "Find more" that continues it: everything in [previous] is
     * excluded and the caps (rounds, cost, target) start again for the new run.
     */
    suspend fun hunt(
        query: RecipeQuery,
        previous: HuntResult? = null,
        policy: HuntPolicy = previous?.policy ?: HuntPolicy(),
        onProgress: (HuntProgress) -> Unit,
    ): HuntResult {
        val avoided = query.avoidedAllergens.ifEmpty { allergens.avoidedAllergens() }
        val dedupe = HuntDedupe()
        val results = previous?.results.orEmpty().toMutableList()
        results.forEach { dedupe.add(it.recipe.sourceUrl, it.recipe.title) }
        val keptAtStart = results.count { it.isKept }
        var seen = previous?.candidatesSeen ?: 0
        var cost = 0.0
        var model = previous?.model.orEmpty()
        var round = 0
        var lastRoundCost = 0.0
        var lastNew: Int? = null
        var stop = HuntStop.TARGET

        while (true) {
            val keptNow = results.count { it.isKept }
            val reason = HuntRules.stopReason(policy, keptNow - keptAtStart, round, cost, lastRoundCost, lastNew)
            if (reason != null) {
                stop = reason
                break
            }
            round++
            onProgress(
                HuntProgress(
                    if (round == 1) "Searching the web for recipe pages…" else "Looking for more recipes…",
                    round, cost, keptNow,
                ),
            )
            val outcome = try {
                gateway.structured(
                    feature = AiFeature.RECIPE_HUNTER,
                    trigger = AiTrigger.USER_ACTION,
                    serializer = HuntCandidates.serializer(),
                    schema = HuntPrompts.schema,
                    system = HuntPrompts.system(avoided),
                    parts = listOf(AiPart.Text(userPrompt(query, avoided, policy.perRound, results))),
                    tools = listOf(ServerTool.WebSearch(maxResults = RESULTS_PER_SEARCH, maxUses = SEARCHES_PER_ROUND)),
                )
            } catch (e: AiError) {
                // Keep what earlier rounds found rather than throwing a good list away. A failure of the very
                // first round has nothing to show, so it is the person's to see.
                if (round == 1 && previous == null) throw e
                round--
                stop = HuntStop.FAILED
                break
            }
            val roundCost = outcome.response.costUsd ?: ASSUMED_ROUND_USD
            cost += roundCost
            lastRoundCost = roundCost
            model = outcome.response.model

            val fresh = outcome.value.candidates.filter { c ->
                c.sourceUrl.startsWith("http") && dedupe.add(c.sourceUrl, c.title)
            }
            lastNew = fresh.size
            seen += fresh.size
            onProgress(HuntProgress("Reading ${fresh.size} recipe pages and checking them on this phone…", round, cost, keptNow))

            val gate = Semaphore(FETCH_CONCURRENCY)
            val judged = coroutineScope {
                fresh.map { c -> async { gate.withPermit { investigate(c, query, avoided) } } }.awaitAll()
            }
            results += judged
            onProgress(HuntProgress("Checked ${judged.size} more", round, cost, results.count { it.isKept }))
        }
        return HuntResult(
            results = results.toList(),
            rounds = (previous?.rounds ?: 0) + round,
            costUsd = (previous?.costUsd ?: 0.0) + cost,
            model = model,
            stop = stop,
            policy = policy,
            lastRunCostUsd = cost,
            candidatesSeen = seen,
        )
    }

    /** Downloads and reads one candidate's page, then judges it locally. Never throws for a bad page. */
    internal suspend fun investigate(c: HuntCandidate, query: RecipeQuery, avoided: Set<String>): HuntedRecipe {
        val bare = HuntRecipe(c.title, c.sourceUrl, c.sourceName, query.servings, null, emptyList(), emptyList(), null, null, null, null)
        fun unverified(why: String) = HuntedRecipe(bare, RecipeVerdict.Unverified(why), AllergenScanResult.CLEAN, Fit.UNCHECKED)

        val page = when (val f = fetcher.fetch(c.sourceUrl)) {
            is FetchResult.Failed -> return unverified("Could not open the page. ${f.message}")
            is FetchResult.Page -> f
        }
        val draft = when (val parsed = RecipePageParser.parse(page.html, page.finalUrl)) {
            is PageParse.NoRecipe -> return unverified("The page has no recipe data this app can read")
            is PageParse.Found -> parsed.recipe
        }
        val n = draft.nutrition
        val recipe = HuntRecipe(
            title = draft.name.ifBlank { c.title },
            sourceUrl = c.sourceUrl,
            sourceName = c.sourceName,
            servings = draft.servings ?: query.servings,
            totalMinutes = draft.totalMinutes ?: listOfNotNull(draft.prepMinutes, draft.cookMinutes).takeIf { it.isNotEmpty() }?.sum(),
            ingredients = draft.ingredients,
            steps = draft.steps,
            kcalPerServing = n?.kcal,
            proteinPerServing = n?.proteinG,
            carbsPerServing = n?.carbsG,
            fatPerServing = n?.fatG,
        )
        return judge(recipe, c, query, avoided)
    }

    internal suspend fun judge(recipe: HuntRecipe, c: HuntCandidate?, query: RecipeQuery, avoided: Set<String>): HuntedRecipe {
        val local = allergens.scan(
            name = recipe.title,
            ingredients = recipe.ingredients.joinToString("\n"),
            checkLabelInIngredients = true,
        )
        // The model's snippet hint can only add a warning; the local scan is the authority and is never overridden.
        val claims = c?.takeIf { it.possibleAllergens.isNotEmpty() }
            ?.let { listOf(ModelAllergenClaim(recipe.title, it.possibleAllergens, it.allergenNote)) }
            .orEmpty()
        val scan = ModelAllergenPolicy.merge(local = listOf(local), claims = claims, avoided = avoided, source = "Recipe Hunter").scan
        val candidate = RecipeCandidate(
            title = recipe.title,
            sourceUrl = recipe.sourceUrl,
            ingredients = recipe.ingredients,
            kcalPerServing = recipe.kcalPerServing,
            proteinPerServing = recipe.proteinPerServing,
            totalMinutes = recipe.totalMinutes,
        )
        val verdict = RecipeFilter.evaluate(candidate, query, scan)
        val fit = RecipeFilter.fitOf(candidate, query)
        return HuntedRecipe(recipe, verdict, scan, fit.fit, fit.reason)
    }

    /** Saves a result to My recipes. Removed and unchecked recipes cannot be saved. */
    suspend fun save(hunted: HuntedRecipe): RecipeEntity {
        require(hunted.isSavable) { "A removed or unchecked recipe cannot be saved" }
        val r = hunted.recipe
        val now = clock.nowMillis()
        val entity = RecipeEntity(
            name = r.title,
            servings = r.servings.coerceAtLeast(1),
            instructions = r.steps.mapIndexed { i, s -> "${i + 1}. $s" }.joinToString("\n"),
            totalTimeMinutes = r.totalMinutes ?: 0,
            origin = RecipeOrigin.HUNTER,
            sourceUrl = r.sourceUrl,
            ingredientsText = r.ingredients.joinToString("\n"),
            kcalPerServing = r.kcalPerServing,
            proteinPerServing = r.proteinPerServing,
            carbsPerServing = r.carbsPerServing,
            fatPerServing = r.fatPerServing,
            allergenStatus = when (hunted.verdict) {
                is RecipeVerdict.NeedsReview -> AllergenStatus.MAY_CONTAIN
                else -> if (hunted.scan.hasCheckLabel) AllergenStatus.CHECK_LABEL else AllergenStatus.CLEAR
            },
            createdAt = now,
            updatedAt = now,
        )
        recipeDao.upsertRecipe(entity)
        return entity
    }

    private fun userPrompt(q: RecipeQuery, avoided: Set<String>, wanted: Int, seen: List<HuntedRecipe>) = buildString {
        appendLine("Find $wanted new recipe pages.")
        q.minProteinPerServing?.let { appendLine("Aim for at least $it g protein per serving.") }
        q.maxKcalPerServing?.let { appendLine("Aim for at most $it kcal per serving.") }
        appendLine("About ${q.servings} servings, good for batch cooking and reheating.")
        q.maxMinutes?.let { appendLine("Total time at most $it minutes.") }
        if (q.cuisines.isNotEmpty()) appendLine("Cuisines: ${q.cuisines.joinToString()}.")
        if (avoided.isNotEmpty()) appendLine("Must be free of: ${avoided.joinToString()}.")
        if (q.notes.isNotBlank()) appendLine("Also: ${q.notes.trim()}")
        if (seen.isNotEmpty()) {
            appendLine("Already returned, so do not return these addresses or these dishes again:")
            seen.takeLast(MAX_EXCLUSIONS).forEach { appendLine("- ${it.recipe.title} ${it.recipe.sourceUrl}") }
        }
    }
}
