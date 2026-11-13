package com.zeroindex.intake.domain.nearby

import java.text.Normalizer
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** One restaurant, cafe or fast-food place from OpenStreetMap. */
data class NearbyPlace(
    val osmId: String,
    val name: String,
    val brand: String?,
    val brandWikidata: String?,
    /** An https address if the map has one, else null. */
    val website: String?,
    val cuisine: String?,
    val amenity: String,
    val lat: Double,
    val lon: Double,
    val distanceM: Double,
) {
    /** What groups branches of one chain: the brand's Wikidata id, else the brand, else the name. */
    val chainKey: String get() = brandWikidata?.lowercase() ?: NameMatch.normalize(brand ?: name)

    /** The name to show: the brand when the map gives one, else the place's own name. */
    val displayName: String get() = brand?.takeIf { it.isNotBlank() } ?: name

    val distanceMiles: Double get() = TravelRadius.metersToMiles(distanceM)
}

/** Name comparison that ignores case, accents, punctuation and a leading "the". */
object NameMatch {
    fun normalize(raw: String): String {
        val folded = Normalizer.normalize(raw, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        return folded.lowercase().replace("&", " and ").replace(Regex("^the\\s+"), "").replace(Regex("[^a-z0-9]+"), "")
    }

    /** True when the two names are the same place or one is the other plus a suffix ("subway" / "subway restaurant"). */
    fun same(a: String, b: String): Boolean {
        val x = normalize(a)
        val y = normalize(b)
        if (x.isEmpty() || y.isEmpty()) return false
        if (x == y) return true
        val (short, long) = if (x.length <= y.length) x to y else y to x
        return short.length >= 5 && long.startsWith(short)
    }
}

/**
 * Reads the answer of one Overpass API request (amenity = restaurant, fast_food or cafe around a point) and orders it.
 * Pure: the request itself is in the data layer.
 */
object OverpassParser {
    private val AMENITIES = setOf("restaurant", "fast_food", "cafe")

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** The query text for one request. Coordinates are the rounded ones; [radiusM] is from [TravelRadius]. */
    fun query(lat: Double, lon: Double, radiusM: Int, timeoutSec: Int = 25): String =
        "[out:json][timeout:$timeoutSec];" +
            "nwr[\"amenity\"~\"^(restaurant|fast_food|cafe)$\"][\"name\"](around:$radiusM,${"%.4f".format(java.util.Locale.US, lat)},${"%.4f".format(java.util.Locale.US, lon)});" +
            "out center tags;"

    /** Parses [body]; places beyond [radiusM] of the origin are dropped. The result is nearest first. Bad input gives an empty list. */
    fun parse(body: String, originLat: Double, originLon: Double, radiusM: Int): List<NearbyPlace> {
        val elements: JsonArray = runCatching { json.parseToJsonElement(body).jsonObject["elements"]?.jsonArray }.getOrNull() ?: return emptyList()
        val out = ArrayList<NearbyPlace>()
        for (el in elements) {
            val o = el as? JsonObject ?: continue
            val tags = o["tags"] as? JsonObject ?: continue
            fun tag(k: String): String? = (tags[k] as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()?.takeIf { it.isNotEmpty() }
            val amenity = tag("amenity") ?: continue
            if (amenity !in AMENITIES) continue
            val name = tag("name") ?: continue
            val center = o["center"] as? JsonObject
            val lat = (o["lat"] as? JsonPrimitive)?.doubleOrNull ?: (center?.get("lat") as? JsonPrimitive)?.doubleOrNull ?: continue
            val lon = (o["lon"] as? JsonPrimitive)?.doubleOrNull ?: (center?.get("lon") as? JsonPrimitive)?.doubleOrNull ?: continue
            val d = distanceM(originLat, originLon, lat, lon)
            if (d > radiusM * 1.05) continue
            val type = (o["type"] as? JsonPrimitive)?.content ?: "node"
            val id = (o["id"] as? JsonPrimitive)?.content ?: continue
            out += NearbyPlace(
                osmId = "$type/$id",
                name = name,
                brand = tag("brand"),
                brandWikidata = tag("brand:wikidata"),
                website = website(tag("website") ?: tag("contact:website") ?: tag("url")),
                cuisine = tag("cuisine"),
                amenity = amenity,
                lat = lat,
                lon = lon,
                distanceM = d,
            )
        }
        return out.sortedBy { it.distanceM }
    }

    /** Keeps the nearest place of each chain, so ten branches of one burger chain are one row. Order is unchanged. */
    fun dedupeByBrand(places: List<NearbyPlace>): List<NearbyPlace> {
        val seen = HashSet<String>()
        return places.filter { seen.add(it.chainKey) }
    }

    /** An https URL from a tag value: adds a missing scheme, upgrades http, and rejects anything that is not a web address. */
    fun website(raw: String?): String? {
        var v = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        v = v.substringBefore(';').trim()
        v = when {
            v.startsWith("https://", true) -> v
            v.startsWith("http://", true) -> "https://" + v.substring(7)
            v.contains("://") -> return null
            v.contains('.') && !v.contains(' ') -> "https://$v"
            else -> return null
        }
        return v
    }

    fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dp = p2 - p1
        val dl = Math.toRadians(lon2 - lon1)
        val a = sin(dp / 2).pow(2) + cos(p1) * cos(p2) * sin(dl / 2).pow(2)
        return 2 * r * asin(sqrt(a))
    }
}
