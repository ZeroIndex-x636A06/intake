package com.zeroindex.intake.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.zeroindex.intake.domain.hydration.DrinkOverride
import com.zeroindex.intake.domain.hydration.DrinkOverrides
import com.zeroindex.intake.domain.hydration.DrinkType
import com.zeroindex.intake.domain.hydration.SweatMeasurement
import com.zeroindex.intake.domain.hydration.WeatherDay
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.hydrationStore: DataStore<Preferences> by preferencesDataStore(name = "intake_hydration")

/** How the weather location is chosen. */
enum class WeatherLocationMode { APPROXIMATE, CITY }

/** The day's forecast, kept so the app asks once a day. [date] is the phone's local date as ISO text. */
data class WeatherCache(val date: String, val lat: Double, val lon: Double, val day: WeatherDay, val fetchedAt: Long)

/**
 * Hydration v2 settings, in their own small store so they stay out of [SettingsRepository].
 *
 * - Weather is **off** until the user turns it on. [lat] and [lon] are always already rounded to one decimal.
 * - [drinkOverrides] are the user's edits to factors, caffeine and strength (Advanced).
 * - [sweat] is the weigh-in test and [useMeasuredSweat] says whether it replaces the default rates.
 */
data class HydrationSettings(
    val weatherEnabled: Boolean = false,
    val locationMode: WeatherLocationMode = WeatherLocationMode.APPROXIMATE,
    val placeName: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val weatherCache: WeatherCache? = null,
    val lastWeatherAttemptAt: Long = 0,
    val drinkOverrides: Map<DrinkType, DrinkOverride> = emptyMap(),
    val sweat: SweatMeasurement? = null,
    val useMeasuredSweat: Boolean = false,
) {
    val hasLocation: Boolean get() = lat != null && lon != null
    val measuredSweatLitresPerHour: Double? get() = if (useMeasuredSweat) sweat?.litresPerHour else null
}

@Singleton
class HydrationPrefs @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val weatherEnabled = booleanPreferencesKey("weather_enabled")
        val locationMode = stringPreferencesKey("weather_location_mode")
        val place = stringPreferencesKey("weather_place")
        val lat = doublePreferencesKey("weather_lat")
        val lon = doublePreferencesKey("weather_lon")
        val cacheDate = stringPreferencesKey("weather_cache_date")
        val cacheLat = doublePreferencesKey("weather_cache_lat")
        val cacheLon = doublePreferencesKey("weather_cache_lon")
        val cacheTemp = doublePreferencesKey("weather_cache_temp_max_c")
        val cacheHumidity = doublePreferencesKey("weather_cache_humidity_pct")
        val cacheAt = longPreferencesKey("weather_cache_fetched_at")
        val lastAttempt = longPreferencesKey("weather_last_attempt_at")
        val overrides = stringPreferencesKey("drink_overrides")
        val sweatPre = doublePreferencesKey("sweat_pre_kg")
        val sweatPost = doublePreferencesKey("sweat_post_kg")
        val sweatFluid = doublePreferencesKey("sweat_fluid_ml")
        val sweatMinutes = doublePreferencesKey("sweat_minutes")
        val sweatUse = booleanPreferencesKey("sweat_use_measured")
    }

    val settings: Flow<HydrationSettings> = context.hydrationStore.data.map { p ->
        val lat = p[Keys.lat]
        val lon = p[Keys.lon]
        val cacheDate = p[Keys.cacheDate]
        val cacheTemp = p[Keys.cacheTemp]
        val cacheLat = p[Keys.cacheLat]
        val cacheLon = p[Keys.cacheLon]
        HydrationSettings(
            weatherEnabled = p[Keys.weatherEnabled] ?: false,
            locationMode = p[Keys.locationMode]?.let { runCatching { WeatherLocationMode.valueOf(it) }.getOrNull() }
                ?: WeatherLocationMode.APPROXIMATE,
            placeName = p[Keys.place],
            lat = lat,
            lon = lon,
            weatherCache = if (cacheDate != null && cacheTemp != null && cacheLat != null && cacheLon != null) {
                WeatherCache(cacheDate, cacheLat, cacheLon, WeatherDay(cacheTemp, p[Keys.cacheHumidity]), p[Keys.cacheAt] ?: 0)
            } else {
                null
            },
            lastWeatherAttemptAt = p[Keys.lastAttempt] ?: 0,
            drinkOverrides = DrinkOverrides.decode(p[Keys.overrides]),
            sweat = run {
                val pre = p[Keys.sweatPre]
                val post = p[Keys.sweatPost]
                val minutes = p[Keys.sweatMinutes]
                if (pre != null && post != null && minutes != null) SweatMeasurement(pre, post, p[Keys.sweatFluid] ?: 0.0, minutes) else null
            },
            useMeasuredSweat = p[Keys.sweatUse] ?: false,
        )
    }

    suspend fun current(): HydrationSettings = settings.first()

    suspend fun setWeatherEnabled(enabled: Boolean) {
        context.hydrationStore.edit { it[Keys.weatherEnabled] = enabled }
    }

    /** Switching mode keeps nothing from the other one: the old coordinates and forecast are dropped. */
    suspend fun setLocationMode(mode: WeatherLocationMode) {
        context.hydrationStore.edit {
            it[Keys.locationMode] = mode.name
            clearLocation(it)
        }
    }

    /** [lat] and [lon] must already be rounded; the caller uses [com.zeroindex.intake.domain.hydration.CoarseCoordinates]. */
    suspend fun setLocation(lat: Double, lon: Double, placeName: String?) {
        context.hydrationStore.edit {
            it[Keys.lat] = lat
            it[Keys.lon] = lon
            if (placeName == null) it.remove(Keys.place) else it[Keys.place] = placeName
            it.remove(Keys.cacheDate)
            it[Keys.lastAttempt] = 0
        }
    }

    suspend fun clearLocation() {
        context.hydrationStore.edit { clearLocation(it) }
    }

    private fun clearLocation(p: androidx.datastore.preferences.core.MutablePreferences) {
        p.remove(Keys.lat)
        p.remove(Keys.lon)
        p.remove(Keys.place)
        p.remove(Keys.cacheDate)
        p.remove(Keys.cacheLat)
        p.remove(Keys.cacheLon)
        p.remove(Keys.cacheTemp)
        p.remove(Keys.cacheHumidity)
        p.remove(Keys.cacheAt)
    }

    suspend fun saveWeather(cache: WeatherCache) {
        context.hydrationStore.edit {
            it[Keys.cacheDate] = cache.date
            it[Keys.cacheLat] = cache.lat
            it[Keys.cacheLon] = cache.lon
            it[Keys.cacheTemp] = cache.day.tempMaxC
            if (cache.day.humidityPct == null) it.remove(Keys.cacheHumidity) else it[Keys.cacheHumidity] = cache.day.humidityPct
            it[Keys.cacheAt] = cache.fetchedAt
        }
    }

    suspend fun markWeatherAttempt(at: Long) {
        context.hydrationStore.edit { it[Keys.lastAttempt] = at }
    }

    suspend fun setDrinkOverride(type: DrinkType, override: DrinkOverride?) {
        context.hydrationStore.edit {
            val map = DrinkOverrides.decode(it[Keys.overrides]).toMutableMap()
            if (override == null || override.isEmpty) map.remove(type) else map[type] = override
            val text = DrinkOverrides.encode(map)
            if (text.isEmpty()) it.remove(Keys.overrides) else it[Keys.overrides] = text
        }
    }

    suspend fun setSweat(measurement: SweatMeasurement?, use: Boolean) {
        context.hydrationStore.edit {
            if (measurement == null) {
                it.remove(Keys.sweatPre)
                it.remove(Keys.sweatPost)
                it.remove(Keys.sweatFluid)
                it.remove(Keys.sweatMinutes)
            } else {
                it[Keys.sweatPre] = measurement.preKg
                it[Keys.sweatPost] = measurement.postKg
                it[Keys.sweatFluid] = measurement.fluidMl
                it[Keys.sweatMinutes] = measurement.minutes
            }
            it[Keys.sweatUse] = use && measurement?.litresPerHour != null
        }
    }

    suspend fun setUseMeasuredSweat(use: Boolean) {
        context.hydrationStore.edit { it[Keys.sweatUse] = use }
    }

    suspend fun rawSnapshot(): Map<String, Any?> = context.hydrationStore.rawSnapshot()

    suspend fun restorePortable(values: Map<String, com.zeroindex.intake.domain.backup.PortableSettings.Value>) =
        context.hydrationStore.restore(values)
}
