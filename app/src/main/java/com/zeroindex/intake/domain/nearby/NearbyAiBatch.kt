package com.zeroindex.intake.domain.nearby

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One restaurant in a batched AI lookup. [id] is what the model echoes back ("r1", "r2", ...). */
data class BatchRestaurant(
    val id: String,
    val name: String,
    val website: String?,
    val cuisine: String?,
    /** True when the place belongs to a known chain or carries a brand tag; false for a local or independent place. */
    val isChain: Boolean,
)

/** One item the model estimated for a restaurant. Numbers are null when the model could not tell. */
@Serializable
data class BatchItem(
    val name: String,
    val description: String = "",
    val kcal: Double? = null,
    val protein: Double? = null,
    val carbs: Double? = null,
    val fat: Double? = null,
    /** Allergens the model knows the item contains. Only ever raises a warning. */
    val containsAllergens: List<String> = emptyList(),
)

@Serializable
data class BatchEntry(
    val id: String,
    val nutritionUrl: String = "",
    val items: List<BatchItem> = emptyList(),
)

@Serializable
data class BatchAnswer(val restaurants: List<BatchEntry> = emptyList())

/** What the answer gave for one restaurant: a nutrition page to read, else estimated items, else nothing. */
data class BatchResolution(val nutritionUrl: String?, val items: List<BatchItem>)

/**
 * The AI half of the nearby search, pure so it can be tested. Rather than one model call per restaurant, up to
 * [BATCH_SIZE] restaurants that no free step could find a menu for go in one call, and a search makes at most
 * [MAX_BATCHES] such calls, nearest restaurants first. The model is asked for each restaurant's own nutrition page
 * address and, for the ones with none, labelled estimates with the allergens it knows each item contains.
 *
 * Nothing here decides an allergen verdict: the answer is raw input for the menu import and the local engine.
 */
object NearbyAiBatch {
    const val BATCH_SIZE = 8
    const val MAX_BATCHES = 3

    /** Fewer items than this is not a menu. */
    const val MIN_ITEMS = 3

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    /** The batches for [pending], nearest first, at most [maxBatches] of at most [batchSize] places each. */
    fun plan(pending: List<NearbyPlace>, batchSize: Int = BATCH_SIZE, maxBatches: Int = MAX_BATCHES): List<List<NearbyPlace>> =
        pending.sortedBy { it.distanceM }.chunked(batchSize).take(maxBatches)

    /** How many model calls looking up [pending] restaurants would make, within the cap. */
    fun callsFor(pending: Int, batchSize: Int = BATCH_SIZE, maxBatches: Int = MAX_BATCHES): Int =
        if (pending <= 0) 0 else minOf(maxBatches, (pending + batchSize - 1) / batchSize)

    /** How many of [pending] restaurants one tap can cover. */
    fun coveredBy(pending: Int, batchSize: Int = BATCH_SIZE, maxBatches: Int = MAX_BATCHES): Int =
        minOf(pending.coerceAtLeast(0), batchSize * maxBatches)

    /** What the card says before the person runs it: the calls it will make and how many restaurants they cover. */
    fun costNote(pending: Int): String {
        val calls = callsFor(pending)
        if (calls == 0) return "Nothing left to look up."
        val covered = coveredBy(pending)
        return "$calls AI call${if (calls == 1) "" else "s"} for ${if (covered == pending) "" else "the nearest "}$covered " +
            "restaurant${if (covered == 1) "" else "s"}, a few cents each on your own key. The cost so far is shown while it runs."
    }

    fun restaurantsOf(places: List<NearbyPlace>, isChain: (NearbyPlace) -> Boolean, nameOf: (NearbyPlace) -> String): List<BatchRestaurant> =
        places.mapIndexed { i, p -> BatchRestaurant("r${i + 1}", nameOf(p), p.website, p.cuisine, isChain(p)) }

    fun system(avoided: Set<String>): String = buildString {
        appendLine("You find nutrition information for restaurants near the user. You get a numbered list; answer for every id in it.")
        appendLine(
            "For each restaurant: (1) Search the web for its own official nutrition, allergen or full menu page or PDF and put its " +
                "https address in nutritionUrl. Never guess an address: if you did not see it in a search result, leave nutritionUrl empty.",
        )
        appendLine(
            "(2) Also give 6 to 10 of its typical menu items in items, with calories, protein, carbs and fat ESTIMATED per standard " +
                "serving from what you find. Never invent a dish that is not on its menu. If you cannot tell what the restaurant serves, return no items.",
        )
        appendLine(
            "(3) For every item, list in containsAllergens each allergen you KNOW the item contains, from the chain's published allergen " +
                "information or from what the dish is plainly made of. Use the names milk, egg, wheat, soy, peanut, tree nut, fish, shellfish and " +
                "sesame. Example: a McDonald's Hotcakes item contains milk, egg and wheat; a Filet-O-Fish contains fish, milk, egg, wheat and soy. " +
                "List everything you are reasonably sure of. Leaving an allergen out never means the item is free of it.",
        )
        appendLine("A local or independent restaurant (not a chain) gets estimates only; leave nutritionUrl empty unless the address is its own menu page.")
        if (avoided.isNotEmpty()) {
            appendLine("The user avoids ${avoided.joinToString()}. Flag any of these an item is likely to contain, including in sauces and breading.")
        }
        append("Reply with JSON only, matching the schema, with one entry per id.")
    }

    fun userText(restaurants: List<BatchRestaurant>): String = buildString {
        appendLine("Restaurants:")
        restaurants.forEach { r ->
            append("${r.id}: ${r.name}")
            append(if (r.isChain) " (a chain)" else " (local or independent)")
            r.cuisine?.let { append(", cuisine $it") }
            r.website?.let { append(", website $it") }
            appendLine()
        }
    }.trimEnd()

    /** Reads the model's reply; null when it is not the expected JSON. */
    fun parse(text: String): BatchAnswer? = runCatching { json.decodeFromString(BatchAnswer.serializer(), text) }.getOrNull()

    /** An https address with no spaces, the only kind a menu import is given. */
    fun validUrl(url: String): String? = url.trim().takeIf { it.startsWith("https://") && it.length > 12 && !it.contains(' ') }

    /**
     * Matches the answer to the restaurants asked about. Entries with an id that was not asked about are dropped, the
     * first entry for an id wins, items without a name or without calories and protein are dropped, and a restaurant
     * with fewer than [MIN_ITEMS] usable items has none. A restaurant the answer does not mention is simply absent.
     */
    fun resolve(asked: List<BatchRestaurant>, answer: BatchAnswer): Map<String, BatchResolution> {
        val ids = asked.map { it.id }.toSet()
        val out = LinkedHashMap<String, BatchResolution>()
        for (e in answer.restaurants) {
            val id = e.id.trim().lowercase()
            if (id !in ids || id in out) continue
            val items = e.items.filter { it.name.isNotBlank() && it.kcal != null && it.protein != null }
            out[id] = BatchResolution(validUrl(e.nutritionUrl), if (items.size >= MIN_ITEMS) items else emptyList())
        }
        return out
    }
}
