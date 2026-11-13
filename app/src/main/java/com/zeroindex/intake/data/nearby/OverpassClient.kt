package com.zeroindex.intake.data.nearby

import com.zeroindex.intake.domain.nearby.NearbyPlace
import com.zeroindex.intake.domain.nearby.OverpassParser
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

/** The map search could not be done; [message] is shown as is. The AI nearby search may be offered instead. */
class NearbyMapException(message: String) : Exception(message)

/**
 * Finds restaurants around a point with OpenStreetMap's Overpass API: one request per search, no key, no account.
 * What leaves the phone is the rounded coordinates (about a kilometre), the radius and the User-Agent below.
 * Nothing about the person, their allergens or their targets is sent.
 */
@Singleton
class OverpassClient @Inject constructor(private val http: OkHttpClient) {

    companion object {
        const val ENDPOINT = "https://overpass-api.de/api/interpreter"
        const val USER_AGENT = "Intake/1.0 (personal nutrition app; one map request per tap)"
    }

    private val patient: OkHttpClient by lazy {
        http.newBuilder().readTimeout(40, java.util.concurrent.TimeUnit.SECONDS).callTimeout(60, java.util.concurrent.TimeUnit.SECONDS).build()
    }

    suspend fun restaurantsAround(lat: Double, lon: Double, radiusM: Int): List<NearbyPlace> = withContext(Dispatchers.IO) {
        val query = OverpassParser.query(lat, lon, radiusM)
        val request = Request.Builder().url(ENDPOINT)
            .header("User-Agent", USER_AGENT)
            .post(FormBody.Builder().add("data", query).build())
            .build()
        val body = try {
            patient.newCall(request).execute().use { r ->
                when {
                    r.code == 429 || r.code == 504 -> throw NearbyMapException("The free map service is busy. Try again in a minute.")
                    !r.isSuccessful -> throw NearbyMapException("The map service answered with an error (${r.code}).")
                    else -> r.body.string()
                }
            }
        } catch (e: IOException) {
            throw NearbyMapException("Could not reach the map service.")
        }
        OverpassParser.parse(body, lat, lon, radiusM)
    }
}
