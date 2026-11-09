package com.zeroindex.intake.data.remote.openmeteo

import com.zeroindex.intake.domain.hydration.WeatherDay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import okhttp3.HttpUrl

/** A place the geocoder found, with coordinates already rounded to one decimal. */
data class GeoPlace(val label: String, val lat: Double, val lon: Double)

/**
 * Open-Meteo (https://open-meteo.com), free for non-commercial use with no key, data under CC BY 4.0.
 * Only two requests exist and both carry nothing but a place name or coordinates rounded to ~10 km:
 *
 * - `GET https://geocoding-api.open-meteo.com/v1/search?name=<city or ZIP>&count=5&language=en&format=json`
 * - `GET https://api.open-meteo.com/v1/forecast?latitude=<1 dp>&longitude=<1 dp>
 *        &daily=temperature_2m_max,relative_humidity_2m_mean&timezone=auto&forecast_days=1`
 */
object OpenMeteo {
    const val GEOCODING_HOST = "geocoding-api.open-meteo.com"
    const val FORECAST_HOST = "api.open-meteo.com"

    fun geocodingUrl(name: String): HttpUrl = HttpUrl.Builder()
        .scheme("https").host(GEOCODING_HOST).addPathSegments("v1/search")
        .addQueryParameter("name", name.trim())
        .addQueryParameter("count", "5")
        .addQueryParameter("language", "en")
        .addQueryParameter("format", "json")
        .build()

    /** The coordinates must already be rounded by the caller; this never rounds or logs them. */
    fun forecastUrl(lat: Double, lon: Double): HttpUrl = HttpUrl.Builder()
        .scheme("https").host(FORECAST_HOST).addPathSegments("v1/forecast")
        .addQueryParameter("latitude", lat.toString())
        .addQueryParameter("longitude", lon.toString())
        .addEncodedQueryParameter("daily", "temperature_2m_max,relative_humidity_2m_mean")
        .addQueryParameter("timezone", "auto")
        .addQueryParameter("forecast_days", "1")
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    /** Places in the order the service ranked them. Coordinates are rounded here, before they are ever stored. */
    fun parseGeocoding(body: String, round: (Double) -> Double): List<GeoPlace> {
        val results = (json.parseToJsonElement(body) as? JsonObject)?.get("results") as? JsonArray ?: return emptyList()
        return results.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val lat = o.number("latitude") ?: return@mapNotNull null
            val lon = o.number("longitude") ?: return@mapNotNull null
            val name = o.text("name") ?: return@mapNotNull null
            val label = listOfNotNull(name, o.text("admin1")?.takeIf { it != name }, o.text("country")).joinToString(", ")
            GeoPlace(label, round(lat), round(lon))
        }
    }

    /** Today's high and mean humidity, or null if the response has no usable temperature. */
    fun parseForecast(body: String): WeatherDay? {
        val daily = (json.parseToJsonElement(body) as? JsonObject)?.get("daily") as? JsonObject ?: return null
        val temp = daily.firstNumber("temperature_2m_max") ?: return null
        return WeatherDay(temp, daily.firstNumber("relative_humidity_2m_mean"))
    }

    private fun JsonObject.number(key: String): Double? = (this[key] as? JsonPrimitive)?.doubleOrNull
    private fun JsonObject.text(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
    private fun JsonObject.firstNumber(key: String): Double? =
        ((this[key] as? JsonArray)?.firstOrNull() as? JsonPrimitive)?.doubleOrNull
}
