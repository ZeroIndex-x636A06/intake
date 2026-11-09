package com.zeroindex.intake.data.weather

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.zeroindex.intake.domain.hydration.CoarseCoordinates
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/** Approximate coordinates, rounded to one decimal (about 10 km) right here, before anything else sees them. */
data class ApproxFix(val lat: Double, val lon: Double)

/**
 * The same pattern Plan B uses: the coarse permission only, never the precise one. The weather feature asks for
 * it from Settings, and takes a fix at most once a day. Nothing is read in the background.
 */
@Singleton
class CoarseLocationProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission") // checked by hasPermission() first
    suspend fun currentFix(): ApproxFix? {
        if (!hasPermission()) return null
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val provider = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.FUSED_PROVIDER, LocationManager.GPS_PROVIDER)
            .firstOrNull { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        val fix: Location? = provider?.let {
            withTimeoutOrNull(15_000) {
                suspendCancellableCoroutine { cont ->
                    LocationManagerCompat.getCurrentLocation(
                        lm,
                        it,
                        null as android.os.CancellationSignal?,
                        ContextCompat.getMainExecutor(context),
                        androidx.core.util.Consumer<Location?> { loc -> if (cont.isActive) cont.resume(loc) },
                    )
                }
            }
        } ?: runCatching { lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER) }.getOrNull()
        return fix?.let { ApproxFix(CoarseCoordinates.round(it.latitude), CoarseCoordinates.round(it.longitude)) }
    }
}
