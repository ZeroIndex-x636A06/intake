package com.zeroindex.intake.wear

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.zeroindex.intake.data.repository.TrainingRepository
import com.zeroindex.intake.data.settings.SettingsRepository
import com.zeroindex.intake.domain.time.Clock
import com.zeroindex.intake.wearprotocol.ActiveWorkout
import com.zeroindex.intake.wearprotocol.WearProtocol
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.suspendCancellableCoroutine

internal suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}

/**
 * Amendment §2: keeps the watch's copy of the session in progress current.
 * Whenever a session starts, a set is logged (here or on the watch) or the
 * session ends, the one DataItem at [WearProtocol.PATH_ACTIVE] is replaced or
 * removed. Phones without Google Play services, or with no watch, just log
 * a line - nothing else in the app depends on this.
 */
@Singleton
class WearBridge @Inject constructor(
    @ApplicationContext private val context: Context,
    private val training: TrainingRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val data by lazy { Wearable.getDataClient(context) }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun start() {
        training.observeInProgress()
            .flatMapLatest { session ->
                if (session == null) {
                    flowOf(null)
                } else {
                    combine(training.observeSets(session.id), settings.settings.map { it.weightUnit.label }.distinctUntilChanged()) { sets, unit ->
                        val prescriptions = training.prescriptionsFor(session.programDayId, excludeSessionId = session.id)
                        val title = session.programDayId?.let { training.day(it)?.name } ?: "Workout"
                        WatchWorkoutMapper.build(session.id, title, prescriptions, sets, unit, clock.nowMillis())
                    }
                }
            }
            .onEach(::publish)
            .launchIn(scope)
    }

    private suspend fun publish(workout: ActiveWorkout?) {
        runCatching {
            if (workout == null) {
                data.deleteDataItems(Uri.parse("wear://*${WearProtocol.PATH_ACTIVE}")).await()
            } else {
                val request = PutDataMapRequest.create(WearProtocol.PATH_ACTIVE).apply {
                    dataMap.putString(WearProtocol.KEY_JSON, WearProtocol.encode(workout))
                }.asPutDataRequest().setUrgent()
                data.putDataItem(request).await()
            }
        }.onFailure { Log.i(TAG, "No watch link: ${it.javaClass.simpleName}") }
    }

    private companion object { const val TAG = "WearBridge" }
}
