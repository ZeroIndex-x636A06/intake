package com.zeroindex.intake.data.weather

import com.zeroindex.intake.data.remote.Connectivity
import com.zeroindex.intake.data.remote.openmeteo.GeoPlace
import com.zeroindex.intake.data.remote.openmeteo.OpenMeteo
import com.zeroindex.intake.data.settings.HydrationPrefs
import com.zeroindex.intake.data.settings.HydrationSettings
import com.zeroindex.intake.data.settings.WeatherCache
import com.zeroindex.intake.data.settings.WeatherLocationMode
import com.zeroindex.intake.domain.hydration.CoarseCoordinates
import com.zeroindex.intake.domain.hydration.WeatherDay
import com.zeroindex.intake.domain.hydration.WeatherStatus
import com.zeroindex.intake.domain.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Opt-in weather for the water target (docs/HYDRATION.md, docs/PRIVACY.md).
 *
 * The simpler of the two options: the forecast is fetched when Today opens and the cached one is not from
 * today. No WorkManager job, no background location. At most one forecast request a day (plus a retry after 30
 * minutes if the phone was offline), and it fails quietly: no network means no adjustment and the card says so.
 */
@Singleton
class WeatherRepository @Inject constructor(
    private val prefs: HydrationPrefs,
    private val http: OkHttpClient,
    private val location: CoarseLocationProvider,
    private val connectivity: Connectivity,
    private val clock: Clock,
) {
    fun status(date: LocalDate): Flow<WeatherStatus> = prefs.settings.map { statusOf(it, date) }

    internal fun statusOf(s: HydrationSettings, date: LocalDate): WeatherStatus = when {
        !s.weatherEnabled -> WeatherStatus.Off
        !s.hasLocation -> WeatherStatus.NeedsLocation
        else -> {
            val c = s.weatherCache
            if (c != null && c.date == date.toString() && c.lat == s.lat && c.lon == s.lon) {
                WeatherStatus.Ready(c.day, s.placeName)
            } else {
                WeatherStatus.Unavailable
            }
        }
    }

    /** Today-open hook. Safe to call as often as you like. */
    suspend fun refreshIfStale() {
        val s = prefs.current()
        if (!s.weatherEnabled) return
        val today = clock.today().toString()
        if (s.weatherCache?.date == today && s.weatherCache.lat == s.lat && s.weatherCache.lon == s.lon) return
        val now = clock.nowMillis()
        if (now - s.lastWeatherAttemptAt < RETRY_AFTER_MS) return

        var lat = s.lat
        var lon = s.lon
        if (s.locationMode == WeatherLocationMode.APPROXIMATE) {
            // One approximate fix a day, only if the permission is already granted.
            val fix = location.currentFix()
            if (fix != null) {
                lat = fix.lat
                lon = fix.lon
                prefs.setLocation(fix.lat, fix.lon, null)
            }
        }
        if (lat == null || lon == null) return
        if (!connectivity.isOnline()) return
        prefs.markWeatherAttempt(now)
        val day = fetchForecast(lat, lon) ?: return
        prefs.saveWeather(WeatherCache(today, lat, lon, day, now))
    }

    /** Looks a typed city or ZIP code up. Returns no places on any failure. */
    suspend fun geocode(query: String): List<GeoPlace> {
        if (query.isBlank() || !connectivity.isOnline()) return emptyList()
        val body = get(OpenMeteo.geocodingUrl(query)) ?: return emptyList()
        return runCatching { OpenMeteo.parseGeocoding(body, CoarseCoordinates::round) }.getOrDefault(emptyList())
    }

    private suspend fun fetchForecast(lat: Double, lon: Double): WeatherDay? {
        val body = get(OpenMeteo.forecastUrl(lat, lon)) ?: return null
        return runCatching { OpenMeteo.parseForecast(body) }.getOrNull()
    }

    private suspend fun get(url: HttpUrl): String? = withContext(Dispatchers.IO) {
        runCatching {
            http.newCall(Request.Builder().url(url).get().build()).execute().use { r ->
                if (r.isSuccessful) r.body?.string() else null
            }
        }.getOrNull()
    }

    private companion object {
        const val RETRY_AFTER_MS = 30 * 60 * 1000L
    }
}
