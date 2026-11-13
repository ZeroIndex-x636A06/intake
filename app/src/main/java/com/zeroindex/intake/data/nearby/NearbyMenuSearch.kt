package com.zeroindex.intake.data.nearby

import android.content.Context
import com.zeroindex.intake.data.ai.AiError
import com.zeroindex.intake.data.ai.AiGateway
import com.zeroindex.intake.data.ai.AiPart
import com.zeroindex.intake.data.ai.S
import com.zeroindex.intake.data.ai.ServerTool
import com.zeroindex.intake.data.ai.menu.Fetched
import com.zeroindex.intake.data.ai.menu.MenuFetcher
import com.zeroindex.intake.data.ai.menu.MenuIngestor
import com.zeroindex.intake.data.ai.menu.MenuPrompts
import com.zeroindex.intake.data.ai.menu.ParsedMenu
import com.zeroindex.intake.data.ai.menu.PdfRoute
import com.zeroindex.intake.data.ai.menu.StagedItem
import com.zeroindex.intake.data.db.dao.MenuDao
import com.zeroindex.intake.data.db.entity.MenuSourceEntity
import com.zeroindex.intake.data.db.entity.MenuSourceKind
import com.zeroindex.intake.data.repository.AllergenRepository
import com.zeroindex.intake.domain.ai.AiFeature
import com.zeroindex.intake.domain.ai.AiTrigger
import com.zeroindex.intake.domain.nearby.ChainDirectory
import com.zeroindex.intake.domain.nearby.ChainEntry
import com.zeroindex.intake.domain.nearby.MenuLinkScorer
import com.zeroindex.intake.domain.nearby.MenuOrigin
import com.zeroindex.intake.domain.nearby.NearbyPlace
import com.zeroindex.intake.domain.nearby.RowPhase
import com.zeroindex.intake.domain.nearby.SourceMatch
import com.zeroindex.intake.domain.time.Clock
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

/** A menu that was found for one restaurant and saved as a source. */
data class DiscoveredMenu(val sourceId: String, val origin: MenuOrigin)

/** What AI looking for a menu may cost the person, and what it has cost so far in this search. */
class AiAllowance(val maxLookups: Int) {
    private var used = 0
    @Synchronized fun take(): Boolean = if (used < maxLookups) { used++; true } else false
}

@Serializable
internal data class FoundMenuUrl(val nutritionUrl: String)

/**
 * Finds a restaurant's menu and brings it in through the ordinary menu import, one restaurant at a time. In order:
 *  1. a menu already saved for the restaurant or its brand (no network);
 *  2. the bundled table of chain nutrition pages, tried with no AI;
 *  3. the restaurant's own website: its home page is read for links that mention nutrition, allergens, menu or a PDF,
 *     and the best few are tried with no AI (one level deep, one request each);
 *  4. only when the person switched it on for this search, AI: ask for the nutrition page's address with a web search and
 *     import that address, or, if none exists, estimates that are labelled as estimates.
 * Every menu found is saved as a source, so the next search finds it in step 1, with no signal.
 */
@Singleton
class NearbyMenuSearch @Inject constructor(
    @ApplicationContext private val context: Context,
    private val menuDao: MenuDao,
    private val ingestor: MenuIngestor,
    private val fetcher: MenuFetcher,
    private val gateway: AiGateway,
    private val allergens: AllergenRepository,
    private val clock: Clock,
) {
    private val chains: List<ChainEntry> by lazy {
        runCatching { context.assets.open("nearby_chains.json").bufferedReader().use { ChainDirectory.parse(it.readText()) } }.getOrDefault(emptyList())
    }

    /**
     * Finds and saves the menu for [place]. Returns null when none could be found; [onPhase] reports "looking" and
     * "reading". [aiCost] is told what each AI lookup cost. Cancelling the coroutine stops it at the next request.
     */
    suspend fun discover(
        place: NearbyPlace,
        allowance: AiAllowance?,
        onPhase: (RowPhase) -> Unit,
        onAiCost: (Double) -> Unit,
    ): DiscoveredMenu? {
        onPhase(RowPhase.LOOKING)
        val chain = ChainDirectory.match(place, chains)

        // 1. Already saved.
        val known = menuDao.sources().filter { it.itemCount > 0 }.map { SourceMatch.Known(it.id, it.restaurantName) }
        SourceMatch.find(place, chain, known)?.let { k ->
            if (menuDao.itemsOf(k.id).any { it.kcal != null }) return DiscoveredMenu(k.id, MenuOrigin.SAVED)
        }

        val name = chain?.name ?: place.displayName

        // 2. The chain's own page.
        for (u in chain?.urls.orEmpty()) {
            tryImport(name, u.url, allowAi = false, route = PdfRoute.PHONE_ONLY, onPhase = onPhase)?.let { return DiscoveredMenu(it, MenuOrigin.CHAIN) }
        }

        // 3. The restaurant's website, one level deep.
        place.website?.let { site ->
            onPhase(RowPhase.LOOKING)
            val page = runCatching { fetcher.fetch(site) }.getOrNull() as? Fetched.Page
            val html = page?.html
            if (html != null) {
                for (link in MenuLinkScorer.candidates(site, html, limit = 3)) {
                    delay(300)
                    tryImport(name, link.url, allowAi = false, route = PdfRoute.PHONE_ONLY, onPhase = onPhase)?.let { return DiscoveredMenu(it, MenuOrigin.WEBSITE) }
                }
            }
        }

        // 4. AI, only when the person turned it on for this search.
        if (allowance != null && gateway.isConfigured() && allowance.take()) {
            onPhase(RowPhase.LOOKING)
            val found = askForUrl(place, name, onAiCost)
            if (found != null) {
                tryImport(name, found, allowAi = true, route = PdfRoute.PHONE_THEN_AI, onPhase = onPhase)?.let { return DiscoveredMenu(it, MenuOrigin.AI_PAGE) }
            }
            if (allowance.take()) {
                estimate(place, name, onAiCost)?.let { return DiscoveredMenu(it, MenuOrigin.AI_ESTIMATE) }
            }
        }
        return null
    }

    /** Imports [url] as a saved source named [restaurant]; null (and nothing saved) when it gives no usable menu. */
    private suspend fun tryImport(restaurant: String, url: String, allowAi: Boolean, route: PdfRoute, onPhase: (RowPhase) -> Unit): String? {
        coroutineContext.ensureActive()
        val now = clock.nowMillis()
        val source = MenuSourceEntity(restaurantName = restaurant, kind = MenuSourceKind.URL, location = url, createdAt = now, updatedAt = now)
        return try {
            val outcome = ingestor.stage(source, AiTrigger.USER_ACTION, onProgress = { onPhase(RowPhase.READING) }, pdfRoute = route, allowAi = allowAi)
            if (outcome.items.count { it.entity.kcal != null } < MIN_ITEMS) return null
            save(source, outcome.items)
            source.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun save(source: MenuSourceEntity, items: List<StagedItem>) {
        // Every item is kept, including ones that contain an allergen: they show disabled in the list, never hidden.
        menuDao.upsertSource(source)
        ingestor.commit(source, items.map { it.entity })
    }

    private suspend fun askForUrl(place: NearbyPlace, restaurant: String, onAiCost: (Double) -> Unit): String? = try {
        val outcome = gateway.structured(
            feature = AiFeature.MENU_IMPORT,
            trigger = AiTrigger.USER_ACTION,
            serializer = FoundMenuUrl.serializer(),
            schema = S.obj("nutritionUrl" to S.string("The https address of the restaurant's own nutrition or allergen page or PDF, or an empty string")),
            system = "You find where a restaurant publishes its nutrition information. Search the web and return the address of " +
                "the restaurant's own nutrition, allergen or full menu page or PDF. Never guess an address: if you did not " +
                "see it in a search result, return an empty string. Reply with JSON only.",
            parts = listOf(AiPart.Text("Restaurant: $restaurant" + (place.website?.let { "\nIts website: $it" } ?: "") + (place.cuisine?.let { "\nCuisine: $it" } ?: ""))),
            tools = listOf(ServerTool.WebSearch(maxResults = 5, maxUses = 2)),
        )
        onAiCost(outcome.response.costUsd ?: 0.0)
        outcome.value.nutritionUrl.trim().takeIf { it.startsWith("https://") && !it.contains(' ') }
    } catch (e: AiError) {
        null
    }

    private suspend fun estimate(place: NearbyPlace, restaurant: String, onAiCost: (Double) -> Unit): String? = try {
        val avoided = allergens.avoidedAllergens()
        val outcome = gateway.structured(
            feature = AiFeature.MENU_IMPORT,
            trigger = AiTrigger.USER_ACTION,
            serializer = ParsedMenu.serializer(),
            schema = MenuPrompts.schema,
            system = MenuPrompts.system(restaurant, avoided) + "\nNo published nutrition could be found for this place. Return 6 to 12 of its typical " +
                "menu items with estimates from the descriptions you find, macrosPublished false. Never invent a dish that is not on its menu.",
            parts = listOf(AiPart.Text("Restaurant: $restaurant" + (place.website?.let { "\nIts website: $it" } ?: ""))),
            tools = listOf(ServerTool.WebSearch(maxResults = 5, maxUses = 2)),
        )
        onAiCost(outcome.response.costUsd ?: 0.0)
        val now = clock.nowMillis()
        val source = MenuSourceEntity(restaurantName = restaurant, kind = MenuSourceKind.TEXT, location = "", pastedText = "AI estimate, ${place.osmId}", createdAt = now, updatedAt = now)
        val staged = ingestor.stageEstimates(source, outcome.value.items)
        if (staged.size < MIN_ITEMS) null else { save(source.copy(enabled = false), staged); source.id }
    } catch (e: AiError) {
        null
    }

    companion object {
        /** A page that gives fewer menu items than this was not a menu. */
        const val MIN_ITEMS = 3
    }
}
