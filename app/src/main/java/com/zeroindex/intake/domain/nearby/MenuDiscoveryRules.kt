package com.zeroindex.intake.domain.nearby

import java.net.URI
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/** A menu address from the bundled chain table. [status] is what the last check saw ("verified", "blocked", ...). */
data class ChainUrl(val url: String, val kind: String, val status: String)

data class ChainEntry(val name: String, val wikidata: String?, val aliases: Set<String>, val urls: List<ChainUrl>)

/** The bundled table of known chain nutrition and allergen pages (assets/nearby_chains.json) and how a place is matched to it. */
object ChainDirectory {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(text: String): List<ChainEntry> {
        val chains = runCatching { json.parseToJsonElement(text).jsonObject["chains"]?.jsonArray }.getOrNull() ?: return emptyList()
        return chains.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            fun s(k: String) = (o[k] as? JsonPrimitive)?.takeIf { it.isString }?.content
            val name = s("name") ?: return@mapNotNull null
            val aliases = ((o["aliases"] as? JsonArray) ?: JsonArray(emptyList())).mapNotNull { (it as? JsonPrimitive)?.content }
                .map(NameMatch::normalize).toSet() + NameMatch.normalize(name)
            val urls = ((o["urls"] as? JsonArray) ?: JsonArray(emptyList())).mapNotNull { u ->
                val uo = u as? JsonObject ?: return@mapNotNull null
                val url = (uo["url"] as? JsonPrimitive)?.content ?: return@mapNotNull null
                ChainUrl(url, (uo["kind"] as? JsonPrimitive)?.content ?: "page", (uo["status"] as? JsonPrimitive)?.content ?: "unverified")
            }
            ChainEntry(name, s("wikidata")?.lowercase(), aliases, urls)
        }
    }

    /** The chain a place belongs to: by brand Wikidata id first, then by the normalised brand or name. */
    fun match(place: NearbyPlace, chains: List<ChainEntry>): ChainEntry? {
        place.brandWikidata?.lowercase()?.let { id -> chains.firstOrNull { it.wikidata == id }?.let { return it } }
        val keys = listOfNotNull(place.brand, place.name).map(NameMatch::normalize).filter { it.isNotEmpty() }
        return chains.firstOrNull { c -> keys.any { it in c.aliases } }
    }
}

/** A link on a restaurant's website that may lead to its nutrition or menu, with why it scored as it did. */
data class MenuLink(val url: String, val text: String, val score: Int)

/**
 * Looks through one page of a restaurant's own site for the way to its nutrition, allergen or menu information, with no
 * model. Words that name nutrition or allergens count most, then ".pdf", then "menu"; careers, gift cards, catering and
 * social sites count against. A link to another site is only kept when it is a PDF.
 */
object MenuLinkScorer {
    const val MIN_SCORE = 5

    private val ANCHOR = Regex("<a\\b[^>]*?href\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')[^>]*>(.*?)</a>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val TAGS = Regex("<[^>]+>")
    private val SKIP_HOSTS = listOf(
        "facebook.", "instagram.", "twitter.", "x.com", "tiktok.", "youtube.", "yelp.", "tripadvisor.", "doordash.", "ubereats.",
        "grubhub.", "google.", "maps.", "apple.com", "linkedin.", "pinterest.", "opentable.", "toasttab.", "squareup.",
    )
    private val BAD_WORDS = listOf("career", "jobs", "gift", "catering", "franchise", "privacy", "terms", "login", "signin", "cart", "wine", "cocktail", "reserve", "press", "contact")

    fun candidates(baseUrl: String, html: String, limit: Int = 5): List<MenuLink> {
        val base = runCatching { URI(baseUrl) }.getOrNull() ?: return emptyList()
        val baseHost = base.host?.removePrefix("www.")?.lowercase() ?: return emptyList()
        val found = LinkedHashMap<String, MenuLink>()
        for (m in ANCHOR.findAll(html)) {
            val href = (m.groupValues[1].ifEmpty { m.groupValues[2] }).trim()
            if (href.isEmpty() || href.startsWith("#") || href.startsWith("mailto:", true) || href.startsWith("tel:", true) || href.startsWith("javascript:", true)) continue
            val resolved = runCatching { base.resolve(href.replace(" ", "%20")) }.getOrNull() ?: continue
            if (resolved.scheme?.lowercase() !in setOf("http", "https")) continue
            val host = resolved.host?.removePrefix("www.")?.lowercase() ?: continue
            if (SKIP_HOSTS.any { host.contains(it) }) continue
            val text = TAGS.replace(m.groupValues[3], " ").replace(Regex("&[a-z]+;"), " ").replace(Regex("\\s+"), " ").trim().take(80)
            val url = resolved.toString().substringBefore('#')
            val isPdf = url.substringBefore('?').lowercase().endsWith(".pdf")
            val sameSite = host == baseHost || host.endsWith(".$baseHost") || baseHost.endsWith(".$host")
            if (!sameSite && !isPdf) continue
            val score = score(url, text, isPdf, sameSite)
            if (score < MIN_SCORE) continue
            val existing = found[url]
            if (existing == null || existing.score < score) found[url] = MenuLink(url, text, score)
        }
        return found.values.sortedByDescending { it.score }.take(limit)
    }

    internal fun score(url: String, text: String, isPdf: Boolean, sameSite: Boolean): Int {
        val u = url.lowercase()
        val t = text.lowercase()
        fun has(w: String) = w in u || w in t
        var s = 0
        if (has("nutrition")) s += 10
        if (has("allergen") || has("allergy")) s += 9
        if (has("calorie")) s += 4
        if (isPdf) s += 6
        if (has("menu")) s += 5
        if (sameSite) s += 1
        if (BAD_WORDS.any { has(it) }) s -= 8
        if (has("kids") && !has("nutrition")) s -= 2
        return s
    }
}

/** Matching a map place to a menu source the person already has, by brand or by normalised name. */
object SourceMatch {
    /** A saved source's id and the restaurant name it was saved under. */
    data class Known(val id: String, val restaurantName: String)

    fun find(place: NearbyPlace, chain: ChainEntry?, known: List<Known>): Known? =
        known.firstOrNull { k ->
            NameMatch.same(k.restaurantName, place.displayName) || NameMatch.same(k.restaurantName, place.name) ||
                (chain != null && (NameMatch.normalize(k.restaurantName) in chain.aliases || NameMatch.same(k.restaurantName, chain.name)))
        }
}
