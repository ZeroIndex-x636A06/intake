package com.zeroindex.intake.data.remote.openmeteo

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.domain.hydration.CoarseCoordinates
import com.zeroindex.intake.domain.hydration.WeatherDay
import org.junit.Test

class OpenMeteoTest {

    @Test
    fun `forecast url carries only rounded coordinates and the two daily fields`() {
        val url = OpenMeteo.forecastUrl(30.3, -97.7).toString()
        assertThat(url).isEqualTo(
            "https://api.open-meteo.com/v1/forecast?latitude=30.3&longitude=-97.7" +
                "&daily=temperature_2m_max,relative_humidity_2m_mean&timezone=auto&forecast_days=1",
        )
    }

    @Test
    fun `geocoding url carries only the typed text`() {
        val url = OpenMeteo.geocodingUrl("  New York ").toString()
        assertThat(url).isEqualTo(
            "https://geocoding-api.open-meteo.com/v1/search?name=New%20York&count=5&language=en&format=json",
        )
        assertThat(OpenMeteo.geocodingUrl("78701").toString()).contains("name=78701")
    }

    @Test
    fun `parses a real forecast response`() {
        val body = """{"latitude":30.295988,"longitude":-97.69159,"generationtime_ms":0.07,"utc_offset_seconds":-21600,
            "timezone":"America/Chicago","timezone_abbreviation":"GMT-6","elevation":189.0,
            "daily_units":{"time":"iso8601","temperature_2m_max":"°C","relative_humidity_2m_mean":"%"},
            "daily":{"time":["2026-11-08"],"temperature_2m_max":[16.6],"relative_humidity_2m_mean":[94]}}"""
        assertThat(OpenMeteo.parseForecast(body)).isEqualTo(WeatherDay(16.6, 94.0))
    }

    @Test
    fun `humidity may be missing and a missing temperature is no forecast`() {
        assertThat(OpenMeteo.parseForecast("""{"daily":{"temperature_2m_max":[31.2],"relative_humidity_2m_mean":[null]}}"""))
            .isEqualTo(WeatherDay(31.2, null))
        assertThat(OpenMeteo.parseForecast("""{"daily":{"temperature_2m_max":[null]}}""")).isNull()
        assertThat(OpenMeteo.parseForecast("""{"error":true,"reason":"nope"}""")).isNull()
    }

    @Test
    fun `geocoding results are rounded before they leave the parser`() {
        val body = """{"results":[
            {"id":4671654,"name":"Austin","latitude":30.26715,"longitude":-97.74306,"country":"United States","admin1":"Texas"},
            {"id":1,"name":"Austin","latitude":43.6666,"longitude":-92.9746,"country":"United States","admin1":"Minnesota"},
            {"name":"No coordinates"}],"generationtime_ms":0.5}"""
        val places = OpenMeteo.parseGeocoding(body, CoarseCoordinates::round)
        assertThat(places).hasSize(2)
        assertThat(places[0]).isEqualTo(GeoPlace("Austin, Texas, United States", 30.3, -97.7))
        assertThat(places[1].lat).isEqualTo(43.7)
        assertThat(places[1].lon).isEqualTo(-93.0)
    }

    @Test
    fun `no results is an empty list, not an error`() {
        assertThat(OpenMeteo.parseGeocoding("""{"generationtime_ms":0.3}""", CoarseCoordinates::round)).isEmpty()
    }
}
