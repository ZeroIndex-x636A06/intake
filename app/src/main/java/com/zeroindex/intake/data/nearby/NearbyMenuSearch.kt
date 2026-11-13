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
import com.zeroindex.intake.data.ai.menu.ParsedItem
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
import com.zeroindex.intake.domain.menu.MenuAllergenEvidence
import com.zeroindex.intake.domain.nearby.BatchAnswer
import com.zeroindex.intake.domain.nearby.BatchItem
import com.zeroindex.intake.domain.nearby.MenuLinkScorer
import com.zeroindex.intake.domain.nearby.NearbyAiBatch
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

/** A menu that was found for one restaurant and saved as a source. */
data class DiscoveredMenu(val sourceId: String, val origin: MenuOrigin)

/** What one batched AI lookup produced: the menus it found by place id, and why it found none, if it did not. */
data class BatchOutcome(val found: Map<String, DiscoveredMenu>, val error: String? = null)

/**
 * Finds a restaurant's menu and brings it in through the ordinary menu import, one restaurant at a time. In order:
 *  1. a menu already saved for the restaurant or its brand (no network);
 *  2. the bundled table of chain nutrition pages, tried with no AI;
 *  3. the restaurant's own website: its home page is read for links that mention nutrition, allergens, menu or a PDF,
 *     and the best few are tried with no AI (one level deep, one request each);
 * Steps 1 to 3 never use AI ([discover]). A restaurant they find nothing for can be looked up by AI, only when the
 * person switched that on or tapped "Look up the rest with AI": [lookupBatch] sends up to 8 such restaurants in one call
 * and asks for each one's nutrition page address (imported with no further AI) or, failing that, estimated items that
 * are labelled as estimates and are never "clear".
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
     * Finds and saves the menu for [place] with no AI. Returns null when none could be found; [onPhase] reports "looking"
     * and "reading". Cancelling the coroutine stops it at the next request.
     */
    suspend fun discover(place: NearbyPlace, onPhase: (RowPhase) -> Unit): DiscoveredMenu? {
        onPhase(RowPhase.LOOKING)
        val chain = ChainDirectory.match(place, chains)

        // 1. Already saved.
        val known = menuDao.sources().filter { it.itemCount > 0 }.map { SourceMatch.Known(it.id, it.restaurantName) }
        SourceMatch.find(place, chain, known)?.let { k ->
            if (menuDao.itemsOf(k.id).any { it.kcal != null }) {
                // A saved AI estimate stays labelled as one: it is not published nutrition.
                val estimate = menuDao.source(k.id)?.pastedText?.startsWith(MenuAllergenEvidence.ESTIMATE_MARK) == true
                return DiscoveredMenu(k.id, if (estimate) MenuOrigin.AI_ESTIMATE else MenuOrigin.SAVED)
            }
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

    /** Whether [place] is a chain (known by the table or by the map's brand tag), as opposed to a local or independent place. */
    private fun isChain(place: NearbyPlace): Boolean = ChainDirectory.match(place, chains) != null || place.brand != null

    private fun nameOf(place: NearbyPlace): String = ChainDirectory.match(place, chains)?.name ?: place.displayName

    /**
     * One AI call for up to [NearbyAiBatch.BATCH_SIZE] restaurants that no free step found a menu for. Each restaurant
     * that gets a nutrition page address has that page read with no further AI; one that does not, but has estimated
     * items, gets those, labelled as estimates. [onPhase] says which restaurant is being read, and [onAiCost] is told
     * what the one call cost. A failed call gives an empty result with the reason, and spends nothing more.
     */
    suspend fun lookupBatch(places: List<NearbyPlace>, onPhase: (NearbyPlace, RowPhase) -> Unit, onAiCost: (Double) -> Unit): BatchOutcome {
        if (places.isEmpty()) return BatchOutcome(emptyMap())
        coroutineContext.ensureActive()
        val asked = NearbyAiBatch.restaurantsOf(places, ::isChain, ::nameOf)
        val avoided = allergens.avoidedAllergens()
        val answer = try {
            val outcome = gateway.structured(
                feature = AiFeature.MENU_IMPORT,
                trigger = AiTrigger.USER_ACTION,
                serializer = BatchAnswer.serializer(),
                schema = batchSchema,
                system = NearbyAiBatch.system(avoided),
                parts = listOf(AiPart.Text(NearbyAiBatch.userText(asked))),
                tools = listOf(ServerTool.WebSearch(maxResults = 8, maxUses = 4)),
                maxTokens = 12_000,
            )
            onAiCost(outcome.response.costUsd ?: 0.0)
            outcome.value
        } catch (e: AiError) {
            return BatchOutcome(emptyMap(), e.message ?: "The AI lookup failed.")
        }
        val resolved = NearbyAiBatch.resolve(asked, answer)
        val found = LinkedHashMap<String, DiscoveredMenu>()
        for ((index, place) in places.withIndex()) {
            coroutineContext.ensureActive()
            val r = resolved[asked[index].id] ?: continue
            val restaurant = asked[index].name
            if (r.nutritionUrl != null) {
                onPhase(place, RowPhase.READING)
                val id = tryImport(restaurant, r.nutritionUrl, allowAi = false, route = PdfRoute.PHONE_ONLY, onPhase = { onPhase(place, it) })
                if (id != null) {
                    found[place.osmId] = DiscoveredMenu(id, MenuOrigin.AI_PAGE)
                    continue
                }
            }
            if (r.items.isNotEmpty()) {
                onPhase(place, RowPhase.READING)
                saveEstimates(place, restaurant, r.items)?.let { found[place.osmId] = DiscoveredMenu(it, MenuOrigin.AI_ESTIMATE) }
            }
        }
        return BatchOutcome(found, if (found.isEmpty()) "The AI found no menu for these places." else null)
    }

    private suspend fun saveEstimates(place: NearbyPlace, restaurant: String, items: List<BatchItem>): String? {
        val now = clock.nowMillis()
        val source = MenuSourceEntity(restaurantName = restaurant, kind = MenuSourceKind.TEXT, location = "", pastedText = "AI estimate, ${place.osmId}", createdAt = now, updatedAt = now)
        val parsed = items.map {
            ParsedItem(
                name = it.name.trim(), description = it.description.trim(), ingredients = "",
                kcal = it.kcal, protein = it.protein, carbs = it.carbs, fat = it.fat,
                macrosPublished = false, priceCents = null, containsAllergens = it.containsAllergens, allergenNotes = "",
            )
        }
        val staged = ingestor.stageEstimates(source, parsed)
        if (staged.size < MIN_ITEMS) return null
        save(source.copy(enabled = false), staged)
        return source.id
    }

    private val batchSchema by lazy {
        S.obj(
            "restaurants" to S.array(
                S.obj(
                    "id" to S.string("The id from the list"),
                    "nutritionUrl" to S.string("The https address of the restaurant's own nutrition, allergen or menu page or PDF, or an empty string"),
                    "items" to S.array(
                        S.obj(
                            "name" to S.string(),
                            "description" to S.string(),
                            "kcal" to S.nullableNumber(),
                            "protein" to S.nullableNumber(),
                            "carbs" to S.nullableNumber(),
                            "fat" to S.nullableNumber(),
                            "containsAllergens" to S.array(S.string()),
                        ),
                    ),
                ),
            ),
        )
    }

    companion object {
        /** A page that gives fewer menu items than this was not a menu. */
        const val MIN_ITEMS = NearbyAiBatch.MIN_ITEMS
    }
}
